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

/** User-confirmed live injection. Recovery verifies the recorded request without repeating injection. */
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

    /** Call only after the explicit live injection confirmation. */
    fun start(context: Context) {
        if (status.value.busy) return
        report(context, "checking", "Checking root and live injection prerequisites", true)
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
            if (prefs.getBoolean("pending", false) && prefs.getString("mode", "") != "direct")
                return@withContext fail("A previous module/reboot operation is pending. Start live injection again; no reboot or automatic injection was requested.")
            if (!prefs.getBoolean("pending", false)) {
                report("checking", "Checking root and the running System Server")
                val check = DirectSystemServerProvisioner.preflight(applicationContext)
                if (!check.success) return@withContext fail(check.output.trim())
                val targetPid = Regex("TOKENX_PREVIOUS_PID=([0-9]+)").find(check.output)
                    ?.groupValues?.get(1)?.toIntOrNull() ?: 0
                if (targetPid <= 0) return@withContext fail("Cannot establish live System Server identity.")
                val request = java.security.SecureRandom().nextLong().and(Long.MAX_VALUE).let { if (it == 0L) 1L else it }
                // Save before invoking the native injector. A restarted worker may only verify.
                if (!prefs.edit().putBoolean("pending", true).putString("mode", "direct")
                        .putInt("previousPid", targetPid).putLong("directRequest", request)
                        .putInt("expectedBuild", BuildConfig.VERSION_CODE)
                        .putLong("requestedAt", System.currentTimeMillis()).commit())
                    return@withContext fail("Cannot persist injection verification state.")
                report("injecting", "Loading TokenX into the running System Server")
                val injection = DirectSystemServerProvisioner.inject(applicationContext, targetPid, request) { report("injecting", it) }
                if (!injection.success || !injection.output.contains("TOKENX_DIRECT_INJECTION_RETURNED"))
                    return@withContext fail(injection.output.trim() + "\nLive injection failed. No soft reboot requested.")
            }
            if (prefs.getInt("expectedBuild", -1) != BuildConfig.VERSION_CODE)
                return@withContext fail("TokenX changed during injection. Start again using the installed APK.")
            report("verifying", "Waiting for the live UID 1000 System Server Binder handshake")
            val targetPid = prefs.getInt("previousPid", 0)
            val request = prefs.getLong("directRequest", 0L)
            val expectedBuild = prefs.getInt("expectedBuild", -1)
            val deadline = android.os.SystemClock.elapsedRealtime() + TimeUnit.MINUTES.toMillis(2)
            do {
                val identity = TokenXXposedSystemServerClient.verifyDirectInjection(expectedBuild, targetPid, request)
                if (identity != null && ProvisionPolicy.acceptsDirect(targetPid, identity.pid, expectedBuild,
                        BuildConfig.VERSION_CODE, request, request, identity.verifiedSystemServer,
                        TokenXXposedSystemServerClient.systemProbe()?.verified == true)) {
                    prefs.edit().putBoolean("pending", false).commit()
                    report("verified", "Live injection verified • UID 1000 • system_server PID ${identity.pid} • matching build and request • framework services passed", false)
                    return@withContext Result.success()
                }
                delay(1000)
            } while (android.os.SystemClock.elapsedRealtime() < deadline)
            val diagnostics = SystemUidProvisioner.runRoot(
                "logcat -d -v threadtime -s 'TokenX/DirectInjection:*' 'TokenX/Injection:*' | tail -n 60"
            ).output.trim()
            fail("Live injection did not verify within 2 minutes. No retry or soft reboot was requested." +
                if (diagnostics.isBlank()) "\nNo payload logs were captured; check native loading and SELinux denials."
                else "\nRecent payload diagnostics:\n$diagnostics")
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            fail("Live injection failed: ${e.message}. No retry or reboot requested.")
        }
    }
}
