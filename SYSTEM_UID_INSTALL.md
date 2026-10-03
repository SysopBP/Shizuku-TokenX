# Shizuku-TokenX — System UID / System Server Bridge Installation

> **Advanced / root-only:** the current TokenX bridge is installed through its KernelSU module. Do not use the old manual `/data/app` or provisioning-DEX procedure.

This document describes the current **Shizuku-TokenX v14.2** System UID / System Server architecture. The old manual `Serv.dex` provisioning instructions are no longer the normal installation path.

## Requirements

- A rooted Android device with **KernelSU** (or a compatible root environment)
- **Shizuku-TokenX** installed
- **TokenX System Server Bridge** KernelSU module
- Shizuku when using Shizuku-backed TokenX features
- A reboot after installing or updating the bridge module

For **Kiosk D2 Guardian** devices, keep the D2-safe boot gate enabled. TokenX must not bypass the lock screen during boot.

## 1. Install Shizuku-TokenX

Install the current Shizuku-TokenX APK normally.

Do not manually copy the TokenX companion into `/data/app`, and do not assign UID 1000 by hand.

## 2. Install the TokenX System Server Bridge

Install the current **TokenX System Server Bridge** ZIP through KernelSU, then reboot.

The module owns provisioning of the System UID companion. The current companion package is:

```text
com.vikram.exp
```

The companion APK is exposed systemlessly as:

```text
/system/priv-app/TokenXServ/Serv.apk
```

There is no normal installation step that requires copying or executing `Serv.dex`.

## 3. Boot and unlock normally

After reboot:

1. Allow Android and KernelSU to finish startup.
2. If Kiosk D2 Guardian is installed, allow D2 to appear normally.
3. Unlock D2 before using TokenX.
4. Open Shizuku-TokenX after the device reaches its normal unlocked state.

The bridge is designed to preserve the D2 security boundary rather than allowing the companion to take over the device before unlock.

## 4. Verify the bridge

From Termux, these read-only checks can be used:

```sh
su -c '
MOD=/data/adb/modules/tokenx_system_server

echo "=== TOKENX BRIDGE STATE ==="
cat "$MOD/state" 2>/dev/null

echo
echo "=== TOKENX PACKAGE ==="
pm path com.vikram.exp

echo
echo "=== TOKENX UID ==="
cmd package list packages -U | grep -F "com.vikram.exp"

echo
echo "=== SYSTEM SERVER ==="
ps -AZ | grep -E "u:r:system_server:s0.*system_server"
'
```

A healthy installation should report the bridge state:

```text
SYSTEM_SERVER_ATTACHED
```

and `com.vikram.exp` should remain associated with **UID 1000**.

## UID 1000 rish

TokenX can provide an interactive UID-1000 shell through its direct Binder path.

A successful connection may report:

```text
TokenX RISH: clientUid=<app uid>, serverUid=1000
TokenX: DIRECT BINDER connected to System Server backend (UID 1000).
TokenX: launching isolated UID-1000 shell worker outside system_server.
```

Inside that worker, `id` should show UID/GID 1000.

### Important security distinction

The interactive rish worker is **not an arbitrary shell inside the persistent Android `system_server` process**.

The expected distinction is:

```text
Android system_server     -> uid=1000, u:r:system_server:s0
TokenX UID1000 rish       -> uid=1000, isolated worker (commonly u:r:ksu:s0)
KernelSU root             -> uid=0,    u:r:ksu:s0
```

UID and SELinux domain are separate security properties.

## Android 17 Settings behavior

On Android 17, ordinary UID-1000 Binder operations can work while some **SettingsProvider mutations** still fail because Android performs additional package/attribution validation.

TokenX therefore uses this routing model:

```text
Normal supported commands
        |
        v
UID 1000 Direct Binder
        |
        +---- Android 17 Settings mutation requiring fallback
                         |
                         v
                  Narrow root fallback
```

This is deliberately **not** a global root shell.

The fallback applies to supported command-mode Settings mutations. Commands typed later inside an already-open interactive UID-1000 rish session are not automatically rewritten through root.

## Current architecture

```text
Shizuku-TokenX
      |
      +-- Direct Binder / UID 1000
      |        |
      |        +-- isolated UID1000 rish worker
      |
      +-- TokenX System Server Bridge
      |        |
      |        +-- Serv.apk / com.vikram.exp
      |        +-- KernelSU-managed provisioning
      |        +-- D2-safe boot gating
      |
      +-- Narrow root fallback
      |        |
      |        +-- operations that Android 17 cannot complete
      |            correctly through the UID1000 path
      |
      +-- Shizuku
               |
               +-- supported Shizuku-backed operations
```

## Experimental System Server Operations

Shizuku-TokenX includes an **Experimental: System Server Operations** control.

It is an advanced, session-only control and should not be interpreted as turning the interactive terminal into an unrestricted `system_server` shell. The UID1000 rish worker remains outside the persistent `system_server` process.

## Diagnostics

The bridge keeps diagnostic information under:

```text
/data/adb/modules/tokenx_system_server/
```

Useful files can include:

```text
state
tokenx-boot.log
tokenx-provision.log
tokenx-migration.log
tokenx-verify.log
verify.sh
```

To inspect the current state without changing the device:

```sh
su -c '
MOD=/data/adb/modules/tokenx_system_server

echo "=== STATE ==="
cat "$MOD/state" 2>/dev/null

echo
echo "=== VERIFY ==="
cat "$MOD/tokenx-verify.log" 2>/dev/null

echo
echo "=== PROVISION ==="
tail -n 100 "$MOD/tokenx-provision.log" 2>/dev/null

echo
echo "=== BOOT ==="
tail -n 100 "$MOD/tokenx-boot.log" 2>/dev/null
'
```

If the module provides `verify.sh`, it can also be run directly:

```sh
su -c 'sh /data/adb/modules/tokenx_system_server/verify.sh'
```

## Troubleshooting

If TokenX does not report the System Server bridge as attached, first verify the module is installed and the companion package exists:

```sh
su -c '
ls -ld /data/adb/modules/tokenx_system_server 2>/dev/null
ls -l /system/priv-app/TokenXServ/Serv.apk 2>/dev/null
pm path com.vikram.exp
cmd package list packages -U | grep -F "com.vikram.exp"
'
```

If the package is present but execution behaves differently than expected, check both the UID and SELinux context. Do not assume UID 1000 means a process is executing inside `system_server`.

For D2-equipped devices, also inspect `tokenx-boot.log` before changing the boot gate. The D2-safe startup path is intentional.

## Do not use the old manual procedure

Current installations should **not**:

- manually create `/data/app/com.android.settings/vikram_shell`
- manually install `com.vikram.shell`
- copy `Serv.dex` to `/data/local/tmp`
- run a provisioning DEX with `app_process`
- repeatedly reprovision the companion for ordinary command failures

Those instructions described an earlier experimental implementation and do not represent the current Shizuku-TokenX bridge.

---

**Shizuku-TokenX v14.2 — UID1000 Direct Binder, KernelSU System Server Bridge, and controlled privilege routing.**
