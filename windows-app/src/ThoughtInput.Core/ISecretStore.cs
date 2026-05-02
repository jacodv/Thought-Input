namespace ThoughtInput.Core;

/// <summary>
/// Abstraction over the platform secret store. The WPF project supplies a
/// Windows Credential Manager implementation; tests use <see cref="InMemorySecretStore"/>.
/// The "account" string is the <see cref="KeychainRef.Account"/> value (a UUID).
/// </summary>
public interface ISecretStore
{
    void SaveString(string account, string value);
    string? LoadString(string account);
    void Delete(string account);
}
