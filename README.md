# Ink Capture Android

Minimal S Pen / touch drawing app for Device B.

## Upload destination
The app currently posts PNG uploads to:

`https://device-b-relay.onrender.com/api/ink_capture?token=abc123xyz789`

Edit `relayBase` and `relayToken` in:

`app/src/main/java/com/deviceb/inkcapture/MainActivity.kt`

## Build
GitHub Actions builds a debug APK using:

`.github/workflows/build-apk.yml`

After a successful workflow run, download the `ink-capture-debug-apk` artifact, extract it, and install the APK on the phone.
