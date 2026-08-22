# MaoozOS Android

Android wrapper for MaoozOS 3.2.0.

## What is included

- The same offline MaoozOS web application bundled inside the APK.
- WebView DOM/local storage so records and settings persist on the Android app.
- External HTTP/HTTPS links open in the device browser.
- File picker support for MaoozOS backup import.
- Native Android full-backup export using the system Save dialog.
- Offline device-to-device migration: export one portable JSON backup and import it on another Android device.
- Backup export validates the MaoozOS container before opening the save dialog.
- Import failures leave the current local dataset unchanged.
- Native Android notification channel.
- Android 13+ `POST_NOTIFICATIONS` permission flow.
- Native notification settings shortcut.
- Optional exact-alarm settings for more precise class reminders.
- Native recurring timetable reminders that continue working when the WebView is not open.
- Reminder recovery after device reboot/app update.
- Quiet-hours handling for native class reminders.

## Build

Use Android Studio with a recent stable version. The project uses:

- Android Gradle Plugin 9.3.0
- Gradle 9.5.0
- Compile SDK 36
- Target SDK 36
- Minimum SDK 26
- Java 17

Open this folder as a project in Android Studio, let Gradle sync and install any missing Android SDK components, then use:

**Build → Generate App Bundles or APKs → Generate APKs → APK**

The debug APK will normally be produced under:

`app/build/outputs/apk/debug/app-debug.apk`

For a release APK, configure your own signing key in Android Studio. Do not publish a debug-signed APK.

## Important data note

The Android WebView has its own local storage. It does not automatically inherit Chrome/Edge's MaoozOS data. Use MaoozOS's **Export Full Backup** on the web version, then use **Import Backup** inside Android to move your data.

## Notification behavior

Android 13 and newer requires the user to grant notification permission. The MaoozOS Settings page exposes Android-specific controls when the app is running inside this wrapper:

- Enable
- System settings
- Test
- Configure precise class reminders

The Android reminder layer currently schedules timetable class reminders. The web app's in-app notification engine remains available as well.

## Offline device migration

On the old Android device:

1. Open **Settings → Data & Backup → Export Full Backup**.
2. Save the `.json` backup somewhere you can transfer offline.
3. Transfer the file with USB, Quick Share, Bluetooth, SD card, or another offline method.

On the new Android device:

1. Open MaoozOS.
2. Choose **Settings → Data & Backup → Import Backup**.
3. Select the backup file.

The backup contains MaoozOS application data and configuration stored by the web app. Android-level permissions (such as notification permission) are controlled by Android and may need to be granted again on the new device. A failed or invalid import does not replace the current local data.


3.6.0 reminder fix: Android now receives both classAt and leadMinutes and recomputes fireAt natively, so 15/1/custom-minute reminders are scheduled before the class. Added a 1-minute native test reminder.


## 3.7.0 Timetable and reminders
- Full weekly timetable entries repeat weekly within optional start/end dates.
- Classes show Upcoming / NEXT / NOW / DONE states.
- Timetable classes can be edited and deleted safely.
- Native notifications include course name, start/end time, room, instructor, type, and reminder lead time.


Version 3.9.0 surgical fixes: customizable grading profiles with editable grade letters/points/bounds and presets; restored missing classStatus function to prevent timetable/dashboard page crashes; timetable/settings changes continue to persist and refresh correctly.
