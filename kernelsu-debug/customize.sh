#!/system/bin/sh
ui_print "- Shizuku TokenX KernelSU Debug"
ui_print "- Diagnostic-only: no system files are replaced"
ui_print "- Logs: /data/local/tmp/ShizukuTokenX-Debug"
set_perm "$MODPATH/service.sh" 0 0 0755
set_perm "$MODPATH/boot-completed.sh" 0 0 0755
set_perm "$MODPATH/action.sh" 0 0 0755
set_perm "$MODPATH/common.sh" 0 0 0755
