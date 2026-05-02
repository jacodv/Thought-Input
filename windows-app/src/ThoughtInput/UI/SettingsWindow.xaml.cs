using System.Diagnostics;
using System.Globalization;
using System.IO;
using System.Reflection;
using System.Windows;
using System.Windows.Data;
using System.Windows.Navigation;
using Microsoft.Win32;
using ThoughtInput.Core;

namespace ThoughtInput.UI;

public partial class SettingsWindow : Window
{
    private readonly AppSettings _settings;
    private readonly DestinationStore _destinationStore;
    private readonly ISecretStore _secretStore;
    private readonly DestinationSender _sender;
    private readonly Action<string> _onHotkeyChanged;

    public SettingsWindow(
        AppSettings settings,
        DestinationStore destinationStore,
        ISecretStore secretStore,
        DestinationSender sender,
        Action<string> onHotkeyChanged)
    {
        _settings = settings;
        _destinationStore = destinationStore;
        _secretStore = secretStore;
        _sender = sender;
        _onHotkeyChanged = onHotkeyChanged;
        Resources.Add("TypeDescriptorConverter", new DestinationTypeDescriptor());
        InitializeComponent();
        RefreshDestinations();

        VersionLabel.Text = $"Version {Assembly.GetEntryAssembly()?.GetName().Version?.ToString(3) ?? "0.1.0"}";
        RuntimeLabel.Text = $".NET {Environment.Version}";
        HotkeyBox.Text = _settings.Hotkey;
        LaunchOnLoginToggle.IsChecked = AutoLaunch.IsEnabled();
    }

    private void RefreshDestinations()
    {
        DestinationsList.ItemsSource = _destinationStore.Destinations;
    }

    private void OnAddClick(object sender, RoutedEventArgs e)
    {
        var editor = new DestinationEditor(null) { Owner = this };
        if (editor.ShowDialog() == true && editor.Result is { } d)
        {
            SaveSecrets(editor);
            _destinationStore.Add(d);
            RefreshDestinations();
        }
    }

    private void OnEditClick(object sender, RoutedEventArgs e)
    {
        if (DestinationsList.SelectedItem is not Destination existing) return;
        var editor = new DestinationEditor(existing) { Owner = this };
        editor.PrefillSecrets(_secretStore);
        if (editor.ShowDialog() == true && editor.Result is { } d)
        {
            SaveSecrets(editor);
            _destinationStore.Update(d);
            RefreshDestinations();
        }
    }

    private void OnDeleteClick(object sender, RoutedEventArgs e)
    {
        if (DestinationsList.SelectedItem is not Destination existing) return;
        var ok = MessageBox.Show(this, $"Delete destination '{existing.Name}'?", "Confirm",
            MessageBoxButton.OKCancel, MessageBoxImage.Question);
        if (ok != MessageBoxResult.OK) return;
        _destinationStore.Delete(existing);
        RefreshDestinations();
    }

    private async void OnTestClick(object sender, RoutedEventArgs e)
    {
        if (DestinationsList.SelectedItem is not Destination dest) return;
        try
        {
            var result = await _sender.TestConnectionAsync(dest);
            var msg = result == ConnectionTestResult.TableMissing
                ? "Connection succeeded but the target table was not found."
                : "Connection OK.";
            MessageBox.Show(this, msg, "Test connection", MessageBoxButton.OK, MessageBoxImage.Information);
        }
        catch (Exception ex)
        {
            MessageBox.Show(this, ex.Message, "Test connection failed", MessageBoxButton.OK, MessageBoxImage.Warning);
        }
    }

    private void OnActiveChanged(object sender, RoutedEventArgs e)
    {
        if (sender is System.Windows.Controls.RadioButton rb && rb.DataContext is Destination d && d.IsActive)
        {
            _destinationStore.SetActive(d);
        }
    }

