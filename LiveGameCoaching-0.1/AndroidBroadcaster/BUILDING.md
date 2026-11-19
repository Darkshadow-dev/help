# Build and first-test notes

1. Edit `app/src/main/java/com/whitearrowe/livegamecoaching/BuildConfigValues.kt` and set the Render WebSocket URL.
2. Open this folder in Android Studio (JDK 17/21 supported by Android Gradle Plugin 8.7).
3. Install Android SDK Platform 35 and Build Tools when Android Studio prompts.
4. Sync Gradle, connect a real Android device, then Run `app`.
5. For a shareable test APK: Build → Build Bundle(s) / APK(s) → Build APK(s).

This environment did not contain Android SDK or Gradle, so no APK was produced here. The Kotlin source is a prototype and needs Android Studio compilation; expect API/dependency fixes if the WebRTC artifact changes.
