package moe.shizuku.manager

import android.app.Application
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import com.topjohnwu.superuser.Shell
import moe.shizuku.manager.ktx.logd
import moe.shizuku.manager.authorization.AuthorizationManager
import moe.shizuku.manager.service.WatchdogService
import moe.shizuku.manager.tokenx.TokenXSystemServerBridge
import moe.shizuku.manager.utils.ShizukuStateMachine
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.core.util.BuildUtils.atLeast30
import rikka.material.app.LocaleDelegate
import rikka.shizuku.Shizuku

class ShizukuApplication : Application() {

    companion object {

        init {
            logd("ShizukuApplication", "init")

            Shell.setDefaultBuilder(Shell.Builder.create().setFlags(Shell.FLAG_REDIRECT_STDERR))
            if (Build.VERSION.SDK_INT >= 28) {
                HiddenApiBypass.setHiddenApiExemptions("")
            }
            if (atLeast30) {
                System.loadLibrary("adb")
            }
        }

        lateinit var application: ShizukuApplication
            private set

        lateinit var appContext: Context
            private set

    }

    private fun init(context: Context) {
        ShizukuSettings.initialize(context)
        // The starter writes its own log into this app's external directories when a device
        // exploit runs it, and they are only created on first use: create both now, so the
        // paths exist before anything tries to write to them. Without this a start that
        // failed had nowhere to leave its account of itself. The media directory matters
        // most, since that is the one another app's process is allowed to write.
        runCatching { getExternalFilesDir(null)?.mkdirs() }
        runCatching { getExternalMediaDirs()?.firstOrNull()?.mkdirs() }
        // The preference is the source of truth, so re-apply it to the boot receiver here:
        // installs from before this read the component back and can be stuck disabled with
        // start on boot switched on.
        ShizukuSettings.updateBootReceiver(context)
        LocaleDelegate.defaultLocale = ShizukuSettings.getLocale()
        AppCompatDelegate.setDefaultNightMode(ShizukuSettings.getNightMode())

        if(ShizukuSettings.getWatchdog()) WatchdogService.start(context)

        // App authorization is desired state, not server-process state. system_server,
        // Serv.apk, root or shell may be replaced while the user still expects the same
        // enabled apps. Replay the persisted list every time a server Binder arrives.
        Shizuku.addBinderReceivedListener {
            runCatching { AuthorizationManager.restoreDesiredGrantsWithRetry() }
                .onFailure { Log.w("TokenX", "Unable to restore app grants", it) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        application = this
        appContext = applicationContext
        init(this)
        // Keep run 395's Shizuku UserService path and also attach to the proven system_server IdentityService.
        TokenXSystemServerBridge.connect(this)
    }

}
