"""Run the real detached migration shell against fake Android package commands."""
import hashlib
import os
import pathlib
import subprocess
import tempfile
import unittest

SOURCE = pathlib.Path("manager/src/main/assets/tokenx-manager/migrate.sh").read_text()

class MigrationTests(unittest.TestCase):
    def run_case(self, failure="", symlink=False):
        with tempfile.TemporaryDirectory() as temporary:
            base = pathlib.Path(temporary)
            stage, ce, de, commands = [base / name for name in ("stage", "ce", "de", "commands")]
            for folder in [stage, ce, de, commands]: folder.mkdir()
            (ce / "preference").write_text("preserve CE")
            (de / "preference").write_text("preserve DE")
            for name in ("ordinary", "system"): (stage / (name + ".apk")).write_text(name)
            state = base / "uid"; state.write_text("10123")
            script = SOURCE.replace("/data/system/tokenx_manager_migration", str(stage))
            script = script.replace("/data/user/0/$PKG", str(ce)).replace("/data/user_de/0/$PKG", str(de))
            script = script.replace("/data/user/0/moe.shizuku.privileged.api", str(ce))
            script = script.replace("/data/user_de/0/moe.shizuku.privileged.api", str(de))
            fake = {
                "id": "echo 0",
                "stat": "echo 0",
                "chown": "exit 0",
                "restorecon": "exit 0",
                "am": "exit 0",
                "cmd": 'u=$(cat "$STATE"); [ -z "$u" ] || echo "package:moe.shizuku.privileged.api uid:$u"',
                "pm": '''
if [ "$1" = uninstall ]; then
  rm -rf "$CE" "$DE"; echo '' > "$STATE"; echo Success; exit 0
fi
case "$*" in
  *system.apk*)
    [ "$FAILURE" != install ] || exit 1
    [ "$FAILURE" != rollback ] || exit 1
    if [ "$FAILURE" = wrong_uid ]; then echo 12345 > "$STATE"; else echo 1000 > "$STATE"; fi;;
  *ordinary.apk*) [ "$FAILURE" != rollback ] || exit 1; echo 10234 > "$STATE";;
  *) exit 2;;
esac
mkdir -p "$CE" "$DE"; echo Success
''',
            }
            for name, body in fake.items():
                file = commands / name; file.write_text("#!/bin/sh\n" + body + "\n"); file.chmod(0o755)
            if symlink:
                for file in ce.iterdir(): file.unlink()
                ce.rmdir(); ce.symlink_to(de, target_is_directory=True)
            env = dict(os.environ, PATH=str(commands)+":"+os.environ["PATH"], STATE=str(state), CE=str(ce), DE=str(de), FAILURE=failure)
            hashes = [hashlib.sha256((stage / (name+".apk")).read_bytes()).hexdigest() for name in ("ordinary", "system")]
            result = subprocess.run(["sh", "-c", script, "migration", "10123", *hashes], env=env, text=True, capture_output=True, timeout=10)
            status = (stage / "status").read_text().strip()
            uid = state.read_text().strip()
            data = [((folder / "preference").read_text() if (folder / "preference").exists() else None) for folder in [ce,de]]
            return result.returncode, status, uid, data

    def test_migration_preserves_both_data_directories(self):
        self.assertEqual(self.run_case(), (0, "installed_uid1000", "1000", ["preserve CE", "preserve DE"]))

    def test_failed_install_restores_ordinary_apk_and_data(self):
        self.assertEqual(self.run_case("install"), (3, "rolled_back", "10234", ["preserve CE", "preserve DE"]))

    def test_wrong_uid_is_removed_before_rollback(self):
        self.assertEqual(self.run_case("wrong_uid"), (3, "rolled_back", "10234", ["preserve CE", "preserve DE"]))

    def test_rollback_failure_is_not_reported_as_success(self):
        code, status, uid, data = self.run_case("rollback")
        self.assertEqual((code,status,uid), (3,"rollback_failed",""))

    def test_symlink_backup_is_rejected_before_uninstall(self):
        code, status, uid, data = self.run_case(symlink=True)
        self.assertEqual((code,status,uid), (2,"backup_failed","10123"))

if __name__ == "__main__": unittest.main()
