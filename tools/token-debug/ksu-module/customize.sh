#!/system/bin/sh
ui_print "*******************************"
ui_print " Shizuku TokenX Debug v1.0.0"
ui_print " Passive diagnostics only"
ui_print "*******************************"
set_perm "$MODPATH/service.sh" 0 0 0755
set_perm "$MODPATH/action.sh" 0 0 0755
set_perm "$MODPATH/token_debug.sh" 0 0 0755
