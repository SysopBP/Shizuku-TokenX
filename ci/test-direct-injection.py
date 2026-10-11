"""Exercise the actual staging shell with fake device files and a harmless injector."""
import hashlib
import os
import pathlib
import shlex
import subprocess
import tempfile
import textwrap
import unittest

SOURCE = pathlib.Path("manager/src/main/java/moe/shizuku/manager/shell/DirectSystemServerProvisioner.kt").read_text()
SCRIPT = textwrap.dedent(SOURCE.split('val script = """', 1)[1].split('""".trimIndent()', 1)[0])


class DirectInjectionTest(unittest.TestCase):
    def run_staging(self, *, symlink=False, changed_pid=False, bad_hash=False, native_failure=False):
        with tempfile.TemporaryDirectory() as directory:
            base = pathlib.Path(directory)
            destination = base / "staged"
            commands = base / "commands"
            commands.mkdir()
            proc = base / "proc" / "123"
            proc.mkdir(parents=True)
            (proc / "cmdline").write_bytes(b"system_server\0")
            # Host containers need not map Android's UID 1000; ownership is a device concern.
            for name, body in {"id": "echo 0", "pidof": "echo 123", "chown": "exit 0"}.items():
                file = commands / name
                file.write_text("#!/bin/sh\n" + body + "\n")
                file.chmod(0o755)
            # Include shell metacharacters in a source filename to exercise quoting.
            apk = base / "payload '$().apk"
            injector = base / "native"
            payload = base / "loader.so"
            apk.write_bytes(b"APK fixture")
            payload.write_bytes(b"native loader fixture")
            injector.write_text("#!/bin/sh\n" + ("exit 9\n" if native_failure else
                'test "$1" = 123 && test "$3" = entry && echo NATIVE_CALLED\n'))
            injector.chmod(0o755)
            if symlink:
                destination.symlink_to(commands, target_is_directory=True)
            hashes = [hashlib.sha256(file.read_bytes()).hexdigest() for file in (apk, injector, payload)]
            script = SCRIPT.replace("$DIRECTORY", str(destination)).replace("/proc/", str(base / "proc") + "/")
            for anchor, file in (("${q(apk.path)}", apk), ("${q(injector.path)}", injector), ("${q(payload.path)}", payload)):
                script = script.replace(anchor, shlex.quote(str(file)))
            for anchor, value in (("$apkHash", "0" * 64 if bad_hash else hashes[0]),
                                  ("$REFERENCE_SHA256", hashes[1]), ("$payloadHash", hashes[2]),
                                  ("$targetPid", "124" if changed_pid else "123"), ("$request", "17")):
                script = script.replace(anchor, value)
            script = script.replace("${'$'}", "$")
            result = subprocess.run(["sh", "-c", script], env=dict(os.environ,
                PATH=str(commands) + ":" + os.environ["PATH"]), capture_output=True, text=True, timeout=5)
            staged = destination / "payload.apk"
            self.assertFalse((base / "native-unexpected").exists())
            return result, staged.is_file(), staged.stat().st_mode & 0o777 if staged.is_file() else None

    def test_success_stages_read_only_apk_and_invokes_native_entry(self):
        result, present, mode = self.run_staging()
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertTrue(present)
        self.assertEqual(0o444, mode)
        self.assertIn("NATIVE_CALLED", result.stdout)
        self.assertIn("TOKENX_DIRECT_INJECTION_RETURNED", result.stdout)

    def test_staging_failures_never_invoke_native(self):
        for case in ({"symlink": True}, {"changed_pid": True}, {"bad_hash": True}):
            with self.subTest(case=case):
                result, _, _ = self.run_staging(**case)
                self.assertNotEqual(0, result.returncode)
                self.assertNotIn("NATIVE_CALLED", result.stdout)
                self.assertNotIn("TOKENX_DIRECT_INJECTION_RETURNED", result.stdout)

    def test_native_failure_is_not_reported_as_success(self):
        result, _, _ = self.run_staging(native_failure=True)
        self.assertNotEqual(0, result.returncode)
        self.assertNotIn("TOKENX_DIRECT_INJECTION_RETURNED", result.stdout)

    def test_live_worker_has_no_module_install_or_soft_reboot(self):
        worker = pathlib.Path("manager/src/main/java/moe/shizuku/manager/shell/SystemUidProvisioning.kt").read_text()
        self.assertNotIn("SystemUidProvisioner.install(", worker)
        self.assertNotIn("SystemUidProvisioner.softReboot(", worker)
        self.assertNotIn("ksud", SOURCE)
        self.assertNotIn("/disable", SOURCE)


if __name__ == "__main__":
    unittest.main()
