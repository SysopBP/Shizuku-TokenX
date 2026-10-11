package moe.shizuku.manager.adb

import android.Manifest
import android.app.AlertDialog
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Button
import android.app.AppOpsManager
import android.app.ForegroundServiceStartNotAllowedException
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.view.isGone
import androidx.core.view.isVisible
import moe.shizuku.manager.AppConstants
import moe.shizuku.manager.app.AppBarActivity
import moe.shizuku.manager.databinding.AdbPairingTutorialActivityBinding
import moe.shizuku.manager.start.localNetworkPermission
import moe.shizuku.manager.utils.SettingsHelper
import moe.shizuku.manager.utils.SettingsPage
import rikka.compatibility.DeviceCompatibility

@RequiresApi(Build.VERSION_CODES.R)
class AdbPairingTutorialActivity : AppBarActivity() {

    private lateinit var binding: AdbPairingTutorialActivityBinding

    private var notificationEnabled: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val context = this

        binding = AdbPairingTutorialActivityBinding.inflate(layoutInflater, rootView, true)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        notificationEnabled = isNotificationEnabled()

        if (notificationEnabled) {
            startPairingService()
        }

        addManualPairingButton()

        binding.apply {
            syncNotificationEnabled()

            if (DeviceCompatibility.isMiui()) {
                miui.isVisible = true
            }

            developerOptions.setOnClickListener {
                SettingsHelper.launchOrHighlightWirelessDebugging(context)
            }

            notificationOptions.setOnClickListener {
                SettingsPage.Notifications.NotificationSettings.launch(context)
            }
        }
    }

    private fun addManualPairingButton() {
        // The button is declared in the layout so it cannot disappear when
        // the scroll view's child hierarchy differs across devices/themes.
        findViewById<Button>(moe.shizuku.manager.R.id.manual_pair_button)
            .setOnClickListener { showManualPairingDialog() }
    }

    private fun showManualPairingDialog() {
        val density = resources.displayMetrics.density
        val fields = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * density).toInt()
            setPadding(pad, pad / 2, pad, 0)
        }
        val host = EditText(this).apply {
            hint = "Pairing IP (e.g. 192.168.1.239)"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val port = EditText(this).apply {
            hint = "Pairing port (not debugging port)"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val code = EditText(this).apply {
            hint = "Six-digit pairing code"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        fields.addView(host)
        fields.addView(port)
        fields.addView(code)
        val dialog = AlertDialog.Builder(this)
            .setTitle("Manual wireless pairing")
            .setMessage("Open Wireless debugging → Pair device with pairing code. Enter the IP and port shown in that pairing dialog, not the main debugging port.")
            .setView(fields)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton("Pair", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val hostname = host.text.toString().trim()
                val portNumber = port.text.toString().toIntOrNull()
                val pairingCode = code.text.toString().trim()
                if (hostname.isEmpty() || portNumber == null || portNumber !in 1..65535 ||
                    !pairingCode.matches(Regex("[0-9]{6}"))) {
                    Toast.makeText(this, "Enter a valid IP, pairing port and six-digit code", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                try {
                    startForegroundService(AdbPairingService.manualPairIntent(this, hostname, portNumber, pairingCode))
                    dialog.dismiss()
                    Toast.makeText(this, "Pairing started; check result notification", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Log.e(AppConstants.TAG, "Manual pairing start failed", e)
                    Toast.makeText(this, "Unable to start pairing: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
        dialog.show()
    }

    private fun syncNotificationEnabled() {
        binding.apply {
            step1.isVisible = notificationEnabled
            step2.isVisible = notificationEnabled
            step3.isVisible = notificationEnabled
            network.isVisible = notificationEnabled
            notification.isVisible = notificationEnabled
            notificationDisabled.isGone = notificationEnabled
        }
    }

    private fun isNotificationEnabled(): Boolean {
        val context = this

        val nm = context.getSystemService(NotificationManager::class.java)
        val channel = nm.getNotificationChannel(AdbPairingService.NOTIFICATION_CHANNEL)
        return nm.areNotificationsEnabled() &&
                (channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE)
    }

    override fun onResume() {
        super.onResume()

        val newNotificationEnabled = isNotificationEnabled()
        if (newNotificationEnabled != notificationEnabled) {
            notificationEnabled = newNotificationEnabled
            syncNotificationEnabled()

            if (newNotificationEnabled) {
                startPairingService()
            }
        }
    }

    private val localNetworkPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // Start pairing whether or not the grant succeeded; a denial simply means
            // discovery/connect will fail and the service surfaces the error.
            doStartPairingService()
        }

    private fun startPairingService() {
        // Shared with the permissions page, which can grant it before pairing is reached.
        val permission = localNetworkPermission()
        if (permission != null && checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            localNetworkPermissionLauncher.launch(permission)
        } else {
            doStartPairingService()
        }
    }

    private fun doStartPairingService() {
        val intent = AdbPairingService.startIntent(this)
        try {
            startForegroundService(intent)
        } catch (e: Throwable) {
            Log.e(AppConstants.TAG, "startForegroundService", e)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && e is ForegroundServiceStartNotAllowedException
            ) {
                val mode = getSystemService(AppOpsManager::class.java)
                    .noteOpNoThrow("android:start_foreground", android.os.Process.myUid(), packageName, null, null)
                if (mode == AppOpsManager.MODE_ERRORED) {
                    Toast.makeText(this, "OP_START_FOREGROUND is denied. What are you doing?", Toast.LENGTH_LONG).show()
                }
                startService(intent)
            }
        }
    }
}
