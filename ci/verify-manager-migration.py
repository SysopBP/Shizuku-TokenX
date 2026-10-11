"""Verify the same-package APK pair with Android's official build tools."""
import re
import subprocess
import sys
import zipfile

ordinary, system, aapt, signer = sys.argv[1:]

def output(*args):
    return subprocess.check_output(args, text=True, stderr=subprocess.STDOUT)

def identity(apk):
    badging = output(aapt, "dump", "badging", apk)
    match = re.search(r"package: name='([^']+)' versionCode='([^']+)'", badging)
    assert match, f"No package identity: {apk}"
    return match.groups()

assert identity(ordinary) == identity(system), "Manager package/build differs"
assert identity(ordinary)[0] == "moe.shizuku.privileged.api"
certificates = []
for apk in [ordinary, system]:
    verified = output(signer, "verify", "--verbose", "--print-certs", apk)
    certs = {digest.lower() for digest in re.findall(
        r"(?:Signer #\d+|V\d+(?:\.\d+)? Signer):? certificate SHA-256 digest: ([0-9a-fA-F]+)", verified)}
    assert re.search(r"Number of signers: 1(?:\n|$)", verified) and len(certs) == 1, verified
    certificates.append(next(iter(certs)))
assert certificates[0] == certificates[1], "Manager APK signers differ"
normal_manifest = output(aapt, "dump", "xmltree", ordinary, "--file", "AndroidManifest.xml")
system_manifest = output(aapt, "dump", "xmltree", system, "--file", "AndroidManifest.xml")
assert "android.uid.system" not in normal_manifest
assert "android.uid.system" in system_manifest
with zipfile.ZipFile(ordinary) as apk:
    assert apk.read("assets/tokenx-manager/system.apk") == open(system, "rb").read()
    assert "assets/tokenx-manager/migrate.sh" in apk.namelist()
with zipfile.ZipFile(system) as apk:
    assert "assets/tokenx-manager/system.apk" not in apk.namelist(), "Recursive manager APK embedding"
print("Same manager package/build/signer, shared UID manifest, and staged APK pair verified")
