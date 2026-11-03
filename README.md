# Zaymbes Voice Lab (Android starter)

This is the first Android Studio project scaffold for Zaymbes Voice Lab.

## Open/build
1. Install Android Studio (recent stable version) and Android SDK 35.
2. Open this folder in Android Studio and let Gradle sync.
3. Connect an Android phone, enable USB debugging, and Run.
4. Grant microphone permission. Use headphones to prevent feedback.

## Current status
- Dark Phantom-God themed UI and preset concept
- Microphone permission and real-time mic-to-headphone monitoring
- Three UI sliders (currently visual controls only)

**Not yet implemented:** pitch/formant shifting, synchronized vocal layers, resonance DSP, saving presets, APK release signing, or Discord microphone routing. The first audio loop is raw monitoring only; it is not yet the Zaymbes voice effect. Android does not normally allow an app to replace Discord's microphone input system-wide.

## Next development milestone
Implement a low-latency DSP chain (pitch/formant, subtle resonance, dual-layer blend), connect sliders to DSP parameters, then test latency/audio behavior on a physical phone.

## Build from your Android phone using GitHub Actions (cloud)
1. Sign in to GitHub at https://github.com and create a repository named `ZaymbesVoiceLab`.
2. Upload the contents of this project folder to the repository root (not the ZIP file itself). Include the hidden `.github/workflows/android.yml` file.
3. Open the repository's **Actions** tab and enable Actions if asked.
4. Select **Build Zaymbes Voice Lab APK** and tap **Run workflow**. A push to `main` also starts a build.
5. When the run succeeds, open it and download the `ZaymbesVoiceLab-debug-apk` artifact. Extract that artifact ZIP to get `app-debug.apk`.

Note: this builds the starter app only. Microphone monitoring is currently raw audio; the Phantom God pitch/formant/layer audio effects are not implemented yet. This debug APK is for testing, not a signed Play Store release.
