# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What Soundcorder is

An audio-first "sound journal" — the recorded **sound itself** is the artifact, deliberately *not* a voice-to-text app. This framing drives most product decisions, so keep it in mind when implementing anything:

- **No transcription.** Do not add speech-to-text as a core feature. Sounds are kept and revisited as audio.
- **No lock-in, no caps.** Import and export audio freely, no format restrictions, no artificial limits on length or count. Treat any feature that would gate this as against the project's intent.
- **Organisation model:** recordings have one home **project**; **journeys** are ordered cross-project selections. Navigated via the bottom tabs.
- **Scope discipline:** intentionally simpler than a sampler like Koala. Prefer the minimal version of a feature.
- **Platform priority:** Android first, iOS to follow.

## Stack

Native Android, single-module:

- **Kotlin 2.2.20**, **Jetpack Compose** (Compose BOM 2026.01.01), Material 3.
- **AGP 8.13.1** on **Gradle 8.13**. `compileSdk`/`targetSdk` 36, `minSdk` 26.
- No database: library metadata is a single `library.json` (`kotlinx.serialization`) in `filesDir`; audio files sit in `filesDir/recordings/`.
- No cloud, no analytics, no extra services.

### Module layout (`app/src/main/java/com/soundcorder/app/`)

| Package | Holds |
| --- | --- |
| `data/` | `Model.kt` (serializable `Recording`/`Project`/`Journey`/`LibraryData`), `LibraryQueries.kt` (pure derivations), `AudioLibrary.kt` (repository: JSON persistence, file management, import/export) |
| `audio/` | `AudioRecorder.kt` (`MediaRecorder` → AAC/`.m4a`), `AudioPlayer.kt` (`MediaPlayer` + progress `StateFlow`) |
| `ui/` | `SoundcorderViewModel.kt` (ties library + recorder + player), `SoundcorderRoot.kt` (Scaffold, tabs, nav state, dialogs), `Format.kt` |
| `ui/screens/` | `LibraryScreens.kt` (Projects / Journeys lists), `CollectionScreen.kt` (project + journey detail bodies) |
| `ui/components/` | `RecordingRow`, `PlayerBar`, `RecordSheet`, `LevelMeter`, `Dialogs`, `EmptyState` |
| `ui/theme/` | `Color.kt`, `Theme.kt`, `Type.kt` |

Navigation is deliberately hand-rolled (tab index + optional open project/journey id in `SoundcorderRoot`) rather than a nav library — keep it that way unless the screen count grows well past this.

## Commands

`local.properties` with `sdk.dir` is required (not checked in). Use the Gradle wrapper.

```bash
./gradlew assembleDebug            # build the debug APK -> app/build/outputs/apk/debug/
./gradlew installDebug             # build + install on a connected device/emulator
./gradlew lintDebug                # Android lint -> app/build/reports/lint-results-debug.html
./gradlew testDebugUnitTest        # JVM unit tests (none yet)
./gradlew testDebugUnitTest --tests "com.soundcorder.app.SomeTest.someCase"   # single test
./gradlew check                    # lint + all tests
```

There is no CI yet. `./gradlew assembleDebug` and `./gradlew lintDebug` are the gate before a PR.

## Contributing conventions

- Branch from `main` using `feature/<slug>` or `fix/<slug>` (e.g. `feature/export-wav`, `fix/record-crash`).
- One concern per PR.
- All contributions are GPL-3.0 — any dependency or included code must be license-compatible (Apache-2.0 / MIT / BSD are fine; anything with a stricter or incompatible licence is not).
