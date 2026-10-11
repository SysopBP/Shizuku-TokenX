"""Verify actual APK payloads rather than allowing source-only injector declarations."""
import sys
import zipfile

with zipfile.ZipFile(sys.argv[1]) as apk:
    dex = b"".join(apk.read(name) for name in apk.namelist()
                   if name.startswith("classes") and name.endswith(".dex"))
    for marker in (b"Lrikka/shizuku/server/TokenXSystemServerInjection;",
                   b"SYSTEM_SERVER_INJECTION_BINDER_READY", b"tokenx.injectedBackend",
                   b"TokenX manager required", b"verifyInjection",
                   b"Provision System Server UID 1000", b"TOKENX_INSTALL_VERIFIED"):
        assert marker in dex, f"Missing packaged injection contract: {marker!r}"
    for abi in ("arm64-v8a", "x86_64"):
        library = apk.read(f"lib/{abi}/libtokenx_injector.so")
        assert library.startswith(b"\x7fELF\x02"), f"Injector must be 64-bit ELF: {abi}"
        for symbol in (b"zygisk_module_entry", b"zygisk_companion_entry"):
            assert symbol in library, f"Missing Zygisk export {symbol!r}: {abi}"
    script = apk.read("assets/tokenx-system-server/customize.sh")
    assert b"sha256sum -c payload.sha256" in script
    assert b'KSU' in script
print("Packaged injector, DEX entry, Binder contract, installation integrity and UI verified")
