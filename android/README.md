# Android broadcaster — implementation stage

The Android broadcaster is intentionally a separate native app. The coach website is already scaffolded, but the native sender is not yet included in this first source package.

Required Android components:
- MediaProjection permission flow for screen capture.
- Foreground service with a persistent notification while broadcasting.
- WebRTC `ScreenCapturerAndroid` at 1280x720 / 60 FPS.
- One peer connection per coach in this 0.1 mesh prototype.
- WebSocket signaling to `/ws`.
- Android `TYPE_APPLICATION_OVERLAY` floating coaching window (requires user-granted overlay permission).

Do not treat the website's STUN-only setup as production-ready. Add a TURN service before relying on connections across restrictive mobile networks. The first prototype is limited to four coaches because each viewer receives a separate peer-to-peer stream.
