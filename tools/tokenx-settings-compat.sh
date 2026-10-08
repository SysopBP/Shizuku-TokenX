#!/system/bin/sh
# TokenX 633: explicit, opt-in settings compatibility.
# Run from Termux as: sh tools/tokenx-settings-compat.sh put system tokenx_probe value
# Uses KernelSU root intentionally; never claims native UID 1000 writes.
set -u
if [ "$#" -lt 3 ] || [ "$#" -gt 4 ]; then
  echo "Usage: $0 put|delete|get system|secure|global KEY [VALUE]" >&2
  exit 64
fi
action=$1; namespace=$2; key=$3
case "$action" in put|delete|get) ;; *) echo "Invalid action" >&2; exit 64;; esac
case "$namespace" in system|secure|global) ;; *) echo "Invalid namespace" >&2; exit 64;; esac
case "$key" in ''|*[!a-zA-Z0-9_.-]*) echo "Invalid key" >&2; exit 64;; esac
if [ "$action" = put ] && [ "$#" -ne 4 ]; then echo "Value required" >&2; exit 64; fi
if [ "$action" != put ] && [ "$#" -ne 3 ]; then echo "Unexpected value" >&2; exit 64; fi
echo "TOKENX_SETTINGS_ROUTE=EXPLICIT_ROOT_COMPAT" >&2
echo "TOKENX_NATIVE_UID1000_SETTINGS_WRITE=false" >&2
if [ "$action" = put ]; then
  # Avoid shell interpolation of caller-controlled values.
  su -c 'exec settings put "$1" "$2" "$3"' -- "$namespace" "$key" "$4"
else
  su -c 'exec settings "$1" "$2" "$3"' -- "$action" "$namespace" "$key"
fi
