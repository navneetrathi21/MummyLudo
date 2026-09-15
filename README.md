# Mummy Ludo

A portrait-first Android Ludo game built with Kotlin and Jetpack Compose. The app supports two to four active color seats with configurable human and bot ownership, deterministic rules testing, a scalable 15×15 board, animated dice, doubles, arrows, captures, safe squares, ranking, and a non-cheating bot that selects only from engine-validated legal moves.

## Run

1. Open the project in Android Studio with JDK 17.
2. Let Gradle use the installed Android SDK 36.
3. Run the `app` debug configuration on an Android 8.0+ device or emulator.

From the command line:

```bash
./gradlew testDebugUnitTest assembleDebug
```

The debug build includes **Finish game automatically**, an emulator QA control that completes a match through the same dice, legal-move generator, reducer, capture rules, and ranking logic used by interactive play. It is omitted from release behavior through `BuildConfig.DEBUG`.

## Play Store preparation

The app builds a Play-compatible Android App Bundle:

```bash
./gradlew testDebugUnitTest bundleRelease
```

Release signing is configured through environment variables so upload keys are never committed:

```bash
export MUMMY_LUDO_RELEASE_STORE_FILE=/absolute/path/to/upload-keystore.jks
export MUMMY_LUDO_RELEASE_STORE_PASSWORD=...
export MUMMY_LUDO_RELEASE_KEY_ALIAS=...
export MUMMY_LUDO_RELEASE_KEY_PASSWORD=...
./gradlew bundleRelease
```

The generated bundle is written to `app/build/outputs/bundle/release/app-release.aab`. Before production rollout, create the Play Console app entry, enroll in Play App Signing, upload through Internal testing first, and keep `versionCode` increasing for every uploaded bundle.

## Tests

- JVM tests cover entry, bonus rolls, three-six discard-and-reroll behavior, arrows, double movement, the capture matrix, and complete seeded matches.
- Compose emulator tests cover setup, mandatory bot presence, board launch, full-game completion, results, and close/discard behavior.
- The product and engineering specifications are in [`docs/`](docs/).

## Verified emulator matches

Five complete matches were run on an Android 36 emulator:

| Match | Configuration | Winner | Ranking |
|---|---|---|---|
| 1 | 1 human vs 1 bot | Red | Red, Yellow |
| 2 | 2 humans vs 2 bots | Yellow | Yellow, Red, Green, Blue |
| 3 | 1 human vs 3 bots | Red | Red, Green, Blue, Yellow |
| 4 | 3 humans vs 1 bot | Red | Red, Yellow, Green, Blue |
| 5 | 1 human vs 1 bot, colors reversed | Red | Red, Yellow |
