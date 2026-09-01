# LocoGym

**v0.9.0-beta01** — No account. No cloud. No nonsense.

LocoGym is a deliberately small, private Android workout tracker. It stores reusable workouts, imports them from JSON, records complete or partial sessions, provides a configurable rest timer, and summarizes local training progress.

## Run it

1. Open the `LocoGym` folder in Android Studio.
2. Let Android Studio sync the project.
3. Select an emulator or Android device running Android 8.0 (API 26) or newer.
4. Click **Run**.

## Safe beta distribution

Use Google Play **Internal testing** first, then move to **Closed testing** when the initial testers are comfortable. Upload a signed Android App Bundle (`.aab`); do not distribute debug APKs as beta releases.

1. In Android Studio select **Build > Generate Signed Bundle / APK**.
2. Select **Android App Bundle** and create a dedicated upload key when prompted.
3. Keep the `.jks` file and its passwords outside this repository and back them up securely.
4. Do not place the key or a password file inside the Dropbox project. Enter the password in Android Studio only when producing a signed release.
5. Build the release bundle and upload it to the Play Console internal-testing track.
6. Add testers by email and send them the Play opt-in link.

Google Play App Signing is recommended: Google protects the app-signing key while the locally held upload key can be reset if it is lost or compromised. Automated release builds can instead provide `LOCOGYM_KEYSTORE_FILE`, `LOCOGYM_KEYSTORE_PASSWORD`, `LOCOGYM_KEY_ALIAS`, and `LOCOGYM_KEY_PASSWORD` as private environment variables.

## Verify this milestone

1. Install as an update and open **Exercises**.
2. Confirm **Bench Press**, **Assisted Chin-Ups**, and **Horizontal Cable Woodchop** appear with their new robot illustrations.
3. Confirm their defaults are respectively `3 × 12 @ 50 kg`, `3 × 12 @ 25 kg assistance`, and `3 × 12 @ 20 kg`.
4. Edit a workout and add any of them from the searchable Exercise Library.
5. Confirm existing workouts, history, and active-workout recovery remain unchanged.

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

This beta milestone adds a hardened release configuration: explicit backup exclusions, blocked cleartext traffic, release shrinking, protected signing settings, Git key exclusions, and an adaptive app icon. LocoGym remains offline and keeps workout data only in its private on-device database.
