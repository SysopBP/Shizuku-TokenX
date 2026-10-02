package moe.shizuku.manager.shell

import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Root-only installer for the external Server.apk + Serv.dex System UID backend.
 *
 * The Server APK intentionally remains outside the TokenX APK/repository. This class only
 * provisions files already supplied by the user on device storage.
 */
object SystemUidProvisioner {

    const val DEFAULT_APK = "/sdcard/Download/Serv.apk"
    const val DEFAULT_DEX = "/sdcard/Serv.dex"
    const val INSTALL_DIR = "/data/app/com.android.settings/vikram_shell"
    const val INSTALLED_APK = "$INSTALL_DIR/base.apk"
    const val STAGED_DEX = "/data/local/tmp/Serv.dex"
    const val LIVE_PACKAGE = "com.vikram.exp"
    const val LEGACY_PACKAGE = "com.vikram.shell"

    data class Result(
        val success: Boolean,
        val exitCode: Int,
        val output: String,
        val command: String,
    )

    /**
     * Reproduces the known-working root provisioning sequence. Server.apk is not bundled.
     * Serv.dex remains a separate external provisioning payload.
     */
    fun install(
        apkPath: String = DEFAULT_APK,
        dexPath: String = DEFAULT_DEX,
    ): Result {
        val script = listOf(
            "set -e",
            "test -r ${q(apkPath)}",
            "test -r ${q(dexPath)}",
            "mkdir -p ${q(INSTALL_DIR)}",
            "cat ${q(apkPath)} > ${q(INSTALLED_APK)}",
            "chmod -R 755 ${q(INSTALL_DIR)}",
            "cp ${q(dexPath)} ${q(STAGED_DEX)}",
            "chmod 644 ${q(STAGED_DEX)}",
            "export CLASSPATH=${q(STAGED_DEX)}",
            // Keep the invocation identical to the sequence already proven on-device.
            "app_process -cp ${q(dexPath)} /system/bin Serv",
        ).joinToString("; ")

        return runRoot(script)
    }

    fun verify(): Result {
        // com.vikram.exp is the live package attached to Android's persistent
        // system process. com.vikram.shell can exist as a legacy/synthetic
        // PackageManager record and must not be used as the readiness signal.
        val script = listOf(
            "echo '=== TokenX System Server bridge ==='",
            "APK=\$(pm path ${q(LIVE_PACKAGE)} 2>/dev/null | head -n 1)",
            "UID=\$(cmd package list packages -U 2>/dev/null | grep '^package:${LIVE_PACKAGE} uid:1000
    private fun runRoot(script: String): Result {
        val command = listOf("su", "-c", script)
        return try {
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()

            val output = BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                buildString {
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        appendLine(line)
                    }
                }
            }

            val exitCode = process.waitFor()
            Result(exitCode == 0, exitCode, output, command.joinToString(" "))
        } catch (t: Throwable) {
            Result(false, -1, "${t.javaClass.simpleName}: ${t.message}", command.joinToString(" "))
        }
    }

    private fun q(value: String): String =
        "'" + value.replace("'", "'\\''") + "'"
}
 || true)",
            "PKG=\$(dumpsys package ${q(LIVE_PACKAGE)} 2>/dev/null | grep -m1 'pkg=Package{' || true)",
            "PROC=\$(dumpsys activity processes 2>/dev/null | grep -m1 -E '[0-9]+:system/1000' || true)",
            "DOMAIN=\$(ps -AZ 2>/dev/null | grep -m1 'u:r:system_server:s0.*system_server' || true)",
            "printf 'package=%s\\nuid=%s\\nparsed=%s\\nprocess=%s\\ndomain=%s\\n' \"\$APK\" \"\$UID\" \"\$PKG\" \"\$PROC\" \"\$DOMAIN\"",
            "test -n \"\$APK\"",
            "test -n \"\$UID\"",
            "test -n \"\$PKG\"",
            "test -n \"\$PROC\"",
            "test -n \"\$DOMAIN\"",
        ).joinToString("; ")

        return runRoot(script)
    }

    private fun runRoot(script: String): Result {
        val command = listOf("su", "-c", script)
        return try {
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()

            val output = BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                buildString {
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        appendLine(line)
                    }
                }
            }

            val exitCode = process.waitFor()
            Result(exitCode == 0, exitCode, output, command.joinToString(" "))
        } catch (t: Throwable) {
            Result(false, -1, "${t.javaClass.simpleName}: ${t.message}", command.joinToString(" "))
        }
    }

    private fun q(value: String): String =
        "'" + value.replace("'", "'\\''") + "'"
}
