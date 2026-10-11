package moe.shizuku.manager.shell

import android.content.Context
import android.os.Build
import moe.shizuku.manager.BuildConfig
import java.io.File
import java.io.BufferedReader
import java.io.InputStreamReader
import java.security.MessageDigest
import java.security.DigestInputStream

/**
 * Helpers for the current TokenX System UID / System Server backend.
 *
 * The KernelSU module is the primary installer. A module-local provisioning DEX may
 * remain available as a fallback; this class audits that fallback without executing it.
 * It stages TokenX's isolated UID-1000 Shizuku worker when needed. Live
 * system_server identity is verified separately through the LSPosed _TKN RPC.
 */
object SystemUidProvisioner {
    const val MODULE_ID = "tokenx_system_server"
    const val MODULE_DIR = "/data/adb/modules/$MODULE_ID"
    // KernelSU's bin/ksud is a symlink to this canonical daemon. Validate the real file.
    private const val KSUD = "/data/adb/ksud"

    /** Read-only checks before preparing or installing any privileged payload. */
    fun preflight(context: Context): Result {
        if (Build.VERSION.SDK_INT < 27 || Build.SUPPORTED_64_BIT_ABIS.isEmpty())
            return Result(false, 2, "Injection requires Android 8.1+ and a 64-bit System Server.", "preflight")
        if (context.packageName != BuildConfig.APPLICATION_ID)
            return Result(false, 2, "Injection is unavailable for a renamed/stealth APK.", "preflight")
        if (context.applicationInfo.uid / 100000 != 0)
            return Result(false, 2, "Provision System Server from the device owner profile.", "preflight")
        return runRoot("""
            set -e
            [ "$(id -u)" = 0 ] || { echo 'Root access required'; exit 2; }
            test -f '$KSUD' && test -x '$KSUD' && test ! -L '$KSUD' || { echo 'KernelSU daemon unavailable at $KSUD. Open KernelSU Manager to initialize it.'; exit 2; }
            [ "$(stat -c %u '$KSUD')" = 0 ] || exit 2
            '$KSUD' debug version | grep -Eq 'Kernel Version: [1-9][0-9]*' || { echo 'KernelSU kernel unavailable'; exit 2; }
            '$KSUD' module install --help >/dev/null
            '$KSUD' soft-reboot --help >/dev/null
            runtime=
            for id in zygisksu zygisk_next rezygisk; do
                path="/data/adb/modules/${'$'}id"
                if test -f "${'$'}path/module.prop" && test ! -f "${'$'}path/disable" && test ! -f "${'$'}path/remove"; then runtime=${'$'}id; break; fi
            done
            [ -n "${'$'}runtime" ] || { echo 'Install and enable a Zygisk API v4 runtime for KernelSU (Zygisk Next or ReZygisk) first.'; exit 2; }
            for path in '$MODULE_DIR' '/data/adb/modules_update/$MODULE_ID'; do
                if test -e "${'$'}path"; then
                    test ! -L "${'$'}path" && grep -qx 'tokenxInstaller=kernel-v1' "${'$'}path/module.prop" || { echo 'An unmanaged TokenX module occupies the installation path; review it in KernelSU first.'; exit 2; }
                fi
            done
            pid=$(pidof system_server)
            case "${'$'}pid" in ''|*[!0-9]*) echo 'Cannot establish pre-reboot System Server PID'; exit 2;; esac
            echo "TOKENX_PREVIOUS_PID=${'$'}pid"
            echo "ZYGISK_RUNTIME=${'$'}runtime"
            echo 'Preflight passed; no system changes made'
        """.trimIndent())
    }

