#!/system/bin/sh
MODDIR=${0%/*}
. "$MODDIR/common.sh"

STAMP="$(date +%Y%m%d_%H%M%S)"
LOG="$OUT/TokenX_BootHandshake_$STAMP.txt"
EVENTS="$OUT/TokenX_BootEvents_$STAMP.txt"

{
  echo "=== TokenX system_server boot timeline ==="
  echo "collector_start=$(date '+%Y-%m-%d %H:%M:%S.%3N' 2>/dev/null || date)"
  echo "uptime=$(cat /proc/uptime 2>/dev/null)"
  echo "KSU=$KSU KSU_VER=$KSU_VER KSU_VER_CODE=$KSU_VER_CODE KSU_RUNTIME_MODE=$KSU_RUNTIME_MODE"
  echo "getenforce=$(getenforce 2>/dev/null)"
} > "$LOG"

# Capture the event stream independently so polling cannot hide short-lived failures.
(logcat -b all -v threadtime 2>/dev/null | grep -Ei --line-buffered 'tokenx|shizuku|system_server|systemserver|binder|lsposed|xposed|zygote|kernelsu|ksud|avc: denied|selinux' > "$EVENTS") &
LPID=$!

LAST_SS=""
LAST_BOOT=""
I=0
while [ "$I" -lt 180 ]; do
  SSPID="$(pidof system_server 2>/dev/null)"
  BOOT="$(getprop sys.boot_completed)"
  if [ "$SSPID" != "$LAST_SS" ] || [ "$BOOT" != "$LAST_BOOT" ]; then
    snapshot "t=$I ss=$SSPID boot=$BOOT" "$LOG"
    LAST_SS="$SSPID"
    LAST_BOOT="$BOOT"
  fi
  # Dense sampling for first minute, then 2s until 3 minutes.
  if [ "$I" -lt 60 ]; then sleep 1; I=$((I+1)); else sleep 2; I=$((I+2)); fi
done

kill "$LPID" 2>/dev/null
wait "$LPID" 2>/dev/null
snapshot "collector_end" "$LOG"
{
  echo
  echo "===== BOOT EVENT STREAM ====="
  cat "$EVENTS" 2>/dev/null
  echo
  echo "===== FINAL AVC / KERNEL BINDER ====="
  dmesg 2>/dev/null | grep -Ei 'avc:|denied|binder|kernelsu|ksud' | tail -n 1600 || true
} >> "$LOG"
chmod 600 "$LOG" "$EVENTS" 2>/dev/null
