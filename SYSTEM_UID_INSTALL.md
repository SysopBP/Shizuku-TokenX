# TokenX System / UID 1000 Installation

This document describes the current TokenX System Server architecture.

## Current architecture

TokenX uses three distinct privilege paths:

- **Root / UID 0** — Shizuku started through KernelSU/root.
- **System Server / UID 1000** — the TokenX LSPosed module installs the private **_TKN Binder RPC** inside Android's real `system_server`; the manager-owned TokenX transport exposes authorized UID-1000 sessions to the new packaged client.
- **Shell / UID 2000** — standard Shizuku ADB/wireless fallback.

The System Server path is considered verified only when the live _TKN RPC reports:

- UID **1000**
- process **system_server**
- SELinux context **u:r:system_server:s0**

A package merely having UID 1000 is not sufficient proof of system_server execution.

## Requirements

1. A working TokenX installation.
2. KernelSU or the compatible root configuration used by the device.
3. LSPosed with the TokenX module enabled for **system_server**.
4. Kiosk D2 Guardian only when the protected D2 startup boundary is desired. D2 is optional to TokenX's System transport.

## Installation

1. Confirm root is working and grant TokenX superuser access.
2. Install/update TokenX.
3. Enable the TokenX LSPosed module for system_server.
4. If using Kiosk D2 Guardian, keep its supported D2 gate integration enabled and reboot when that integration requires it.
5. Open TokenX and run **Check System Integration**.
6. Verify **System Server RPC** reports the _TKN identity as UID 1000 / system_server.
7. Verify Root/Shizuku independently reports UID 0 when Root mode is selected.

## D2 startup boundary

D2 is optional. When installed, its gate protects TokenX startup so privileged integration does not intentionally race ahead of the D2 lock screen. After reboot, allow the D2 lock screen to appear normally, unlock it, then verify TokenX recovery.

## rish and the new TokenX client

`rish_shizuku.dex` contains the TokenX RISH protocol v3 client. The launcher selects the requested backend; the manager resolves and classifies the backend slot, and the client validates the routed UID delivered with that session. This avoids a redundant raw Binder UID probe that can report `-1` on Android 17 even when the selected System Binder is healthy:

```text
rish --root              -> Root session / UID 0
rish --system            -> System session / UID 1000
rish --sserver           -> legacy alias for --system
rish --shell             -> Shell session / UID 2000
TOKENX_RISH_BACKEND=...  -> explicit backend selector
```

The client talks to the manager-owned TokenX transport. Interactive rish remains outside Android's persistent `system_server`; the `_TKN` RPC provides the verified framework rendezvous rather than making the terminal process itself `u:r:system_server:s0`.

## Verification

Use the in-app **Check System Integration** and **Verify System Server** controls. A healthy configuration should show:

- D2 boundary healthy when D2 is installed.
- LSPosed _TKN RPC active.
- System Server identity: UID 1000, process system_server, SELinux u:r:system_server:s0.
- Root/Shizuku UID 0 available when Root mode is active.
- Shell UID 2000 available as fallback where configured.

## Backend lifecycle

Root, System and Shell are independent backend slots. Root can use normal process start/stop/restart. System uses safe start/rebind/detach semantics: TokenX must never terminate Android's real `system_server` to implement a System stop. Per-app routing can follow the global/default route or be pinned to Root, System or Shell.

## Recovery

If System Server verification fails:

1. Confirm LSPosed is running.
2. Confirm the TokenX module is enabled for system_server.
3. Reboot once after changing the LSPosed/module configuration.
4. If D2 is installed, unlock D2 before expecting the protected integration to finish.
5. Keep Root or Shell available as the recovery path while diagnosing the framework RPC.

Avoid running an interactive shell inside system_server. A crash or deadlock there can restart Android's framework.

## Architecture summary

```text
TokenX authorization
        |
        +-- Root / Shizuku -------- UID 0
        |
        +-- LSPosed _TKN RPC ------ system_server / UID 1000
        |
        +-- Shell / ADB ----------- UID 2000
```

The execution router keeps app authorization independent of backend lifecycle. The global/default route is used unless a package is explicitly pinned to Root, System, or Shell; a backend becoming available does not replace the Binder slot for another backend.
