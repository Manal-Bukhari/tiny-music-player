# Tiny Music Player — Refactored Build

Self-contained Android Gradle project containing the refactored source. Builds independently of the parent `../app/` module.

## Layout

```
refactored_implementation/
├── REFACTORING_LOG.md             # Before/after walkthrough (retro-terminal style)
├── README.md                      # This file
├── build.gradle                   # Root build script
├── settings.gradle                # Module include
├── gradle.properties
├── gradlew / gradlew.bat / gradle/wrapper/   # Gradle wrapper
└── app/
    ├── build.gradle               # Android module config
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── res/{layout,drawable-nodpi}/
        └── java/
            ├── com/martinmimigames/tinymusicplayer/
            │   ├── AudioPlayer.java       (verbatim copy)
            │   ├── Exceptions.java        (verbatim copy)
            │   ├── HWListener.java        (refactored)
            │   ├── Launcher.java          (refactored)
            │   ├── Notifications.java     (refactored)
            │   ├── PlaybackCommand.java   (NEW — Replace Type Code with Strategy)
            │   └── Service.java           (refactored)
            └── mg/utils/notify/
                ├── NotificationHelper.java  (vendored utility — unchanged)
                └── ToastHelper.java         (vendored utility — unchanged)
```

## Build

From this directory:

```bash
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

## Install on a connected device / emulator

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Notes

- Android SDK required. If `local.properties` is missing, create one with:
  ```
  sdk.dir=/path/to/your/Android/sdk
  ```
- Java 16+ toolchain required (per `app/build.gradle` `compileOptions`).
- Wire-protocol byte values (PlaybackCommand.code) match the original
  Launcher constants exactly — PendingIntents stored on installed devices
  remain decodable.
- Only the `mg/utils/notify/` subpackage of the vendored mg.utils library
  was copied; the rest (graphics/net/clipboard/...) is unused by this app
  and therefore omitted to keep the module minimal.
