using System.Text.Json;
using System.Text.Json.Serialization;

namespace ThoughtInput.Core;

public sealed record SettingsBackup(
    [property: JsonPropertyName("schemaVersion")] int SchemaVersion,
    [property: JsonPropertyName("exportedAt")] DateTimeOffset ExportedAt,
    [property: JsonPropertyName("sourcePlatform")] string SourcePlatform,
    [property: JsonPropertyName("destinations")] IReadOnlyList<BackupDestination> Destinations)
{
    public const int CurrentSchemaVersion = 1;
}

public sealed record BackupDestination(
    [property: JsonPropertyName("destination")] Destination Destination,
    [property: JsonPropertyName("secrets")] IReadOnlyDictionary<string, string> Secrets);

public sealed class SettingsBackupException(string message) : Exception(message);

public static class SettingsBackupService
{
    public static byte[] Export(DestinationStore store, ISecretStore secrets)
    {
        var backups = store.Destinations.Select(d => new BackupDestination(
            Destination: d,
            Secrets: d.KeychainRefs
                .Select(r => (r.Account, Value: secrets.LoadString(r.Account)))
                .Where(t => t.Value is not null)
                .ToDictionary(t => t.Account, t => t.Value!))).ToList();

        var backup = new SettingsBackup(
            SchemaVersion: SettingsBackup.CurrentSchemaVersion,
            ExportedAt: DateTimeOffset.UtcNow,
            SourcePlatform: CapturePayload.SourcePlatformWindows,
            Destinations: backups);

        return JsonSerializer.SerializeToUtf8Bytes(backup, DestinationJson.BackupOptions);
    }

    public static void Import(byte[] data, DestinationStore store, ISecretStore secrets)
    {
        SettingsBackup? backup;
        try
        {
            backup = JsonSerializer.Deserialize<SettingsBackup>(data, DestinationJson.DefaultOptions);
        }
        catch (Exception ex)
        {
            throw new SettingsBackupException($"Couldn't read the backup file: {ex.Message}");
        }
        if (backup is null)
            throw new SettingsBackupException("Backup file was empty");

        if (backup.SchemaVersion != SettingsBackup.CurrentSchemaVersion)
            throw new SettingsBackupException(
                $"Unsupported backup schema version ({backup.SchemaVersion}). This file was made by a newer version of the app.");

        Guid? activeId = null;
        var newDestinations = new List<Destination>();
        foreach (var entry in backup.Destinations)
        {
            foreach (var (account, value) in entry.Secrets)
                secrets.SaveString(account, value);

            var clone = new Destination
            {
                Id = entry.Destination.Id,
                Name = entry.Destination.Name,
                Type = entry.Destination.Type,
                IsActive = false,
            };
            if (entry.Destination.IsActive) activeId = entry.Destination.Id;
            newDestinations.Add(clone);
        }

        store.ReplaceAll(newDestinations);

        if (activeId is { } id)
        {
            var active = store.Destinations.FirstOrDefault(d => d.Id == id);
            if (active is not null) store.SetActive(active);
        }
    }
}
