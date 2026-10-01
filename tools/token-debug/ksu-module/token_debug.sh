#!/system/bin/sh
# Shizuku-TokenX passive token diagnostics
# Does not request, mint, replace, or mutate tokens.
OUT="${1:-/sdcard/Download/ShizukuTokenX_TokenDebug_$(date +%Y%m%d_%H%M%S).txt}"
TMP="${OUT}.tmp"
{
echo "=== Shizuku-TokenX Token Debug ==="
date
echo
echo "[IDENTITY]"
id
echo "pid=$$ ppid=$PPID"
echo
echo "[BUILD]"
getprop ro.product.model
getprop ro.build.version.release
getprop ro.build.version.sdk
getprop ro.build.fingerprint
echo
echo "[BOOT]"
getprop sys.boot_completed
getprop ro.boot.bootreason
cat /proc/uptime 2>/dev/null
echo
echo "[PROCESSES]"
ps -A -o USER,PID,PPID,NAME,ARGS 2>/dev/null | grep -Ei 'shizuku|tokenx|riru|zygisk|lsposed|system_server' || true
echo
echo "[BINDER/SERVICE]"
service list 2>/dev/null | grep -Ei 'shizuku|package|activity|permission' || true
echo
echo "[SHIZUKU PROPERTIES]"
getprop 2>/dev/null | grep -Ei 'shizuku|token' || true
echo
echo "[PACKAGE]"
dumpsys package moe.shizuku.privileged.api 2>/dev/null | grep -Ei 'version|userId|granted|permission|enabled|stopped' | head -n 120 || true
echo
echo "[RECENT LOGCAT - TOKEN/BINDER/BOOT]"
logcat -d -v threadtime -t 2500 2>/dev/null | grep -Ei 'shizuku|token|binder|system_server|zygote|lsposed|permission|uid.?1000|uid.?2000|denied|exception|fatal|boot' | tail -n 1200 || true
echo
echo "[KERNEL DENIALS]"
dmesg 2>/dev/null | grep -Ei 'avc:|denied|shizuku|token' | tail -n 250 || true
echo
echo "=== END ==="
} > "$TMP"
mv "$TMP" "$OUT"
chmod 0644 "$OUT" 2>/dev/null
echo "$OUT"
