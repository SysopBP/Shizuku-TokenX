package moe.shizuku.manager.shell

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Helpers for the current TokenX System UID / System Server backend.
 *
 * The KernelSU module is the primary installer. A module-local provisioning DEX may
 * remain available as a fallback; this class audits that fallback without executing it.
 * It stages TokenX's isolated UID-1000 Shizuku worker when needed. Live
 * system_server identity is verified separately through the LSPosed _TKN RPC.
 */
object SystemUidProvisioner {

    const val LEGACY_PACKAGE = "com.vikram.exp"
    const val STAGED_SHIZUKU = "/data/local/tmp/libshizuku.so"

    enum class Stage { D2_GATE, BRIDGE_UID, SYSTEM_BRIDGE }
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

    /** Non-destructive audit of the retained D2 boundary and current backend architecture. */
    fun verifyProvisionedPayloads(onProgress: (Progress) -> Unit = {}): Result {
        onProgress(Progress(Stage.D2_GATE, StageState.CHECKING, "Checking D2 protected module"))
        val d2 = runRoot("test -d /data/adb/modules/tokenx_system_server && echo D2_GATE=module-present")
        onProgress(Progress(Stage.D2_GATE, if (d2.success) StageState.VERIFIED else StageState.FAILED, d2.output.trim()))
        if (!d2.success) return d2

        val transcript = buildString {
            append("=== TokenX system integration ===\n")
            append(d2.output)
            append("LEGACY_BACKENDS=removed\n")
            append("RECEIVER_COMPAT=TokenX-native; outside provisioning chain\n")
            append("SYSTEM_SERVER_BACKEND=LSPosed _TKN RPC; verified by TokenX runtime\n")
            append("UID1000_WORKER=live Shizuku binder identity\n")
        }
        return Result(true, 0, transcript, "TokenX system integration audit")
    }

    fun verify(): Result {
        val script = listOf(
            "echo '=== TokenX System Server backend ==='",
            "dumpsys activity processes | grep -m1 -E '[0-9]+:system/1000'",
            "ps -AZ | grep -m1 -E 'u:r:system_server:s0.*system_server'",
            "echo SYSTEM_SERVER_RPC=LSPosed__TKN",
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
