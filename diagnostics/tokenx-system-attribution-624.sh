#!/system/bin/sh
# TokenX build 624 diagnostic: read-only UID 1000 attribution investigation.
# No changes to settings, permissions, Binder hooks or security policies.
echo "TOKENX_SYSTEM_ATTRIBUTION_DIAGNOSTIC_V1"
date
echo "== identity =="; id; id -Z
echo "== process =="; echo "pid=$$ ppid=$PPID"; cat /proc/self/attr/current 2>&1
echo "== package mappings =="; /system/bin/cmd package list packages -U 2>&1 | grep -E 'uid:1000|moe.shizuku|com.vikram.exp' | head -40
echo "== UID package associations =="; /system/bin/dumpsys package com.vikram.exp 2>&1 | grep -E 'userId=|sharedUser|codePath=' | head -12
echo "== Binder service discovery =="; for s in activity package appops settings; do /system/bin/service check "$s"; done
echo "== read-only settings probe =="; /system/bin/settings --user 0 get secure tokenx_privilege_test_20261008; echo "settings_read_rc=$?"
echo "== appops probe =="; /system/bin/cmd appops get com.android.settings 2>&1 | head -12; echo "appops_probe_rc=$?"
echo "== completed: no changes made =="
