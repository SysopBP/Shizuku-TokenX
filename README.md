<div align="center">

<img src="docs/tokenx-app-icon.png" width="132" alt="TokenX app icon">

# Shizuku-TokenX

### Shizuku-compatible unified multi-backend privilege engine for Android

**v14.2.0-TKN-preview.1 · Preview**

**System / UID 1000 · Root / UID 0 · Shell / UID 2000 · RISH Protocol v3 · System Server RPC**

[![Build](https://github.com/SysopBP/Shizuku-TokenX/actions/workflows/tokenx-debug.yml/badge.svg)](https://github.com/SysopBP/Shizuku-TokenX/actions/workflows/tokenx-debug.yml)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)

</div>

> [!IMPORTANT]
> **Read the README before installing or updating Shizuku-TokenX.** Root / UID 0 requires a working root solution. System / UID 1000 uses TokenX's LSPosed `_TKN` system_server RPC and the manager-owned TokenX transport. The current System path does **not** require the legacy Serv.apk/UID-1000 package bridge.

## Preview status

This is a **preview build** intended for device testing before beta/stable. Root, System, and Shell routing are being validated independently; do not treat a successful System route as proof that Root or Shell has also been tested on the same boot. The packaged rish client uses **TokenX RISH protocol v3** with manager-classified backend identity.

## Shizuku-TokenX at a glance

Shizuku-TokenX combines Shizuku-compatible app authorization with multiple Android privilege backends: **System / UID 1000**, **Root / UID 0**, and **Shell / UID 2000**. The current interface includes the TokenX Control Center, execution routing, System Server Bridge verification, App Ops, Firewall, Autostart, Root Console, Shell, and Diagnostics & Logs.

### Before installing

1. Read the **Requirements** and **Installation notes** below.
2. For the System / UID 1000 backend, enable the TokenX LSPosed module for `system_server` and follow [SYSTEM_UID_INSTALL.md](SYSTEM_UID_INSTALL.md).
3. Verify the System Server Bridge after reboot before relying on UID 1000 routing.
4. Remember that UID 1000, UID 0, and UID 2000 are different Android security contexts.
5. Kiosk D2 Guardian integration is optional and only applies when D2 is installed.

## What TokenX has become

TokenX started as an effort to push Shizuku beyond a single execution path. During development it became clear that **authorization**, **transport**, **backend identity**, and **framework-side System Server access** are different problems and should not be collapsed into one global state.

The result is a unified privilege environment where Root and System can stay online at the same time, Shell remains available as a fallback/ADB route, applications keep their authorization independently of the active backend, and the manager can observe each route separately.

The current design is built around five principles:

1. **One authorization layer, multiple execution backends.**
2. **Backend configured is not the same as backend alive.**
3. **Changing the default route must not tear down another healthy backend.**
4. **Interactive UID-1000 execution must never require killing or restarting Android's persistent `system_server`.**
5. **Runtime identity must be verified, not inferred from a package name or package UID.**

### Current capability map

| Capability | State | Notes |
| --- | --- | --- |
| Root backend | UID 0 | Independent Binder, heartbeat, client count and RISH route |
| System backend | UID 1000 | Independent Binder and explicit RISH route |
| Shell / ADB | UID 2000 | Wireless, USB and PC/ADB paths |
| Root + System concurrently | Supported | Both privilege backends can remain bound |
| Default route | Selectable | Does not redefine authorization |
| Per-app routing foundation | Supported | Authorization and execution route are separate |
| RISH Protocol | v3 | Explicit Root/System/Shell selectors |
| System Server RPC | Verified architecture | Separate LSPosed `_TKN` framework-side path |
| D2 protection | Integrated | Optional protected boot/startup boundary |
| Watchdog | Live | Transport/backend heartbeat and health |
| Apps manager | Integrated | Grant/revoke, filtering, backend-aware controls |
| App Ops | Integrated | Labs capability |
| Shell | Integrated | In-app command tooling |
| Firewall | Integrated | Labs capability |
| Autostart | Integrated | Labs capability |
| Root Console | Integrated | UID 0 tooling |
| TokenX Control Center | Integrated | Root · D2 · System RPC |
| Secure Chain Monitor | Integrated | Live D2/System RPC observation |
| Device & Runtime | Integrated | Expandable manager/kernel/security diagnostics |
| Restart controls | Integrated | Soft reboot and System UI restart |
| UI systems | MIUIX + Material | TokenX theme/style support |

## The three privilege routes

### Root — UID 0

The Root backend is the unrestricted rooted-device execution route. It has its own lifecycle and can be started, stopped and restarted without using the System backend as a proxy.

A healthy Root route can be tested with:

```bash
export RISH_APPLICATION_ID=com.termux
./rish --root -c 'id; echo ROOT_OK'
```

A verified session returns UID 0 and `ROOT_OK`.

### System — UID 1000

TokenX exposes an explicit System route:

```bash
./rish --system -c 'id; echo SYSTEM_OK'
```

The physical-device validation path has returned:

```text
TOKENX_RISH_PROTOCOL=3
TOKENX_RISH_REQUEST backend=system
TokenX session attached backend=SYSTEM_SERVER
uid=1000(system) gid=1000(system)
SYSTEM_OK
```

The manager can therefore describe this route as **System Server — UID 1000**, but the wording has an important technical boundary: the interactive command worker is isolated from Android's persistent `system_server` process.

### Shell — UID 2000

Shell remains the Shizuku/ADB-compatible route and fallback. TokenX surfaces Wireless Debugging, USB Debugging and PC/ADB independently so the UI does not pretend that all UID-2000 connectivity is one indistinguishable state.

```bash
./rish --shell -c 'id; echo SHELL_OK'
```

Plain `./rish` uses the configured default route.

## RISH Protocol v3 and the transport breakthrough

One of the largest development changes was moving the packaged RISH path from a legacy single-backend assumption to a TokenX-aware client.

The packaged `rish_shizuku.dex` contains the **TokenX Multi-Backend Client / TokenXTransportClient**. Protocol v3 carries the requested backend through the manager-owned transport:

```text
rish
  └─ TokenX RISH Protocol v3
       └─ TokenXTransportClient
            └─ manager-classified route
                 ├─ ROOT          → UID 0
                 ├─ SYSTEM_SERVER → UID 1000
                 └─ SHELL         → UID 2000
```

### Android 17 finding: manager-classified identity

During Android 17 testing, an important transport problem surfaced: re-probing raw Binder caller identity at the wrong point in the handoff could lose the route classification or produce an identity that did not represent the backend the manager had already authenticated.

TokenX v3 therefore treats the **manager-classified backend/UID handoff** as authoritative for an explicit routed request rather than redundantly trying to rediscover the same fact later in the transport.

This is one of the key reasons explicit `--root` and `--system` routing can coexist reliably.

## Root + System at the same time

TokenX no longer models privilege as a single global server that must be replaced whenever the user changes modes.

The manager tracks the backends independently:

```text
Dual Backend Active
├─ ROOT   → Bound → UID 0
├─ SYSTEM → Bound → UID 1000
└─ DEFAULT → Root or another selected route
```

The default answers **where an unpinned request should go**. It does not answer **which backends are allowed to stay alive**.

This distinction enables the Global Connections UI to report a Root Binder and System Binder simultaneously, each with its own heartbeat and client count.

## System Server RPC: separate from the UID-1000 interactive route

TokenX deliberately separates two concepts that are easy to confuse:

```text
Interactive System route
./rish --system
   ↓
TokenX v3 transport
   ↓
SYSTEM_SERVER backend classification
   ↓
isolated UID-1000 execution

Framework-side System Server RPC
TokenX
   ↓
LSPosed / Xposed _TKN bridge
   ↓
real Android system_server
   ↓
supported framework operation
```

The second path is the one used when TokenX needs verified framework-side access. Development checks have used runtime evidence such as UID 1000, process identity and the `u:r:system_server:s0` SELinux domain rather than declaring success merely because an APK was assigned Android's system UID.

This separation is also a safety feature: TokenX's System controls use **bind/rebind/detach semantics**. They must never terminate or restart the persistent Android `system_server` process.

## What we learned from the UID-1000 bridge experiments

Earlier TokenX development explored systemless UID-1000 packages, including the `com.vikram.exp` / Serv bridge path. Those experiments were valuable because they established several boundaries:

- A package registered as `android.uid.system` / UID 1000 proves package identity, **not** that its code is executing inside `system_server`.
- A UID-1000 worker can perform useful privileged operations while remaining a separate process/context.
- Framework-side identity needs its own runtime proof and RPC path.
- Interactive command execution should remain outside `system_server`.
- System lifecycle controls must be fail-safe: losing a Binder is recoverable; killing `system_server` is not an acceptable recovery mechanism.

The current TokenX architecture keeps the useful UID-1000 execution model while treating the LSPosed `_TKN` RPC as the distinct framework-side System Server path.

## Watchdog, heartbeat and backend health

The TokenX watchdog evolved from a generic “is Shizuku running?” check into multi-backend observation.

The transport uses a lightweight **HELLO** health transaction, while Root, System and Shell health can be checked through their own Binder state. The manager can distinguish states such as **ONLINE**, **STALE** and **OFFLINE**, and expose backend/client information without taking ownership of Android's `system_server` lifecycle.

Current UI surfaces include:

- TokenX Multi-Backend Transport heartbeat.
- Root heartbeat and active-client count.
- System heartbeat and active-client count.
- Transport generation/protocol information.
- Backend availability versus configured/default route.
- Independent connection state instead of a single misleading global “connected” flag.

## D2-protected startup

Kiosk D2 Guardian integration became an important boot-order constraint during development.

When D2 is present, TokenX can hold privileged provisioning behind the D2 gate until the lock screen has been dismissed. The integration is intended to preserve the protected boot boundary rather than race around it.

Conceptually:

```text
Boot
  ↓
D2 gate closed
  ↓
TokenX privileged startup waits
  ↓
D2 unlock / gate release
  ↓
TokenX backend provisioning
  ↓
Root / System health verification
```

D2 remains optional; normal startup is used when it is not installed.

## Authorization persistence and client routing

A major architectural lesson was that **permission state should survive backend churn**.

TokenX persists the user's desired authorization and can replay it when a new live Binder appears. A backend crash, detach, restart or reboot is therefore not interpreted as the user revoking an app.

The Apps interface builds on this with:

- Granted and revoked views.
- User, System, Disabled and Hidden filters.
- Search and sorting.
- Batch grant/revoke.
- Root and System UID route filters.
- Per-app authorization controls.
- Foundation for persistent per-app backend routing.

## Global Connections

The Global Connections section is designed as an operational view of the privilege engine rather than a decorative status page.

It can surface:

- **TokenX Multi-Backend Transport** — protocol/client transport health.
- **Root · UID 0** — Root Binder, heartbeat, client count and `./rish --root`.
- **System · UID 1000** — System Binder, heartbeat, client count and `./rish --system`.
- **Wireless debugging · UID 2000**.
- **USB debugging · UID 2000**.
- **PC / ADB · UID 2000**.
- Expandable **Device & Runtime** information.

This makes it possible to see *why* a route is ready instead of reducing the entire runtime to one green/red indicator.

## System Integration health

The System Integration panel exposes the chain separately:

```text
D2 protection
   ↓
Xposed / LSPosed
   ↓
System Server RPC
```

The manager can report System UID 1000 state, D2 gate protection, native compatibility, Xposed RPC state, RPC route state and Binder state. The **Check System Integration** action is intended to verify the chain rather than infer it from a single installed component.

The TokenX Control Center expands that view with System Hook Health, including LSPosed detection, PackageManager/System UID observations, System Server identity and `_TKN` RPC verification.

## Labs and power-user tools

The current Labs surface intentionally exposes the features that are useful enough to test as part of TokenX:

- **App Ops**
- **Shell**
- **Firewall**
- **Autostart**
- **TokenX Control Center** — Root · D2 · System RPC
- **Root Console** — UID 0
- **Secure Chain Monitor** — live D2/System RPC observation

Experimental ideas that are not ready should not be presented as completed capabilities simply to make the feature list longer.

## Startup and recovery controls

TokenX includes operational controls around the privilege engine:

- Start on boot.
- Watchdog/recovery.
- Root as a selectable default start method.
- USB and wireless-debugging behavior controls.
- Start without Wi-Fi where supported.
- Custom System start method support.
- Soft reboot.
- System UI restart.

The System path is deliberately handled differently from Root: Root may own a real process lifecycle, while System uses safe connection/rebind/detach behavior and never treats `system_server` as a disposable TokenX process.

## Device & Runtime diagnostics

The expandable Device & Runtime section exists to make debugging reproducible. TokenX development repeatedly showed that “installed” and “working” are different states, so the manager surfaces runtime facts such as manager/backend state, Android/platform context and security/runtime observations rather than hiding them behind a generic status.

This same philosophy drives TokenX's exported diagnostics and logs: record the route, identity, Binder state and gate/lifecycle evidence needed to explain a failure.

## New theme capability — MIUIX + Material

TokenX now includes a **new theme capability with MIUIX + Material support**. This is a first-class TokenX appearance feature, not just a glass-style refresh: users can choose the UI design system while the same multi-backend engine, routing, authorization and diagnostics remain underneath.

- **MIUIX** — TokenX's MIUIX presentation option.
- **Material** — the Material-based presentation option.
- **TokenX glass/floating surfaces** — retained where configured by the selected appearance options.

Theme selection is intentionally isolated from privilege state: changing the UI style does not reconnect, replace or otherwise alter Root, System or Shell backends.

The current interface also includes:

- Floating bottom navigation.
- Glass/frosted cards and controls where selected.
- Backend-aware status cards.
- Expandable runtime details.
- Consistent Root/System/Shell identity labels.
- Transparent top app bars over the TokenX background on the main Apps, Labs, Settings and TokenX views.

## Screenshots

<div align="center">

<img src="docs/TokenX_collage_equal_sizes.png" width="100%" alt="Shizuku-TokenX v14.2.0-TKN interface and capability preview">

</div>

## About TokenX

TokenX extends the Shizuku foundation into a multi-backend Android privilege engine while preserving Shizuku API compatibility for client apps.

Instead of treating System, Root, and Shell as separate authorization systems, TokenX keeps app authorization independent from the execution backend. An app can remain enabled while TokenX changes the backend used to reach Android system services.

```text
Client app
   ↓
TokenX authorization / Shizuku-compatible Binder
   ↓
TokenX Router
   ├─ System / UID 1000
   ├─ Root / UID 0
   └─ Shell / UID 2000
   ↓
Android system services
```

TokenX is a fork in the Shizuku family. The original Shizuku server, API, shell, and core architecture come from [RikkaApps/Shizuku](https://github.com/RikkaApps/Shizuku). This project also carries work from [thedjchi/Shizuku](https://github.com/thedjchi/Shizuku).

## Current architecture

### TokenX Privilege Engine

The manager coordinates the available privilege paths and reports the runtime state separately from the configured default.

- **System** — UID 1000 path provided through the TokenX LSPosed `_TKN` RPC plus the manager-owned TokenX transport.
- **Root** — UID 0 backend for rooted devices.
- **Shell** — UID 2000 Shizuku/ADB path and fallback.
- **Token Boot** — coordinates startup while Root, System and Shell remain independently discoverable; selecting one route does not replace the others.
- **Watchdog** — monitors the active engine and recovery path.
- **TokenX Router** — keeps client authorization separate from the backend currently serving it.

A package running as UID 1000 is not, by itself, proof that code is executing inside the real `system_server` process. TokenX therefore verifies bridge/runtime state instead of inferring it only from package UID.

## TokenX System Server Integration

The current System / UID 1000 framework path uses the TokenX LSPosed module and the private **_TKN Binder RPC** inside Android's real `system_server` process. TokenX verifies the returned identity directly: UID 1000, process `system_server`, and SELinux `u:r:system_server:s0`.

Root/Shizuku remains the UID 0 backend. Shell/ADB remains the UID 2000 backend/fallback. The packaged `rish_shizuku.dex` now contains the TokenX multi-backend transport client; `rish --root`, `rish --system` (or legacy `--sserver`), and `rish --shell` request UID 0, UID 1000, and UID 2000 sessions respectively. Interactive execution stays outside `system_server`.

Current integration behavior includes:

- LSPosed _TKN RPC identity verification.
- UID 1000 framework capability routing.
- D2-safe startup gating when Kiosk D2 Guardian is installed.
- Root/Shizuku lifecycle recovery and persisted app authorization.
- Safe fallback when the framework RPC is temporarily unavailable.

### D2-safe startup

On devices using Kiosk D2 Guardian, TokenX can hold the privileged companion until the D2 boot lock releases the gate. This prevents the privileged startup path from intentionally racing ahead of the lock screen.

D2 is optional. Devices without D2 use the normal bridge startup path.

## Persistent app authorization

TokenX separates **what the user authorized** from **which backend is currently running**.

When an app is enabled, TokenX persists the desired grant using the package name and Android user. When a new TokenX/Shizuku Binder is received after a server or `system_server` restart, TokenX reapplies the desired grants to the live server.

```text
Enable app
   ↓
Persist package + Android user
   ↓
Apply grant to active server
   ↓
Binder/server restarts
   ↓
TokenX receives the new Binder
   ↓
Replay persisted grants
```

A temporary backend outage is therefore not treated as an intentional revoke.

## Start methods and global routing

TokenX exposes the available launch paths instead of hiding them behind one generic state. Root, System and Shell are tracked as independent backends. A client can use the global/default route or be pinned per package to Root, System, or Shell without redefining app authorization.

| Method | Typical UID | Purpose |
| --- | ---: | --- |
| System UID | 1000 | TokenX System Server Integration |
| Root | 0 | Rooted-device backend |
| Wireless debugging | 2000 | Shizuku/ADB shell transport |
| USB debugging | 2000 | Classic ADB shell transport |

The exact operations available still depend on Android's permission and SELinux boundaries for the active backend. UID 0, UID 1000, and UID 2000 are not interchangeable security contexts.

## Systemless mounting

Legacy TokenX experiments used systemless-mounted UID-1000 packages and Meta Magic Mount RS. The current LSPosed `_TKN` System backend does not use the legacy `Serv.apk`/`com.vikram.exp` package bridge as its runtime transport. Do not install an old TokenX UID-1000 bridge solely for the current System route.

## Features inherited from Shizuku Next / thedjchi

TokenX retains the Shizuku-compatible functionality inherited through the fork it is built on, including features such as:

- Wireless debugging and pairing flows.
- Root and ADB start methods.
- Watchdog and restart handling.
- Start/stop automation intents.
- TCP mode and persistent-port tooling where supported.
- Android/Google TV support inherited from the fork.
- MediaTek fixes inherited from the fork.
- In-app shell and command tooling.
- App authorization and permission management.
- App ops, firewall, autostart, battery, and package-management tooling.
- Material 3 manager interface.
- Android 17 compatibility work.

For upstream behavior and history, see [thedjchi/Shizuku](https://github.com/thedjchi/Shizuku) and [RikkaApps/Shizuku](https://github.com/RikkaApps/Shizuku).

## Requirements

**Minimum Android version:** Android 7+

Backend-specific requirements:

- **System / UID 1000:** LSPosed with the TokenX module enabled for `system_server`; root is still required for the rooted TokenX environment and Root backend.
- **Root / UID 0:** a working root solution.
- **Wireless debugging:** Android 11+ on supported devices.
- **USB debugging:** ADB access.
- **D2-safe gate:** Kiosk D2 Guardian is optional and only affects devices where that integration is installed.

## Installation notes

This fork is signed with its own signing key. Android will not install it over another Shizuku build signed with a different key.

If migrating from official Shizuku or another fork, uninstall the conflicting package first. A server started by the previous installation may remain alive until it is stopped or the device is rebooted.

The manager keeps the Shizuku package/API compatibility expected by Shizuku client apps.

### System UID backend

The System route is formed by the TokenX manager/transport plus the TokenX LSPosed hook inside `system_server`. See [SYSTEM_UID_INSTALL.md](SYSTEM_UID_INSTALL.md) for installation and verification. The legacy standalone `Serv.apk` UID-1000 bridge is not the current runtime path.

## Diagnostics

TokenX diagnostics distinguish boot/gate sequencing, LSPosed _TKN RPC identity, Root/Shizuku lifecycle state, UID 1000 routing, and client authorization recovery.

This separation is intentional: a package can be installed correctly while the runtime Binder or privileged bridge is not healthy.

## Privacy

TokenX does not add advertising or analytics to the Shizuku foundation.

The app requires network-related permissions for Shizuku wireless-debugging functionality and update checks. Privileged operations are performed through the backend selected or available on the device.

Review the source and Android manifest before installing any privileged build.

## Building

Clone the repository with submodules:

```bash
git clone --recurse-submodules https://github.com/SysopBP/Shizuku-TokenX.git
cd Shizuku-TokenX
```

Build the manager:

```bash
./gradlew :manager:assembleDebug
```

or:

```bash
./gradlew :manager:assembleRelease
```

The debug task produces a debuggable server build suitable for development and tracing.

## Contributing

Issues, testing results, logs, and pull requests are welcome.

When reporting a backend problem, include the active method (System, Root, Wireless, or USB), Android version, device model, and the relevant TokenX/bridge diagnostics when possible.

## Development findings worth preserving

TokenX's development produced several lessons that now define the project:

- **UID is evidence, not the whole identity.** UID 1000 alone does not prove execution inside `system_server`.
- **Authorization and execution route are separate state.** An app can stay authorized while its backend changes.
- **Backend configured ≠ backend live.** UI and recovery logic must observe actual Binder/runtime health.
- **Root and System are peers, not mutually exclusive modes.** Both can stay bound.
- **Default routing is policy, not lifecycle.** Selecting Root as default must not disconnect System.
- **System lifecycle is special.** Never “restart System” by terminating Android's `system_server`.
- **Android 17 transport identity needs an explicit handoff.** Manager-classified routing avoids losing backend identity later in the Binder path.
- **Boot ordering matters.** D2 integration needs a real protected gate rather than an arbitrary delay.
- **A watchdog should observe before it acts.** Health reporting and lifecycle ownership are deliberately separate.
- **Diagnostics need proof.** Process, Binder, UID, SELinux and route evidence are more useful than a single success badge.
- **Compatibility matters.** TokenX keeps the Shizuku-compatible authorization/API model while extending how requests are executed.

## Verification philosophy

For preview development, a route is not considered proven because a UI card says it is available. TokenX has been validated with direct command output and runtime evidence.

Typical RISH checks:

```bash
export RISH_APPLICATION_ID=com.termux

./rish --root -c 'id; echo ROOT_OK'
./rish --system -c 'id; echo SYSTEM_OK'
./rish --shell -c 'id; echo SHELL_OK'
./rish -c 'id; echo DEFAULT_OK'
```

For System Server RPC, validation is kept separate from the interactive UID-1000 RISH check because they prove different things.

## Project status

**v14.2.0-TKN-preview.1** represents the transition from a modified Shizuku manager into the TokenX multi-backend architecture. The preview line is intentionally focused on validating backend independence, persistence, transport identity, safe System lifecycle, D2-aware startup and the user-facing controls that explain those states.

The project remains preview software. Device/OEM Android changes, SELinux policy, root implementation and LSPosed behavior can affect what a given backend is allowed to do.

## Credits

TokenX stands on several projects and contributions:

- **[RikkaW / RikkaApps](https://github.com/RikkaApps/Shizuku)** — original Shizuku server, API, shell, and foundation.
- **[thedjchi](https://github.com/thedjchi/Shizuku)** — the Shizuku fork this project was originally based on and the features inherited from it.
- **@wr3cckl3ss (Wreckless)** — special thanks for always being willing to help, share knowledge, test ideas, and support the continued learning and development behind TokenX.
- **@reckaH2281 (Rackah)** — special thanks for the continued help, testing, feedback, and support throughout TokenX development.
- **@Vikramaditya015** — special thanks for the continued help, knowledge, and support throughout TokenX development.
- **[@Vikramaditya015](https://github.com/Vikramaditya015)** — additional credit for work that helped inform TokenX's System Server development.
- **[Meta Magic Mount RS](https://github.com/Tools-cx-app/meta-magic_mount-rs)** — systemless Magic Mount layer used by the current TokenX System Server Integration setup.
- Everyone who contributed to upstream Shizuku and its forks, plus the translators and testers who continue to help validate TokenX.

## License

Unless a file states otherwise, project code remains licensed under the repository's [Apache 2.0 license](LICENSE). Third-party projects and inherited components remain subject to their respective licenses.

---

<div align="center">

**Shizuku-TokenX — one authorization layer, multiple Android privilege backends.**

</div>
