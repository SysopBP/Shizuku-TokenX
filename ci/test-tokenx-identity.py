#!/usr/bin/env python3
"""Guard TokenX identity, original artwork, and third-party Binder compatibility."""
from pathlib import Path
import sys
import subprocess
import tempfile
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
android = '{http://schemas.android.com/apk/res/android}'
manifest = ET.parse(root / 'manager/src/main/AndroidManifest.xml').getroot()
for node in manifest.findall('permission') + manifest.findall('permission-group'):
    assert not node.attrib[android + 'name'].startswith('moe.shizuku.manager.permission')
assert 'applicationId = "com.sysopbp.tokenx"' in (root / 'manager/build.gradle').read_text()
strings = ET.parse(root / 'manager/src/main/res/values/strings.xml').getroot()
assert strings.find("string[@name='app_name']").text == 'TokenX'
assert not list((root / 'manager/src/main/res').glob('mipmap*/ic_launcher*.png'))
for path in (root / 'manager/src/main/res').rglob('*.xml'):
    ET.parse(path)
    assert '@mipmap/ic_launcher' not in path.read_text(), path
# Source namespaces and this protocol key are not the distributed app identity.
wire_key = 'moe.shizuku.privileged.api.intent.extra.BINDER'
for path in ['manager/src/main/java/moe/shizuku/manager/ShizukuManagerProvider.kt',
             'starter/src/main/java/moe/shizuku/starter/ServiceStarter.java',
             'server/src/main/java/rikka/shizuku/server/ShizukuService.java']:
    assert wire_key in (root / path).read_text(), path
settings = (root / 'settings.gradle').read_text()
assert 'MANAGER_APPLICATION_ID = "com.sysopbp.tokenx"' in settings
assert 'PERMISSION = "com.sysopbp.tokenx.permission.API_V23"' in settings
assert 'tokenxProviderManifest.setText' in settings
# Execute the actual discovery helper: old clients, new clients, and unrelated apps.
if "--source-only" in sys.argv:
    print("TokenX source identity/XML checks passed; Java discovery execution deferred to CI.")
    sys.exit(0)
with tempfile.TemporaryDirectory() as tmp:
    tmp = Path(tmp)
    stub = tmp / 'moe/shizuku/server/BuildConfig.java'
    stub.parent.mkdir(parents=True)
    stub.write_text('package moe.shizuku.server; public class BuildConfig { public static final String MANAGER_APPLICATION_ID="com.sysopbp.tokenx"; }')
    test = tmp / 'IdentityTest.java'
    test.write_text('''import rikka.shizuku.server.ServerConstants;
public class IdentityTest {
    public static void main(String[] args) {
        if (!ServerConstants.requestsClientApi(new String[]{"moe.shizuku.manager.permission.API_V23"})) throw new AssertionError("old client");
        if (!ServerConstants.requestsClientApi(new String[]{"com.sysopbp.tokenx.permission.API_V23"})) throw new AssertionError("new client");
        if (ServerConstants.requestsClientApi(null)) throw new AssertionError("null");
        if (ServerConstants.requestsClientApi(new String[]{"android.permission.INTERNET"})) throw new AssertionError("unrelated app");
        if (!ServerConstants.PERMISSION.equals("com.sysopbp.tokenx.permission.API_V23")) throw new AssertionError("grant namespace");
    }
}''')
    subprocess.run(['javac', '-d', str(tmp), str(stub), str(test), str(root / 'server/src/main/java/rikka/shizuku/server/ServerConstants.java')], check=True)
    subprocess.run(['java', '-cp', str(tmp), 'IdentityTest'], check=True)
print('TokenX identity checks passed: own package/permissions/artwork, XML, and old/new client discovery.')
