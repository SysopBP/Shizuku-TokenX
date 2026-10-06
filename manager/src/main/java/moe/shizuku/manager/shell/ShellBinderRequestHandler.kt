package moe.shizuku.manager.shell

import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.Parcel
import moe.shizuku.manager.utils.Logger.LOGGER
import moe.shizuku.manager.ShizukuManagerProvider
import moe.shizuku.manager.ShizukuSettings
import rikka.shizuku.Shizuku

object ShellBinderRequestHandler {

    fun handleRequest(context: Context, intent: Intent): Boolean {
        if (intent.action != "rikka.shizuku.intent.action.REQUEST_BINDER") {
            return false
        }

        val binder = intent.getBundleExtra("data")?.getBinder("binder") ?: return false
        val requested = intent.getStringExtra("tokenx_backend")
        val callingPackage = intent.getStringExtra("tokenx_calling_package")
        val route = when (requested) {
            "system", "sserver" -> ShizukuSettings.BACKEND_SYSTEM
            "root" -> ShizukuSettings.BACKEND_ROOT
            else -> callingPackage?.let { ShizukuSettings.getBackendRoute(it) } ?: ShizukuSettings.BACKEND_ROOT
        }

        // TokenX keeps both privileged endpoints. A rish session can override the saved
        // per-app route without mutating that preference.
        val shizukuBinder = ShizukuManagerProvider.backendBinder(route)
            ?: if (route == ShizukuSettings.BACKEND_ROOT) Shizuku.getBinder() else null
        if (shizukuBinder == null) {
            LOGGER.w("Requested TokenX %s backend is not available", route)
        } else {
            LOGGER.i("TokenX rish binder route=%s package=%s", route, callingPackage ?: "unknown")
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
