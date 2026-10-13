# TokenX System Server UID 1000 provisioning

Build 674 (run 38097555780, commit c1e036e8) is the functional baseline.
Build 675 built an older manager graph which omitted the modern Xposed project and
client while retaining checks for their DEX markers. The recovery merge restores
the Build 674 tree and modern packaging; no backend verification is skipped.

## Current live native injection

The Home button now uses the ARM64 live native injector.
It stages the installed TokenX APK and verifies its Binder in the existing
System Server process, without a requested reboot or KernelSU module install.
See [the live injection implementation](tokenx-live-injection.md).

The module-based installer described below is retained for compatibility but is
no longer called by the Home provisioning action. Its old reboot verification
rules apply only to that former path.

## Retained module installer

The Home button **Provision System Server UID 1000** installs a KernelSU module
using the supported ksud module installer. Prerequisites are Android 8.1 or newer,
a 64-bit System Server, root authorization, and an enabled Zygisk API v4 runtime
for KernelSU (Zygisk Next or ReZygisk). TokenX does not install or enable that
runtime automatically. An unknown existing tokenx_system_server module is rejected
instead of overwritten.

The Zygisk API header is pinned to upstream commit 7bb941ac8edfcffd1d23761e401c45ca95409dc1; its ISC license is retained.
Provisioning is performed from the device owner profile.

The module contains the injector compiled with this APK and DEX extracted from
this APK. SHA-256 checks validate the archive, installer payload, and staged files.
After installation verifies, the confirmed operation invokes ksud soft-reboot.
There is no reboot fallback and no retry of the reboot on recovery.
KernelSU deletes customize.sh after sourcing it: the complete ZIP hash protects
that installer, and the installed checksum manifest covers the files that persist.
A module-archive regression test exercises that cleanup and DEX/ABI rejection.

Zygisk runs TokenX only in the System Server specialization callback. A root
companion transfers DEX into native memory; InMemoryDexClassLoader starts a bounded
worker after specialization. No filesystem access to /data/adb from system_server,
framework replacements, ART hooks, shared UID APKs, legacy packages, system
partition writes, or SELinux policy changes are needed.

The endpoint publishes to TokenX's existing protected provider. The manager
authenticates the UID-1000 caller and its kernel-reported PID, and validates the native Binder's identity,
functional probe, protocol/build version and a random challenge. The endpoint
accepts privileged transactions only from the installed TokenX manager UID.
Provider re-publication follows the manager process lifetime.

Root, isolated System/Shizuku and Shell slots retain Build 674 routing. Injection
uses a separate endpoint for the same _TKN contract; existing LSPosed remains the
fallback for that contract. Interactive shells remain outside system_server.

## Progress and recovery

Confirmation explicitly warns of a soft reboot and unsaved work. A persistent
WorkManager task and notifications track checking, installation, reboot,
verification, success or failure. After restart, success requires a different
System Server PID and the installed payload's real Binder challenge and framework
service probe. Root output and process presence never establish backend readiness.
The verification timeout is two minutes; failure cannot trigger another reboot.

After updating the APK, provision again to update its injected DEX snapshot.
To remove injection, disable/uninstall **TokenX System Server** in KernelSU and
restart Android through the device's normal controls. If Android cannot finish
booting, use the device's KernelSU module recovery procedure.

## Validation boundary

CI compiles the debug APK and all native ABIs, runs manager tests, verifies modern
LSPosed metadata and backend DEX markers, checks rish assets and routing, verifies
the new injection DEX/native exports/UI/installer, and checks the APK signature.

The cloud environment cannot validate physical device injection, Zygisk API
compatibility, OEM SELinux restrictions on provider handoff, the soft reboot,
notification permission behavior, or Root/System shell behavior after injection.
Those require a KernelSU device. No device was modified or rebooted during CI.
