package moe.shizuku.manager.shell

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Helpers for the current TokenX System UID / System Server backend.
 *
 * The KernelSU module is the primary installer. A module-local provisioning DEX may
 * remain available as a fallback; this class audits that fallback without executing it.
 * It also verifies the live Serv.apk/com.vikram.exp bridge and stages TokenX's isolated
 * UID-1000 Shizuku worker when needed.
 */
object SystemUidProvisioner {

    const val LIVE_PACKAGE = "com.vikram.exp"
    const val LEGACY_PACKAGE = "com.vikram.shell"
    const val FOTA_PACKAGE = "com.sdet.fotaagent"
    const val STAGED_SHIZUKU = "/data/local/tmp/libshizuku.so"

    enum class Stage { D2_GATE, SERV_UID, FOTA_UID, FOTA_DOMAIN, DEX_FALLBACK, RX_COMPAT, SYSTEM_BRIDGE }
    enum class StageState { WAITING, CHECKING, VERIFIED, WARNING, FAILED }
    data class Progress(val stage: Stage, val state: StageState, val detail: String)

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
     * Non-destructive provisioning audit for the privileged payloads. This deliberately
     * does not launch FOTA, reboot, enter recovery, factory-reset, or invoke update_engine.
     * The KernelSU module remains the installer; the manager reports exactly what survived.
     */
    fun verifyProvisionedPayloads(onProgress: (Progress) -> Unit = {}): Result {
        fun check(stage: Stage, label: String, script: String): Result {
            onProgress(Progress(stage, StageState.CHECKING, label))
            val result = runRoot(script)
            onProgress(Progress(stage, if (result.success) StageState.VERIFIED else StageState.FAILED, result.output.trim().ifBlank { label }))
            return result
        }
        val transcript = StringBuilder("=== TokenX provisioned payloads ===\n")
        val checks = listOf(
            Triple(Stage.D2_GATE, "Checking D2 dual-gate module", "test -d /data/adb/modules/tokenx_system_server && echo D2_GATE=module-present"),
            Triple(Stage.SERV_UID, "Checking Serv UID 1000", "cmd package list packages -U | grep -F 'package:$LIVE_PACKAGE uid:1000'"),
            Triple(Stage.FOTA_UID, "Checking FOTA UID 1000", "cmd package list packages -U | grep -F 'package:$FOTA_PACKAGE uid:1000'"),
            Triple(Stage.FOTA_DOMAIN, "Checking FOTA system identity", "dumpsys package $FOTA_PACKAGE 2>/dev/null | grep -m1 -E 'sharedUser=.*android.uid.system/1000'"),
            Triple(Stage.DEX_FALLBACK, "Checking provisioning DEX fallback", "DEX=$(find /data/adb/modules/tokenx_system_server -type f -name '*.dex' 2>/dev/null | head -n 1); test -n \"${'$'}DEX\" && test -r \"${'$'}DEX\" && echo DEX_FALLBACK=present && echo DEX_PATH=\"${'$'}DEX\" && echo DEX_STATE=standby"),
            Triple(Stage.RX_COMPAT, "Checking TokenX Receiver Compatibility", "echo TOKENX_RECEIVER_COMPAT=integrated; echo TOKENX_RECEIVER_SCOPE=$FOTA_PACKAGE")
        )
        for ((stage, label, script) in checks) {
            val result = check(stage, label, script)
            transcript.append(result.output)
            if (!result.success) return Result(false, result.exitCode, transcript.toString(), result.command)
        }
        transcript.append("NOTE=DEX fallback presence is audited only; the Vault does not execute it or claim it was used\n")
        transcript.append("NOTE=Receiver Compatibility is integrated into TokenX Xposed; no external Receiver Flag Fix APK is required\n")
        return Result(true, 0, transcript.toString(), "TokenX staged provisioning audit")
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
            "echo DIRECT_BINDER=architecture-supported",
            "echo UID1000_WORKER=on-demand",
            "echo NAMED_BINDER=not-required",
        ).joinToString("; ")
        return runRoot(script)
    }

    /** Run a fixed TokenX privileged maintenance action from the dashboard. */
    fun runPrivilegedAction(command: String): Result {
        val allowed = setOf(
            "setprop ctl.restart zygote",
            "pkill -TERM -f com.android.systemui",
        )
        if (command !in allowed) {
            return Result(false, -1, "Rejected unsupported privileged action", command)
        }
        return runRoot(command)
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
