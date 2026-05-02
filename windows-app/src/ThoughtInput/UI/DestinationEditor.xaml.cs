using System.Windows;
using System.Windows.Controls;
using ThoughtInput.Core;

namespace ThoughtInput.UI;

public partial class DestinationEditor : Window
{
    private readonly Destination? _existing;

    public Destination? Result { get; private set; }

    /// <summary>
    /// Account-id → secret-value pairs the caller should write to the secret store
    /// after a successful save. Empty value means "delete".
    /// </summary>
    public Dictionary<string, string> SecretsToSave { get; } = new();

    public DestinationEditor(Destination? existing)
    {
        _existing = existing;
        InitializeComponent();
        if (existing is not null)
        {
            NameBox.Text = existing.Name;
            SelectTypeFor(existing.Type);
            PopulateExisting(existing);
        }
        else
        {
            TypeCombo.SelectedIndex = 0;
        }
    }

    public void PrefillSecrets(ISecretStore secretStore)
    {
        if (_existing is null) return;
        switch (_existing.Type)
        {
            case SupabaseDestinationType s:
                SupabaseKey.Password = secretStore.LoadString(s.Config.ApiKeyRef.Account) ?? "";
                break;
            case RestApiKeyDestinationType r:
                RestApiKeyValue.Password = secretStore.LoadString(r.Config.ApiKeyRef.Account) ?? "";
                break;
            case RestOAuthPasswordDestinationType p:
                OAuthPwdUsername.Text = secretStore.LoadString(p.Config.UsernameRef.Account) ?? "";
                OAuthPwdPassword.Password = secretStore.LoadString(p.Config.PasswordRef.Account) ?? "";
                break;
            case RestOAuthClientCredentialsDestinationType c:
                OAuthClientId.Text = secretStore.LoadString(c.Config.ClientIDRef.Account) ?? "";
                OAuthClientSecret.Password = secretStore.LoadString(c.Config.ClientSecretRef.Account) ?? "";
                break;
        }
    }

    private void OnTypeChanged(object sender, SelectionChangedEventArgs e)
    {
        SupabasePanel.Visibility = Visibility.Collapsed;
        RestNoAuthPanel.Visibility = Visibility.Collapsed;
        RestApiKeyPanel.Visibility = Visibility.Collapsed;
        OAuthPasswordPanel.Visibility = Visibility.Collapsed;
        OAuthClientPanel.Visibility = Visibility.Collapsed;

        var tag = (TypeCombo.SelectedItem as ComboBoxItem)?.Tag as string;
        switch (tag)
        {
            case "supabase": SupabasePanel.Visibility = Visibility.Visible; break;
            case "restNoAuth": RestNoAuthPanel.Visibility = Visibility.Visible; break;
            case "restApiKey": RestApiKeyPanel.Visibility = Visibility.Visible; break;
            case "restOAuthPassword": OAuthPasswordPanel.Visibility = Visibility.Visible; break;
            case "restOAuthClientCredentials": OAuthClientPanel.Visibility = Visibility.Visible; break;
        }
    }

    private void SelectTypeFor(DestinationType type)
    {
        var tag = type.DiscriminatorKey;
        foreach (var item in TypeCombo.Items.OfType<ComboBoxItem>())
        {
            if ((string?)item.Tag == tag)
            {
                TypeCombo.SelectedItem = item;
                return;
            }
        }
    }

    private void PopulateExisting(Destination existing)
    {
        switch (existing.Type)
        {
            case SupabaseDestinationType s:
                SupabaseUrl.Text = s.Config.ProjectURL;
                SupabaseTable.Text = s.Config.TableName;
                break;
            case RestNoAuthDestinationType r:
                RestNoAuthUrl.Text = r.Config.EndpointURL;
                break;
            case RestApiKeyDestinationType r:
                RestApiKeyUrl.Text = r.Config.EndpointURL;
                RestApiKeyHeader.Text = r.Config.HeaderName;
                break;
            case RestOAuthPasswordDestinationType p:
                OAuthPwdEndpoint.Text = p.Config.EndpointURL;
                OAuthPwdTokenUrl.Text = p.Config.TokenURL;
                break;
            case RestOAuthClientCredentialsDestinationType c:
                OAuthClientEndpoint.Text = c.Config.EndpointURL;
                OAuthClientTokenUrl.Text = c.Config.TokenURL;
                break;
        }
    }

