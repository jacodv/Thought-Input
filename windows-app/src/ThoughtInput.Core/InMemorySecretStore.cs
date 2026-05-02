using System.Collections.Concurrent;

namespace ThoughtInput.Core;

public sealed class InMemorySecretStore : ISecretStore
{
    private readonly ConcurrentDictionary<string, string> _store = new();

    public void SaveString(string account, string value) => _store[account] = value;

    public string? LoadString(string account) => _store.TryGetValue(account, out var v) ? v : null;

    public void Delete(string account) => _store.TryRemove(account, out _);
}
