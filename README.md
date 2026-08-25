<div align="center">

# Maximus VPN

**Professional, high-performance VLESS & Xray-core VPN client by DrFXAi**

Android (arm64-v8a) • Windows x64 • Ubuntu x64

</div>

---

Maximus VPN tunnels all device traffic through a bundled **Xray-core** engine:
VpnService TUN on Android, system-wide TUN on Windows/Linux. Routing controls,
diagnostics, leak checks, and secure profile management included.

## Feature matrix

| Capability | Status |
|---|---|
| VLESS over TCP / TLS / WebSocket / gRPC | ✅ via bundled Xray-core |
| REALITY (xtls-rprx-vision) | ✅ via bundled Xray-core |
| UDP through tunnel | ✅ handled inside Xray tun stack |
| DNS through tunnel | ✅ resolved via Xray DNS module |
| Fail-closed design | ✅ no direct fallback; failure tears down the TUN |
| Kill switch (Android) | ✅ optional lockdown while connecting |
| Server import (`vless://`) | ✅ Android + Desktop |
| QR camera scan | ❌ not implemented (paste/payload import only) |

## Downloads

Create a Git tag (e.g. `v1.0.0`) and push it — the release pipeline builds:

| Platform | Artifact |
|---|---|
| Android arm64-v8a | APK (+ AAB for Play) |
| Windows x64 | MSI installer + standalone EXE |
| Ubuntu x64 | DEB package |

Xray-core binaries are pinned (`v26.7.28`) and downloaded by CI at build time;
they are never committed to the repository.

## Architecture

```
Android                          Desktop (Win/Linux)
Kotlin + Compose                 Kotlin + Compose Multiplatform
Android VpnService               Desktop VPN controller
        │                              │
        └──────────┬───────────────────┘
                   ▼
             Xray-core 26.7.28
          VLESS / REALITY / TLS
                   ▼
              TUN → Network
```

- `app/` — Android client (`com.drfxai.maximusvpn`)
- `desktop/` — Windows/Ubuntu client (`com.drfxai.maximus.desktop`)
- `docs/PROJECT_AUDIT.md` — technical audit of the original prototype

## Building locally

**Prerequisites:** JDK 21, Android SDK (API 36) for Android; Gradle 9.x is wrapped.

```bash
# Android (requires libxray.so in app/src/main/jniLibs/arm64-v8a/)
gradle :app:assembleRelease -PversionName=1.0.0 -PversionCode=1

# Desktop (Windows: also place xray.exe in desktop/src/main/resources/xray/)
cd desktop && gradle packageDistributionForCurrentOS -PappVersion=1.0.0
```

### Android signing

Configure CI secrets `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD` for production-signed releases.
Without them, unsigned release artifacts are produced.
