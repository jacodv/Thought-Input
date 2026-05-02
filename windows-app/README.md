# Thought Input — Windows

Native Windows app. Spotlight-style overlay summoned by `Ctrl+Shift+Space`, system-tray icon, settings UI for destinations and import/export. Mirrors the macOS app's behaviour and shares the API contract.

## Requirements

- Windows 10 or 11 (x64)
- [.NET 10 Desktop Runtime](https://dotnet.microsoft.com/download/dotnet/10.0) — installed automatically on most modern Windows machines via Windows Update
- For development: .NET 10 SDK

## Build & test

The Core library and tests are cross-platform — you can build them on macOS or Linux:

```bash
cd windows-app
dotnet build ThoughtInput.sln -c Release
dotnet test src/ThoughtInput.Tests/ThoughtInput.Tests.csproj -c Release
```

The WPF executable (`src/ThoughtInput/`) targets `net10.0-windows`. It compiles on non-Windows machines too (we set `EnableWindowsTargeting=true`) but only runs on Windows.

## Layout

```
windows-app/
  src/
    ThoughtInput.Core/   net10.0 library — payload model, services, JSON converters
    ThoughtInput/        net10.0-windows WPF executable (UI, tray, hotkey, secrets)
    ThoughtInput.Tests/  xUnit + FluentAssertions, runs on any OS
  installer/
    ThoughtInput.iss     Inno Setup script (per-user install, HKCU Run for auto-start)
    assets/              icon + wizard imagery (generated in CI from PNG/SVG sources)
```

## Architecture

- **App layer** (`App.xaml.cs`): single-instance guard (`Mutex` + named pipe), composition root that wires the Core services, system-tray icon (`H.NotifyIcon.Wpf`), and the global hotkey.
- **Core layer** (`ThoughtInput.Core`): `CaptureService` (POST + offline retry), `DestinationSender` (per-type HTTP), `OAuthTokenManager`, `DestinationStore` (JSON file under `%AppData%`), `PendingCaptureStore` (JSON files under `%LocalAppData%\pending`), `SettingsBackup` (Swift-Codable-compatible JSON for cross-platform import/export).
- **UI layer**: `CaptureWindow` (frameless, topmost, Win11 acrylic backdrop, hidden from Alt+Tab), `SettingsWindow` (destinations / import-export / about), `DestinationEditor` (one editor for all five destination types).
- **Secrets**: `WindowsCredentialSecretStore` writes generic credentials to Windows Credential Manager under target name `ThoughtInput:<account-uuid>`.
- **Auto-launch**: HKCU `Run` registry value with `--hidden` so the app starts to the tray on login.

## Default keyboard shortcut

`Ctrl+Shift+Space`, configurable in Settings → About → Capture shortcut.

## Distribution

CI builds an [Inno Setup](https://jrsoftware.org/isinfo.php) installer (`ThoughtInput-Windows-Setup.exe`) on every push to `main` and attaches it to the GitHub Release alongside the macOS `.dmg` and Android `.apk`.

The installer is per-user (no UAC), installs to `%LocalAppData%\ThoughtInput`, and can register a Run-key entry for auto-launch on login.
