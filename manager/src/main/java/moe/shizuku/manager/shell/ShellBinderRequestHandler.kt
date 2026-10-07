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
            val transport = runCatching {
                ShizukuManagerProvider.ensureTokenXTransport(context)
            }.onFailure {
                LOGGER.w(it, "TokenX transport on-demand publication failed")
            }.getOrNull()?.takeIf { it.isBinderAlive }
            val snapshot = TokenXRendezvous.snapshot()
            LOGGER.i("TokenX transport request reply generation=%d published=%s alive=%s",
                snapshot.generation, transport != null, transport?.isBinderAlive == true)
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

        // Explicit Root must preserve the same proven binder path used by default rish.
        // Retained Root is preferred, but a missing retained slot must not make an
        // otherwise healthy root Shizuku server unreachable.
        val shizukuBinder = when (requestedBackend) {
            "sserver", "system" -> ShizukuManagerProvider.systemBinder()?.takeIf { it.isBinderAlive }.also {
                if (it == null) LOGGER.w("TokenX System Shizuku Binder requested but not published/alive")
            }
            "root" -> (
                ShizukuManagerProvider.rootBinder()?.takeIf { it.isBinderAlive }
                    ?: Shizuku.getBinder()?.takeIf { it.isBinderAlive }
                ).also {
                    if (it == null) LOGGER.w("TokenX Root Binder requested but neither retained nor default binder is alive")
                }
            "shell" -> ShizukuManagerProvider.shellBinder()?.takeIf { it.isBinderAlive }.also {
                if (it == null) LOGGER.w("TokenX Shell Binder requested but not published/alive")
            }
            else -> Shizuku.getBinder()?.takeIf { it.isBinderAlive }.also {
                if (it == null) LOGGER.w("Binder not received or Shizuku service not running")
            }
        }

        // Never deliver a null/dead explicit backend binder. This turns the failure into
        // a precise manager-side routing error instead of letting the client wait for the
        // generic Shizuku request timeout.
        if (sessionBackend != null && shizukuBinder == null) {
            LOGGER.w("TokenX explicit rish route unavailable backend=%s requested=%s",
                sessionBackend.name, requestedBackend ?: "default")
            return false
        }

        val data = Parcel.obtain()
        return try {
            val packageName = intent.getStringExtra("tokenx_package")
                ?: intent.`package`
                ?: "rish"

            val localPackageUid = runCatching {
                context.packageManager.getApplicationInfo(packageName, 0).uid
            }.getOrDefault(-1)
            val packageUid = if (localPackageUid >= 0) {
                localPackageUid
            } else {
                TokenXXposedSystemServerClient.packageUid(packageName)
            }
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
            LOGGER.w(e, "TokenX rish Binder delivery failed backend=%s", requestedBackend ?: "default")
            false
        } finally {
            data.recycle()
        }
    }
}
