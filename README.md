# Thought Input — Instant Capture

<!--
  LLM Context: Thought Input is a cross-platform instant thought capture tool.
  It provides native macOS and Android apps that let users capture text or voice
  notes in under 2 seconds from anywhere on the device (global hotkey, Quick Settings
  tile, home screen widget). There is NO backend in this repository — both apps POST
  a JSON payload to one or more user-configured HTTP endpoints (Supabase, REST with
  API key, REST with OAuth, etc.). The apps share an API contract (JSON Schema) but
  zero code. Offline-first: failed submissions are queued locally and retried
  automatically with UUID-based idempotency for deduplication.
-->

Ultra-fast thought capture from anywhere. Summon a tiny input surface, type or dictate your thought, hit Enter. Under two seconds, then it's gone.

**No backend included** — both apps POST captured thoughts to your own endpoint(s). See the [Backend Setup Guide](docs/SETUP-GUIDE.md) for Supabase, REST, and OAuth configuration.

## Install

Download from the [latest release](https://github.com/jacodv/Thought-Input/releases/latest) and follow the steps for your OS.

### 🍎 macOS (15.0+)

1. Download [`ThoughtInput-macOS.dmg`](https://github.com/jacodv/Thought-Input/releases/latest/download/ThoughtInput-macOS.dmg).
2. Open the `.dmg` and drag `ThoughtInput.app` onto the Applications folder.
3. **First launch only** — macOS will say "Apple cannot check it for malicious software." Either right-click the app and choose **Open**, or run:
   ```bash
   xattr -dr com.apple.quarantine /Applications/ThoughtInput.app
   ```
4. Grant Accessibility (for the global `Cmd+Shift+Space` shortcut) and Microphone + Speech Recognition when prompted.

### 🤖 Android (10+)

1. Download [`ThoughtInput-Android.apk`](https://github.com/jacodv/Thought-Input/releases/latest/download/ThoughtInput-Android.apk).
2. Tap the file on your device. If prompted, allow **Install unknown apps** for your browser/file manager.
3. Grant Microphone permission when prompted.

> The APK is debug-signed (free distribution, no Play Store fees). If a future update fails with "signatures don't match," uninstall the existing app first, then install the new APK.

### 🪟 Windows

_Coming soon._

---

After installing, open the app, add a destination (Settings → Add Destination), and tap **Test Connection**. For Supabase, **Initialize Database** sets up the schema in one click.

## How It Works

1. **Trigger** — Global hotkey (macOS `Cmd+Shift+Space`) or Quick Settings tile / home screen widget (Android)
2. **Capture** — A minimal overlay appears with the cursor focused. Type or tap the mic to dictate.
3. **Submit** — Press Enter. The thought is POST-ed to all configured destinations.
4. **Done** — The overlay dismisses with subtle feedback. Total time: < 2 seconds.

Failed submissions are queued offline and retried automatically on next launch. Each capture includes a UUID `idempotency_key` so your backend can deduplicate safely.

## Repository Structure

```
contracts/api-schema/        # Shared JSON Schema for the capture payload
macos-app/                   # Native macOS app (Swift 6.2 / SwiftUI / AppKit)
android-app/                 # Native Android app (Kotlin / Jetpack Compose)
docs/prd/                    # Product requirements
docs/SETUP-GUIDE.md          # Backend setup guide (Supabase, REST, OAuth)
```

## API Contract

Both apps POST the same JSON payload defined in [`contracts/api-schema/capture-payload.json`](contracts/api-schema/capture-payload.json):

```json
{
  "text": "Buy milk on the way home",
  "timestamp": "2026-03-27T10:30:00Z",
  "source_platform": "macos",
  "client_version": "0.1.0",
  "capture_method": "typed",
  "idempotency_key": "550e8400-e29b-41d4-a716-446655440000",
  "device_name": "Jaco's MacBook Pro"
}
```

| Field | Type | Description |
|-------|------|-------------|
| `text` | string | The captured thought (never empty) |
| `timestamp` | ISO 8601 string | When the capture was initiated on the device |
| `source_platform` | `"macos"` \| `"android"` | Originating platform |
| `client_version` | semver string | App version (e.g. `"0.1.0"`) |
| `capture_method` | `"typed"` \| `"voice"` | How the text was entered |
| `idempotency_key` | UUID string | Unique per capture, use for server-side deduplication |
| `device_name` | string | Human-readable device name (e.g. `"Pixel 9"`) |

## Supported Destination Types

Each app can send captures to **multiple destinations simultaneously**. Configured in Settings:

| Type | Auth Method | Use Case |
|------|-------------|----------|
| **Supabase** | Anon/service-role key | Direct insert into a Supabase table |
| **REST (No Auth)** | None | Simple POST endpoint |
| **REST (API Key)** | Custom header (e.g. `X-API-Key`) | Key-authenticated endpoint |
| **REST (OAuth Password)** | OAuth 2.0 ROPC grant | Token-based with username/password |
| **REST (OAuth Client Credentials)** | OAuth 2.0 Client Credentials grant | Server-to-server token-based |

See the [Backend Setup Guide](docs/SETUP-GUIDE.md) for detailed configuration instructions for each type.

## macOS App

### Requirements
- Xcode 26+ (Swift 6.2)
- macOS 15.0+

### Build & Test
```bash
cd macos-app
swift build
swift test
```
Or open in Xcode and build.

### Default Shortcut
`Cmd+Shift+Space` — configurable in Settings.

### Architecture
- **App layer** — `AppDelegate` manages menu bar icon, global shortcut registration, and the floating capture panel
- **Core layer** — `CaptureService` (network + multi-destination dispatch), `PendingCaptureStore` (file-based offline queue in `~/Library/Application Support/ThoughtInput/pending/`), `SpeechRecognizer` (on-device dictation)
- **UI layer** — `CapturePanel` (NSPanel floating window), `CaptureView` (SwiftUI Spotlight-style input), `SettingsView` (destination management)

### Debug Mode
Launch with `--debug` flag to print detailed request/response logs to the console.

## Android App

### Requirements
- Android Studio Panda 2+
- JDK 17
- Android SDK 35

### Build & Test
```bash
cd android-app
./gradlew assembleDebug
./gradlew test
```

### Architecture
- **Data layer** — `CaptureRepository`, `ApiClient`, `PendingCaptureStore` (SharedPreferences-based offline queue)
- **UI layer** — `CaptureScreen` / `SettingsScreen` (Jetpack Compose + Material 3)
- **System integrations** — `CaptureTileService` (Quick Settings tile), `CaptureWidgetProvider` (home screen widget), `SpeechRecognizerManager`

## Offline-First Design

Both apps follow the same pattern:
1. Attempt to POST the capture to all configured destinations
2. On network failure, persist the payload as a JSON file (macOS) or SharedPreferences entry (Android)
3. On next app launch, retry all pending captures
4. The `idempotency_key` (UUID) ensures the backend can safely deduplicate retried submissions

## License

Proprietary — all rights reserved.
