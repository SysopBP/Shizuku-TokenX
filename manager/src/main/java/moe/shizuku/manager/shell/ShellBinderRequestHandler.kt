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
import moe.shizuku.manager.tokenx.transport.TokenXRendezvous
import moe.shizuku.manager.authorization.AuthorizationManager
import rikka.shizuku.Shizuku

object ShellBinderRequestHandler {

    fun isSystemRishConnected(): Boolean = TokenXSessionRegistry.hasSystemSession()

    fun handleRequest(context: Context, intent: Intent): Boolean {
        val tokenXTransportRequest = intent.action == "moe.shizuku.tokenx.intent.action.REQUEST_TRANSPORT"
        if (!tokenXTransportRequest && intent.action != "rikka.shizuku.intent.action.REQUEST_BINDER") {
            return false
        }

        val binder = intent.getBundleExtra("data")?.getBinder("binder") ?: return false
        if (tokenXTransportRequest) {
            val snapshot = TokenXRendezvous.snapshot()
            val transport = snapshot.binder?.takeIf { it.isBinderAlive }
            val data = Parcel.obtain()
            return try {
                data.writeStrongBinder(transport)
                data.writeInt(snapshot.generation.toInt())
                binder.transact(2, data, null, IBinder.FLAG_ONEWAY)
            } catch (e: Throwable) {
                LOGGER.w(e, "TokenX transport Binder delivery failed")
                false
            } finally {
                data.recycle()
            }
        }

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

            // Explicit TokenX rish routes must pass the same authorization gate as
            // the transport session API.  The broadcast itself does not preserve
            // Binder caller identity, so resolve the package UID here and bind the
            // resulting session to the client-owned receiver Binder lifetime.
            val packageUid = runCatching {
                context.packageManager.getApplicationInfo(packageName, 0).uid
            }.getOrDefault(-1)
            val authorized = sessionBackend == null || (
                packageUid >= 0 &&
                    runCatching { AuthorizationManager.granted(packageName, packageUid) }
                        .getOrDefault(false)
            )
            if (sessionBackend != null && !authorized) {
                LOGGER.w("TokenX rish session denied backend=%s package=%s uid=%d",
                    sessionBackend.name, packageName, packageUid)
                return false
            }

            val session = if (shizukuBinder != null && sessionBackend != null) {
                TokenXSessionRegistry.register(binder, packageName, sessionBackend)
            } else {
                null
            }
            if (sessionBackend != null && session == null) {
                LOGGER.w("TokenX rish Binder withheld because session registration failed backend=%s package=%s",
                    sessionBackend.name, packageName)
                return false
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
