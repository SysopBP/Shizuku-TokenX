package moe.shizuku.manager.shell

import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.Parcel
import moe.shizuku.manager.utils.Logger.LOGGER
import moe.shizuku.manager.tokenx.TokenXXposedSystemServerClient
import rikka.shizuku.Shizuku

object ShellBinderRequestHandler {

    fun handleRequest(context: Context, intent: Intent): Boolean {
        if (intent.action != "rikka.shizuku.intent.action.REQUEST_BINDER") {
            return false
        }

        val binder = intent.getBundleExtra("data")?.getBinder("binder") ?: return false
        val requestedBackend = intent.getStringExtra("tokenx_backend")
        val shizukuBinder = if (requestedBackend == "sserver") {
            TokenXXposedSystemServerClient.binder().also {
                if (it == null) LOGGER.w("TokenX System Server Binder was requested but is not published")
            }
        } else {
            Shizuku.getBinder().also {
                if (it == null) LOGGER.w("Binder not received or Shizuku service not running")
            }
        }

        val data = Parcel.obtain()
        return try {
            data.writeStrongBinder(shizukuBinder)
            data.writeString(context.applicationInfo.sourceDir)
            binder.transact(1, data, null, IBinder.FLAG_ONEWAY)
            true
        } catch (e: Throwable) {
            e.printStackTrace()
            false
        } finally {
            data.recycle()
        }
    }
}
