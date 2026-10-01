#!/system/bin/sh
OUT=/data/local/tmp/ShizukuTokenX-SystemServer
mkdir -p "$OUT"
chmod 700 "$OUT" 2>/dev/null

snapshot() {
  LABEL="$1"
  LOG="$2"
  {
    echo
    echo "===== SNAPSHOT $LABEL ====="
    echo "wall=$(date '+%Y-%m-%d %H:%M:%S.%3N' 2>/dev/null || date)"
    echo "uptime=$(cat /proc/uptime 2>/dev/null)"
    echo "boot_completed=$(getprop sys.boot_completed)"
    echo "zygote=$(getprop init.svc.zygote) zygote64=$(getprop init.svc.zygote64)"
    SSPID=$(pidof system_server 2>/dev/null)
    echo "system_server_pid=$SSPID"
    [ -n "$SSPID" ] && {
      echo "--- system_server status ---"
      grep -E '^(Name|Pid|PPid|Uid|Gid|TracerPid):' "/proc/$SSPID/status" 2>/dev/null
      echo "--- system_server cmdline ---"
      tr '\000' ' ' < "/proc/$SSPID/cmdline" 2>/dev/null; echo
    }
    echo "--- TokenX/Shizuku processes ---"
    ps -A -o USER,PID,PPID,NAME,ARGS 2>/dev/null | grep -Ei 'shizuku|tokenx|rikka' || true
    echo "--- relevant Binder services ---"
    service list 2>/dev/null | grep -Ei 'shizuku|tokenx|binder' || true
    echo "--- binder devices ---"
    ls -l /dev/binder /dev/hwbinder /dev/vndbinder 2>&1 || true
    echo "--- SELinux ---"
    getenforce 2>&1
  } >> "$LOG" 2>&1
}

capture_now() {
  STAMP="$(date +%Y%m%d_%H%M%S)"
  LOG="$OUT/TokenX_SystemServer_$STAMP.txt"
  {
    echo "=== TokenX system_server boot-handshake diagnostic ==="
    echo "device=$(getprop ro.product.model)"
    echo "android=$(getprop ro.build.version.release) api=$(getprop ro.build.version.sdk)"
    echo "fingerprint=$(getprop ro.build.fingerprint)"
    echo "kernel=$(uname -a)"
    echo "KSU=$KSU KSU_VER=$KSU_VER KSU_VER_CODE=$KSU_VER_CODE KSU_KERNEL_VER_CODE=$KSU_KERNEL_VER_CODE KSU_RUNTIME_MODE=$KSU_RUNTIME_MODE"
    echo "collector_id=$(id)"
  } > "$LOG"
  snapshot "manual" "$LOG"
  {
    echo
    echo "===== RELEVANT LOGCAT ====="
    logcat -d -v threadtime -t 4000 2>/dev/null | grep -Ei 'tokenx|shizuku|system_server|systemserver|binder|lsposed|xposed|zygote|kernelsu|ksud|avc: denied|selinux' || true
    echo
    echo "===== KERNEL / AVC ====="
    dmesg 2>/dev/null | grep -Ei 'avc:|denied|binder|kernelsu|ksud' | tail -n 1200 || true
  } >> "$LOG" 2>&1
  chmod 600 "$LOG"
  echo "$LOG"
}
