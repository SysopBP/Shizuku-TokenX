#!/system/bin/sh
MODDIR=${0%/*}
LOGDIR=/data/local/tmp/shizuku-tokenx-debug
mkdir -p "$LOGDIR"
chmod 0700 "$LOGDIR"
# Preserve an early snapshot before waiting for Android to finish boot.
"$MODDIR/token_debug.sh" "$LOGDIR/early_boot.txt" >/dev/null 2>&1
i=0
while [ "$(getprop sys.boot_completed)" != "1" ] && [ "$i" -lt 180 ]; do
  sleep 1
  i=$((i+1))
done
sleep 5
"$MODDIR/token_debug.sh" "$LOGDIR/post_boot.txt" >/dev/null 2>&1
# Copy a shareable report only after emulated storage exists.
OUT=/sdcard/Download/ShizukuTokenX_TokenDebug_Boot_$(date +%Y%m%d_%H%M%S).txt
if [ -d /sdcard/Download ]; then
  {
    echo "### EARLY BOOT ###"; cat "$LOGDIR/early_boot.txt" 2>/dev/null
    echo; echo "### POST BOOT ###"; cat "$LOGDIR/post_boot.txt" 2>/dev/null
  } > "$OUT"
  chmod 0644 "$OUT"
fi
