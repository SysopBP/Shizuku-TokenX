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


## Build 640 device regression — 2026-10-09

Device: Samsung SM-S948U1, Android 17 / One UI 9. Termux caller: `com.termux`, `RISH_APPLICATION_ID=com.termux`.

### Observed route behavior
- `./rish --root`: binder verified UID 0, shell `uid=0(root)` / `u:r:ksu:s0`. Compound `settings put system tokenx_640_compare test640` returned 0; readback was `test640`; deletion returned 0.
- `./rish --system`: binder verified UID 1000, isolated shell `uid=1000(system)` / `u:r:ksu:s0`. Compound `settings put system tokenx_640_compare test640` returned 255; deletion returned 255; both raised `NullPointerException`.
- System namespace, Secure namespace and Global namespace: compound put/delete all returned 255; readback was `null`.
- System route read `screen_off_timeout=600000`, and `cmd appops get com.termux RUN_IN_BACKGROUND` reported `allow`.
- System connection banner printed `TOKENX_ROUTE_BUILD=637` although the installed test target was Build 640; shell environment variables of the same names were empty. The marker's source must be traced before claiming stale binaries.

### Stack trace and interpretation
`SettingsProvider.getCallingPackage -> AppOpsManager.checkPackage -> AppOpsService.checkPackage -> NullPointerException`. `TokenXXposedEntry.kt:132` also appears in the Binder interception stack; this alone does not prove the hook is the cause. UID 1000 identity does not establish valid calling-package attribution or native Settings mutation capability.

### Build 641 acceptance criteria
1. Preserve Root UID 0, System UID 1000, Shell UID 2000, D2 gate, reboot recovery, and UI themes.
2. Explicitly classify a command as native-System, root-compatibility, or unsupported. Never silently elevate a compound script.
3. For explicitly authorized, narrowly scoped Settings compatibility commands, preserve exact exit status, stdout and stderr and show the effective route.
4. Cover standalone and compound commands across system/secure/global; verify writes by readback and successful cleanup.
5. Cover Root unavailable, unauthorized caller, shell backend, and post-reboot behavior.
6. Resolve the Build 637 banner marker and report launcher, runtime and APK versions separately.
7. Do not claim native UID 1000 Settings writes are fixed until verified on-device.
