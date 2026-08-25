# Maximus VPN — Technical Audit Report

**Audited:** `main` branch, pre-production state
**Scope:** Android app (`app/`), desktop app (`desktop/`), build system, CI/CD, security posture
**Method:** Full source inspection of all Kotlin files, Gradle configuration, workflows, manifests, and resources. No code was modified before this report was written.

---

## 1. Project Overview

| Aspect | Finding |
|---|---|
| Language | Kotlin (Android + JVM desktop) |
| Android UI | Jetpack Compose + Material 3, single-activity, Navigation-Compose |
| Android data | Room (server profiles), SharedPreferences + Android Keystore AES-GCM (settings/secrets) |
| Desktop | Kotlin/JVM + Compose Multiplatform (Compose 1.11.1, Kotlin 2.4.10), separate Gradle build |
| CI | GitHub Actions `release.yml` (tag-triggered; Android APK/AAB, Windows MSI/EXE, Ubuntu DEB) |
| VPN core (Android) | **Custom hand-written VLESS/TCP stack — NOT Xray-core** (see §3) |
| VPN core (Desktop) | Genuine Xray-core process in TUN mode (binary downloaded at CI build time) |

### Module inventory (Android, `app/src/main/java/com/example/**`)

- `vpn/RayVpnService.kt` — Android `VpnService`; builds TUN, routes packets
- `vpn/TunnelManager.kt` — TUN read loop, demuxes ICMP/UDP/TCP
- `vpn/packet/IpPacket.kt` — IPv4/TCP/UDP header parse + raw packet builder
- `vpn/tunnel/TcpVlessTunnel.kt` — userspace TCP state machine → VLESS over TLS
- `vpn/tunnel/UdpRelay.kt` — UDP relay (**direct, not tunneled** — see §3.3)
- `vpn/tunnel/DnsRelay.kt` — DNS interception (**direct, not tunneled** — see §3.3)
- `vpn/tunnel/IcmpHandler.kt` — ICMP echo synthesis
- `xray/XrayEngine.kt` — "engine" facade; **does not load any native lib** (see §3.1)
- `xray/XrayConfigBuilder.kt` — generates valid Xray JSON config **that is never used by any Xray process** (dead output)
- `xray/XrayLogManager.kt` — in-memory ring log
- `vless/VlessParser.kt` + `VlessHeader.kt` — vless:// URI parse/validate + VLESS request-header encoder
- `data/` — Room DB, repositories, SecureStorage
- `ui/` — Home, Servers, AddServer, Diagnostics, Settings screens + ViewModels + theme

---

## 2. What Works Well (kept)

- **Room-backed server profiles** with proper DAO/repository layering
- **SecureStorage**: Android Keystore AES-256-GCM encryption for secrets
- **VlessValidator**: strict UUID/port/transport/REALITY validation with typed exceptions
- **SecretRedactor**: redacts credentials before logging config previews
- **TUN/IP packet parser**: correct IPv4/TCP/UDP header handling, checksum building
- **Theme system** (`AppColors`): dark premium palette with neon accents — matches target design
- **Compose navigation** with bottom bar, ViewModels, coroutines throughout
- **Desktop engine**: `XrayDesktopEngine` genuinely spawns an Xray-core process with a real config (TUN inbound, proxy outbound, routing rules)

---

## 3. Critical Findings

### 3.1 The Android "XrayEngine" is not Xray-core (HIGH — misrepresentation)

`XrayEngineImpl.start()` attempts `System.loadLibrary("xray")` inside a try/catch. **No `libxray.so` exists anywhere in the project** (no jniLibs, no NDK config, no dependency providing one). The load always fails, and the code logs:

> "Native libxray binary not linked directly; starting internal high-performance proxy bridge router."

and silently proceeds with the custom stack. Additionally `ENGINE_VERSION = "Xray-core 1.8.24 (RayTunnel Unified)"` is shown to users — a false claim.

Meanwhile `XrayConfigBuilder.buildJson()` produces a complete, valid Xray-core config (SOCKS + dokodemo-door inbounds, VLESS outbound with TLS/REALITY, routing) — **but no Xray process ever consumes it**. It is dead output that creates the illusion of Xray integration.

### 3.2 Traffic-leak: direct fallback on VLESS failure (CRITICAL — security)

`TcpVlessTunnel.establishUpstream()` catch block calls `tryDirectFallback()`, which opens a **plain, unencrypted, direct socket** to the destination. Any VLESS handshake failure (bad UUID, server down, TLS failure) causes user traffic to exit the device in the clear, bypassing the VPN. This is exactly the "VLESS failed → direct socket → Internet" anti-pattern that must be fail-closed.

### 3.3 UDP and DNS bypass the tunnel (CRITICAL — security)

- `UdpRelay.handleUdpPacket()` opens a protected `DatagramSocket` and sends the payload **directly to the destination**. The `profile` field is accepted but never used. UDP traffic never enters any tunnel.
- `DnsRelay` does the same for port 53: it forwards DNS queries directly from the device's real network interface.

