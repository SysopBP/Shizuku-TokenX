package moe.shizuku.manager.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import moe.shizuku.manager.R
import moe.shizuku.manager.MainActivity
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.adb.AdbStarter
import moe.shizuku.manager.receiver.ShizukuReceiverStarter
import moe.shizuku.manager.start.runningMethodLabel
import moe.shizuku.manager.start.runningMethodSuffix
import moe.shizuku.manager.starter.Starter
import moe.shizuku.manager.utils.EnvironmentUtils
import moe.shizuku.manager.utils.SettingsPage
import moe.shizuku.manager.utils.ShizukuStateMachine
import moe.shizuku.manager.tokenx.transport.TokenXWatchdog
import java.util.concurrent.atomic.AtomicBoolean

class WatchdogService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var pendingRestart = false
    private var tokenXHealthLoopStarted = false

    /** Set while the user owes a notification that an outage is over. */
    @Volatile
    private var awaitingRecovery = false

    /** Only one restart at a time, and not one every few seconds for the same outage. */
    private val restartInFlight = AtomicBoolean(false)

    @Volatile
    private var lastRestartAt = 0L

    private val stateListener: (ShizukuStateMachine.State) -> Unit = { state ->
        // Deliberately not cleared when the server is seen running again: the binder that
        // reports that is the sticky one, and during a replacement it arrives while the old
        // server is still up, an instant before the death the mark was set for. Clearing
        // there wiped the mark and the death came through as a crash after all. The mark
        // carries its own deadline, so a stale one drops itself.
        when (state) {
            ShizukuStateMachine.State.CRASHED ->
                // Three ways for this to be somebody's decision rather than a crash: the
                // server is being replaced, or it was stopped on purpose, or a start that
                // asked for it is already on its way. Reporting any of those as a crash and
                // starting again over the top of it is what made a deliberate restart look
                // like Shizuku falling over, twice.
                when {
                    WatchdogGuard.consumeExpectedDeath() ->
                        Log.d(TAG, "Server went down as expected, nothing to report")

                    ShizukuSettings.getManuallyStopped() ->
                        Log.d(TAG, "Server was stopped on purpose, nothing to report")

                    else -> {
                        Log.w(TAG, "Server died: reporting it and starting it again")
                        showCrashNotification()
                        awaitingRecovery = true
                        attemptRestart()
                    }
                }

            ShizukuStateMachine.State.RUNNING -> {
                // Server is back, so there is nothing left for the screen-on retry to do,
                // and any outage the user was told about is over.
                pendingRestart = false
                if (awaitingRecovery) {
                    awaitingRecovery = false
                    showRecoveryNotification()
                }
            }

            else -> Unit
        }
    }

    /**
     * Screen-on receiver: when the user turns the screen on after a crash, trigger
     * a fresh restart. Crash-time restart attempts frequently fail (mDNS / wireless
     * debugging don't work with the screen off) and WorkManager then accumulates
     * exponential backoff, making the restart indefinitely slow.
     */
    private val screenOnReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != Intent.ACTION_USER_PRESENT) return
            if (pendingRestart) {
                Log.d(TAG, "Screen unlocked with pending restart retrying now")
                attemptRestart()
            } else {
                // Self-heal on unlock: catches deaths whose CRASHED transition was
                // never observed (e.g. the manager process was dead at the time).
                checkServerAndRestartIfDead()
            }
        }
    }

    /**
     * Event-based crash detection alone is not enough: the CRASHED transition is
     * lost if the manager process was dead when the server died, and the state
     * machine boots as STOPPED after every process restart. So whenever the
     * watchdog (re)starts and on screen unlock probe whether the server is
     * actually running and restart it if not, unless the user stopped it on
     * purpose (manual stop sets the suppression flag; any start request or a
     * confirmed RUNNING state clears it).
     */
    private fun checkServerAndRestartIfDead() {
        serviceScope.launch {
            // Grace period so a sticky binder delivered right after process start
            // can flip the state to RUNNING before we probe it.
            delay(BINDER_GRACE_MS)
            if (ShizukuSettings.getManuallyStopped()) return@launch
            // A replacement is in flight: the old server being gone right now is the point
            // of it, and starting another one would race it.
            if (WatchdogGuard.isExpectingDeath()) return@launch
            when (ShizukuStateMachine.get()) {
                // A start/stop appears to be in flight. Give it ample time to
                // resolve instead of skipping outright a state stuck at
                // STARTING/STOPPING from a silently failed operation would
                // otherwise disable this check forever.
                ShizukuStateMachine.State.STARTING,
                ShizukuStateMachine.State.STOPPING -> {
                    delay(IN_FLIGHT_GRACE_MS)
                    if (ShizukuSettings.getManuallyStopped()) return@launch
                }
                else -> Unit
            }
            if (ShizukuStateMachine.update() != ShizukuStateMachine.State.RUNNING) {
                Log.d(TAG, "Server not running while watchdog active attempting restart")
                attemptRestart()
            }
        }
    }

    private fun attemptRestart() {
        if (WatchdogGuard.isExpectingDeath() || ShizukuSettings.getManuallyStopped()) {
            Log.d(TAG, "Restart attempt skipped: one is expected, or the stop was deliberate")
            return
        }

        // The binder listener and the unlock probe can land here for the same outage, and a
        // restart that keeps failing must not turn into a start every few seconds.
        val now = SystemClock.elapsedRealtime()
        if (now - lastRestartAt < RESTART_COOLDOWN_MS) {
            Log.d(TAG, "Restart attempt skipped: one was made moments ago")
            return
        }
        if (!restartInFlight.compareAndSet(false, true)) {
            Log.d(TAG, "Restart attempt skipped: one is already in flight")
            return
        }
        lastRestartAt = now

        // Cancel any prior WorkManager attempt so we don't inherit exponential backoff
        WorkManager.getInstance(applicationContext).cancelUniqueWork("adb_start_worker")

        serviceScope.launch {
            try {
                val tcpPort = EnvironmentUtils.getAdbTcpPort()
                val usbMethod =
                    ShizukuSettings.getStartMethod() == ShizukuSettings.StartMethod.USB
                if (usbMethod && tcpPort > 0 && EnvironmentUtils.isUsbDebuggingEnabled()) {
                    // Direct TCP restart for the USB method fastest path, no mDNS
                    // needed. A wireless setup must restart over TLS below: taking the
                    // classic port here is what made a wireless setup come back
                    // reporting itself as USB debugging after a crash.
                    pendingRestart = false
                    AdbStarter.startAdb(applicationContext, tcpPort)
                    Starter.waitForBinder()
                } else {
                    // mDNS-based restart via WorkManager. Mark pending so the
                    // screen-on receiver can retry if this attempt fails.
                    pendingRestart = true
                    ShizukuReceiverStarter.start(applicationContext, forceStart = true)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Direct restart failed, falling back", e)
                pendingRestart = true
                ShizukuReceiverStarter.start(applicationContext, forceStart = true)
            } finally {
                restartInFlight.set(false)
            }

            // A start reports success as soon as the binder appears, and the interesting case
            // is the one that appears and then goes away again, so the restart is checked
            // rather than trusted: until the server is actually there, the screen-on retry
            // stays armed.
            delay(RECOVERY_CHECK_MS)
            ShizukuStateMachine.update()
            if (ShizukuStateMachine.isRunning()) {
                pendingRestart = false
            } else if (!ShizukuSettings.getManuallyStopped()) {
                Log.w(TAG, "Restart did not bring the server back, leaving the retry armed")
                pendingRestart = true
            }
        }
    }

    private fun startTokenXHealthLoop() {
        if (tokenXHealthLoopStarted) return
        tokenXHealthLoopStarted = true
        serviceScope.launch {
            while (true) {
                val health = TokenXWatchdog.probe()
                Log.d(TAG, "TokenX health transport=${health.transport} generation=${health.generation} root=${health.root.state} system=${health.system.state} shell=${health.shell.state}")
                delay(TOKENX_HEARTBEAT_MS)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning.set(true)
        sendWatchdogChangedBroadcast(applicationContext, true)
        // The state it starts in is worth a line: a watchdog that came up after the server
        // was already gone is the case it cannot see a transition for, and that is what the
        // unlock probe is for.
        Log.i(TAG, "Watchdog started, server is ${ShizukuStateMachine.get()}")
        ShizukuStateMachine.addListener(stateListener)
        registerReceiver(screenOnReceiver, IntentFilter(Intent.ACTION_USER_PRESENT))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "ACTION_STOP_SERVICE") {
            // User explicitly turned the watchdog off via the notification persist
            // the setting directly instead of calling setWatchdog() (which would
            // redundantly call stop() while we're already stopping via stopSelf).
            ShizukuSettings.getPreferences().edit()
                .putBoolean(ShizukuSettings.Keys.KEY_WATCHDOG, false).apply()
            stopSelf()
            return START_NOT_STICKY
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID_WATCHDOG,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(
                NOTIFICATION_ID_WATCHDOG,
                buildNotification()
            )
        }
        checkServerAndRestartIfDead()
        startTokenXHealthLoop()
        return START_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        pendingRestart = false
        ShizukuStateMachine.removeListener(stateListener)
        runCatching { unregisterReceiver(screenOnReceiver) }
        isRunning.set(false)
        sendWatchdogChangedBroadcast(applicationContext, false)
        // Do NOT persist watchdog=false here: onDestroy runs both when the user
        // manually stops Shizuku (temporary) and when the notification stop button
        // is used (permanent). Only the notification stop button and the settings
        // toggle should persist the preference.
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        // The v2 suffix is the point, not an oversight: a channel's settings are frozen
        // once it exists, so turning the badge off on the old id would do nothing for
        // anyone who already has it. The service is always running, so a dot over it says
        // nothing except that something is permanently there.
        val channelId = "shizuku_watchdog_v2"
        val channelName = "Watchdog"

        val channel = NotificationChannel(
            channelId,
            channelName,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setShowBadge(false)
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or 
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }
        val launchPendingIntent = PendingIntent.getActivity(
            this, 0, launchIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, WatchdogService::class.java).apply {
            action = "ACTION_STOP_SERVICE"
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Say how Shizuku is running, like every other status notification does.
        val runningMethod = runningMethodLabel()

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.watchdog_running))
            .apply { if (runningMethod != null) setContentText(runningMethod) }
            .setSmallIcon(R.drawable.ic_system_icon)
            .setContentIntent(launchPendingIntent)
            .addAction(
                R.drawable.ic_close_24,
                getString(R.string.watchdog_turn_off),
                stopPendingIntent
            )
            .setOngoing(true)
            .build()
    }

    private fun showCrashNotification() {
        val channelId = CRASH_CHANNEL_ID
        val channelName = "Crash Reports"

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            channelId,
            channelName,
            NotificationManager.IMPORTANCE_DEFAULT
        )
        nm.createNotificationChannel(channel)

        val learnMoreIntent = Intent(Intent.ACTION_VIEW).apply {
            setData(Uri.parse("https://github.com/thedjchi/Shizuku/wiki#shizuku-keeps-stopping-randomly"))
        }
        val learnMorePendingIntent = PendingIntent.getActivity(this, 0, learnMoreIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val disableIntent = SettingsPage.Notifications.NotificationChannel.buildIntent(applicationContext)
        val disablePendingIntent = PendingIntent.getActivity(this, 0, disableIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.watchdog_shizuku_crashed_title))
            .setContentText(
                getString(R.string.watchdog_shizuku_crashed_text) + runningMethodSuffix()
            )
            .setSmallIcon(R.drawable.ic_system_icon)
            .setContentIntent(learnMorePendingIntent)
            .setAutoCancel(true)
            .addAction(0, getString(R.string.watchdog_shizuku_crashed_action_turn_off_alerts), disablePendingIntent)
            .build()

        nm.notify(NOTIFICATION_ID_CRASH, notification)
    }

    /**
     * The other half of the crash notification, and only ever the other half: it is posted
     * when a restart answered an outage the user was already told about, so nothing about a
     * service that came and went on its own reaches anyone.
     */
    private fun showRecoveryNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                CRASH_CHANNEL_ID,
                "Crash Reports",
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )

        val notification = NotificationCompat.Builder(this, CRASH_CHANNEL_ID)
            .setContentTitle(getString(R.string.watchdog_shizuku_recovered_title))
            .setContentText(
                getString(R.string.watchdog_shizuku_recovered_text) + runningMethodSuffix()
            )
            .setSmallIcon(R.drawable.ic_system_icon)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .setAutoCancel(true)
            .build()

        nm.notify(NOTIFICATION_ID_RECOVERY, notification)
        // The alarm it answers is spent either way.
        nm.cancel(NOTIFICATION_ID_CRASH)
    }

    companion object {
        private const val TAG = "ShizukuWatchdog"
        private const val BINDER_GRACE_MS = 3000L
        private const val TOKENX_HEARTBEAT_MS = 5_000L
        private const val IN_FLIGHT_GRACE_MS = 90_000L

        /** Long enough for a wireless start to have got somewhere, short enough to be news. */
        private const val RECOVERY_CHECK_MS = 15_000L

        /** Two restarts within this are the same outage, not two. */
        private const val RESTART_COOLDOWN_MS = 15_000L
        private const val NOTIFICATION_ID_WATCHDOG = 1001
        private const val NOTIFICATION_ID_CRASH = 1002
        private const val NOTIFICATION_ID_RECOVERY = 1003
        const val CRASH_CHANNEL_ID = "crash_reports"
        const val ACTION_WATCHDOG_CHANGED = "WATCHDOG_CHANGED"
        const val EXTRA_WATCHDOG_STATUS = "status"

        private val isRunning = AtomicBoolean(false)

        // Broadcast so automation apps (e.g. MacroDroid/Tasker) can react to
        // the watchdog being enabled or disabled.
        @JvmStatic
        fun sendWatchdogChangedBroadcast(context: Context, enabled: Boolean) {
            val intent = Intent("${context.packageName}.$ACTION_WATCHDOG_CHANGED").apply {
                putExtra(EXTRA_WATCHDOG_STATUS, if (enabled) 1 else 0)
                addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            }
            context.sendBroadcast(intent)
        }

        @JvmStatic
        fun start(context: Context) {
            try {
                context.startForegroundService(Intent(context, WatchdogService::class.java))
            } catch (e: Exception) {
                Log.e("ShizukuApplication", "Failed to start WatchdogService: ${e.message}" )
            }
        }

        @JvmStatic
        fun stop(context: Context) {
            context.stopService(Intent(context, WatchdogService::class.java))
        }

        @JvmStatic
        fun isRunning(): Boolean = isRunning.get()
    }
}
