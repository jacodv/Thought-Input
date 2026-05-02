using System.Text.Json;
using FluentAssertions;
using ThoughtInput.Core;
using Xunit;

namespace ThoughtInput.Tests;

public class SettingsBackupTests : IDisposable
{
    private readonly string _tempDir;

    public SettingsBackupTests()
    {
        _tempDir = Path.Combine(Path.GetTempPath(), "thoughtinput-tests-" + Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(_tempDir);
    }

    public void Dispose()
    {
        try { Directory.Delete(_tempDir, recursive: true); } catch { }
    }

    [Fact]
    public void Export_Then_Import_PreservesDestinationsAndSecrets()
    {
        var secretsA = new InMemorySecretStore();
        var apiKeyRef = KeychainRef.Create();
        secretsA.SaveString(apiKeyRef.Account, "super-secret");

        var storeA = new DestinationStore(Path.Combine(_tempDir, "a", "destinations.json"), secretsA);
        storeA.Add(new Destination
        {
            Name = "Supa",
            Type = new SupabaseDestinationType(new SupabaseConfig(
                ProjectURL: "https://x.supabase.co",
                TableName: "thoughts",
                ApiKeyRef: apiKeyRef)),
        });

        var bytes = SettingsBackupService.Export(storeA, secretsA);

        var secretsB = new InMemorySecretStore();
        var storeB = new DestinationStore(Path.Combine(_tempDir, "b", "destinations.json"), secretsB);
        SettingsBackupService.Import(bytes, storeB, secretsB);

        storeB.Destinations.Should().HaveCount(1);
        var imported = storeB.Destinations.Single();
        imported.Name.Should().Be("Supa");
        imported.IsActive.Should().BeTrue(); // first destination is auto-activated
        var supa = imported.Type.Should().BeOfType<SupabaseDestinationType>().Subject;
        supa.Config.ProjectURL.Should().Be("https://x.supabase.co");
        secretsB.LoadString(supa.Config.ApiKeyRef.Account).Should().Be("super-secret");
    }

    [Fact]
    public void Export_HasIso8601Timestamp_AndPrettyPrinted()
    {
        var secrets = new InMemorySecretStore();
        var store = new DestinationStore(Path.Combine(_tempDir, "destinations.json"), secrets);
        store.Add(new Destination
        {
            Name = "n",
            Type = new RestNoAuthDestinationType(new RestNoAuthConfig("https://x")),
        });

        var bytes = SettingsBackupService.Export(store, secrets);
        var text = System.Text.Encoding.UTF8.GetString(bytes);

        text.Should().Contain("\n"); // pretty-printed (indented) implies newlines
        text.Should().MatchRegex("\"exportedAt\":\\s*\"\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}");

        // Top-level fields must appear sorted: destinations, exportedAt, schemaVersion, sourcePlatform
        var dIdx = text.IndexOf("\"destinations\"", StringComparison.Ordinal);
        var eIdx = text.IndexOf("\"exportedAt\"", StringComparison.Ordinal);
        var schIdx = text.IndexOf("\"schemaVersion\"", StringComparison.Ordinal);
        var spIdx = text.IndexOf("\"sourcePlatform\"", StringComparison.Ordinal);
        dIdx.Should().BeLessThan(eIdx);
        eIdx.Should().BeLessThan(schIdx);
        schIdx.Should().BeLessThan(spIdx);
    }

    [Fact]
    public void Import_RejectsNewerSchemaVersion()
    {
        var bogus = """
        {
          "schemaVersion": 999,
          "exportedAt": "2026-01-01T00:00:00Z",
          "sourcePlatform": "macos",
          "destinations": []
        }
        """u8.ToArray();

        var secrets = new InMemorySecretStore();
        var store = new DestinationStore(Path.Combine(_tempDir, "destinations.json"), secrets);

        Action act = () => SettingsBackupService.Import(bogus, store, secrets);
        act.Should().Throw<SettingsBackupException>()
            .WithMessage("*Unsupported backup schema version (999)*");
    }

    [Fact]
    public void Import_FromMacOSStyleBackup_Succeeds()
    {
        // Hand-rolled JSON in the exact shape macOS produces (Swift Codable + sortedKeys + iso8601).
        const string macosBackup = """
        {
          "destinations" : [
            {
              "destination" : {
                "id" : "11111111-1111-1111-1111-111111111111",
                "isActive" : true,
                "name" : "Mac Supabase",
                "type" : {
                  "supabase" : {
                    "_0" : {
                      "apiKeyRef" : {
                        "account" : "AAAAAAAA-AAAA-AAAA-AAAA-AAAAAAAAAAAA"
                      },
                      "projectURL" : "https://mac.supabase.co",
                      "tableName" : "thoughts"
                    }
                  }
                }
              },
              "secrets" : {
                "AAAAAAAA-AAAA-AAAA-AAAA-AAAAAAAAAAAA" : "the-key"
              }
            }
          ],
          "exportedAt" : "2026-05-02T12:34:56Z",
          "schemaVersion" : 1,
          "sourcePlatform" : "macos"
        }
        """;
        var bytes = System.Text.Encoding.UTF8.GetBytes(macosBackup);

        var secrets = new InMemorySecretStore();
        var store = new DestinationStore(Path.Combine(_tempDir, "destinations.json"), secrets);
        SettingsBackupService.Import(bytes, store, secrets);

        store.Destinations.Should().HaveCount(1);
        var d = store.Destinations.Single();
        d.Name.Should().Be("Mac Supabase");
        d.IsActive.Should().BeTrue();
        var supa = d.Type.Should().BeOfType<SupabaseDestinationType>().Subject;
        supa.Config.ProjectURL.Should().Be("https://mac.supabase.co");
        supa.Config.TableName.Should().Be("thoughts");
        supa.Config.ApiKeyRef.Account.Should().Be("AAAAAAAA-AAAA-AAAA-AAAA-AAAAAAAAAAAA");
        secrets.LoadString("AAAAAAAA-AAAA-AAAA-AAAA-AAAAAAAAAAAA").Should().Be("the-key");
    }
}
