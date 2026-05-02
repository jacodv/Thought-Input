using System.Text.Json;

namespace ThoughtInput.Core;

public sealed class DestinationStore
{
    private readonly string _filePath;
    private readonly ISecretStore _secretStore;
    private readonly List<Destination> _destinations = [];
    private readonly Lock _gate = new();

    public DestinationStore(string filePath, ISecretStore secretStore)
    {
        _filePath = filePath;
        _secretStore = secretStore;
        Directory.CreateDirectory(Path.GetDirectoryName(_filePath)!);
        Load();
    }

    public IReadOnlyList<Destination> Destinations
    {
        get { lock (_gate) return _destinations.ToArray(); }
    }

    public Destination? ActiveDestination
    {
        get { lock (_gate) return _destinations.FirstOrDefault(d => d.IsActive); }
    }

    public void Add(Destination destination)
    {
        lock (_gate)
        {
            if (_destinations.Count == 0)
                destination.IsActive = true;
            _destinations.Add(destination);
            Save();
        }
    }

    public void Update(Destination destination)
    {
        lock (_gate)
        {
            var index = _destinations.FindIndex(d => d.Id == destination.Id);
            if (index < 0) return;
            _destinations[index] = destination;
            Save();
        }
    }

    public void Delete(Destination destination)
    {
        lock (_gate)
        {
            foreach (var r in destination.KeychainRefs)
                _secretStore.Delete(r.Account);
            _destinations.RemoveAll(d => d.Id == destination.Id);
            if (!_destinations.Any(d => d.IsActive) && _destinations.Count > 0)
                _destinations[0].IsActive = true;
            Save();
        }
    }

    public void SetActive(Destination destination)
    {
        lock (_gate)
        {
            foreach (var d in _destinations)
                d.IsActive = d.Id == destination.Id;
            Save();
        }
    }

    /// <summary>Replaces all destinations; used by SettingsBackup import.</summary>
    public void ReplaceAll(IEnumerable<Destination> destinations)
    {
        lock (_gate)
        {
            foreach (var d in _destinations.ToArray())
            {
                foreach (var r in d.KeychainRefs)
                    _secretStore.Delete(r.Account);
            }
            _destinations.Clear();
            _destinations.AddRange(destinations);
            Save();
        }
    }

    private void Load()
    {
        if (!File.Exists(_filePath)) return;
        try
        {
            var bytes = File.ReadAllBytes(_filePath);
            var loaded = JsonSerializer.Deserialize<List<Destination>>(bytes, DestinationJson.DefaultOptions);
            if (loaded is not null)
            {
                _destinations.Clear();
                _destinations.AddRange(loaded);
            }
        }
        catch
        {
            // Corrupt file — start fresh rather than crash. Existing file is left in place
            // so the user can recover it manually.
        }
    }

    private void Save()
    {
        var bytes = JsonSerializer.SerializeToUtf8Bytes(_destinations, DestinationJson.DefaultOptions);
        var tmp = _filePath + ".tmp";
        File.WriteAllBytes(tmp, bytes);
        File.Move(tmp, _filePath, overwrite: true);
    }
}
