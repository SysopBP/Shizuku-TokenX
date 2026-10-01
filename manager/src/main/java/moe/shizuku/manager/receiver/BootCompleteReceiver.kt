package moe.shizuku.manager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.topjohnwu.superuser.Shell
import moe.shizuku.manager.AppConstants
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.service.WatchdogService
import moe.shizuku.manager.start.reapplyAdbWithoutDeveloperOptionsIfEnabled
import moe.shizuku.manager.utils.ShizukuStateMachine

/**
 * Boot arbitration for TokenX.
 *
 * Priority:
 *   1. LSPosed/system_server may already have published the binder as UID 1000.
 *   2. If it has not, use root (UID 0) when root is available.
 *   3. Otherwise use the existing ADB transport, whose server runs as shell UID 2000.
 *
 * Every stage checks the binder before starting another backend, so a late system_server
 * launch and BOOT_COMPLETED cannot deliberately create two Shizuku servers.
 */
class BootCompleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        reapplyAdbWithoutDeveloperOptionsIfEnabled(context)

        if (ShizukuSettings.getStartOnBoot(context)) {
            val pending = goAsync()
            Thread({
                try {
                    startWithBootFallback(context.applicationContext)
                } finally {
                    pending.finish()
                }
            }, "TokenX-BootArbiter").start()
        }

        if (ShizukuSettings.getWatchdog()) WatchdogService.start(context)
    }

    private fun startWithBootFallback(context: Context) {
        // Give the LSPosed system_server entry point a short head start. It is the preferred
        // UID 1000 backend and normally appears before BOOT_COMPLETED.
        if (waitForBinder(2500L)) {
            Log.i(AppConstants.TAG, "BOOT_TOKEN CONFIRMED: system_server/UID 1000 already active")
            ShizukuSettings.setRunningStartMethod(ShizukuSettings.StartMethod.SYSTEM)
            return
        }

        val rooted = runCatching { Shell.getShell().isRoot }.getOrDefault(false)
        if (rooted) {
            Log.i(AppConstants.TAG, "BOOT_TOKEN CLAIMED: ROOT/UID 0")
            ShizukuReceiverStarter.start(
                context,
                startMethod = ShizukuSettings.StartMethod.ROOT
            )
            if (waitForBinder(5000L)) {
                Log.i(AppConstants.TAG, "BOOT_TOKEN CONFIRMED: ROOT/UID 0")
                return
            }
            Log.w(AppConstants.TAG, "BOOT_TOKEN root timeout; falling back to shell/UID 2000")
        } else {
            Log.i(AppConstants.TAG, "BOOT_TOKEN root unavailable; falling back to shell/UID 2000")
        }

        // The manager cannot adopt uid 2000 itself. Its existing ADB worker launches the
        // Shizuku server through adbd, which is the shell-UID backend.
        if (ShizukuStateMachine.update() != ShizukuStateMachine.State.RUNNING) {
            Log.i(AppConstants.TAG, "BOOT_TOKEN CLAIMED: ADB/SHELL UID 2000")
            ShizukuReceiverStarter.start(
                context,
                startMethod = ShizukuSettings.StartMethod.WIRELESS
            )
        }
    }

    private fun waitForBinder(timeoutMs: Long): Boolean {
        val deadline = android.os.SystemClock.elapsedRealtime() + timeoutMs
        do {
            if (ShizukuStateMachine.update() == ShizukuStateMachine.State.RUNNING) return true
            try {
                Thread.sleep(200L)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return false
            }
        } while (android.os.SystemClock.elapsedRealtime() < deadline)
        return ShizukuStateMachine.update() == ShizukuStateMachine.State.RUNNING
    }
}
