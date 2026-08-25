# LocoGym

**v0.6.0-dev** — No account. No cloud. No nonsense.

LocoGym is a deliberately small, private Android workout tracker. It stores reusable workouts, imports them from JSON, records complete or partial sessions, provides a configurable rest timer, and summarizes local training progress.

## Run it

1. Open the `LocoGym` folder in Android Studio.
2. Let Android Studio sync the project.
3. Select an emulator or Android device running Android 8.0 (API 26) or newer.
4. Click **Run**.

## Verify this milestone

1. Complete a workout, including trying a partial workout, and open **History**.
2. Tap **Export**, save both CSV and JSON, and verify the files through Android's save location.
3. Tap **Clear history**, choose **Export first** once, then confirm deletion; workout templates must remain.
4. Open **Progress** and verify this month's volume, workouts, sets, training days, weekly chart, personal records, and exercise progress.

The generated illustrations are bundled locally and require no network access. They are identification aids, not coaching or medical instructions.

## Privacy baseline

- No account or backend
- No remote analytics or cloud sync
- No `INTERNET` permission
- Vibration permission used only for timer alerts
- Android backups disabled
- Workout data stored in the app's private on-device SQLite database (`locogym.db`)
- JSON import and history export use Android's system file pickers
- No broad storage permission

Uninstalling the app removes its local data.

## Scope

This milestone adds CSV/JSON history export, confirmed history cleanup, and on-device progress analysis. Training volume is completed-set weight × reps. Partial workouts contribute only completed sets. Personal records use the heaviest completed set for each exercise, with repetitions as the tie-breaker.
