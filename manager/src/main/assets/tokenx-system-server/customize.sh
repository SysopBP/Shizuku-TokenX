#!/system/bin/sh
[ "$KSU" = true ] || abort "TokenX injection requires KernelSU"
[ "$API" -ge 27 ] || abort "TokenX injection requires Android 8.1 or newer"
[ "$IS64BIT" = true ] || abort "TokenX injection requires a 64-bit system_server"
cd "$MODPATH" || abort "Cannot access module directory"
sha256sum -c payload.sha256 || abort "TokenX payload integrity verification failed"
set_perm_recursive "$MODPATH" 0 0 0755 0644
set_perm "$MODPATH/customize.sh" 0 0 0755
ui_print "TokenX system_server injection staged; activation requires a soft reboot"
