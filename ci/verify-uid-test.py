#!/usr/bin/env python3
"""Check real packaged APK manifests, certificates, endpoint DEX and embedded payloads."""
from pathlib import Path
import os
import re
import subprocess
import zipfile

sdk = Path(os.environ['ANDROID_HOME']) / 'build-tools' / '37.0.0'
def run(*args):
    return subprocess.check_output(list(map(str, args)), text=True)

manager, = Path('manager/build/outputs/apk/debug').glob('*.apk')
ordinary, = Path('uid-test/build/outputs/apk/ordinary/debug').glob('*.apk')
system, = Path('uid-test/build/outputs/apk/system/debug').glob('*.apk')

def certificate(apk):
    output = run(sdk / 'apksigner', 'verify', '--verbose', '--print-certs', apk)
    # Build Tools 37 identifies verified certificates by scheme ("V2 Signer")
    # instead of the older "Signer #1" label. Multiple schemes may repeat the
    # same certificate; multiple signer identities must still fail.
    digests = set(re.findall(r'(?:Signer #\d+|V\d+(?:\.\d+)? Signer):? certificate SHA-256 digest: ([0-9a-f]+)', output))
    assert re.search(r'Number of signers: 1(?:\n|$)', output) and len(digests) == 1, (apk, output)
    return next(iter(digests))

assert certificate(ordinary) == certificate(system), 'Companion flavors have different signers'
assert certificate(manager) != certificate(ordinary), 'Manager signing identity must not gain shared UID trust'
versions = []
for apk, shared in [(manager, False), (ordinary, False), (system, True)]:
    manifest = run(sdk / 'aapt2', 'dump', 'xmltree', apk, '--file', 'AndroidManifest.xml')
    assert ('sharedUserId' in manifest) == shared, (apk, manifest)
    if shared:
        assert 'android.uid.system' in manifest
    assert not re.search(r'android:process[^\n]*="system"', manifest), 'Companion must not host components in framework process'
    badging = run(sdk / 'aapt2', 'dump', 'badging', apk)
    versions.append(re.search(r"versionCode='(\d+)'", badging)[1])
    if apk != manager:
        assert "name='moe.shizuku.tokenx.uidtest'" in badging
        minimum = re.search(r"^(?:minSdkVersion|sdkVersion):'(\d+)'$", badging, re.MULTILINE)
        assert minimum and int(minimum[1]) == 36, (apk, badging)
        with zipfile.ZipFile(apk) as archive:
            dex = b''.join(archive.read(n) for n in archive.namelist() if re.fullmatch(r'classes\d*\.dex', n))
            assert b'moe/shizuku/uidtest/ProbeService' in dex
            assert b'moe.shizuku.tokenx.uidtest.IProbe' in dex
            assert not any(n.startswith('lib/') for n in archive.namelist()), 'No borrowed native injector permitted'
assert len(set(versions)) == 1, versions
with zipfile.ZipFile(manager) as archive:
    assert archive.read('assets/tokenx-uid-test/ordinary.apk') == ordinary.read_bytes()
    assert archive.read('assets/tokenx-uid-test/system.apk') == system.read_bytes()
    dex = b''.join(archive.read(n) for n in archive.namelist() if re.fullmatch(r'classes\d*\.dex', n))
    for marker in [b'TokenXUidTestSigning', b'UidTestLineagePolicy', b'UidTestActivity', b'signing-recovery.json']:
        assert marker in dex, f'Missing companion implementation: {marker!r}'
print('Companion manifests, dedicated signer, build IDs, Binder endpoints and exact embedded APKs verified')
