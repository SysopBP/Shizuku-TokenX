package moe.shizuku.manager.shell

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Parcel
import android.os.Process
import androidx.core.app.NotificationCompat
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import moe.shizuku.manager.BuildConfig
import moe.shizuku.manager.R
import moe.shizuku.manager.tokenx.TokenXXposedSystemServerClient
import moe.shizuku.manager.ui.UidTestActivity
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Fixed-package experiment; never changes the manager's UID or backend routing. */
object UidTestCompanion {
    const val PACKAGE = "moe.shizuku.tokenx.uidtest"
    private const val WORK = "tokenx-uid-test"
    private const val CHANNEL = "tokenx-uid-test"
    private val mutableStatus = MutableStateFlow(ProvisionStatus())
    val status = mutableStatus.asStateFlow()
    private fun prefs(context: Context) = context.getSharedPreferences(WORK, Context.MODE_PRIVATE)

    fun initialize(context: Context) {
        val saved = prefs(context)
        mutableStatus.value = ProvisionStatus(saved.getString("phase", "idle").orEmpty(),
            saved.getString("detail", "Install the ordinary test app first.").orEmpty(), false)
    }
    fun start(context: Context, operation: String) {
        require(operation in setOf("ordinary", "provision", "verify", "remove"))
        WorkManager.getInstance(context).enqueueUniqueWork(WORK, ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<UidTestWorker>().setInputData(workDataOf("operation" to operation)).build())
    }
    internal fun report(context: Context, phase: String, detail: String, busy: Boolean) {
        check(prefs(context).edit().putString("phase", phase).putString("detail", detail).commit())
        mutableStatus.value = ProvisionStatus(phase, detail, busy)
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "UID 1000 test companion", NotificationManager.IMPORTANCE_LOW))
        runCatching { manager.notify(3111, notification(context, detail, busy)) }
    }
    internal fun notification(context: Context, detail: String, busy: Boolean) = NotificationCompat.Builder(context, CHANNEL)
        .setSmallIcon(R.drawable.ic_system_icon).setContentTitle("TokenX UID test").setContentText(detail)
        .setStyle(NotificationCompat.BigTextStyle().bigText(detail)).setOngoing(busy)
        .setContentIntent(PendingIntent.getActivity(context, 3111, Intent(context, UidTestActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)).build()

    internal fun execute(context: Context, operation: String) {
        check(Build.VERSION.SDK_INT >= 36) { "UID companion experiment requires Android 16+" }
        check(BuildConfig.APPLICATION_ID == "moe.shizuku.privileged.api" && Process.myUid() / 100000 == 0) {
            "Use the unchanged TokenX manager in the device owner profile"
        }
        val payloads = prepare(context)
        val saved = prefs(context)
        if (operation in setOf("ordinary", "provision")) check(!saved.getBoolean("pending", false)) {
            "Interrupted provisioning requires recovery. Use Remove & restore before starting again."
        }
        when (operation) {
            "ordinary" -> {
                check(installedUid(context) == -1) { "Test companion already installed; verify or remove it first" }
                stage(payloads)
                report(context, "installing", "Installing ordinary test app", true)
                root("pm install --user 0 ${q(payloads.rootOrdinary)}", success = true)
                val proof = probe(context)
                check(proof.uid >= 10000 && proof.flavor == "ordinary") { "Ordinary identity mismatch: $proof" }
                report(context, "ordinary", proof.detail(), false)
            }
            "provision" -> {
                check(Build.VERSION.SDK_INT >= 36) { "Shared UID provisioning requires Android 16+" }
                val proof = probe(context)
                check(proof.uid >= 10000 && proof.flavor == "ordinary") { "Install and verify the ordinary app first" }
                stage(payloads)
                val preflight = TokenXXposedSystemServerClient.uidTestSigning(20, payloads.certificate)
                check(!preflight.getBoolean("receipt") && !preflight.getBoolean("authorized")) {
                    "Existing signing enrollment detected; remove and restore it first"
                }
                // Durable intent precedes all signing mutations and package removal.
                check(saved.edit().putBoolean("pending", true).putBoolean("everEnrolled", true).commit())
                report(context, "enrolling", "Saving recovery record and enrolling the dedicated test certificate", true)
                TokenXXposedSystemServerClient.uidTestSigning(21, payloads.certificate)
                report(context, "replacing", "Replacing only the disposable test companion", true)
                root("pm uninstall $PACKAGE", success = true)
                check(installedUid(context) == -1) { "Ordinary companion removal not verified; stopping" }
                root("pm install --user 0 ${q(payloads.rootSystem)}", success = true)
                val actual = probe(context)
                check(actual.uid == 1000 && actual.flavor == "system") { "UID 1000 handshake failed: $actual" }
                check(TokenXXposedSystemServerClient.packageUid(PACKAGE) == 1000) { "System Server package UID probe failed" }
                check(saved.edit().putBoolean("pending", false).commit())
                report(context, "provisioned", actual.detail() + "\nTokenX System Server identity, nonce and package UID verified. Verify again after your next reboot.", false)
            }
            "verify" -> {
                val proof = probe(context)
                if (proof.uid == 1000) {
                    TokenXXposedSystemServerClient.uidTestSigning(20, payloads.certificate)
                    check(TokenXXposedSystemServerClient.packageUid(PACKAGE) == 1000) { "System Server package UID mismatch" }
                }
                report(context, "verified", proof.detail() + if (proof.uid == 1000) "\nTokenX System Server handshake also verified." else "", false)
            }
            "remove" -> {
                root("[ \"${'$'}(id -u)\" = 0 ]")
                // Check access to the durable journal BEFORE deleting the app. Main-app
                // prefs are not authoritative: they may have been cleared or restored.
                val recovery = TokenXXposedSystemServerClient.uidTestSigning(20, payloads.certificate)
                if (installedUid(context) != -1) {
                    validateInstalled(context, payloads)
                    root("pm uninstall $PACKAGE", success = true)
                    check(installedUid(context) == -1) { "Test companion still installed; trust restoration not attempted" }
                }
                if (recovery.getBoolean("receipt") || recovery.getBoolean("authorized") || saved.getBoolean("everEnrolled", false)) {
                    TokenXXposedSystemServerClient.uidTestSigning(22, payloads.certificate)
                }
                check(saved.edit().putBoolean("pending", false).putBoolean("everEnrolled", false).commit())
                root("rm -f ${q(payloads.rootOrdinary)} ${q(payloads.rootSystem)}")
                report(context, "removed", "Test companion removed. Signing recovery completed. No reboot requested.", false)
            }
        }
    }

    private data class Payloads(val ordinary: File, val system: File, val certificate: ByteArray) {
        val rootOrdinary = "/data/local/tmp/tokenx-uid-test-ordinary.apk"
        val rootSystem = "/data/local/tmp/tokenx-uid-test-system.apk"
    }
    private fun prepare(context: Context): Payloads {
        val directory = File(context.filesDir, "uid-test").apply { check(mkdirs() || isDirectory) }
        fun extract(name: String): File = File(directory, "$name.apk").apply {
            context.assets.open("tokenx-uid-test/$name.apk").use { source -> outputStream().use { source.copyTo(it) } }
        }
        val ordinary = extract("ordinary"); val system = extract("system")
        fun archive(file: File) = context.packageManager.getPackageArchiveInfo(file.path, PackageManager.GET_SIGNING_CERTIFICATES)
            ?: error("Invalid test APK ${file.name}")
        val first = archive(ordinary); val second = archive(system)
        check(first.packageName == PACKAGE && second.packageName == PACKAGE && first.sharedUserId == null &&
            second.sharedUserId == "android.uid.system" && first.longVersionCode == BuildConfig.VERSION_CODE.toLong() &&
            second.longVersionCode == first.longVersionCode) { "Test APK identity/build mismatch" }
        val a = first.signingInfo?.apkContentsSigners ?: error("Ordinary signature unavailable")
        val b = second.signingInfo?.apkContentsSigners ?: error("System signature unavailable")
        check(a.size == 1 && b.size == 1 && a[0] == b[0]) { "Test APK signatures differ" }
        val manager = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        check(manager.signingInfo?.apkContentsSigners?.none { it == a[0] } == true) { "Test must use a certificate separate from TokenX" }
        return Payloads(ordinary, system, a[0].toByteArray())
    }
    private fun validateInstalled(context: Context, payloads: Payloads) {
        val info = context.packageManager.getPackageInfo(PACKAGE, PackageManager.GET_SIGNING_CERTIFICATES)
        check(info.longVersionCode == BuildConfig.VERSION_CODE.toLong() && info.signingInfo?.apkContentsSigners?.let {
            it.size == 1 && it[0].toByteArray().contentEquals(payloads.certificate)
        } == true) { "Companion belongs to another build; use its original manager for recovery" }
    }
    private fun installedUid(context: Context) = try { context.packageManager.getApplicationInfo(PACKAGE, 0).uid }
        catch (_: PackageManager.NameNotFoundException) { -1 }
    private fun stage(payloads: Payloads) {
        root("""
            [ "${'$'}(id -u)" = 0 ] || exit 1
            for target in ${q(payloads.rootOrdinary)} ${q(payloads.rootSystem)}; do
              [ ! -L "${'$'}target" ] || exit 1
              [ ! -e "${'$'}target" ] || [ -f "${'$'}target" ] || exit 1
            done
            cp ${q(payloads.ordinary.path)} ${q(payloads.rootOrdinary)} || exit 1
            cp ${q(payloads.system.path)} ${q(payloads.rootSystem)} || exit 1
            chmod 600 ${q(payloads.rootOrdinary)} ${q(payloads.rootSystem)} || exit 1
            [ "${'$'}(sha256sum ${q(payloads.rootOrdinary)} | cut -d ' ' -f 1)" = '${sha256(payloads.ordinary)}' ] || exit 1
            [ "${'$'}(sha256sum ${q(payloads.rootSystem)} | cut -d ' ' -f 1)" = '${sha256(payloads.system)}' ] || exit 1
        """.trimIndent())
    }
    private fun sha256(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
    private fun q(value: String) = "'" + value.replace("'", "'\\''") + "'"
    private fun root(script: String, success: Boolean = false) {
        val process = ProcessBuilder("su", "-c", script).redirectErrorStream(true).start()
        val output = StringBuilder()
        val collector = Thread {
            runCatching { process.inputStream.bufferedReader().useLines { lines -> lines.forEach {
                synchronized(output) { if (output.length < 16384) output.appendLine(it.take(1024)) }
            } } }
        }.apply { isDaemon = true; start() }
        val completed = process.waitFor(60, TimeUnit.SECONDS)
        if (!completed) process.destroy()
        collector.join(1000)
        val text = synchronized(output) { output.toString() }
        check(completed && process.exitValue() == 0 && (!success || text.lineSequence().any { it.trim() == "Success" })) {
            "Root operation ${if (completed) "failed (${process.exitValue()})" else "timed out; outcome uncertain"}: $text"
        }
    }
    private data class Proof(val uid: Int, val pid: Int, val process: String, val selinux: String, val flavor: String) {
        fun detail() = "Actual Binder UID $uid · PID $pid\nProcess: $process\nSELinux: $selinux\nVariant: $flavor · nonce/build verified"
    }
    private fun probe(context: Context): Proof {
        validateInstalled(context, prepare(context))
        val latch = CountDownLatch(1)
        var binder: IBinder? = null
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) { binder = service; latch.countDown() }
            override fun onServiceDisconnected(name: ComponentName) { binder = null }
            override fun onNullBinding(name: ComponentName) { latch.countDown() }
        }
        val intent = Intent().setComponent(ComponentName(PACKAGE, "moe.shizuku.uidtest.ProbeService"))
        check(context.bindService(intent, connection, Context.BIND_AUTO_CREATE)) { "Companion service unavailable" }
        try {
            check(latch.await(15, TimeUnit.SECONDS)) { "Companion Binder connection timed out" }
            val endpoint = binder ?: error("Companion supplied no Binder")
            val nonce = SecureRandom().nextLong()
            val data = Parcel.obtain(); val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken("moe.shizuku.tokenx.uidtest.IProbe"); data.writeLong(nonce)
                check(endpoint.transact(0x5f544b4e, data, reply, 0)) { "Companion handshake unsupported" }
                reply.readException()
                check(reply.readInt() == 1 && reply.readInt() == BuildConfig.VERSION_CODE && reply.readLong() == nonce) {
                    "Companion protocol/build/nonce mismatch"
                }
                val proof = Proof(reply.readInt(), reply.readInt(), reply.readString().orEmpty(),
                    reply.readString().orEmpty(), reply.readString().orEmpty())
                check(proof.uid == installedUid(context) && proof.pid > 0 && proof.process == PACKAGE &&
                    proof.selinux.startsWith("u:r:") && !proof.selinux.contains("system_server") &&
                    ((proof.flavor == "ordinary" && proof.uid >= 10000) || (proof.flavor == "system" && proof.uid == 1000))) {
                    "Companion process identity mismatch: $proof"
                }
                return proof
            } finally { data.recycle(); reply.recycle() }
        } finally { context.unbindService(connection) }
    }
}

class UidTestWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val operation = inputData.getString("operation") ?: return@withContext Result.failure()
        UidTestCompanion.report(applicationContext, "checking", "Checking test companion prerequisites", true)
        try {
            setForeground(ForegroundInfo(3110, UidTestCompanion.notification(applicationContext, "UID test in progress", true),
                if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0))
            UidTestCompanion.execute(applicationContext, operation)
            Result.success()
        } catch (e: Exception) {
            UidTestCompanion.report(applicationContext, "failed", "${e.message}\nNo reboot requested. If provisioning started, use Remove & restore.", false)
            Result.failure()
        }
    }
}
