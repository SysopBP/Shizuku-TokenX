# TokenX live System Server injection

The Provision System Server UID 1000 action now uses live native injection into
the existing ARM64 System Server process. It stages the installed TokenX APK as
a read-only payload, invokes the supplied JBCompanion injector with a TokenX
native loader, and verifies TokenX's authenticated Binder endpoint.

This action requires existing root. It does not install a KernelSU module,
invoke `ksud`, request a soft reboot, change SELinux mode, or write a Zygisk
disable marker. Existing Zygisk/LSPosed modules remain independently managed.

The Java payload verifies the actual System Server UID, process name and SELinux
context. The manager checks framework services, the same target PID, payload
build and a fresh per-operation request token. Returning from the injector is
not sufficient to claim success. A resumed worker only verifies the recorded
operation; it cannot automatically repeat an injection.

## Reference provenance

The user supplied `JBCompanion_1.0.apk` and its staged
`JBCompanion-system.apk`. Those files were byte-for-byte identical:

- APK SHA-256: `197e3830c1e6551c3998114cf0aaa34fda7537b6dca9e616c6b69f63df07e33d`
- Package: `jb.companion`; version code 1; shared UID `android.uid.system`.
- Certificate SHA-256: `14296df529682e1ada0a4a37f2cab5751f0951706051435394d597168f45dd8b`
- Original ARM64 `libinject.so` SHA-256:
  `35bbb34e14cddc2c0c1df23321ac118eab73bdac151d7494f0b76fd33c80140b`.

The original injector is packaged unchanged as `libtokenx_direct_inject.so`.
Its root CLI takes a process ID, native payload path and `entry` symbol.
TokenX's own C++ loader attaches a worker to ART, uses a DexClassLoader for the
staged installed APK and calls `TokenXSystemServerInjection.startDirect`.
The supplied JBCompanion payload is not bundled or executed.

## Scope and testing

This matches the live native injection architecture of the reference. It does
not clone the complete JBCompanion application. In particular it does not move
the TokenX manager to shared UID 1000, replace the platform signing lineage,
block app uninstallation or silently provision an app after boot. The existing
explicit UID-test companion workflow is separate from native injection.

ARM64 is the only supported architecture for the supplied reference injector.
The build checks both the new loader and the exact reference binary checksum.
Device verification is still required: a successful CI build is not proof of
successful System Server injection on the Samsung test device.

Diagnostics use `TokenX/DirectInjection` and `TokenX/Injection` log tags. SELinux
or ptrace denials are reported as failures; the app does not change security
policy to suppress them. Native code runs in a critical Android process, so a
payload or compatibility fault can still restart the framework even though the
action itself never requests a restart.
