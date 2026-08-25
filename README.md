<div align="center">
<img width="1200" height="475" alt="GHBanner" src="https://ai.google.dev/static/site-assets/images/share-ais-513315318.png" />
</div>

# Run and deploy your AI Studio app

This contains everything you need to run your app locally.

View your app in AI Studio: https://ai.studio/apps/bea8c4bc-bf78-4ead-8a69-e7087a044b0d

## Run Locally

**Prerequisites:**  [Android Studio](https://developer.android.com/studio)


1. Open Android Studio
2. Select **Open** and choose the directory containing this project
3. Allow Android Studio to fix any incompatibilities as it imports the project.
4. Create a file named `.env` in the project directory and set `GEMINI_API_KEY` in that file to your Gemini API key (see `.env.example` for an example)
5. Remove this line from the app's `build.gradle.kts` file: `signingConfig = signingConfigs.getByName("debugConfig")`
6. Run the app on an emulator or physical device
7. If you have already published your app in AI Studio, please [request upload key reset](https://support.google.com/googleplay/android-developer/answer/9842756#zippy=%2Crequest-an-upload-key-reset) in Google Play Console.

## Multi-platform releases

Maximus now includes a GitHub Actions release pipeline for:

| Platform | Artifact | Notes |
|---|---|---|
| Android | APK | Installable Android package |
| Android | AAB | Google Play bundle; production distribution should use a release keystore |
| Windows | MSI / EXE | 64-bit desktop build with bundled Xray-core |
| Ubuntu | DEB | 64-bit desktop build with bundled Xray-core |

Create a Git tag such as `v1.0.0` and push it to GitHub. The workflow builds all supported packages and publishes a GitHub Release automatically. GitHub Actions downloads the pinned Xray-core `26.7.28` Windows x64 and Linux x64 binaries during the desktop build; the binaries are not committed to this repository.

### Android signing

For a production-signed Android release, configure these GitHub Actions secrets:

- `KEYSTORE_PATH`
- `STORE_PASSWORD`
- `KEY_PASSWORD`

Without them, the Android Gradle release outputs are produced without the production release signing configuration. AAB distribution to Google Play should use a proper upload/release key.

### Desktop VPN permissions

The desktop client uses Xray TUN mode. Xray documents TUN support on Windows and Linux. Windows may require an elevated session, while Ubuntu may require root or suitable network capabilities for system routing.
