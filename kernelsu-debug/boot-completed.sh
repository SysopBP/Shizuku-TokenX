#!/system/bin/sh
MODDIR=${0%/*}
. "$MODDIR/common.sh"
LOG="$OUT/TokenX_BootCompleted_$(date +%Y%m%d_%H%M%S).txt"
echo "=== KernelSU boot-completed callback ===" > "$LOG"
snapshot "boot-completed" "$LOG"
{
  echo
  echo "===== RECENT HANDSHAKE EVENTS ====="
  logcat -d -v threadtime -t 2500 2>/dev/null | grep -Ei 'tokenx|shizuku|system_server|systemserver|binder|lsposed|xposed|zygote|kernelsu|ksud|avc: denied|selinux' || true
} >> "$LOG"
chmod 600 "$LOG"
