# TokenX UID 1000 test companion

This experiment is independent of the main TokenX manager and its existing Root/System routes. It starts with an ordinary app and replaces only that disposable app with a source-built variant declaring `android.uid.system`. The companion runs in its own process, not inside `system_server`.

Open **System Server injection → UID 1000 test companion** in TokenX. Install the ordinary app, verify its actual UID and Binder identity, then explicitly confirm provisioning. Provisioning requires Android 16+, root and the current build's authenticated native TokenX System Server endpoint. Configure the existing KernelSU/Zygisk injection first. The test does not request a reboot.

The confirmation describes the persistent signing-trust change and deletion of test data. Only the dedicated companion certificate is enrolled. The main manager's certificate is rejected. A fresh disposable certificate is generated per CI run; the private key is never uploaded. The two companion flavors share that certificate.

The Package Manager reflection contract, existing signing lineage and actual `/data/system/packages.xml` contents are checked before changing signing state. The XML/ABX resolver reads certificate indices and capability flags. The existing platform lineage and flags are preserved; the proposed test capability is verified before applying it. A root-owned-by-system recovery record is written and read back at `/data/system/tokenx_uid_test/signing-recovery.json` before mutation. Package settings are read back after synchronous persistence.

The manager checks root command exit codes, bounded execution and staged APK hashes. It uninstalls only `moe.shizuku.tokenx.uidtest`, installs its matching system flavor, binds the source-built companion endpoint and verifies its protocol, build, random nonce, PID, UID, process and SELinux identity. Separately, TokenX's System Server handshake and package UID probe must pass. UID 1000 alone is never reported as proof of framework injection.

**Remove & restore signing state** verifies removal before restoring the exact recorded lineage. It refuses automatic restoration if another UID 1000 package uses that certificate, the platform certificate changed, or the lineage differs from the recorded state. Failure retains the recovery record. Interrupted provisioning is not silently retried. No global package-verifier setting is changed, no uninstall blocking is added, and no Samsung DMC/VaultKeeper functionality or bootstrapv2 binaries are copied.

Use **Remove & restore** with the matching manager APK before upgrading to another build or CI rerun. Keep that APK until recovery completes. Different builds have different disposable certificates; automatic adoption of another build's recovery record is deliberately refused. If injection is unavailable, restore the matching build's injection first. Do not manually delete the recovery record or replace all of `packages.xml`.

## Verification

CI builds the manager and both companion variants, verifies their actual manifests, signatures, build IDs, DEX endpoints and exact embedded APK bytes, and runs lineage preservation/recovery unit tests alongside the existing route and injection checks. Passing these checks establishes packaging and policy behavior, not successful provisioning on a phone.

Device verification still required: ordinary UID → UID 1000 transition; OEM Package Manager contract/allowlist compatibility; actual SELinux and Binder behavior; persistence after a manual reboot; removal and disk-verified trust restoration; failure/interruption recovery; continued Root/System/rish operation. A previous successful bootstrapv2 test on this phone does not establish that this new implementation passes those checks.

For local builds, generate a dedicated disposable key first:

```sh
keytool -genkeypair -keystore uid-test.keystore -storepass tokenx-test-only \
  -keypass tokenx-test-only -alias uid-test -keyalg RSA -keysize 3072 \
  -validity 365 -dname 'CN=TokenX Disposable UID Test' -noprompt
./gradlew :manager:assembleDebug :manager:testDebugUnitTest
```

Never commit or distribute the test private key.
