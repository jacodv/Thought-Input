# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Thought Input is an ultra-fast thought capture tool (<2 seconds). Two native apps (macOS + Android) share an API contract but have no code in common. There is no backend in this repo — both apps POST to a user-configured endpoint.

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

### Key Patterns
- Both apps implement identical offline-first retry: failed submissions are persisted locally and retried later using `idempotency_key` for deduplication
- Both apps use the same `CapturePayload` JSON shape with snake_case field names over the wire
- macOS uses `@MainActor` and Swift concurrency (`async/await`); Android uses coroutines
- The capture UI is designed as a minimal overlay (Spotlight-style on macOS, dialog activity on Android) — not a full app window
