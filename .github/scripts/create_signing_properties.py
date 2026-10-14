#!/usr/bin/env python3
# Modified/created by SysopBP, 2026-10-14. Licensed under Apache-2.0.
"""Write signing properties without interpolating credentials into shell code."""
import base64
import os
from pathlib import Path
import sys

def escape(value):
    out = []
    for ch in value:
        if ch in "\\:=#! ":
            out.append("\\" + ch)
        elif ch in "\n\r\t\f":
            out.append({"\n": "\\n", "\r": "\\r", "\t": "\\t", "\f": "\\f"}[ch])
        elif ord(ch) > 127:
            out.extend("\\u%04x" % int.from_bytes(b, "big")
                       for b in [ch.encode("utf-16-be")[i:i+2]
                                 for i in range(0, len(ch.encode("utf-16-be")), 2)])
        else:
            out.append(ch)
    return "".join(out)

def main():
    mode = sys.argv[1]
    names = ("KEYSTORE", "KEYSTORE_PASSWORD", "KEYSTORE_ALIAS", "KEYSTORE_ALIAS_PASSWORD")
    if mode == "lawnchair":
        names = ("KEYSTORE", "KEYSTORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD")
    if mode not in ("tokenx", "lawnchair"):
        raise SystemExit("Unsupported signing format")
    values = {name: os.environ.get(name, "") for name in names}
    if not all(values.values()):
        raise SystemExit("Signing configuration is incomplete")
    key = base64.b64decode("".join(values["KEYSTORE"].split()), validate=True)
    if not key:
        raise SystemExit("Signing keystore is empty")
    os.umask(0o077)
    Path("key.jks").write_bytes(key)
    Path("key.jks").chmod(0o600)
    if mode == "tokenx":
        props = {"KEYSTORE_PASSWORD": values["KEYSTORE_PASSWORD"],
                 "KEYSTORE_ALIAS": values["KEYSTORE_ALIAS"],
                 "KEYSTORE_ALIAS_PASSWORD": values["KEYSTORE_ALIAS_PASSWORD"],
                 "KEYSTORE_FILE": "../key.jks"}
        destination = Path("signing.properties")
    else:
        props = {"storePassword": values["KEYSTORE_PASSWORD"],
                 "keyAlias": values["KEY_ALIAS"],
                 "keyPassword": values["KEY_PASSWORD"],
                 "storeFile": str(Path("key.jks").resolve())}
        destination = Path("keystore.properties")
    destination.write_text("".join(escape(k) + "=" + escape(v) + "\n"
                                   for k, v in props.items()), encoding="ascii")
    destination.chmod(0o600)

if __name__ == "__main__":
    main()