    private void OnExportClick(object sender, RoutedEventArgs e)
    {
        var dialog = new SaveFileDialog
        {
            Title = "Export settings",
            Filter = "Thought Input backup (*.json)|*.json",
            FileName = $"ThoughtInput-backup-{DateTime.Now:yyyy-MM-dd}.json",
        };
        if (dialog.ShowDialog(this) != true) return;
        try
        {
            var bytes = SettingsBackupService.Export(_destinationStore, _secretStore);
            File.WriteAllBytes(dialog.FileName, bytes);
            BackupStatus.Text = $"Exported to {dialog.FileName}";
        }
        catch (Exception ex)
        {
            BackupStatus.Text = $"Export failed: {ex.Message}";
        }
    }

    private void OnImportClick(object sender, RoutedEventArgs e)
    {
        var dialog = new OpenFileDialog
        {
            Title = "Import settings",
            Filter = "Thought Input backup (*.json)|*.json|All files (*.*)|*.*",
        };
        if (dialog.ShowDialog(this) != true) return;
        var confirm = MessageBox.Show(this,
            "Importing will replace your current destinations and secrets. Continue?",
            "Confirm import", MessageBoxButton.OKCancel, MessageBoxImage.Warning);
        if (confirm != MessageBoxResult.OK) return;

        try
        {
            var bytes = File.ReadAllBytes(dialog.FileName);
            SettingsBackupService.Import(bytes, _destinationStore, _secretStore);
            RefreshDestinations();
            BackupStatus.Text = "Import succeeded.";
        }
        catch (Exception ex)
        {
            BackupStatus.Text = $"Import failed: {ex.Message}";
        }
    }

    private void OnHotkeyApplyClick(object sender, RoutedEventArgs e)
    {
        var newHotkey = HotkeyBox.Text.Trim();
        _onHotkeyChanged(newHotkey);
    }

    private void OnLaunchToggleClick(object sender, RoutedEventArgs e)
    {
        if (LaunchOnLoginToggle.IsChecked == true)
        {
            try { AutoLaunch.Enable(); }
            catch (Exception ex) { MessageBox.Show(this, ex.Message, "Couldn't enable auto-launch", MessageBoxButton.OK, MessageBoxImage.Warning); }
        }
        else
        {
            try { AutoLaunch.Disable(); }
            catch (Exception ex) { MessageBox.Show(this, ex.Message, "Couldn't disable auto-launch", MessageBoxButton.OK, MessageBoxImage.Warning); }
        }
        _settings.LaunchOnLogin = LaunchOnLoginToggle.IsChecked == true;
        _settings.Save();
    }

    private void OnNavigate(object sender, RequestNavigateEventArgs e)
    {
        try
        {
            Process.Start(new ProcessStartInfo(e.Uri.AbsoluteUri) { UseShellExecute = true });
            e.Handled = true;
        }
        catch { }
    }

    private void SaveSecrets(DestinationEditor editor)
    {
        foreach (var (account, value) in editor.SecretsToSave)
        {
            if (string.IsNullOrEmpty(value))
                _secretStore.Delete(account);
            else
                _secretStore.SaveString(account, value);
        }
    }
}

internal sealed class DestinationTypeDescriptor : IValueConverter
{
    public object Convert(object value, Type targetType, object parameter, CultureInfo culture) => value switch
    {
        SupabaseDestinationType s => $"Supabase — {s.Config.ProjectURL}",
        RestNoAuthDestinationType r => $"REST (no auth) — {r.Config.EndpointURL}",
        RestApiKeyDestinationType r => $"REST (API key) — {r.Config.EndpointURL}",
        RestOAuthPasswordDestinationType r => $"REST (OAuth password) — {r.Config.EndpointURL}",
        RestOAuthClientCredentialsDestinationType r => $"REST (OAuth client creds) — {r.Config.EndpointURL}",
        _ => "(unknown)",
    };

    public object ConvertBack(object value, Type targetType, object parameter, CultureInfo culture)
        => throw new NotSupportedException();
}
