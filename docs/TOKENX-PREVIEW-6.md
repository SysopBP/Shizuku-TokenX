# TokenX Preview 6 — Dual Backend & CORE Interface

TokenX Preview 6 packages the current multi-backend interface, Root/System routing, CORE search, wireless reliability fixes and restored user-created blue launcher artwork. It builds on successful TokenX Debug Build 721, source `99f2b2a4c3087ea695bcaf9db815785ad377b679`, and incorporates the newer Control Center health wording from `6575e97`.

## Current interface

- Home shows Root and System Binder states separately, the configured default route, and Dual Backend Active when both are bound.
- Connections brings together the TokenX transport, Root, System UID 1000, wireless debugging, USB debugging and PC/ADB controls.
- Apps supports authorization, user/system/disabled/hidden filters, and Root/System route filters. Granted-app counts, selected-filter results and connected-session counts describe different things.
- CORE search links to connections, startup/recovery, ADB pairing, system integration, runtime information and tools.
- Retains the existing glass appearance, MIUIX/Material options, wallpaper styling and bottom navigation. Preview 6 is not a new UI rewrite.
- Control Center distinguishes an isolated UID-1000 worker from verified Android System Server RPC rather than treating UID alone as proof of framework identity.

## Backends and identity

Root uses UID 0. System execution uses a dedicated UID-1000 backend; an interactive System RISH worker is outside Android's persistent `system_server`. Controlled framework operations use the separate LSPosed/Xposed `_TKN` RPC path. Shell/ADB uses UID 2000 when started and authorized.

A bound Binder, an active terminal session, a granted app and verified System Server RPC are separate observations. The default route does not require disconnecting another healthy backend. System recovery remains rebind/detach-oriented; this release adds no System Server restart action.

## Wireless fixes retained from Preview 5

- Serializes discovery lifecycle and discards stale session callbacks.
- Bounds discovery retries and authorization waits; command reads have an inactivity timeout.
- Validates ADB packet headers and payload sizes before allocating the body.
- Refreshes pairing-port notifications and validates six-digit replies.
- Cancels stale accessibility pairing work and closes failed TLS-handshake state.

Pairing still needs Android authorization and an available ADB endpoint. A pairing port is different from the normal wireless connection port.

## Included assets

- **TokenX-Preview-6.apk** — signed debug APK from the successful CI job for this release commit.
- **TokenX-Preview-6-RISH.zip** — matching `rish` and `rish_shizuku.dex` extracted from that APK.
- **TokenX-Preview-6-Guide.md** — these release notes and setup instructions.
- **BUILD-INFO.txt** — exact source commit, workflow run and build number.
- **SHA256SUMS.txt** — checksums of the distributed files.

No legacy privileged companion APK or old KernelSU bridge installer is bundled. KernelSU supplies the root environment; the real System Server integration requires a compatible modern Xposed framework and appropriate scope. Existing diagnostic-only KSU modules are not privilege providers.

## Update and setup

1. Install the APK over the existing `com.sysopbp.tokenx` installation. An older `moe.shizuku.privileged.api` installation is a different package; do not assume an in-place migration or automatic permission transfer.
2. Retain your working root/framework setup. Grant TokenX root access and review System Framework scope if using real System Server RPC.
3. Open Connections and connect the required backends. Confirm the Home Binder states. Use System integration to inspect actual framework identity separately.
4. Replace both RISH files together with the files from this release. In Termux, place them in the same private directory, then run:

```sh
chmod 700 rish
chmod 400 rish_shizuku.dex
export RISH_APPLICATION_ID=com.termux
./rish --root -c 'id; echo ROOT_OK'
./rish --system -c 'id; echo SYSTEM_OK'
```

Use `./rish --shell -c 'id'` only when Shell/ADB is available. Plain `./rish` follows the configured default route. UID 1000 does not automatically confer every permission or the System Server SELinux context.

## Validation and limits

The owner confirmed the preceding installed UI works and supplied an SM-S948U1 / Android 17 / One UI 9 screenshot showing Root Bound, System Bound and Default Root. This confirms the displayed connection state at that moment; it does not prove all permissions, terminal commands, wireless reconnection or reboot recovery on the new APK.

Publication is gated on this release build's existing APK compilation, manager unit tests, RISH routing checks, independent identity checks, modern Xposed entry packaging, required System Server RPC markers and retired-backend absence checks. Consult the Actions run linked by BUILD-INFO.txt for the actual results.

After installing, verify both explicit RISH routes, Galaxy Island authorization, safe System detach/rebind and wireless pairing/reconnect. Boot/watchdog/D2-gate behavior remains configuration- and firmware-dependent. Coordinate reboot testing on temporary-root devices; a full reboot is not required for these UI checks.

## Credits

Special thanks to **@wr3cckl3ss**, **@reckaH2281**, and **@Vikramaditya015** for contributions, testing and feedback.

Thanks to **RikkaW / RikkaApps and Shizuku contributors**, **thedjchi**, **KernelSU**, **LSPosed**, and the upstream UI/library projects acknowledged in the bundled notices. Original Shizuku/RISH foundations and third-party licenses remain applicable. TokenX is an independent project.

This is a prerelease for continued testing, not a stable certification across Android devices.
