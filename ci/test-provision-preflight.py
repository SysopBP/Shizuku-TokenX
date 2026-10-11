"""Execute the real read-only shell preflight against isolated fake device commands."""
import os
import pathlib
import re
import subprocess
import tempfile
import textwrap
import unittest

SOURCE = pathlib.Path("manager/src/main/java/moe/shizuku/manager/shell/SystemUidProvisioner.kt").read_text()
SCRIPT = textwrap.dedent(SOURCE.split('return runRoot("""', 1)[1].split('""".trimIndent()', 1)[0])
KSUD = re.search(r'private const val KSUD = "([^"]+)"', SOURCE).group(1)


class PreflightTest(unittest.TestCase):
    def run_preflight(self, runtime=True, disabled=False, root=True, kernel=True,
                      collision=False, owner=True, pid="123", soft=True, daemon_symlink=False,
                      daemon_missing=False, daemon_executable=True):
        with tempfile.TemporaryDirectory() as directory:
            base = pathlib.Path(directory)
            commands = base / "bin"
            commands.mkdir()
            modules = base / "modules"
            modules.mkdir()
            if runtime:
                target = modules / "zygisksu"
                target.mkdir()
                (target / "module.prop").write_text("id=zygisksu\n")
                if disabled:
                    (target / "disable").touch()
            if collision:
                target = modules / "tokenx_system_server"
                target.mkdir()
                (target / "module.prop").write_text("id=tokenx_system_server\n")
            programs = {
                "id": "echo " + ("0" if root else "2000"),
                "stat": "echo " + ("0" if owner else "2000"),
                "pidof": "echo " + pid,
                "ksud": """case "$*" in
                    'debug version') echo 'Kernel Version: %s';;
                    'module install --help') exit 0;;
                    'soft-reboot --help') exit %s;;
                    *) echo 'UNAUTHORIZED ACTION' >&2; exit 99;;
                    esac""" % ("40000" if kernel else "0", "0" if soft else "1"),
            }
            for name, body in programs.items():
                file = base / "ksud" if name == "ksud" else commands / name
                file.write_text("#!/bin/sh\n" + body + "\n")
                file.chmod(0o755)
            # Reproduce upstream DAEMON_PATH and DAEMON_LINK_PATH, rather than
            # replacing the configured path with an always-valid regular file.
            link_directory = base / "ksu" / "bin"
            link_directory.mkdir(parents=True)
            (link_directory / "ksud").symlink_to(base / "ksud")
            daemon = base / "ksud"
            if daemon_symlink:
                daemon.rename(base / "real-daemon")
                daemon.symlink_to(base / "real-daemon")
            if daemon_missing:
                daemon.unlink()
            elif not daemon_executable:
                daemon.chmod(0o644)
            self.assertTrue(KSUD.startswith("/data/adb/"))
            script = SCRIPT.replace("$KSUD", str(base / KSUD.removeprefix("/data/adb/")))
            script = script.replace("$MODULE_DIR", str(modules / "tokenx_system_server"))
            script = script.replace("$MODULE_ID", "tokenx_system_server")
            script = script.replace("/data/adb/modules_update/", str(base / "updates") + "/")
            script = script.replace("/data/adb/modules/", str(modules) + "/")
            script = script.replace("${'$'}", "$")
            env = dict(os.environ, PATH=str(commands) + ":" + os.environ["PATH"])
            result = subprocess.run(["sh", "-c", script], env=env, capture_output=True, text=True, timeout=5)
            self.assertNotIn("UNAUTHORIZED ACTION", result.stderr)
            return result

    def test_valid_preflight_reports_pid(self):
        result = self.run_preflight()
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn("TOKENX_PREVIOUS_PID=123", result.stdout)

    def test_failures_never_invoke_installation_or_reboot(self):
        for case in (dict(root=False), dict(kernel=False), dict(runtime=False), dict(disabled=True),
                     dict(collision=True), dict(owner=False), dict(pid=""), dict(pid="12 13"), dict(soft=False),
                     dict(daemon_symlink=True), dict(daemon_missing=True), dict(daemon_executable=False)):
            with self.subTest(case=case):
                self.assertNotEqual(0, self.run_preflight(**case).returncode)


if __name__ == "__main__":
    unittest.main()

