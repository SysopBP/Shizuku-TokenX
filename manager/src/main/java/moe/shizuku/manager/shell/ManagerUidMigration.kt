package moe.shizuku.manager.shell

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.os.ServiceManager
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import moe.shizuku.manager.BuildConfig
import moe.shizuku.manager.tokenx.TokenXXposedSystemServerClient
import java.io.File
import java.security.MessageDigest

/** Same-package migration; System Server access remains on the existing LSPosed RPC. */
object ManagerUidMigration {
    private const val PACKAGE = "moe.shizuku.privileged.api"
    private const val DIRECTORY = "/data/system/tokenx_manager_migration"
    private const val WORK = "tokenx-manager-uid-migration"
    private val mutableStatus = MutableStateFlow(ProvisionStatus())
    val status = mutableStatus.asStateFlow()

    fun initialize(context: Context) {
        if (Process.myUid() == Process.SYSTEM_UID) {
            mutableStatus.value = ProvisionStatus("installed", "TokenX app UID 1000 • verify the Xposed connection")
        }
    }

    fun start(context: Context, operation: String = "provision") {
        require(operation in setOf("provision", "verify", "restoreSigning"))
        if (status.value.busy) return
        report("checking", "Checking TokenX package and the existing Xposed Binder", true)
        WorkManager.getInstance(context).enqueueUniqueWork(WORK, ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<ManagerUidMigrationWorker>()
                .setInputData(workDataOf("operation" to operation)).build())
    }

    internal fun report(phase: String, detail: String, busy: Boolean = false) {
        mutableStatus.value = ProvisionStatus(phase, detail, busy)
    }

    internal fun execute(context: Context, operation: String) {
        check(context.packageName == PACKAGE && BuildConfig.APPLICATION_ID == PACKAGE && Process.myUid() / 100000 == 0) {
            "Use the original TokenX package in the owner profile"
        }
        val activity = ServiceManager.getService("activity")
        val identity = TokenXXposedSystemServerClient.identity(activity)
        check(identity?.verifiedSystemServer == true && TokenXXposedSystemServerClient.systemProbe(activity)?.verified == true) {
            "The existing Xposed System Server Binder must be connected first. Enable TokenX for System Framework in LSPosed."
        }
        val installed = context.packageManager.getPackageInfo(PACKAGE, PackageManager.GET_SIGNING_CERTIFICATES)
        val signers = installed.signingInfo?.apkContentsSigners ?: error("Installed signer unavailable")
        check(signers.size == 1 && installed.longVersionCode == BuildConfig.VERSION_CODE.toLong()) { "Unsupported installed package identity" }
        val certificate = signers.single().toByteArray()
        if (operation == "verify" || Process.myUid() == 1000 && operation == "provision") {
            check(Process.myUid() == 1000 && installed.applicationInfo?.uid == 1000 && installed.sharedUserId == "android.uid.system") {
                "TokenX manager is not installed with system shared UID 1000"
            }
            connectCompanionServer(context)
            report("verified", "TokenX app UID 1000 • Xposed System Server PID ${identity!!.pid} • companion server Binder UID 1000 connected")
            return
        }
        check(Process.myUid() >= 10000) { "Migration and signing recovery require the ordinary manager" }
        if (operation == "restoreSigning") {
            TokenXXposedSystemServerClient.managerUidSigning(32, certificate)
            report("restored", "Manager signing enrollment restored after rollback")
            return
        }
        check(Build.VERSION.SDK_INT >= 36) { "Manager migration requires Android 16+" }
        val directory = File(context.cacheDir, "manager-migration").apply { check(mkdirs() || isDirectory) }
        val system = File(directory, "system.apk")
        context.assets.open("tokenx-manager/system.apk").use { source -> system.outputStream().use { source.copyTo(it) } }
        val archive = context.packageManager.getPackageArchiveInfo(system.path, PackageManager.GET_SIGNING_CERTIFICATES)
            ?: error("Invalid system-UID manager APK")
        check(archive.packageName == PACKAGE && archive.sharedUserId == "android.uid.system" &&
            archive.longVersionCode == installed.longVersionCode && archive.signingInfo?.apkContentsSigners?.let {
                it.size == 1 && it[0] == signers[0]
            } == true) { "System APK package, build or signer mismatch; no changes made" }
        val ordinary = File(context.applicationInfo.sourceDir)
        val script = File(directory, "migrate.sh")
        context.assets.open("tokenx-manager/migrate.sh").use { source -> script.outputStream().use { source.copyTo(it) } }
        val ordinaryHash = sha256(ordinary)
        val systemHash = sha256(system)
        val scriptHash = sha256(script)
        connectCompanionServer(context)
        val preflight = TokenXXposedSystemServerClient.managerUidSigning(30, certificate)
        check(!preflight.getBoolean("receipt")) { "Manager signing recovery record already exists; restore after rollback before another migration" }
        report("staging", "Staging signed APKs and detached recovery process", true)
        root("""
            set -e
            [ "$(id -u)" = 0 ] || exit 2
            for command in tar setsid sha256sum restorecon chown am pm; do command -v "${'$'}command" >/dev/null || exit 2; done
            [ ! -L '$DIRECTORY' ] || exit 2
            if [ -e '$DIRECTORY' ]; then
                [ -d '$DIRECTORY' ] && [ "$(stat -c %u '$DIRECTORY')" = 0 ] || exit 2
                # Never overwrite an earlier backup or an interrupted transaction.
                [ ! -e '$DIRECTORY/status' ] || { echo 'Migration recovery files already exist; review the previous operation first'; exit 2; }
            fi
            mkdir -p '$DIRECTORY'; chown 0:0 '$DIRECTORY'; chmod 700 '$DIRECTORY'
            stage() {
                [ ! -e "${'$'}2" ] && [ ! -L "${'$'}2" ] || exit 2
                cp "${'$'}1" "${'$'}2"; chmod 600 "${'$'}2"
                [ "$(sha256sum "${'$'}2" | cut -d ' ' -f 1)" = "${'$'}3" ] || exit 2
            }
            stage ${q(ordinary.path)} '$DIRECTORY/ordinary.apk' '$ordinaryHash'
            stage ${q(system.path)} '$DIRECTORY/system.apk' '$systemHash'
            stage ${q(script.path)} '$DIRECTORY/migrate.sh' '$scriptHash'
        """.trimIndent())
        if (!preflight.getBoolean("authorized")) {
            report("enrolling", "Saving signing recovery and enrolling the installed TokenX certificate", true)
            TokenXXposedSystemServerClient.managerUidSigning(31, certificate)
        }
        report("replacing", "TokenX will close during reinstall. Recovery files are staged; the detached process restores app data.", true)
        root("""
            set -e
            echo staged > '$DIRECTORY/status'
            setsid /system/bin/sh '$DIRECTORY/migrate.sh' '${Process.myUid()}' '$ordinaryHash' '$systemHash' \
                > '$DIRECTORY/migration.log' 2>&1 < /dev/null &
            echo TOKENX_MANAGER_MIGRATION_STARTED
        """.trimIndent())
    }

