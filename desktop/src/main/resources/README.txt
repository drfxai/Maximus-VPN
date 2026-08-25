The xray executable and wintun.dll are injected into this resources directory
by .github/workflows/release.yml during CI.
Do not commit platform binaries here.

Required files (Windows):
  xray.exe    — XTLS/Xray-core windows-64, version pinned by XRAY_VERSION in release.yml
  wintun.dll  — Wintun 0.14.1 amd64, sha256 e5da8447dc2c320edc0fc52fa01885c103de8c118481f683643cacc3220dafce

Required files (Linux):
  xray        — XTLS/Xray-core linux-64, same pinned version
