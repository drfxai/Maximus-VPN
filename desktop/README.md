# Maximus VPN Desktop

Kotlin/JVM + Compose Multiplatform desktop client for Windows x64 and Ubuntu x64.

The desktop build bundles an Xray-core binary at release time and uses Xray TUN mode for system-wide routing. Xray documents TUN support for Windows and Linux. The desktop app expects an elevated Windows session or suitable Linux network privileges for system routing.

The current desktop client accepts VLESS links and supports the Xray transport/security fields implemented by the parser. REALITY combinations are restricted to transports supported by the current Xray documentation.

The Xray binary is deliberately **not committed to the repository**. GitHub Actions downloads the pinned Xray version during the release build.
