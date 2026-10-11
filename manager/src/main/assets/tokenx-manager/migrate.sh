#!/system/bin/sh
# Detached, fixed-package migration. This process survives removal of the manager.
set -u
D=/data/system/tokenx_manager_migration
PKG=moe.shizuku.privileged.api
CE=/data/user/0/$PKG
DE=/data/user_de/0/$PKG
OLD_UID=$1
ORDINARY_HASH=$2
SYSTEM_HASH=$3
status() { printf '%s\n' "$1" > "$D/status"; chmod 644 "$D/status"; }
uid() { cmd package list packages -U --user 0 | awk '$1 == "package:moe.shizuku.privileged.api" {sub(/^uid:/,"",$2); print $2}'; }
launch() { am start --user 0 -n "$PKG/moe.shizuku.manager.MainActivity" >/dev/null 2>&1 || true; }
restore_data() {
    target=$1
    for pair in 'ce /data/user/0/moe.shizuku.privileged.api' 'de /data/user_de/0/moe.shizuku.privileged.api'; do
        set -- $pair
        archive=$D/backup-$1.tar
        destination=$2
        [ -f "$archive" ] || continue
        [ -d "$destination" ] && [ ! -L "$destination" ] || return 1
        tar -xpf "$archive" -C "$destination" || return 1
        chown -hR "$target:$target" "$destination" || return 1
        restorecon -RF "$destination" || return 1
    done
}
rollback() {
    status rollback
    am force-stop --user 0 "$PKG" >/dev/null 2>&1 || true
    if [ -n "$(uid)" ]; then pm uninstall "$PKG" || { status rollback_failed; return 1; }; fi
    pm install --user 0 "$D/ordinary.apk" || { status rollback_failed; return 1; }
    actual=$(uid)
    case "$actual" in ''|*[!0-9]*) status rollback_failed; return 1;; esac
    [ "$actual" -ge 10000 ] || { status rollback_failed; return 1; }
    restore_data "$actual" || { status rollback_data_failed; return 1; }
    status rolled_back
    launch
}
[ "$(id -u)" = 0 ] || exit 2
[ ! -L "$D" ] && [ "$(stat -c %u "$D")" = 0 ] || exit 2
[ "$(sha256sum "$D/ordinary.apk" | cut -d ' ' -f 1)" = "$ORDINARY_HASH" ] || exit 2
[ "$(sha256sum "$D/system.apk" | cut -d ' ' -f 1)" = "$SYSTEM_HASH" ] || exit 2
[ "$(uid)" = "$OLD_UID" ] || { status identity_changed; exit 2; }
status backing_up
# Stop before snapshotting SQLite databases and preferences.
am force-stop --user 0 "$PKG" || { status stop_failed; exit 2; }
for pair in 'ce /data/user/0/moe.shizuku.privileged.api' 'de /data/user_de/0/moe.shizuku.privileged.api'; do
    set -- $pair
    [ ! -L "$2" ] || { status backup_failed; launch; exit 2; }
    if [ -d "$2" ]; then
        tar -cpf "$D/backup-$1.tar" -C "$2" . || { status backup_failed; launch; exit 2; }
        chmod 600 "$D/backup-$1.tar"
        tar -tf "$D/backup-$1.tar" >/dev/null || { status backup_failed; launch; exit 2; }
    fi
done
status replacing
pm uninstall "$PKG" || { status uninstall_failed; launch; exit 3; }
if ! pm install --user 0 "$D/system.apk" || [ "$(uid)" != 1000 ]; then rollback; exit 3; fi
if ! restore_data 1000; then rollback; exit 4; fi
status installed_uid1000
launch
# A new manager process verifies Process.myUid() and the existing Xposed Binder.
# No native endpoint, uninstall block, or automatic framework restart is used.
