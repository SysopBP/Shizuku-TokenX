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
            "shell" -> ShizukuSettings.BACKEND_SHELL
            else -> callingPackage?.let { ShizukuSettings.getBackendRoute(it) } ?: ShizukuSettings.BACKEND_ROOT
        }

        // Keep the legacy/default root path as the known-good transport. The retained
        // backend registry is preferred for explicit routes, but an explicit Root request
        // must be able to use the same live binder that an unqualified rish session uses.
        val retainedBinder = ShizukuManagerProvider.backendBinder(route)
        val shizukuBinder = when (route) {
            ShizukuSettings.BACKEND_ROOT -> retainedBinder ?: Shizuku.getBinder()
            else -> retainedBinder
        }

        if (shizukuBinder == null || !shizukuBinder.isBinderAlive) {
            LOGGER.w(
                "TokenX rish route unavailable requested=%s route=%s package=%s",
                requested ?: "default",
                route,
                callingPackage ?: "unknown"
            )
            // Do not send a null/dead binder and let rish sit until its generic request
            // timeout. Returning false keeps the failure local and makes the real backend
            // availability problem visible in manager logs.
            return false
        }

        LOGGER.i(
            "TokenX rish binder route=%s requested=%s package=%s",
            route,
            requested ?: "default",
            callingPackage ?: "unknown"
        )

        val data = Parcel.obtain()
        return try {
            data.writeStrongBinder(shizukuBinder)
            data.writeString(context.applicationInfo.sourceDir)
            binder.transact(1, data, null, IBinder.FLAG_ONEWAY)
            true
        } catch (e: Throwable) {
            LOGGER.e(e, "TokenX rish binder delivery failed route=%s", route)
            false
        } finally {
            data.recycle()
        }
    }
}
