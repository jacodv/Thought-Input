using System.Net.Http;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media.Imaging;
using H.NotifyIcon;
using ThoughtInput.Core;
using ThoughtInput.UI;

namespace ThoughtInput;

public partial class App : Application
{
    // Services live for the app lifetime.
    private SingleInstance? _singleInstance;
    private GlobalHotkey? _hotkey;
    private TaskbarIcon? _trayIcon;
    private CaptureWindow? _captureWindow;
    private SettingsWindow? _settingsWindow;

    private AppSettings _settings = null!;
    private DestinationStore _destinationStore = null!;
    private PendingCaptureStore _pendingStore = null!;
    private CaptureService _captureService = null!;
    private DestinationSender _sender = null!;
    private OAuthTokenManager _tokenManager = null!;
    private ISecretStore _secretStore = null!;
    private HttpClient _httpClient = null!;

    protected override void OnStartup(StartupEventArgs e)
    {
        base.OnStartup(e);

        // Single-instance: hand off to the existing instance and exit.
        _singleInstance = new SingleInstance();
        if (!_singleInstance.IsFirstInstance)
        {
            _singleInstance.NotifyExisting();
            _singleInstance.Dispose();
            Shutdown();
            return;
        }
        _singleInstance.ShowRequested += () => Dispatcher.BeginInvoke(ToggleCapture);
        _singleInstance.StartServer();

        AppPaths.EnsureCreated();
        _settings = AppSettings.Load();

        _httpClient = new HttpClient();
        _secretStore = new WindowsCredentialSecretStore();
        _destinationStore = new DestinationStore(AppPaths.DestinationsFile, _secretStore);
        _pendingStore = new PendingCaptureStore(AppPaths.PendingDir);
        _tokenManager = new OAuthTokenManager(_httpClient, _secretStore);
        _sender = new DestinationSender(_httpClient, _secretStore, _tokenManager);
        _captureService = new CaptureService(_destinationStore, _pendingStore, _sender);

        InstallTray();
        InstallHotkey();

        // Retry any captures that were queued offline.
        _ = _captureService.RetryPendingAsync();

        var hidden = e.Args.Any(a => string.Equals(a, "--hidden", StringComparison.OrdinalIgnoreCase));
        var firstRun = _destinationStore.Destinations.Count == 0;
        if (firstRun && !hidden)
        {
            ShowSettings();
        }
    }

    private void InstallHotkey()
    {
        _hotkey = new GlobalHotkey();
        _hotkey.Triggered += ToggleCapture;
        if (!_hotkey.Register(_settings.Hotkey))
        {
            MessageBox.Show(
                $"Couldn't register the hotkey '{_settings.Hotkey}'. Another app may already use it.\n" +
                "Open Settings to choose a different shortcut.",
                "Thought Input", MessageBoxButton.OK, MessageBoxImage.Warning);
        }
    }

    private void InstallTray()
    {
        _trayIcon = new TaskbarIcon
        {
            ToolTipText = "Thought Input",
            IconSource = new BitmapImage(new Uri("pack://application:,,,/Resources/AppIcon.png", UriKind.Absolute)),
        };
        _trayIcon.TrayLeftMouseDown += (_, _) => ToggleCapture();

        var menu = new ContextMenu();
        var captureItem = new MenuItem { Header = "Capture" };
        captureItem.Click += (_, _) => ShowCapture();
        var settingsItem = new MenuItem { Header = "Settings…" };
        settingsItem.Click += (_, _) => ShowSettings();
        var quitItem = new MenuItem { Header = "Quit" };
        quitItem.Click += (_, _) => Shutdown();

        menu.Items.Add(captureItem);
        menu.Items.Add(settingsItem);
        menu.Items.Add(new Separator());
        menu.Items.Add(quitItem);
        _trayIcon.ContextMenu = menu;
    }

    private void ToggleCapture()
    {
        if (_captureWindow?.IsVisible == true)
        {
            _captureWindow.HideCapture();
        }
        else
        {
            ShowCapture();
        }
    }

    private void ShowCapture()
    {
        _captureWindow ??= new CaptureWindow(_captureService);
        _captureWindow.ShowCapture();
    }

    private void ShowSettings()
    {
        if (_settingsWindow is { IsVisible: true })
        {
            _settingsWindow.Activate();
            return;
        }
        _settingsWindow = new SettingsWindow(
            _settings,
            _destinationStore,
            _secretStore,
            _sender,
            ApplyHotkeyChange);
        _settingsWindow.Closed += (_, _) => _settingsWindow = null;
        _settingsWindow.Show();
    }

    private void ApplyHotkeyChange(string newHotkey)
    {
        _settings.Hotkey = newHotkey;
        _settings.Save();
        _hotkey?.Register(newHotkey);
    }

    protected override void OnExit(ExitEventArgs e)
    {
        _hotkey?.Dispose();
        _trayIcon?.Dispose();
        _captureWindow?.Close();
        _settingsWindow?.Close();
        _singleInstance?.Dispose();
        _httpClient?.Dispose();
        base.OnExit(e);
    }
}
