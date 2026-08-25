# LocoGym

**v0.4.2-dev** — No account. No cloud. No nonsense.

LocoGym is a deliberately small, private Android workout tracker. It stores reusable workouts, imports them from JSON, records complete or partial sessions, and provides a configurable in-app rest timer.

## Run it

1. Open the `LocoGym` folder in Android Studio.
2. Let Android Studio sync the project.
3. Select an emulator or Android device running Android 8.0 (API 26) or newer.
4. Click **Run**.

## Verify this milestone

1. Start a short rest timer and confirm its sound and vibration stop automatically.
2. Start a workout and complete only some planned sets.
3. Choose **Finish workout**, review the partial-workout warning, and select **Finish and save**.
4. Confirm History labels that session **Partial** and records only the completed sets.
5. Complete every set in another workout and confirm History labels it **Completed**.

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

This milestone supports reusable workouts, offline JSON import, actual set recording, configurable foreground rest alerts, complete and partial session snapshots, and history summaries. Background timer notifications, active-session recovery, session-detail screens, charts, export, and backup are not included yet.
