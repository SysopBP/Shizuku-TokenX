package moe.shizuku.manager.shell

import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.Parcel
import moe.shizuku.manager.utils.Logger.LOGGER
import moe.shizuku.manager.ShizukuManagerProvider
import moe.shizuku.manager.tokenx.TokenXBackend
import moe.shizuku.manager.tokenx.TokenXSessionRegistry
import moe.shizuku.manager.tokenx.TokenXXposedSystemServerClient
import rikka.shizuku.Shizuku

object ShellBinderRequestHandler {

    fun isSystemRishConnected(): Boolean = TokenXSessionRegistry.hasSystemSession()

    fun handleRequest(context: Context, intent: Intent): Boolean {
        if (intent.action != "rikka.shizuku.intent.action.REQUEST_BINDER") {
            return false
        }

        val binder = intent.getBundleExtra("data")?.getBinder("binder") ?: return false
        val requestedBackend = intent.getStringExtra("tokenx_backend")
        val sessionBackend = when (requestedBackend) {
            "sserver", "system" -> TokenXBackend.SYSTEM_SERVER
            "root" -> TokenXBackend.ROOT
            "shell" -> TokenXBackend.SHELL
            else -> null
        }
        val shizukuBinder = when (requestedBackend) {
            "sserver", "system" -> TokenXXposedSystemServerClient.binder().also {
                if (it == null) LOGGER.w("TokenX System Server Binder was requested but is not published")
            }
            "root" -> ShizukuManagerProvider.rootBinder().also {
                if (it == null) LOGGER.w("TokenX Root Binder was requested but is not published")
            }
            "shell" -> ShizukuManagerProvider.shellBinder().also {
                if (it == null) LOGGER.w("TokenX Shell Binder was requested but is not published")
            }
            else -> Shizuku.getBinder().also {
                if (it == null) LOGGER.w("Binder not received or Shizuku service not running")
            }
        }

        val data = Parcel.obtain()
        return try {
            val packageName = intent.getStringExtra("tokenx_package")
                ?: intent.`package`
                ?: "rish"
            val session = if (shizukuBinder != null && sessionBackend != null) {
                TokenXSessionRegistry.register(binder, packageName, sessionBackend)
            } else {
                null
            }

            data.writeStrongBinder(shizukuBinder)
            data.writeString(context.applicationInfo.sourceDir)
            data.writeLong(session?.id ?: 0L)
            data.writeString(session?.backend?.name)
            val delivered = binder.transact(1, data, null, IBinder.FLAG_ONEWAY)

            if (!delivered && session != null) {
                TokenXSessionRegistry.remove(binder)
            } else if (delivered && session != null) {
                LOGGER.i("TokenX session %d registered backend=%s package=%s",
                    session.id, session.backend.name, session.packageName)
            } else if (delivered && shizukuBinder != null && sessionBackend != null) {
                LOGGER.w("TokenX Binder delivered but client session registration failed")
            }
            delivered
        } catch (e: Throwable) {
            e.printStackTrace()
            false
        } finally {
            data.recycle()
        }
    }
}
