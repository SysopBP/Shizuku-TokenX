#!/system/bin/sh
# TokenX System Bridge provisioning preflight (read-only).
# Does NOT inject into system_server, alter package state, or reboot.
OUT="${1:-/sdcard/Download/TokenX_System_Bridge_Preflight.txt}"
mkdir -p "${OUT%/*}" 2>/dev/null
{
  echo "TOKENX_BRIDGE_PREFLIGHT_V1"
  date
  echo "=== DEVICE ==="
  getprop ro.product.model
  getprop ro.build.version.release
  getprop ro.build.fingerprint
  echo "=== CALLER ==="
  id
  echo "=== SELINUX ==="
  getenforce
  echo "=== BACKEND PACKAGE IDENTITY ==="
  for pkg in com.tokenx.bridgetest jb.companion moe.shizuku.privileged.api; do
    echo "--- $pkg ---"
    cmd package list packages -U "$pkg"
    pm path "$pkg"
    dumpsys package "$pkg" 2>/dev/null | grep -m 5 -E 'sharedUser=|userId=|codePath=|privateFlags='
  done
  echo "=== KERNELSU MODULE ==="
  ls -ld /data/adb/modules/tokenx_system_server 2>&1
  echo "=== SERVICE DISCOVERY ==="
  service list 2>/dev/null | grep -i -E 'shizuku|tokenx' | head -20
  echo "=== REBOOT STATUS ==="
  echo "Soft reboot: NOT REQUESTED"
  echo "Full reboot persistence: NOT VERIFIED"
  echo "Provisioning: NOT ATTEMPTED"
} > "$OUT" 2>&1
chmod 644 "$OUT" 2>/dev/null
echo "$OUT"
