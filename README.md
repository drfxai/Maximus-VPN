<div align="center">

# Maximus VPN

**Professional, high-performance VLESS & Xray-core VPN client by DrFXAi**

Android • Windows x64 • Ubuntu x64

</div>

---

Maximus VPN features real VpnService tunneling on Android, routing controls, diagnostics, and secure profile management. The desktop client runs Xray-core in TUN mode for system-wide routing on Windows and Ubuntu.

## Multi-platform releases

Maximus includes a GitHub Actions release pipeline for:

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

## Building locally

**Prerequisites:** [Android Studio](https://developer.android.com/studio) (Android) and JDK 17+ (Desktop)

1. Open the project root in Android Studio, or run Gradle from the command line
2. Android app: `gradle :app:assembleRelease`
3. Desktop app: see `desktop/README.md`
