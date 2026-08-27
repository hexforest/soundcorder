# Soundcorder

**Sound memories, not transcripts.** An audio-first sound journal — record, import and export sounds freely, organised into projects and journeys. Notes with sound, no limits.

---

## Why Soundcorder

Most "voice note" apps are racing to turn your voice into text. Soundcorder does the opposite: the **sound itself** is the point. A street in a city you visited, a baby's laugh, an idea hummed at 2am, a field recording — captured, kept, and grouped into projects you can revisit. No transcription bloat, no artificial limits, no walled garden. Record it, keep it, move it in and out freely.

It's deliberately simpler than a sampler like Koala, and free in a way the transcription crowd isn't: open source, and yours to build, fork, and keep.

## What it does

- **Record** sounds straight from your device.
- **Import and export** audio freely — no format lock-in, no caps.
- **Organise** recordings into projects and journeys, with tabs to move between them.
- **Revisit** your sound memories whenever you like.

## Status

Early development. **Android first**, iOS to follow. Expect rough edges — issues and ideas welcome.

## Building

Native Android — Kotlin + Jetpack Compose, a single `app` module. You need JDK 17 and the Android SDK (`compileSdk` 36); Android Studio bundles both.

```bash
git clone https://github.com/<your-username>/soundcorder.git
cd soundcorder
echo "sdk.dir=/path/to/Android/Sdk" > local.properties   # or open the project in Android Studio

./gradlew assembleDebug     # APK -> app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug      # build + install on a connected device or emulator
```

Recordings are stored as AAC (`.m4a`) in the app's private storage, with a plain `library.json` index — no database, no account, no cloud.

## Contributing

Contributions are welcome — see [CONTRIBUTING.md](CONTRIBUTING.md).

## Licence

Soundcorder is free software, released under the **GNU General Public License v3.0**. See [LICENSE](LICENSE) for the full text.

You're free to use, study, share, and modify it. If you distribute a modified version, it has to stay open under the same licence.
