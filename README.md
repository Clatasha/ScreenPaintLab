# Screen Paint Lab (Clatasha)

Unrooted Android diagnostic/prototype for studying secure window capture and transparent drawing overlays. Designed for Samsung Galaxy A14 (Android 10+ compatible; minSdk 29). This app **does not bypass** WhatsApp View Once protections, and no results are guaranteed for protected apps.

## Features

1. **MediaProjection screen probe:** With Android's consent prompt, captures one PNG approximately every 2 seconds (maximum 40 per session). Android secure-window protections remain in force. All captures are saved to `Pictures/ScreenPaintLab` using MediaStore.
2. **Protected test screen:** Synthetic colorful drawing with a toggle for `FLAG_SECURE`; compare captured frames with protection enabled and disabled.
3. **Transparent painting overlay:** Tap **Enable/start paint overlay**, allow "Appear on top", then start again. The floating bar opens in **TOUCH APP** mode, allowing interaction with underlying apps. Tap **DRAW** to draw strokes over the current screen, **TOUCH APP** to interact with the underlying app again, and **SAVE PNG** to save only the strokes. It never reads pixels from behind the overlay.
4. **Saved-frame count:** Shows successfully written probe PNGs (cumulative since install) and storage path.

## Building the APK

### Option A: GitHub Actions (no local Android Studio installation)

1. Create a new **private** GitHub repository (e.g. `ScreenPaintLab`).
2. Upload the **contents of this directory** (including `.github/workflows/build-apk.yml`) to the repository root. GitHub's web upload sometimes skips hidden folders; verify `.github/workflows/build-apk.yml` exists.
3. On the Actions tab, run **Build debug APK**, or push to `main` to start it.
4. On the finished workflow run, download the `ScreenPaintLab-debug-apk` artifact, unzip it, and install `app-debug.apk` on your phone. Android may require enabling "Install unknown apps" for the app you use to open the APK.

The GitHub workflow is provided but was **not run or verified** in this environment.

### Option B: Android Studio

1. Create a new **Empty Views Activity** Java project in Android Studio, with package `com.clatasha.screenpaintlab` and minimum API 29.
2. Copy this project's `app/src/main/` directory into the new project, replacing the generated files. Copy the relevant values from `app/build.gradle` (compileSdk 35, minSdk 29, targetSdk 34, Android Gradle Plugin 8.6.1) and AndroidManifest.xml. Or open this repository as a Gradle project; if your Android Studio needs a Gradle wrapper JAR, generate a wrapper using `gradle wrapper --gradle-version 8.7`.
3. Build → Build APK(s), then install `app-debug.apk`.

No Android SDK was available in the authoring environment, so **the APK has not been compiled or tested on the Samsung A14**. If a build issue occurs, inspect the Gradle output; source is intentionally dependency-free Java.

## Test protocol

1. First, use **Start screen probe**, grant Android screen-sharing permission (choose **Entire screen** if Android asks what to share), and tap **Open protected sample**.
2. Let a few frames be captured with **FLAG_SECURE ON**, toggle **OFF**, and let a few more frames be captured. Return to the app and stop the probe.
3. In **Gallery** or **My Files → Pictures → ScreenPaintLab**, compare PNGs. On protected windows Android normally excludes the colors/text from the captured screen.
4. Test the painting overlay on the sample. Try **TOUCH APP** to interact with the screen, **DRAW** to trace shapes, then **SAVE PNG**. The saved PNG contains only paint strokes.
5. A WhatsApp View Once message is consumable: if you choose to test it, expect it may be permanently unavailable afterward. Any saved capture that doesn't include the protected photo is the expected security behavior, **not** a bug in the drawing algorithm.

## Caveats

- Screen-capture consent is mandatory and must be granted by the phone's user.
- Secure windows may appear black or empty in captures. The painting overlay doesn't circumvent this.
- Android or WhatsApp may hide floating overlays on sensitive screens.
- The probe limits storage to 40 PNGs per session but can still consume substantial disk space; remove files manually from Gallery/My Files.
- A capture will never recover a previously consumed View Once image.
- This is an experimental debug build, not a verified production application.
