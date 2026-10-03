package moe.shizuku.manager.shell

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Helpers for the current TokenX System UID / System Server backend.
 *
 * Legacy Serv.dex provisioning is retired. The bridge is installed by the KernelSU
 * module; this class only verifies the live Serv.apk/com.vikram.exp bridge and stages
 * TokenX's isolated UID-1000 Shizuku worker when needed.
 */
object SystemUidProvisioner {

    const val LIVE_PACKAGE = "com.vikram.exp"
    const val LEGACY_PACKAGE = "com.vikram.shell"
    const val FOTA_PACKAGE = "com.sdet.fotaagent"
    const val RECEIVER_FIX_PACKAGE = "com.eliteone.receiver"
    const val STAGED_SHIZUKU = "/data/local/tmp/libshizuku.so"

    data class Result(
        val success: Boolean,
        val exitCode: Int,
        val output: String,
        val command: String,
    )

    /**
     * Stage the Shizuku native launcher without ever hard-coding Android's
     * randomized /data/app/~~... install directory. nativeLibraryDir is the
     * authoritative location for this installation and changes safely across
     * updates/reinstalls.
     */
    fun stageShizukuUid1000(context: Context): Result {
        val nativeDir = context.applicationInfo.nativeLibraryDir
        val source = "$nativeDir/libshizuku.so"
        val apk = context.applicationInfo.sourceDir
        val script = listOf(
            "set -e",
            "test -r ${q(source)}",
            "test -r ${q(apk)}",
            "cp ${q(source)} ${q(STAGED_SHIZUKU)}",
            "chmod 755 ${q(STAGED_SHIZUKU)}",
            "chown 1000:1000 ${q(STAGED_SHIZUKU)}",
            "test -x ${q(STAGED_SHIZUKU)}",
            "echo source=${q(source)}",
            "echo apk=${q(apk)}",
            "echo staged=${q(STAGED_SHIZUKU)}",
        ).joinToString("; ")
        return runRoot(script)
    }

    /**
     * Start TokenX's normal native Shizuku starter as UID 1000. The explicit
     * --apk argument matters after staging: /data/local/tmp no longer sits next
     * to base.apk, so the starter cannot infer the manager APK from /proc/self/exe.
     */
    fun startShizukuUid1000(context: Context): Result {
        val apk = context.applicationInfo.sourceDir
        val inner = "${q(STAGED_SHIZUKU)} --apk=${q(apk)}"
        val script = listOf(
            "set -e",
            "test -x ${q(STAGED_SHIZUKU)}",
            "test -r ${q(apk)}",
            "cmd package list packages -U | grep -F 'package:$LIVE_PACKAGE uid:1000'",
            "su 1000 -c ${q(inner)}",
        ).joinToString("; ")
        return runRoot(script)
    }

    /**
     * Stage and start in one operation. A successful return means the native
     * starter was launched as UID 1000; callers must still verify the Shizuku
     * Binder UID before presenting Sserver as READY.
     */
    fun prepareAndStartShizukuUid1000(context: Context): Result {
        val staged = stageShizukuUid1000(context)
        if (!staged.success) return staged
        return startShizukuUid1000(context)
    }

    /**
     * Non-destructive provisioning audit for the two UID-1000 payloads. This deliberately
     * does not launch FOTA, reboot, enter recovery, factory-reset, or invoke update_engine.
     * The KernelSU module remains the installer; the manager reports exactly what survived.
     */
    fun verifyProvisionedPayloads(): Result {
        val script = listOf(
            "echo '=== TokenX provisioned payloads ==='",
            "echo '-- Serv --'",
            "cmd package list packages -U | grep -F 'package:$LIVE_PACKAGE uid:1000' || echo 'SERV_UID1000=0'",
            "dumpsys package $LIVE_PACKAGE 2>/dev/null | grep -m1 -F 'sharedUser=SharedUserSetting' || true",
            "echo '-- FOTA --'",
            "cmd package list packages -U | grep -F 'package:$FOTA_PACKAGE uid:1000' || echo 'FOTA_UID1000=0'",
            "dumpsys package $FOTA_PACKAGE 2>/dev/null | grep -m1 -E 'sharedUser=.*android.uid.system/1000' || true",
            "PID=\\$(pidof $FOTA_PACKAGE 2>/dev/null || true); if [ -n \"\\$PID\" ]; then echo FOTA_PID=\\$PID; ps -AZ | grep -F '$FOTA_PACKAGE' | head -n1; else echo FOTA_PID=stopped; fi",
            "echo '-- Android 17 receiver compatibility --'",
            "if cmd package list packages | grep -q -F 'package:$RECEIVER_FIX_PACKAGE'; then echo RECEIVER_FIX_PACKAGE=present; else echo RECEIVER_FIX_PACKAGE=absent; fi",
            "echo 'NOTE=receiver-fix package presence does not prove an LSPosed hook is active'",
        ).joinToString("; ")
        return runRoot(script)
    }

    fun verify(): Result {
        // Verify the live package and Android's real persistent system_server.
        // The legacy com.vikram.shell synthetic record is not a readiness signal.
        val packageName = q(LIVE_PACKAGE)
        val script = listOf(
            "echo '=== TokenX System Server bridge ==='",
            // Samsung/Android 17 can return FAILED_TRANSACTION from pm path even while
            // PackageManager has a valid UID-1000 record. Keep it as diagnostic only.
            "(pm path " + packageName + " 2>&1 || true) | sed 's/^/pm_path=/'",
            "cmd package list packages -U | grep -F 'package:" + LIVE_PACKAGE + " uid:1000'",
            "dumpsys package " + packageName + " | grep -m1 -F 'pkg=Package{'",
            "dumpsys activity processes | grep -m1 -E '[0-9]+:system/1000'",
            "ps -AZ | grep -m1 -E 'u:r:system_server:s0.*system_server'",
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