    /** Retains the existing dedicated SYSTEM slot and its on-demand starter. */
    private fun connectCompanionServer(context: Context) {
        fun binder() = moe.shizuku.manager.ShizukuManagerProvider.systemBinder()
            ?.takeIf { it.isBinderAlive && it.pingBinder() }
        if (binder() == null) {
            report("connecting", "Connecting the existing UID-1000 companion server", true)
            val start = SystemUidProvisioner.prepareAndStartShizukuUid1000(context)
            check(start.success) { "Companion server start failed: ${start.output}" }
            val deadline = android.os.SystemClock.elapsedRealtime() + 15_000L
            while (binder() == null && android.os.SystemClock.elapsedRealtime() < deadline) Thread.sleep(250)
        }
        val endpoint = binder() ?: error("Companion server Binder did not connect; app UID change alone is not server readiness")
        val data = android.os.Parcel.obtain()
        val reply = android.os.Parcel.obtain()
        try {
            data.writeInterfaceToken("moe.shizuku.server.IShizukuService")
            check(endpoint.transact(3, data, reply, 0)) { "Companion server identity transaction unavailable" }
            reply.readException()
            check(reply.readInt() == 1000) { "Companion server must answer as UID 1000" }
        } finally { data.recycle(); reply.recycle() }
    }

    private fun root(script: String) {
        val result = SystemUidProvisioner.runRoot(script)
        check(result.success) { result.output.trim().ifBlank { "Root operation failed (${result.exitCode})" } }
    }
    private fun q(value: String) = ProvisionPolicy.quote(value)
    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(65536)
            while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

class ManagerUidMigrationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            ManagerUidMigration.execute(applicationContext, inputData.getString("operation") ?: "verify")
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) {
            ManagerUidMigration.report("failed", e.message ?: "Manager migration failed")
            Result.failure()
        }
    }
}
