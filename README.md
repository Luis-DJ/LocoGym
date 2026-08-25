# LocoGym

**v0.5.0-dev** — No account. No cloud. No nonsense.

LocoGym is a deliberately small, private Android workout tracker. It stores reusable workouts, imports them from JSON, records complete or partial sessions, and provides a configurable in-app rest timer.

## Run it

1. Open the `LocoGym` folder in Android Studio.
2. Let Android Studio sync the project.
3. Select an emulator or Android device running Android 8.0 (API 26) or newer.
4. Click **Run**.

## Verify this milestone

1. Open **My Workouts** and confirm each workout appears as a compact illustrated summary.
2. Tap a workout and review its description and complete exercise list.
3. Confirm the trial illustrations appear for shoulder press, leg press, and neutral-grip lat pulldown; other exercises use a placeholder.
4. Start and edit a workout from its detail page.
5. Confirm home, workout details, active workout, and workout editor controls remain above Android's navigation bar and keyboard.

The generated illustrations are bundled locally and require no network access. They are identification aids, not coaching or medical instructions.

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

This milestone adds compact workout summaries, a dedicated workout detail page, local generated cover art, and a trial set of exercise thumbnails. Remaining exercises intentionally use placeholders until the illustration style is validated.
