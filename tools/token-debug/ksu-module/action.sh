#!/system/bin/sh
MODDIR=${0%/*}
OUT=/sdcard/Download/ShizukuTokenX_TokenDebug_Manual_$(date +%Y%m%d_%H%M%S).txt
"$MODDIR/token_debug.sh" "$OUT"
echo "Saved: $OUT"
