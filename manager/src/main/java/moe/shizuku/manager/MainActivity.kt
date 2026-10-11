package moe.shizuku.manager

import android.Manifest
import android.app.NotificationManager
import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
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
        val dialog = Dialog(this)
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density + 0.5f).toInt()
        fun background(color: Int, radius: Int, stroke: Int? = null) =
            GradientDrawable().apply {
                setColor(color)
                cornerRadius = dp(radius).toFloat()
                if (stroke != null) setStroke(dp(1), stroke)
            }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(22))
            background = background(Color.rgb(17, 17, 20), 28, Color.rgb(66, 48, 57))
        }
        fun line(value: String, size: Float, color: Int, bold: Boolean = false, top: Int = 0) {
            panel.addView(TextView(this).apply {
                text = value
                textSize = size
                setTextColor(color)
                if (bold) setTypeface(null, Typeface.BOLD)
                setPadding(0, dp(top), 0, 0)
            })
        }
        line("◆  TOKENX  /  SYSTEM INTEGRATION", 11f, Color.rgb(210, 104, 139), true)
        line("System Bridge Recovery", 22f, Color.WHITE, true, 18)
        line("Keep your UID 1000 bridge easy to restore after a reboot.", 14f,
            Color.rgb(191, 191, 199), false, 10)
        line("AFTER REBOOT", 11f, Color.rgb(210, 104, 139), true, 22)
        line("Get a reminder to review the System backend and reconnect when needed.",
            14f, Color.rgb(225, 225, 231), false, 8)
        line("Nothing is injected or rebooted automatically. You stay in control.",
            12f, Color.rgb(160, 160, 171), false, 14)
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(22), 0, 0)
        }
        fun action(label: String, primary: Boolean, onTap: () -> Unit) {
            val button = TextView(this).apply {
                text = label
                textSize = 14f
                gravity = Gravity.CENTER
                setTypeface(null, Typeface.BOLD)
                setTextColor(if (primary) Color.WHITE else Color.rgb(209, 209, 217))
                background = background(
                    if (primary) Color.rgb(132, 35, 69) else Color.rgb(34, 34, 39), 16)
                setOnClickListener { onTap(); dialog.dismiss() }
            }
            actions.addView(button, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50)).apply {
                topMargin = dp(9)
            })
        }
        action("Enable recovery reminder", true) {
            prefs.edit().putBoolean("asked", true).apply()
            sendBroadcast(Intent(this, SystemBridgeRecoveryReceiver::class.java)
                .setAction(SystemBridgeRecoveryReceiver.ACTION_ENABLE))
        }
        action("Not now", false) {
            prefs.edit().putBoolean("asked", true).apply()
        }
        panel.addView(actions)
        line("SYSTEM SERVER LAB", 11f, Color.rgb(210, 104, 139), true, 22)
        line("Provision System Server UID 1000", 16f, Color.WHITE, true, 8)
        line("Device will soft reboot after successful provisioning. Save your work before continuing.",
            12f, Color.rgb(231, 183, 190), false, 8)
        line("Preflight, backend verification, recovery timeout, and diagnostics are required before enabling provisioning.",
            12f, Color.rgb(160, 160, 171), false, 8)
        val provisionButton = TextView(this).apply {
            text = "Provision & Soft Reboot · Not ready"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(145, 145, 153))
            background = background(Color.rgb(38, 38, 43), 16)
            isEnabled = false
        }
        panel.addView(provisionButton, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(50)).apply { topMargin = dp(12) })
        dialog.setContentView(panel)
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(resources.displayMetrics.widthPixels - dp(36),
                ViewGroup.LayoutParams.WRAP_CONTENT)
            addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes = attributes.apply { dimAmount = 0.78f }
        }
        dialog.show()
        dialog.window?.setLayout(resources.displayMetrics.widthPixels - dp(36),
            ViewGroup.LayoutParams.WRAP_CONTENT)
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
