# Live Game Coaching 0.1 — complete prototype

This repository is the Render service root. Upload the **contents of this folder** to one GitHub repository (keep `package.json`, `server/`, and `website/` at the repository root), then create a Render **Web Service** from that repository. Render detects `render.yaml`; build command `npm install`, start command `npm start`.

## Website + signaling server
- `website/` is the coach website, served by Express.
- `server/server.js` hosts `/ws` WebSocket signaling and `/health`.
- Coaches open the Render URL and enter the six-character room code shown in the Android app.

## Android broadcaster
Open `AndroidBroadcaster/` in Android Studio. This is a separate Kotlin app and is not deployed to Render. Build/install it from Android Studio on a physical Android phone. The app asks for screen-capture permission, creates a room, and streams to up to four coaches.

## First test steps
1. Deploy this repository to Render. Wait for `/health` to return `ok: true`.
2. In `AndroidBroadcaster/app/src/main/java/com/whitearrowe/livegamecoaching/BuildConfigValues.kt`, set `SIGNALING_URL` to `wss://YOUR-SERVICE.onrender.com/ws` (the default placeholder must be changed).
3. Open Android Studio → Open → `AndroidBroadcaster/`; allow Gradle sync.
4. Run on a physical phone (Android 8+ recommended). Grant screen capture and overlay permissions. Tap **Start broadcast** and share the room code with a coach.
5. On a second device/browser open the Render site and join the room. Test on the same Wi-Fi first.

## Important prototype limits
- WebRTC uses public STUN only. Some carrier/NAT networks require TURN; add a TURN service for reliable cross-network use.
- Each coach gets an individual peer connection; four viewers multiply upload/encoder load.
- 720p/60 is the requested capture format, not a guaranteed delivered frame rate.
- Private room codes are unguessable-ish short-lived routing codes, not authentication. Anyone with the code can join.
- Render free instances may sleep; first connection after inactivity can take time.
- No APK is included unless you build one locally.
