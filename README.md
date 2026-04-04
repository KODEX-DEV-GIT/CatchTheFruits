# Catch The Fruits

A small reflex game built with Kotlin for Android. Catch the fruit that appears randomly and reach the highest score in 10 seconds.

![Gameplay](game.gif)

## Overview

- Single-screen arcade-style game
- 3x3 fruit grid with random visibility every 500 ms
- 10-second countdown game loop
- Score tracking and replay dialog

## Modernized Tech Stack

- Kotlin (Android)
- Android Gradle Plugin 8.5.0
- Gradle 8.7
- Data Binding
- Material 3 theme
- ConstraintLayout + GridLayout

## Project Structure

- `app/src/main/java/com/halil/ozel/catchthefruits/MainActivity.kt` - game loop, score logic, timer, replay dialog
- `app/src/main/res/layout/activity_main.xml` - UI with Data Binding
- `app/src/main/res/values/strings.xml` - localized UI texts and formatted strings
- `app/src/main/res/values/styles.xml` - Material 3 app theme

## How the Game Works

1. Game starts with score `0` and time `10`.
2. Every 500 ms, all fruits hide and one random fruit is shown.
3. Tapping visible fruit increases score.
4. At the end of 10 seconds, game stops and replay dialog appears.

## Build and Run

### Requirements

- Android Studio (recent stable version)
- JDK 17
- Android SDK 34

### Commands

```zsh
cd "/Users/halilozel1903/AndroidStudioProjects/CatchTheFruits"
./gradlew assembleDebug
```

APK output:

- `app/build/outputs/apk/debug/`

## Key Components

- `CountDownTimer` for countdown
- `Handler` + `Runnable` for fruit switching cycle
- `AlertDialog` for restart flow
- Data Binding click handlers for scoring

## Notes

- The project is intentionally simple and beginner-friendly.
- You can swap fruit assets in `app/src/main/res/drawable/`.
- You can tune difficulty by changing game constants in `MainActivity.kt`.

## License

MIT License

Copyright (c) 2023 Halil OZEL
