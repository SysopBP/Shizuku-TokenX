package moe.shizuku.manager.receiver

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import moe.shizuku.manager.MainActivity

/**
 * Opt-in boot reminder. Never invokes su, injects into system_server, or reboots.
 * "Review" opens the manager where the user can check available backends.
 */
class SystemBridgeRecoveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("tokenx_bridge_recovery", Context.MODE_PRIVATE)
        when (intent.action) {
            ACTION_ENABLE -> {
                prefs.edit().putBoolean("enabled", true).apply()
                notifyReview(context)
            }
            ACTION_DISABLE -> {
                prefs.edit().putBoolean("enabled", false).apply()
                manager(context).cancel(ID)
            }
            Intent.ACTION_BOOT_COMPLETED -> {
                if (prefs.getBoolean("enabled", false)) notifyReview(context)
            }
        }
    }

    private fun notifyReview(context: Context) {
        val nm = manager(context)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "TokenX System Bridge Recovery", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val open = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val review = PendingIntent.getActivity(context, 801, open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val later = Intent(context, SystemBridgeRecoveryReceiver::class.java).setAction(ACTION_DISABLE)
        val dismiss = PendingIntent.getBroadcast(context, 802, later,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("TokenX · Review System Bridge")
            .setContentText("Check UID 1000 after reboot. Provisioning requires root and confirmation.")
            .setContentIntent(review)
            .addAction(Notification.Action.Builder(null, "Review bridge", review).build())
            .addAction(Notification.Action.Builder(null, "Don't ask again", dismiss).build())
            .setAutoCancel(true)
            .build()
        try { nm.notify(ID, n) } catch (_: SecurityException) {
            // Android 13+ notification permission may be denied.
        }
    }

    private fun manager(context: Context) =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val ACTION_ENABLE = "moe.shizuku.manager.tokenx.ENABLE_BRIDGE_REMINDER"
        const val ACTION_DISABLE = "moe.shizuku.manager.tokenx.DISABLE_BRIDGE_REMINDER"
        private const val CHANNEL = "tokenx_bridge_recovery"
        private const val ID = 19017
    }
}
