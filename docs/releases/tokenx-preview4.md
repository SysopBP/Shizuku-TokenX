# TokenX 14.2.0 — Preview 4
## Android 17 pairing and backend preview
**Draft candidate: Build 670. Device validation is pending.**

This candidate packages the successful TokenX Debug Build [670](https://github.com/SysopBP/Shizuku-TokenX/actions/runs/38032634537), commit `3ff7e39cba7941e4df1d2572b2136233012261a4` from `codex/tokenx-666-pairing-fixes`. The release tag identifies the bundle; the APK retains its existing build version. It is a debug APK, not a newly production-signed build.

## Changes since Preview 3
- Wireless pairing lifecycle fixes ported onto the Build 666 RPC baseline.
- Pairing discovery and endpoint handling improvements, stale-callback handling, and notification permission handling.
- Pairing notification includes an **Enter code** action.
- Pairing client/service handling refinements and watchdog receiver fixes.
- Manager unit tests run alongside APK packaging verification.

These are implemented changes, not confirmation that pairing works on every device. Root/Shell availability and the Enter code flow still require physical-device verification on this exact APK.

## Backend architecture
TokenX separates Root UID 0, System UID 1000, and Shell UID 2000. Availability depends on the active backend, authorization, Android firmware, root configuration, and hooks.
The packaged Xposed System Server RPC integration uses the private `_TKN` rendezvous. An isolated UID-1000 terminal process is different from Android's real `system_server`.
System recovery must rebind/detach without killing `system_server`. Historical successful tests from older builds do not certify this candidate.

## Bundle
- **TokenX-Preview4-Build670-debug.apk** — exact successful CI APK.
- **TokenX-Preview4-Build670-RISH.zip** — matching rish and rish_shizuku.dex extracted from that APK.
- **TokenX-KernelSU-Diagnostics-v1.1.0.zip** — optional boot timeline tracing module. Diagnostic only; does not provision a privileged backend.
- **TokenX-Preview4-Setup.md** and these release notes.
- **SHA256SUMS.txt** — checksums of packaged assets.

The older Preview 3 KSU bridge installer is deliberately excluded: this candidate's packaging rejects retired companion backends. Do not assume an old bridge module is required by this bundle.

## Validation
Passed in Build 670: debug APK compilation, manager unit tests, APK identity checks, fresh RISH pair comparison, Xposed metadata/entry packaging, System Server RPC markers, and retired-backend absence checks.
Pending on device: Root/System/Shell/default routing, wireless Enter code notification and successful pairing, authorization persistence, full reboot and D2 recovery if installed, safe System detach/rebind, and backend heartbeats.
Compilation and packaging checks cannot certify runtime Binder acquisition or OEM wireless behavior.

## Credits
Special thanks to **@wr3cckl3ss**, **@reckaH2281**, and **@Vikramaditya015**.
Thanks to **RikkaW / RikkaApps and Shizuku contributors**, **thedjchi**, **KernelSU**, and **LSPosed** for the foundations and tools used by TokenX.
TokenX is an independent project. Original licensing and upstream attribution remain applicable.
