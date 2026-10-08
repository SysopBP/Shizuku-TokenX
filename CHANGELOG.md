# Changelog

## 14.2.0-TKN-preview.3

### Preview highlights

- TokenX multi-backend execution routing for Root / UID 0, System / UID 1000, and Shell / UID 2000.
- TokenX RISH protocol v3 with explicit `rish --root`, `rish --system`, legacy `--sserver`, and `rish --shell` selectors.
- Manager-classified routed UID handoff for explicit sessions, avoiding the unreliable Android 17 raw Binder UID re-probe.
- Dedicated on-demand System / UID 1000 backend while keeping interactive execution outside persistent `system_server`.
- LSPosed `_TKN` System Server RPC identity verification.
- D2-aware startup boundary when Kiosk D2 Guardian is installed.
- Independent backend lifecycle model: Root can start/stop/restart; System uses safe start/rebind/detach semantics and must never terminate `system_server`.
- Persistent app authorization and per-app backend routing.
- TokenX Control Center, diagnostics, Root Console, Shell, App Ops, Firewall, Autostart, and the new **MIUIX + Material theme capability**, alongside TokenX glass/floating appearance controls.
- Protocol/client packaging checks and retired-backend guards in CI.


### Reboot & backend recovery

- Preview 3 retains TokenX's reboot-recovery architecture. After device startup, TokenX restores its privilege environment using the configured startup method.
- On D2-protected devices, privileged recovery remains gated until the D2 unlock boundary opens; TokenX does not intentionally bypass D2 during boot.
- Root and System are recovered independently rather than treating one backend as a replacement for the other.
- Persisted client authorization and routing state can be reapplied as the corresponding backend Binder becomes available again.
- The watchdog resumes independent transport/backend health monitoring after recovery.
- System recovery is limited to safe bind/rebind/reattach behavior and must never terminate or restart Android's persistent `system_server` process.
- Final Preview 3 validation should include a cold reboot followed by D2 unlock where applicable, then explicit Root/System route tests to confirm both backends recover correctly.

### Preview safeguards

- Unfinished OneUIX, CorePatch, FLAG_SECURE, and Liquid Glass experimental screens are not exposed through Preview navigation.
- CI verifies the packaged RISH launcher/client pair and protocol-v3 markers.
- CI rejects restoration of the redundant explicit-route raw Binder UID probe.
- CI verifies modern Xposed descriptors, the TokenX Xposed entry point, System Server backend markers, and absence of retired helper backends.

### Known preview validation

The explicit `--system` TokenX RISH protocol-v3 route has now been physically verified on the Android 17 test device: the request is classified as `backend=system`, attaches to the `SYSTEM_SERVER` backend, and returns UID 1000 with `SYSTEM_OK`. The explicit `--root` route has also been physically verified to return UID 0 with `ROOT_OK`, while Root and System remain independent backends.

Root, System, and Shell are independent routes. A pass on one backend does not imply the others are active or tested on the same boot.

### Before publishing

1. Install the final Preview candidate from CI.
2. Verify `rish --system -c 'id; echo SYSTEM_OK'` returns UID 1000 and `SYSTEM_OK`.
3. Reconnect Root and verify `rish --root -c 'id; echo ROOT_OK'` returns UID 0 and `ROOT_OK`.
4. Verify Shell/ADB routing where configured.
5. Reboot, pass the D2 gate if installed, and repeat System verification.
6. Confirm enabled-app authorization survives the reboot.
7. Confirm System stop/detach never restarts or terminates `system_server`.
8. Publish the exact tested commit as the GitHub Preview tag/release.
