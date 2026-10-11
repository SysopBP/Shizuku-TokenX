package moe.shizuku.manager.receiver

import android.content.Context
import android.content.Intent
import androidx.work.WorkManager
import moe.shizuku.manager.BuildConfig
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.utils.ShizukuStateMachine
import rikka.shizuku.Shizuku

class ManualStopReceiver : AuthenticatedReceiver() {
    override fun onAuthenticated(context: Context, intent: Intent) {
        val applicationId = BuildConfig.APPLICATION_ID
        if (intent.action != "${applicationId}.STOP") return

        // Mark the stop as intentional so the watchdog's dead-check doesn't undo
        // it, and drop any queued background start.
        ShizukuSettings.setManuallyStopped(true)
        WorkManager.getInstance(context).cancelUniqueWork("adb_start_worker")

        if (!ShizukuStateMachine.isRunning()) return

        // UID 1000 is hosted by Android's system_server. Shizuku.exit() ultimately asks
        // the server process to terminate; doing that from this backend terminates
        // system_server itself and causes a framework/phone restart. TokenX never owns
        // that host process, so "Stop" means detach locally and suppress automatic restart.
        val serverUid = runCatching { Shizuku.getUid() }.getOrDefault(-1)
        if (serverUid == 1000) {
            android.util.Log.w(
                moe.shizuku.manager.AppConstants.TAG,
                "TOKENX_SYSTEM_SERVER_STOP_GUARDED: refusing Shizuku.exit() for UID 1000"
            )
            ShizukuStateMachine.update()
            return
        }

        ShizukuStateMachine.set(ShizukuStateMachine.State.STOPPING)
        runCatching { Shizuku.exit() }
    }
}