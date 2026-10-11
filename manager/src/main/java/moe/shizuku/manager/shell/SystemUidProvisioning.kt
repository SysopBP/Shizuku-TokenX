package moe.shizuku.manager.shell

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import moe.shizuku.manager.BuildConfig
import moe.shizuku.manager.MainActivity
import moe.shizuku.manager.R
import moe.shizuku.manager.tokenx.TokenXXposedSystemServerClient
import java.util.concurrent.TimeUnit

data class ProvisionStatus(val phase: String = "idle", val detail: String = "", val busy: Boolean = false)

/** User-confirmed work, persisted before reboot and resumed without requesting another reboot. */
object SystemUidProvisioning {
    private const val WORK = "tokenx-system-server-provision"
    private const val CHANNEL = "tokenx-system-server-provision"
    private const val ACTIVE_NOTIFICATION = 3100
    private const val STATUS_NOTIFICATION = 3101
    private val mutableStatus = MutableStateFlow(ProvisionStatus())
    val status = mutableStatus.asStateFlow()
    internal fun prefs(context: Context) = context.getSharedPreferences(WORK, Context.MODE_PRIVATE)

    fun initialize(context: Context) {
        val prefs = prefs(context)
        mutableStatus.value = ProvisionStatus(prefs.getString("phase", "idle").orEmpty(),
            prefs.getString("detail", "").orEmpty(), prefs.getBoolean("busy", false))
        if (prefs.getBoolean("pending", false)) {
            WorkManager.getInstance(context).enqueueUniqueWork(WORK, ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<SystemUidProvisionWorker>().build())
        }
    }

    /** Call only after the explicit soft reboot confirmation. */
    fun start(context: Context) {
        if (status.value.busy) return
        report(context, "checking", "Checking KernelSU and injection prerequisites", true)
        WorkManager.getInstance(context).enqueueUniqueWork(WORK, ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<SystemUidProvisionWorker>().build())
    }

    internal fun report(context: Context, phase: String, detail: String, busy: Boolean) {
        prefs(context).edit().putString("phase", phase).putString("detail", detail).putBoolean("busy", busy).commit()
        mutableStatus.value = ProvisionStatus(phase, detail, busy)
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "System Server provisioning",
            NotificationManager.IMPORTANCE_LOW))
        runCatching { manager.notify(if (busy) ACTIVE_NOTIFICATION else STATUS_NOTIFICATION, notification(context, detail, busy)) }
    }

    private fun notification(context: Context, detail: String, busy: Boolean): Notification {
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_system_icon)
            .setContentTitle("TokenX • System Server UID 1000")
            .setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setContentIntent(open).setOnlyAlertOnce(true).setOngoing(busy)
            .setProgress(0, 0, busy).setAutoCancel(!busy).build()
    }

    internal fun foreground(context: Context): ForegroundInfo {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "System Server provisioning",
            NotificationManager.IMPORTANCE_LOW))
        val notification = notification(context, status.value.detail, true)
        return if (Build.VERSION.SDK_INT >= 34)
            ForegroundInfo(ACTIVE_NOTIFICATION, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else ForegroundInfo(ACTIVE_NOTIFICATION, notification)
    }
}

class SystemUidProvisionWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = SystemUidProvisioning.prefs(applicationContext)
        fun report(phase: String, detail: String, busy: Boolean = true) =
            SystemUidProvisioning.report(applicationContext, phase, detail, busy)
        fun fail(detail: String): Result {
            prefs.edit().putBoolean("pending", false).commit()
            report("failed", detail, false)
            return Result.failure()
        }
        try {
            setForeground(SystemUidProvisioning.foreground(applicationContext))
            if (!prefs.getBoolean("pending", false)) {
                report("checking", "Checking KernelSU, Zygisk and the packaged injection payload")
                val check = SystemUidProvisioner.preflight(applicationContext)
                if (!check.success) return@withContext fail(check.output.trim() + "\nNo soft reboot requested.")
                val installation = SystemUidProvisioner.install(applicationContext) { report("installing", it) }
                if (!installation.success || !installation.output.contains("TOKENX_INSTALL_VERIFIED"))
                    return@withContext fail(installation.output.trim() + "\nInstallation did not verify. No soft reboot requested.")
                val previousPid = Regex("TOKENX_PREVIOUS_PID=([0-9]+)").findAll(installation.output)
                    .lastOrNull()?.groupValues?.get(1)?.toIntOrNull() ?: 0
                if (previousPid <= 0) return@withContext fail("Cannot establish reboot verification identity. No soft reboot requested.")
                // Commit synchronously before KernelSU can terminate this process.
                if (!prefs.edit().putBoolean("pending", true).putInt("previousPid", previousPid)
                        .putInt("expectedBuild", BuildConfig.VERSION_CODE)
                        .putLong("requestedAt", System.currentTimeMillis()).commit())
                    return@withContext fail("Cannot persist recovery state. No soft reboot requested.")
                report("rebooting", "Installation verified. Soft rebooting Android; verification will resume automatically.")
                // The same work request survives a soft reboot. A retry enters verification only.
                val reboot = SystemUidProvisioner.softReboot()
                if (!reboot.success) return@withContext fail("KernelSU declined the soft reboot: " + reboot.output.trim())
            }
            if (prefs.getInt("expectedBuild", -1) != BuildConfig.VERSION_CODE)
                return@withContext fail("TokenX changed during provisioning. Provision the installed APK again.")
            report("verifying", "Waiting for the new System Server to complete the TokenX Binder handshake")
            val previousPid = prefs.getInt("previousPid", 0)
            val expectedBuild = prefs.getInt("expectedBuild", -1)
            val deadline = android.os.SystemClock.elapsedRealtime() + TimeUnit.MINUTES.toMillis(2)
            do {
                val identity = TokenXXposedSystemServerClient.verifyInjection(expectedBuild, previousPid)
                if (identity != null && ProvisionPolicy.accepts(previousPid, identity.pid, expectedBuild,
                        BuildConfig.VERSION_CODE, identity.verifiedSystemServer,
                        TokenXXposedSystemServerClient.systemProbe()?.verified == true)) {
                    prefs.edit().putBoolean("pending", false).commit()
                    report("verified", "Verified UID 1000 • system_server PID ${identity.pid} • TokenX Binder handshake and framework services passed", false)
                    return@withContext Result.success()
                }
                delay(1000)
            } while (android.os.SystemClock.elapsedRealtime() < deadline)
            fail("System Server injection did not verify within 2 minutes. Root and System routes remain available. No additional reboot will be triggered. Review Zygisk compatibility or disable TokenX System Server in KernelSU.")
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            fail("Provisioning failed: ${e.message}. No additional reboot requested.")
        }
    }
}
