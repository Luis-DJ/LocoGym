# LocoGym

**v0.4.1-dev** — No account. No cloud. No nonsense.

LocoGym is a deliberately small, private Android workout tracker. It stores reusable workouts, imports them from JSON, records completed sessions, and provides a configurable in-app rest timer.

## Run it

1. Open the `LocoGym` folder in Android Studio.
2. Let Android Studio sync the project.
3. Select an emulator or Android device running Android 8.0 (API 26) or newer.
4. Click **Run**.

For command-line builds, use `gradlew.bat assembleDebug` on Windows or `./gradlew assembleDebug` on macOS/Linux with a compatible JDK and Android SDK 35.

## Verify this milestone

1. From **My Workouts**, open **Timer alert** and choose Sound, Vibration, or Both.
2. Use **Test alert** and confirm the selected alert is noticeable.
3. Start a workout, edit a numeric field, then mark a later set **Done**.
4. Confirm the keyboard closes, the list does not jump back to the earlier field, and the rest timer starts.
5. Confirm **Finish workout** remains above Android's navigation bar and keyboard.
6. Complete every set, finish the workout, and confirm it remains in **History** after restarting.

The timer runs while LocoGym is open. Unfinished active sessions do not yet survive force-closing the app.

## Privacy baseline

- No account or backend
- No analytics or cloud sync
- No `INTERNET` permission
- Vibration permission used only for timer alerts
- Android backups disabled
- Workout data stored in the app's private on-device SQLite database (`locogym.db`)
- JSON import uses Android's system file picker

Uninstalling the app removes its local data.

## Scope

This milestone supports reusable workouts, offline JSON import, actual set recording, configurable foreground rest alerts, completed-session snapshots, and history summaries. Background timer notifications, active-session recovery, session-detail screens, charts, export, and backup are not included yet.
