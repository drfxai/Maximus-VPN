# Maximus VPN Desktop

Kotlin/JVM + Compose Multiplatform desktop client for **Windows x64** and **Ubuntu x64**.

## Features

- Dashboard: connect/disconnect, current server, live upload/download, public IP, connection time
- Servers: import `vless://` links, favorites, per-server connect/delete (persisted in `~/.maximus-vpn/servers.json`)
- Connection: one-off quick-connect from a pasted URI
- Diagnostics: tunnel status, Xray process status, DNS check, public egress IP
- Logs: live Xray-core output with credential redaction; safe diagnostics export
- Settings: version info, data directory

The client runs the official **Xray-core** executable in TUN mode for system-wide routing.
All traffic (TCP/UDP/DNS) flows through Xray — there is no direct fallback path (fail-closed).

## Platform packages

| OS | Artifacts | Built by |
|---|---|---|
| Windows x64 | `.msi`, standalone `.exe` installer | CI on tag push |
| Ubuntu x64 | `.deb` | CI on tag push |

The Xray binary is downloaded by CI at build time and embedded in the app jar
(`resources/xray/xray[.exe]`); it is extracted to `~/.maximus-vpn/` on first run.

## Build locally

```bash
cd desktop
# put an xray binary in place (or set MAXIMUS_XRAY_PATH env var at runtime)
curl -L -o xray.zip https://github.com/XTLS/Xray-core/releases/download/v26.7.28/Xray-windows-64.zip   # or Xray-linux-64.zip
unzip xray.zip && mkdir -p src/main/resources/xray && cp xray.exe src/main/resources/xray/  # linux: cp xray ...
gradle packageDistributionForCurrentOS -PappVersion=1.0.0
```

Outputs land in `build/compose/binaries/main/{exe,msi,deb}`.

Windows may require an elevated session for TUN routing; Ubuntu may require root or suitable network capabilities.
