package moe.shizuku.manager

import android.Manifest
import android.app.NotificationManager
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import moe.shizuku.manager.adb.AdbPairingService
import moe.shizuku.manager.home.showAccessibilityDialog
import moe.shizuku.manager.receiver.ShizukuReceiverStarter
import moe.shizuku.manager.receiver.SystemBridgeRecoveryReceiver
import moe.shizuku.manager.ui.ShizukuApp

class MainActivity : ComponentActivity() {

    /**
     * Starting Shizuku is announced in a notification, which also carries the buttons to
     * retry or cancel the attempt but on Android 13+ the app has to ask for the
     * notification permission at runtime, or none of it is ever shown.
     */
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) return

        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        requestNotificationPermission()
        offerBridgeRecoveryReminder()
        setContent {
            ShizukuApp()
        }
    }

    private fun offerBridgeRecoveryReminder() {
        val prefs = getSharedPreferences("tokenx_bridge_recovery", Context.MODE_PRIVATE)
        if (prefs.getBoolean("asked", false)) return
        AlertDialog.Builder(this)
            .setTitle("TokenX System Bridge recovery")
            .setMessage("Show a reminder after reboot to review the UID 1000 bridge? This never injects or soft-reboots automatically.")
            .setPositiveButton("Enable reminder") { _, _ ->
                prefs.edit().putBoolean("asked", true).apply()
                sendBroadcast(Intent(this, SystemBridgeRecoveryReceiver::class.java)
                    .setAction(SystemBridgeRecoveryReceiver.ACTION_ENABLE))
            }
            .setNegativeButton("Not now") { _, _ ->
                prefs.edit().putBoolean("asked", true).apply()
            }
            .show()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return

        if (intent.getBooleanExtra(AppConstants.EXTRA_START_SERVICE_VIA_WADB, false)) {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .cancel(AdbPairingService.NOTIFICATION_ID)
            ShizukuReceiverStarter.start(this, forceStart = true, userInitiated = true)
        }

        if (intent.getBooleanExtra(AppConstants.EXTRA_SHOW_PAIRING_DIALOG, false)) {
            showAccessibilityDialog()
        }
    }
}