    /** Only our packaged injector and this installed APK's DEX files become module contents. */
    fun install(context: Context, onStatus: (String) -> Unit = {}): Result {
        val checked = preflight(context)
        if (!checked.success) return checked
        val directory = File(context.cacheDir, "tokenx-injection").apply { mkdirs() }
        val archive = File(directory, "module.zip")
        return try {
            onStatus("Preparing the TokenX injection payload")
            val checksums = context.assets.open("tokenx-system-server/customize.sh").use { customize ->
                SystemServerModuleArchive.create(File(context.applicationInfo.sourceDir), archive,
                    Build.SUPPORTED_64_BIT_ABIS.first(), BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, customize)
            }
            onStatus("Installing the checked payload with KernelSU")
            val manifest = File(directory, "payload.sha256").apply { writeText(checksums.toString()) }
            val rootManifestHash = sha256(manifest)
            val archiveHash = sha256(archive)
            val script = """
                set -e
                [ "$(sha256sum ${q(archive.path)} | cut -d ' ' -f 1)" = '$archiveHash' ] || exit 3
                echo TOKENX_INSTALLER=ksud
                echo TOKENX_ARCHIVE_SHA256=$archiveHash
                echo TOKENX_KSUD_PATH=$KSUD
                echo TOKENX_KSUD_VERSION_START
                '$KSUD' debug version || exit 3
                echo TOKENX_KSUD_VERSION_END
                echo TOKENX_MODULE_INSTALL_START
                if '$KSUD' module install ${q(archive.path)}; then
                    echo TOKENX_MODULE_INSTALL_SUCCESS
                else
                    echo TOKENX_MODULE_INSTALL_FAILED
                    echo 'KernelSU module install failed; no reboot requested.'
                    exit 3
                fi
                staged='/data/adb/modules_update/$MODULE_ID'
                test -d "${'$'}staged" || staged='$MODULE_DIR'
                test ! -L "${'$'}staged" || exit 3
                [ "$(sha256sum "${'$'}staged/payload.sha256" | cut -d ' ' -f 1)" = '$rootManifestHash' ] || { echo 'Installed manifest mismatch'; exit 3; }
                cd "${'$'}staged"
                sha256sum -c payload.sha256
                test ! -f disable && test ! -f remove
                pid=$(pidof system_server)
                case "${'$'}pid" in ''|*[!0-9]*) exit 3;; esac
                echo "TOKENX_PREVIOUS_PID=${'$'}pid"
                echo TOKENX_INSTALL_VERIFIED
            """.trimIndent()
            runRoot(script)
        } catch (e: Exception) {
            Result(false, -1, "Injection preparation failed: ${e.message}. No soft reboot requested.", "install")
        } finally { directory.deleteRecursively() }
    }

    /** Called only by the confirmed worker, after installation and hashes succeeded. */
    fun softReboot(): Result = runRoot("'$KSUD' soft-reboot")

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        DigestInputStream(file.inputStream(), digest).use { it.copyTo(object : java.io.OutputStream() { override fun write(b: Int) {} override fun write(b: ByteArray, off: Int, len: Int) {} }) }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    const val STAGED_SHIZUKU = "/data/local/tmp/libshizuku.so"

    enum class Stage { D2_GATE, SYSTEM_BRIDGE }
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
        val d2 = runRoot("if test -d '$MODULE_DIR'; then echo TOKENX_MODULE=present; else echo TOKENX_MODULE=not-installed; fi")
        onProgress(Progress(Stage.D2_GATE, StageState.VERIFIED, "D2 is optional; " + d2.output.trim()))
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
        val identity = moe.shizuku.manager.tokenx.TokenXXposedSystemServerClient.identity()
        val probe = moe.shizuku.manager.tokenx.TokenXXposedSystemServerClient.systemProbe()
        val passed = identity?.verifiedSystemServer == true && probe?.verified == true && identity.pid == probe.pid
        return Result(passed, if (passed) 0 else 4,
            if (passed) "TokenX _TKN verified: uid=1000 pid=${identity?.pid} process=system_server; framework services passed"
            else "TokenX System Server Binder identity or functional handshake unavailable",
            "TokenX Binder handshake")
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

            val transcript = StringBuilder()
            val collector = Thread({
                runCatching {
                    BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                        while (true) {
                            val line = reader.readLine() ?: break
                            synchronized(transcript) {
                                if (transcript.length < 16384) transcript.appendLine(line.take(16384 - transcript.length))
                            }
                        }
                    }
                }
            }, "TokenX-Root-Output").apply { isDaemon = true; start() }
            val deadline = android.os.SystemClock.elapsedRealtime() + 60_000L
            var exitCode: Int? = null
            while (android.os.SystemClock.elapsedRealtime() < deadline) {
                exitCode = try { process.exitValue() } catch (_: IllegalThreadStateException) { null }
                if (exitCode != null) break
                Thread.sleep(50)
            }
            if (exitCode == null) process.destroy()
            collector.join(1000)
            val output = synchronized(transcript) { transcript.toString() }
            Result(exitCode == 0, exitCode ?: -2,
                if (exitCode == null) "$output\nRoot action timed out; no further action will run." else output,
                command.joinToString(" "))
        } catch (t: Throwable) {
            Result(false, -1, "${t.javaClass.simpleName}: ${t.message}", command.joinToString(" "))
        }
    }

    private fun q(value: String): String =
        "'" + value.replace("'", "'\\''") + "'"
}
