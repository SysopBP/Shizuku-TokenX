#!/system/bin/sh
MODDIR=${0%/*}
. "$MODDIR/common.sh"
LOG="$(capture_now)"
echo "TokenX system_server diagnostic captured:"
echo "$LOG"
echo
echo "Boot traces:"
ls -1t "$OUT"/TokenX_BootHandshake_*.txt 2>/dev/null | head -n 3
