#!/system/bin/sh
# TokenX Build 640: capture UID-1000 SettingsProvider attribution failures.
# No AppOps bypass or persistent settings modifications.
set -u
export RISH_APPLICATION_ID="${RISH_APPLICATION_ID:-com.termux}"
RISH="${RISH:-./rish}"
OUT="${OUT:-$HOME/TokenX_640_Attribution_Probe.txt}"
KEY="tokenx_640_probe_$$"
{
  echo "TOKENX_ATTRIBUTION_PROBE=640"
  date
  echo "RISH_APPLICATION_ID=$RISH_APPLICATION_ID"
  for ROUTE in root system; do
    echo "===== ROUTE=$ROUTE ====="
    "$RISH" --"$ROUTE" -c "
      echo '[IDENTITY]'
      id
      echo '[PROCESS SELINUX]'
      cat /proc/self/attr/current
      echo '[PACKAGE UID LOOKUP]'
      cmd package list packages --uid 1000 | head -15
      echo '[SETTINGS READ]'
      settings get system screen_off_timeout
      echo '[SETTINGS PUT]'
      settings put system '$KEY' probe640
      echo PUT_EXIT=\$?
      echo '[SETTINGS GET]'
      settings get system '$KEY'
      echo '[SETTINGS DELETE]'
      settings delete system '$KEY'
      echo DELETE_EXIT=\$?
    " 2>&1
  done
  echo "NOTE: SettingsProvider caller attribution is checked independently of process UID."
  echo "NOTE: Failures are captured, not bypassed."
} 2>&1 | tee "$OUT"
echo "REPORT=$OUT"
