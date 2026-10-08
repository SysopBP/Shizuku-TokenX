# TokenX Android 17 Settings compatibility — verified 2026-10-08

Tested on Samsung SM-S948U1, Android 17, via Termux `./rish --system`.

## Verified
- System Server backend classified UID 1000; isolated worker `id` returned `uid=1000(system)`, context `u:r:ksu:s0`.
- Individual `settings put` and `settings delete` commands are routed to **KernelSU root compatibility**, not native UID 1000 Settings mutation.
- `settings get` runs in isolated UID 1000 worker.
- system, secure and global: put/read/delete of `tokenx_uid1000_probe=tokenx_test` succeeded; delete reported one row each.
- Compound scripts run in UID 1000 worker, and Settings mutations there failed with `NullPointerException` at `AppOpsService.checkPackage` via SettingsProvider calling-package attribution.

## Required implementation work
1. Preserve Root/UID0, System/UID1000 and Shell/UID2000 backend behavior, D2 gate and reboot recovery.
2. Handle compound scripts explicitly. Do **not** silently elevate an arbitrary compound script to root; prefer an opt-in, narrowly scoped Settings compatibility command or an audited command dispatcher with explicit authorization and failure reporting.
3. Preserve exact exit codes/stdout/stderr for compatibility writes. Make routing visible in diagnostics and UI.
4. Regression tests: standalone and compound commands, three Settings namespaces, failed writes, root unavailable, unauthorized client, shell backend, and post-reboot.
5. Document that UID 1000 shell identity does not imply native UID 1000 Settings mutation authority.
6. Review README for stale bridge/package statements against the current build artifacts before publishing.

## Device regression commands
```sh
export RISH_APPLICATION_ID=com.termux
for ns in system secure global; do
  ./rish --system -c "settings put $ns tokenx_uid1000_probe tokenx_test"
  ./rish --system -c "settings get $ns tokenx_uid1000_probe"
  ./rish --system -c "settings delete $ns tokenx_uid1000_probe"
done
```

## Release gate
Do not label native UID1000 Settings writes as fixed; do not publish a new release before CI and device regressions pass.