Consequence: with the TUN routing all traffic, UDP/DNS still leave via the physical interface → **DNS/IP leak surface**. The app cannot claim full-device VPN protection.

### 3.4 Trust-all TLS (HIGH — security)

`TcpVlessTunnel.createTrustAllSslContext()` installs an `X509TrustManager` with empty `checkServerTrusted`. All TLS connections (including "tls" security profiles) accept any certificate → trivially MITM-able.

### 3.5 REALITY is not actually supported (HIGH — misrepresentation)

When `security == "reality"`, the code wraps the socket in **ordinary TLS** and sends a VLESS header. REALITY requires Xray-core's protocol implementation (auth key exchange, certificate forgery of the SNI target). The current code cannot interoperate with a REALITY server; the UI/README imply otherwise.

### 3.6 Kill switch is cosmetic (MEDIUM)

`killSwitchEnabled` persists and toggles in Settings but is never applied to `VpnService.Builder` (no `setBlocking`-style enforcement, no always-on). Toggling has no effect.

### 3.7 Dead/misleading code (MEDIUM)

- `XrayConfigBuilder.buildJson()` — valid config, zero consumers
- `ServerTester` — TCP-connect latency test only; reasonable, kept with honest labeling
- Template tests: `ExampleUnitTest`, `ExampleRobolectricTest`, `GreetingScreenshotTest` — AI-Studio template remnants

### 3.8 Development naming (LOW)

- Package `com.example` across 45+ files (applicationId already `com.drfxai.maximusvpn` — mismatch)
- `RayApplication`, `RayTunnel` names in code, prefs files (`raytunnel_settings`), and log strings
- Firebase BOM + `firebase-ai` + `firebase-appcheck` + google-services plugin declared with **zero** source references (verified by grep) — pure template weight
- Retrofit/Moshi/OkHttp declared, zero source references
- secrets-gradle-plugin (removed in prior pass along with `.env.example`/`metadata.json`/`assets/.aistudio`)

---

## 4. Desktop Audit

- `XrayDesktopEngine` — real process spawn, binary extraction from resources, `MAXIMUS_XRAY_PATH` override. Good foundation.
- `XrayConfigBuilder` (desktop) — valid TUN-mode config; `network: "raw"` for tcp is wrong per Xray docs (`tcp` is correct); routing sends everything to proxy including the tunnel's own server traffic (acceptable for TUN with auto-route, but should exclude server IP).
- `Main.kt` — single-screen paste-a-URI UI; no server list, no logs view, no diagnostics, no settings. Does not meet the target spec.
- No server persistence, no log capture from the Xray process stdout, no connection state machine.

---

## 5. Remediation Plan (implemented in this change set)

1. **Android VPN core → real Xray-core**: bundle official `libxray.so` (gomobile bind from XTLS/Xray-core releases) for arm64-v8a; `RayVpnService` feeds TUN file descriptor to Xray's tun inbound; delete custom TCP/UDP/DNS VLESS stack paths from the data flow.
2. **Fail-closed everywhere**: remove `tryDirectFallback`; on engine failure → tear down TUN, block traffic, surface error. UDP/DNS flow through Xray (tun inbound handles them); no direct sockets for user traffic.
3. **Remove trust-all TLS**: with Xray handling TLS/REALITY natively, the custom TLS path is deleted entirely.
4. **Kill switch**: wire to `VpnService.Builder` (lockdown mode via always-on/blocking where available) and document behavior.
5. **Naming**: `com.example` → `com.drfxai.maximusvpn`; `Ray*` → `Maximus*`; prefs files renamed.
6. **Dependencies**: strip Firebase, google-services plugin, Retrofit/Moshi/OkHttp, template tests.
7. **Desktop**: add navigation (Dashboard/Servers/Diagnostics/Logs/Settings), server persistence (JSON file in app dir), Xray stdout log capture, connection state, honest version display.
8. **Diagnostics**: DNS status, tunnel status, routing status, public IP fetch through tunnel (leak indicator), safe export (redacted).
9. **CI**: add `build.yml` (push/PR builds all platforms); keep `release.yml` with signing fallback; README honesty pass (only claim what ships).
10. **Versioning**: `versionName` from `-PversionName=` (default 1.0.0), displayed in Settings/About.

## 6. Honest capability statement (post-remediation)

| Feature | Status |
|---|---|
| VLESS over TCP/TLS/WS/gRPC | ✅ via bundled Xray-core |
| REALITY | ✅ via bundled Xray-core |
| UDP through tunnel | ✅ via Xray tun inbound |
| DNS through tunnel | ✅ via Xray tun inbound + Xray DNS module |
| Fail-closed kill switch | ✅ TUN torn down on failure |
| Android arm64-v8a | ✅ (x86_64 emulator support: add `Xray-android-amd64` lib on request) |
| Windows x64 / Ubuntu x64 | ✅ Xray-core TUN (elevated privileges required) |
| QR camera scan | ❌ not implemented (paste/QR-payload import only) — stated honestly |
