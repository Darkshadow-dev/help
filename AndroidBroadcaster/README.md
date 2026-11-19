# Live Game Coaching — Android Broadcaster 0.1

Native Kotlin Android app. Open this folder in Android Studio.

Before running, set the deployed Node server's secure WebSocket URL in:
`app/src/main/java/com/whitearrowe/livegamecoaching/BuildConfigValues.kt`

Example: `wss://your-service.onrender.com/ws`

The app uses Android MediaProjection for capture, WebRTC for video, OkHttp WebSocket for room signaling, and an Android overlay service for floating chat. The screen capture request is always user-approved by Android.

**This is source code, not an APK.** This workspace did not have Android SDK or Gradle installed, so it could not produce or verify an APK. Open in Android Studio, install SDK Platform 35 when prompted, sync, and build on a physical device.
