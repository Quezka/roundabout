# Roundabout

A car dashboard launcher for an old Android tablet (Android 5.0+), laid out like Android Auto with Material 3 surfaces and a round, Mini-style speed gauge.

- Left rail: clock, battery, your navigation and music apps, up to 3 pinned apps, settings and the app grid.
- Drive card: GPS speed gauge (tap it to change the ambient colour) and a big Navigate button.
- Media card: what is playing in any player, with previous / play-pause / next.
- Keeps the screen on while the dashboard is shown and turns off the swipe lock screen.
- Long-press an app in the grid to pin it, or to use it for navigation or music.

## Build

```
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew testDebugUnitTest assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Media info needs notification access (Settings → Notification access, or
`adb shell settings put secure enabled_notification_listeners io.github.quezka.roundabout/io.github.quezka.roundabout.service.MediaListener`).
