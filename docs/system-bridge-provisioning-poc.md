# TokenX System Bridge provisioning proof of concept

This branch begins a **non-destructive** test path for a possible independent privileged companion. It does **not** yet build or install a persistent UID 1000 service.

## Lifecycle
1. Rooted preflight: run `su -c 'sh /path/to/system-bridge-preflight.sh'`.
2. Verify existing Root, System, and Shell routes before any changes.
3. Provisioning is **not implemented** in this POC. Do not interpret an existing UID 1000 package as a persistent independent service.
4. Soft reboot should only be offered after an actual supported provisioning step and explicit confirmation.
5. After a framework restart, compare package UID, granted permissions, Binder service identity, process SELinux context, and functionality.
6. After a **full reboot without root**, repeat the checks; only mark persistent when independent boot-recognized service availability is demonstrated.

## Guardrails
- No injection into `system_server`, package-manager state mutation, or automated framework restart.
- Never terminate the actual Android `system_server`.
- Never run unrestricted commands on behalf of third-party apps through a persistent bridge.
- Authenticate clients and authorize individual operations in any future service.
- KernelSU systemless mounts are not proof of root-independent persistence.
- Provide a rollback/recovery path before testing any privileged system installation.

## Current status
Preflight script only; no APK or system companion produced yet. The existing bridge module remains unchanged.
