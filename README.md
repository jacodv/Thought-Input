# Brain Input — Instant Capture

Ultra-fast note capture from anywhere. Summon a tiny text input, type or dictate, submit. Under two seconds.

## Architecture

Two native apps sharing an API contract:

- **macOS** — Swift + SwiftUI/AppKit, global hotkey trigger, menu bar icon, floating capture panel
- **Android** — Kotlin + Jetpack Compose, Quick Settings tile, dialog-style capture activity

## Repository Structure

```
docs/prd/                    # Product requirements
contracts/api-schema/        # Shared API payload schema
macos-app/                   # Native macOS app (Swift)
android-app/                 # Native Android app (Kotlin)
```

## macOS App

### Requirements
- Xcode 26+ (Swift 6.2)
- macOS 15.0+

### Build
Open `macos-app/BrainInput.xcodeproj` in Xcode and build, or:
```bash
cd macos-app
swift build
```

### Default Shortcut
`Cmd+Shift+Space` — configurable in Settings.

## Android App

### Requirements
- Android Studio Panda 2+
- JDK 17
- Android SDK 35

### Build
```bash
cd android-app
./gradlew assembleDebug
```

## API Contract

The capture payload schema is defined in `contracts/api-schema/capture-payload.json`. Both apps POST this JSON to a user-configured endpoint.

## License

Proprietary — all rights reserved.