    private void OnCancelClick(object sender, RoutedEventArgs e)
    {
        DialogResult = false;
    }

    private void OnSaveClick(object sender, RoutedEventArgs e)
    {
        var name = NameBox.Text.Trim();
        if (string.IsNullOrEmpty(name))
        {
            MessageBox.Show(this, "Please enter a name.", "Required", MessageBoxButton.OK, MessageBoxImage.Information);
            return;
        }
        var tag = (TypeCombo.SelectedItem as ComboBoxItem)?.Tag as string;
        var (type, secrets) = BuildType(tag);
        if (type is null) return;

        Result = new Destination
        {
            Id = _existing?.Id ?? Guid.NewGuid(),
            IsActive = _existing?.IsActive ?? false,
            Name = name,
            Type = type,
        };
        foreach (var (k, v) in secrets) SecretsToSave[k] = v;
        DialogResult = true;
    }

    private (DestinationType? Type, Dictionary<string, string> Secrets) BuildType(string? tag)
    {
        var secrets = new Dictionary<string, string>();
        switch (tag)
        {
            case "supabase":
                {
                    var keyRef = (_existing?.Type as SupabaseDestinationType)?.Config.ApiKeyRef ?? KeychainRef.Create();
                    secrets[keyRef.Account] = SupabaseKey.Password;
                    return (new SupabaseDestinationType(new SupabaseConfig(
                        ProjectURL: SupabaseUrl.Text.Trim(),
                        TableName: SupabaseTable.Text.Trim(),
                        ApiKeyRef: keyRef)), secrets);
                }
            case "restNoAuth":
                return (new RestNoAuthDestinationType(new RestNoAuthConfig(RestNoAuthUrl.Text.Trim())), secrets);
            case "restApiKey":
                {
                    var keyRef = (_existing?.Type as RestApiKeyDestinationType)?.Config.ApiKeyRef ?? KeychainRef.Create();
                    secrets[keyRef.Account] = RestApiKeyValue.Password;
                    return (new RestApiKeyDestinationType(new RestApiKeyConfig(
                        EndpointURL: RestApiKeyUrl.Text.Trim(),
                        HeaderName: RestApiKeyHeader.Text.Trim(),
                        ApiKeyRef: keyRef)), secrets);
                }
            case "restOAuthPassword":
                {
                    var existingType = _existing?.Type as RestOAuthPasswordDestinationType;
                    var userRef = existingType?.Config.UsernameRef ?? KeychainRef.Create();
                    var pwdRef = existingType?.Config.PasswordRef ?? KeychainRef.Create();
                    secrets[userRef.Account] = OAuthPwdUsername.Text;
                    secrets[pwdRef.Account] = OAuthPwdPassword.Password;
                    return (new RestOAuthPasswordDestinationType(new RestOAuthPasswordConfig(
                        EndpointURL: OAuthPwdEndpoint.Text.Trim(),
                        TokenURL: OAuthPwdTokenUrl.Text.Trim(),
                        UsernameRef: userRef,
                        PasswordRef: pwdRef)), secrets);
                }
            case "restOAuthClientCredentials":
                {
                    var existingType = _existing?.Type as RestOAuthClientCredentialsDestinationType;
                    var idRef = existingType?.Config.ClientIDRef ?? KeychainRef.Create();
                    var secRef = existingType?.Config.ClientSecretRef ?? KeychainRef.Create();
                    secrets[idRef.Account] = OAuthClientId.Text;
                    secrets[secRef.Account] = OAuthClientSecret.Password;
                    return (new RestOAuthClientCredentialsDestinationType(new RestOAuthClientCredentialsConfig(
                        EndpointURL: OAuthClientEndpoint.Text.Trim(),
                        TokenURL: OAuthClientTokenUrl.Text.Trim(),
                        ClientIDRef: idRef,
                        ClientSecretRef: secRef)), secrets);
                }
            default:
                return (null, secrets);
        }
    }
}
