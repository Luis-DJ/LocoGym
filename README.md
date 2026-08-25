# LocoGym

**v0.4.0-dev** — No account. No cloud. No nonsense.

LocoGym is a deliberately small, private Android workout tracker. It stores reusable workouts, imports them from JSON, records completed sessions, and provides an in-app rest timer.

## Run it

1. Open the `LocoGym` folder in Android Studio.
2. Let Android Studio sync the project.
3. Select an emulator or Android device running Android 8.0 (API 26) or newer.
4. Click **Run**.

For command-line builds, use `gradlew.bat assembleDebug` on Windows or `./gradlew assembleDebug` on macOS/Linux with a compatible JDK and Android SDK 35.

## Verify this milestone

1. Open **My Workouts** and select **Start workout** on an imported or manually created workout.
2. Adjust the actual weight or reps if needed and mark a set **Done**.
3. Confirm the configured rest countdown starts and produces an audible ping at zero.
4. Complete every planned set and choose **Finish workout**.
5. Confirm the workout appears in **History** and remains after restarting LocoGym.

The timer runs while LocoGym is open. Unfinished active sessions do not yet survive force-closing the app.

## Privacy baseline

- No account or backend
- No analytics or cloud sync
- No `INTERNET` permission
- Android backups disabled
- Workout data stored in the app's private on-device SQLite database (`locogym.db`)
- JSON import uses Android's system file picker

Uninstalling the app removes its local data.

## Scope

This milestone supports reusable workout plans, offline JSON import, actual set recording, an in-app rest timer, completed-session snapshots, and history summaries. Background timer notifications, active-session recovery, session-detail screens, charts, export, and backup are not included yet.
