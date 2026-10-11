package moe.shizuku.manager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.service.WatchdogService
import moe.shizuku.manager.start.reapplyAdbWithoutDeveloperOptionsIfEnabled

class BootCompleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        // A reboot clears the ADB toggles, so the setting that keeps them on has to be put
        // back before anything tries to use them.
        reapplyAdbWithoutDeveloperOptionsIfEnabled(context)

        // The receiver is enabled by either start on boot or ADB without Developer options,
        // so its presence says nothing about this setting; ask the setting itself.
        if (ShizukuSettings.getStartOnBoot(context)) ShizukuReceiverStarter.start(context)
        if (ShizukuSettings.getWatchdog()) WatchdogService.start(context)
    }
}