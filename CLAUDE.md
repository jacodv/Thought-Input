# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Thought Input is an ultra-fast thought capture tool (<2 seconds). Three native apps (macOS, Android, Windows) share an API contract but have no code in common. There is no backend in this repo — all apps POST to a user-configured endpoint.

## Build & Test

### macOS (Swift 6.2, macOS 15+)
```bash
cd macos-app
swift build
swift test
```
Or open in Xcode 26+ and build.

### Android (Kotlin, SDK 35, JDK 17)
```bash
cd android-app
./gradlew assembleDebug
./gradlew test           # unit tests
```

### Windows (.NET 10, WPF)
```bash
cd windows-app
dotnet build ThoughtInput.sln -c Release
dotnet test src/ThoughtInput.Tests/ThoughtInput.Tests.csproj -c Release
```
The WPF executable targets `net10.0-windows` (compiles on macOS/Linux via `EnableWindowsTargeting`, only runs on Windows). Core library + tests are pure `net10.0`.

## Architecture

### Shared API Contract
`contracts/api-schema/capture-payload.json` — JSON Schema defining the POST payload both apps send. Fields: `text`, `timestamp`, `source_platform`, `client_version`, `capture_method` (typed/voice), `idempotency_key`. Both apps must conform to this schema; change the contract first when modifying the payload shape.

### macOS App (`macos-app/`)
- **Swift Package** (Package.swift) — single executable target `ThoughtInput` + test target
- **App layer** (`App/`): `ThoughtInputApp` entry point, `AppDelegate` manages menu bar icon, global shortcut, and the floating capture panel
- **Core layer** (`Core/`): `CaptureService` (network submission + retry), `CapturePayload` (model matching JSON contract with `CodingKeys` for snake_case), `PendingCaptureStore` (file-based offline queue in Application Support), `SpeechRecognizer` (dictation), `KeyboardShortcut` / `GlobalShortcutManager` (Cmd+Shift+Space default)
- **UI layer** (`UI/`): `CaptureView` (SwiftUI, Spotlight-style search bar), `CapturePanel` (NSPanel subclass for floating window), `CaptureViewController`, `SettingsView`
- Settings stored in `UserDefaults` (key: `apiEndpoint`)
- Failed captures are queued as JSON files in `~/Library/Application Support/ThoughtInput/pending/` and retried on app launch

### Android App (`android-app/`)
- **Single module** (`app/`) with Jetpack Compose + Material 3
- **Data layer** (`data/`): `CaptureRepository` (coordinates submission + retry), `ApiClient` (HTTP POST), `CapturePayload` (model), `PendingCaptureStore` (SharedPreferences-based offline queue)
- **UI layer** (`ui/`): `CaptureScreen` (Compose), `SettingsScreen`, theme files
- **Entry points**: `CaptureActivity` (dialog-style, launched from tile/widget), `MainActivity` (full settings)
- **System integrations**: `CaptureTileService` (Quick Settings tile), `CaptureWidgetProvider` (home screen widget)
- **Speech**: `SpeechRecognizerManager`
- Settings stored in `SharedPreferences` (file: `thought_input_settings`)

### Windows App (`windows-app/`)
- **Three projects** in `ThoughtInput.sln`:
  - `src/ThoughtInput.Core/` (`net10.0`) — pure model/service code: `CapturePayload`, `Destination` + tagged-union `DestinationType`, `CaptureService`, `DestinationSender`, `PendingCaptureStore`, `DestinationStore`, `OAuthTokenManager`, `SettingsBackup`, `ISecretStore` abstraction. `DestinationJsonConverters.cs` reproduces Swift's Codable shape for backup-file interop with the macOS app
  - `src/ThoughtInput/` (`net10.0-windows`) — WPF executable: `App.xaml.cs` composition root, `CaptureWindow` (frameless overlay), `SettingsWindow` (destinations/import-export/about), `DestinationEditor`, `GlobalHotkey` (RegisterHotKey P/Invoke), `WindowsCredentialSecretStore` (Credential Manager via P/Invoke), `SingleInstance` (Mutex + named pipe), `AutoLaunch` (HKCU Run key)
  - `src/ThoughtInput.Tests/` (`net10.0`) — xUnit + FluentAssertions; runs on any OS
- **Settings**: JSON in `%AppData%\ThoughtInput\` (`destinations.json`, `settings.json`); secrets in Windows Credential Manager under target name `ThoughtInput:<account-uuid>`
- **Offline queue**: JSON files under `%LocalAppData%\ThoughtInput\pending\`, named by `idempotency_key`
- **Distribution**: per-user Inno Setup installer (`installer/ThoughtInput.iss`) — installs to `%LocalAppData%\ThoughtInput`, registers HKCU Run for auto-launch with `--hidden`
- **Default hotkey**: `Ctrl+Shift+Space`

### Key Patterns
- All three apps implement identical offline-first retry: failed submissions are persisted locally and retried later using `idempotency_key` for deduplication
- All three apps use the same `CapturePayload` JSON shape with snake_case field names over the wire
- macOS uses `@MainActor` and Swift concurrency (`async/await`); Android uses coroutines; Windows uses `Task` / `async/await` with WPF dispatcher marshalling
- The capture UI is designed as a minimal overlay (Spotlight-style on macOS/Windows, dialog activity on Android) — not a full app window
- Settings backup format is interchangeable between macOS and Windows: Swift Codable's enum-with-associated-values shape (`{"<case>": {"_0": {…}}}`) plus upper-case UUIDs is reproduced exactly by `DestinationJsonConverters` on the Windows side
