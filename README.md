# Screenshot Lab — Clatasha

A premium-styled Android screen capture utility built from the Screen Paint Lab research prototype. The app is **not** a tool for bypassing protected screens. Android's `FLAG_SECURE` restrictions remain in force.

## Features in v1.0 beta

- **One-tap screenshots**: user-approved MediaProjection capture, automatically returns to the previous screen and saves a PNG to `Pictures/ScreenshotLab`.
- **MP4 screen recording**: 30 FPS video with selectable 720p/1080p resolution (short-edge target) saved to `Movies/ScreenshotLab`. Use the notification's **Stop** action to finish. Video is currently silent; microphone and internal audio aren't implemented.
- **On-screen markup**: user-approved capture session with a floating draw toolbar (DRAW/TOUCH, SAVE, UNDO, COLOR, CLEAR, CLOSE). The **SAVE** action hides the toolbar, captures the normal screen, and composites the drawing paths onto it, then saves a PNG. Normal screen backgrounds are supported; protected/secure regions remain black.
- **Custom app icon**, dark UI and gallery shortcuts.
- No root, account or internet permission required.

## Build

The GitHub workflow creates a debug APK (Java 17, AGP 8.6.1, Gradle 8.7, Android SDK 35). Download `ScreenshotLab-debug-apk` from the latest successful GitHub Actions workflow artifact. Extract `app-debug.apk` before installing.

Because GitHub Actions uses a new debug keystore on each build, you may need to **uninstall the earlier Screen Paint Lab debug APK** before installing this release due to a signature mismatch. Any Gallery screenshots remain in their normal media folders.

## Quick start

1. Tap **TAKE SCREENSHOT**, approve Android's screen sharing prompt, and select **Entire screen** if asked. The app moves to the background and saves the next screen after a short delay.
2. Tap **START RECORDING**, approve the prompt, then use the foreground notification's **Stop** action. The saved video appears in Gallery / My Files → Movies / ScreenshotLab.
3. Tap **START MARKUP**, grant **Appear on top** the first time, then approve screen capture. Draw with the overlay; use **TOUCH** to interact with the app underneath and **DRAW** to resume drawing. Tap **SAVE** for the background plus your drawing.

## Limitations

- WhatsApp View Once and other protected content are excluded from capture by Android. The app deliberately makes no attempt to override `FLAG_SECURE`.
- On some devices Android may revoke a projection session on screen lock, another capture, or user action. Start a new capture if this happens.
- Current release supports portrait-oriented sessions best; rotating the device mid-recording may letterbox the video.
- The on-screen floating toolbar is hidden briefly during save, but a capture may fail if Android stops the session.
- Permission is required for every new screen-capture session (Android 14+).

© 2026 Clatasha. Personal beta, no telemetry.