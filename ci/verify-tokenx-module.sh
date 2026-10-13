#!/usr/bin/env bash
# Validate a TokenX KernelSU module ZIP without installing or executing it.
set -euo pipefail
ZIP="${1:?Usage: bash ci/verify-tokenx-module.sh module.zip}"
python3 - "$ZIP" <<'PY'
import hashlib, io, pathlib, sys, zipfile
p = pathlib.Path(sys.argv[1])
with zipfile.ZipFile(p) as z:
    names = set(z.namelist())
    required = ('module.prop','customize.sh','zygisk/arm64-v8a.so','payload.sha256','payload/dex.count','payload/classes.dex')
    missing = [x for x in required if x not in names]
    if missing: raise SystemExit('Missing module files: '+', '.join(missing))
    scripts = [n for n in names if n.endswith('.sh') and not n.endswith('/')]
    for name in scripts:
        data=z.read(name)
        if b'\r' in data: raise SystemExit(f'CR/CRLF in {name}; normalize to LF')
        if b'\0' in data: raise SystemExit(f'NUL byte in {name}')
        print('Script checked:',name)
    manifest=z.read('payload.sha256').decode()
    for line in manifest.splitlines():
        if not line.strip(): continue
        digest, sep, filename=line.partition('  ')
        if not sep: raise SystemExit('Malformed sha256 manifest line: '+repr(line))
        filename=filename.removeprefix('./')
        if filename not in names: raise SystemExit('Missing hashed payload: '+filename)
        if hashlib.sha256(z.read(filename)).hexdigest().lower()!=digest.lower():
            raise SystemExit('Hash mismatch: '+filename)
    print('TokenX module structure and hashes verified')
PY
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
unzip -qq "$ZIP" '*.sh' -d "$tmp"
while IFS= read -r -d '' f; do
  /bin/sh -n "$f"
  echo "POSIX shell syntax OK: ${f#"$tmp"/}"
done < <(find "$tmp" -type f -name '*.sh' -print0)
