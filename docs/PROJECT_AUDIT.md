# Maximus VPN — Current Technical Audit

**Audit date:** 2026-08-26  
**Scope:** Android app, desktop client, Xray integration, security-sensitive routing, lifecycle, build/CI metadata.

## Result

The repository was re-audited from source and the following defects were fixed in-place. The Android data path now uses the bundled Xray-core process rather than the obsolete hand-written VLESS/TCP stack described by the previous audit.

## Fixed high-impact defects

| Severity | Defect | Fix |
|---|---|---|
| Critical | Android Xray TUN FD was placed in an unsupported JSON `fd` setting. | FD is inherited by the child process and supplied through the documented `XRAY_TUN_FD` environment variable. |
| Critical | Android TUN config used stale `inet4_address` / `inet6_address` fields. | Current Xray TUN config uses the documented `gateway` address list. |
| Critical | ALLOW_ONLY split tunneling mixed Android allowed/disallowed application modes. | Allow-only now uses only the allow-list; the Maximus/Xray process remains outside it to avoid a routing loop. Empty/invalid lists fail closed. |
| High | QR camera scanner relied on `ByteBuffer.array()` and wrong luminance dimensions. | Y-plane data is copied safely from the buffer and decoded with the correct stride/dimensions; callback returns on the main executor. |
| High | Xray process death could leave the VPN reporting CONNECTED. | A watchdog observes process exit and tears down the VPN on unexpected death. |
| High | Xray runtime files were placed beside the native library. | Runtime config/assets now live in writable `filesDir/xray-runtime`. |
| High | SecureStorage fell back to plaintext after Keystore failure. | Keystore failures now abort the write; legacy plaintext records are not accepted. |
| High | Server tester used trust-all TLS. | Default trust + hostname verification is used for ordinary TLS; REALITY is tested only at TCP level. |
| Medium | `setBlocking(true)` was presented as a network kill switch. | Removed; Android lockdown is correctly documented as an OS Always-on VPN + “Block connections without VPN” setting. |
| Medium | Custom bypass values were all emitted as domain rules. | IP literals are emitted as `ip` rules and hostnames as `domain` rules. |
| Medium | Connect/disconnect operations could overlap from UI/network callbacks. | Operations are serialized with a coroutine mutex. |
| Medium | Old product/Xray version strings remained in UI. | App version is read from `BuildConfig`; Xray version matches the pinned 26.7.28 runtime. |
| Low | README/audit said QR scanning was absent although the UI shipped a scanner. | Documentation updated to match source. |

## Security posture after repair

- Default routing is fail-closed: unmatched TCP/UDP traffic goes to the proxy outbound.
- Private/LAN bypass rules use explicit ranges and no longer depend on a missing GeoIP database.
- Hysteria2 profiles are rejected before connection because the bundled Xray-core configuration does not provide a Hysteria2 outbound.
- Unexpected Xray process exit closes the Android TUN interface.
- The app does not claim that a local toggle can enable Android system lockdown.

## Validation limits

A full Gradle/Android build could not be executed in this container because no Gradle distribution/wrapper installation was available locally and external package download was unavailable from the execution environment. Static source inspection, project-wide reference checks, and targeted code-path review were completed. The repaired archive should therefore be treated as source-audited rather than device/build-certified.

## Remaining engineering limitations

- Android package currently bundles arm64-v8a Xray only.
- Desktop TUN mode still requires platform privileges required by the OS.
- Android lockdown/“Block connections without VPN” must be enabled by the user in system VPN settings.
