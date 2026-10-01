#!/system/bin/sh
OUT=/data/local/tmp/ShizukuTokenX-Debug
mkdir -p "$OUT"
chmod 700 "$OUT" 2>/dev/null
STAMP="$(date +%Y%m%d_%H%M%S)"
LOG="$OUT/TokenX_Debug_$STAMP.txt"
{
echo "=== Shizuku TokenX / KernelSU Debug ==="
date
echo
echo "=== DEVICE ==="
getprop ro.product.model
getprop ro.build.version.release
getprop ro.build.version.sdk
getprop ro.build.fingerprint
echo
echo "=== KERNEL / KSU ==="
uname -a
echo "KSU=$KSU KSU_VER=$KSU_VER KSU_VER_CODE=$KSU_VER_CODE KSU_KERNEL_VER_CODE=$KSU_KERNEL_VER_CODE KSU_RUNTIME_MODE=$KSU_RUNTIME_MODE"
id
echo
echo "=== SHIZUKU / TOKENX PROCESSES ==="
ps -A -o USER,PID,PPID,NAME,ARGS 2>/dev/null | grep -Ei 'shizuku|tokenx|rikka' || true
echo
echo "=== SERVICES ==="
service list 2>/dev/null | grep -Ei 'shizuku|binder|package|activity' || true
echo
echo "=== PACKAGES ==="
pm list packages -U 2>/dev/null | grep -Ei 'shizuku|tokenx|rikka' || true
echo
echo "=== BINDERFS ==="
ls -la /dev/binderfs 2>&1 || true
ls -l /dev/binder /dev/hwbinder /dev/vndbinder 2>&1 || true
echo
echo "=== SELINUX ==="
getenforce 2>&1
echo
echo "=== RECENT LOGCAT ==="
logcat -d -v threadtime -t 1200 2>/dev/null | grep -Ei 'shizuku|tokenx|binder|kernelsu|ksud|system_server' || true
echo
echo "=== DMESG TAIL ==="
dmesg 2>/dev/null | tail -n 300 || true
} > "$LOG" 2>&1
chmod 600 "$LOG"
echo "$LOG"
