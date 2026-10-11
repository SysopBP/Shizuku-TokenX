# Same-app TokenX UID 1000 provisioning

The main provisioning card now targets `moe.shizuku.privileged.api`, the manager
itself. It no longer directs this action to the disposable test companion or
waits for a second native Binder endpoint. Existing Root and System backends
remain independently routed. Manager UID 1000 is not System Server process identity.

The flow follows JBCompanion's shared-user enrollment and same-package reinstall
architecture. It uses adapted TokenX Java code through the existing authenticated
LSPosed `_TKN` route, not JB's unchanged native payload. The original JB payload
is hardcoded to JB's package. This implementation must not be described as the
original payload reused byte-for-byte.

CI builds ordinary and system-UID identities with identical manager package,
build and signing certificate. The ordinary debug APK contains the system APK;
the system APK does not contain a recursive copy. CI verifies both signatures.

Prerequisites: owner profile, Android 16+, existing root, and the updated TokenX
LSPosed module connected to System Framework. Reboot after installing the update
to load its new migration RPC before attempting provisioning.

The signing action only accepts the installed manager's Binder UID and targets
the fixed manager package. It preserves existing shared-user signing history and
records recovery before enrollment. Root stages checked APKs under
`/data/system/tokenx_manager_migration`. A detached shell stops the manager,
backs up its CE/DE data, reinstalls the same package as UID 1000, restores app data
with the new owner, applies SELinux file labels, and reopens TokenX. Failed system
installation or restoration attempts rollback to the ordinary APK and data.
Signing trust is retained on rollback until the ordinary manager explicitly
runs Restore signing after rollback; it is never removed while system-UID apps
still depend on the certificate. Recovery files are retained and never silently
overwritten. Logs: `/data/system/tokenx_manager_migration/migration.log`.

Android Keystore entries belong to the old UID and are not migrated by restoring
files. Root authorization and LSPosed state must be checked after reinstall. No
uninstall blocking or automatic framework restart is performed. Use existing
Restart Controls to soft reboot, then Verify app UID & Xposed connection. Both
actual app UID 1000 and real System Server identity/framework probes must pass.
The dedicated companion SYSTEM Binder is connected using its existing starter
and must answer its getUid transaction as 1000 before migration and verification.

Future updates to an already migrated manager must use the matching system-UID
APK; an ordinary APK cannot update across a shared-UID change. Keep the ordinary
APK for recovery. A successful build is not device verification of migration,
data recovery, root routing, or reboot persistence.
