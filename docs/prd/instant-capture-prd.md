# Instant Capture MVP — Product Requirements

> Capture a thought in under two seconds, from anywhere, without making the user "open an app."

See the full PRD in the project issue/task description. This file is a quick reference.

## Core Flow
1. User triggers from anywhere (hotkey on macOS, Quick Settings tile on Android)
2. Compact capture surface appears instantly with cursor focused
3. User types or dictates
4. Enter/Save submits text to configured API
5. UI closes immediately with subtle feedback

## Functional Requirements
- FR-1: At least one from-anywhere trigger per platform
- FR-2: Minimal capture surface with free-text field
- FR-3: Auto-focused text field
- FR-4: Keyboard and touch submission
- FR-5: Voice dictation into same field
- FR-6: Configurable API endpoint
- FR-7: Lightweight submission feedback
- FR-8: Network failure tolerance (retry/queue)
- FR-9: Capture logging for debugging
- FR-10: Settings screen for configuration

## Success Metrics
- Trigger to focused input: < 700ms (warm)
- Trigger to completed capture: < 2s for short note
- Capture completion rate: ≥ 95%
