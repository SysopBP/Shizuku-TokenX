package moe.shizuku.manager.shell

import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * TokenX native System UID backend diagnostics.
 *
 * Legacy Serv.apk / Serv.dex installation is intentionally unsupported.
 * Provisioning must be performed by the current TokenX KernelSU module.
 * Never claim that UID 1000 is active based only on the system_server process.
 */
object SystemUidProvisioner {
    const val MODULE_DIR = "/data/adb/modules/tokenx_system_server"

    data class Result(
        val success: Boolean,
        val exitCode: Int,
        val output: String,
        val command: String,
    )

    /** Preflight for a future user-confirmed provisioning action. */
    fun preflight(): Result = runRoot(
        "id -u; test -d '$MODULE_DIR' && echo 'TokenX module found' || " +
            "{ echo 'TokenX system module missing'; exit 2; }; " +
            "test ! -f '$MODULE_DIR/disable' || { echo 'Module disabled'; exit 3; }; " +
            "echo 'Preflight passed; no changes made'"
    )

    /** Does not mutate the device or confuse the system_server process with a live binder. */
    fun verify(): Result = runRoot(
        "echo '=== TokenX UID 1000 diagnostic ==='; " +
            "id; " +
            "if test -d '$MODULE_DIR'; then " +
            "echo 'module=present'; " +
            "else echo 'module=missing'; exit 2; fi; " +
            "if test -f '$MODULE_DIR/disable'; then " +
            "echo 'module=disabled'; exit 3; fi; " +
            "echo 'backend=unverified (requires TokenX binder handshake)'"
    )

    /**
     * Deliberately fail closed until the native module provisioning entry point
     * is identified. No arbitrary system partition edits or soft reboot.
     */
    fun install(): Result = Result(
        false, 4,
        "Native TokenX provisioning entry point not yet wired. No changes made.",
        "none"
    )

    private fun runRoot(script: String): Result {
        val command = listOf("su", "-c", script)
        return try {
            val process = ProcessBuilder(command).redirectErrorStream(true).start()
            val output = BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                buildString {
                    var line: String?
                    while (reader.readLine().also { line = it } != null) appendLine(line)
                }
            }
            val exitCode = process.waitFor()
            Result(exitCode == 0, exitCode, output, command.joinToString(" "))
        } catch (t: Throwable) {
            Result(false, -1, "${t.javaClass.simpleName}: ${t.message}", command.joinToString(" "))
        }
    }
}
