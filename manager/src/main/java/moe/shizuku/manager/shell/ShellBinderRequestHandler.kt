package moe.shizuku.manager.shell

import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.Parcel
import android.os.SystemClock
import moe.shizuku.manager.utils.Logger.LOGGER
import moe.shizuku.manager.ShizukuManagerProvider
import moe.shizuku.manager.tokenx.TokenXBackend
import moe.shizuku.manager.tokenx.TokenXSessionRegistry
import moe.shizuku.manager.tokenx.transport.TokenXRendezvous
import rikka.shizuku.Shizuku

object ShellBinderRequestHandler {

    /**
     * SYSTEM is an independent UID-1000 Shizuku worker, not the manager's current
     * compatibility Binder and not the _TKN system_server rendezvous Binder.
     * Start it on demand and wait briefly for METHOD_SEND_BINDER to classify/publish it.
     * ROOT remains untouched and continues using the proven compatibility path.
     */
    private fun ensureSystemBinder(context: Context): IBinder? {
        ShizukuManagerProvider.systemBinder()?.takeIf { it.isBinderAlive }?.let { return it }

        val start = SystemUidProvisioner.prepareAndStartShizukuUid1000(context)
        if (!start.success) {
            LOGGER.w("TokenX UID-1000 Shizuku start failed exit=%d output=%s", start.exitCode, start.output.trim())
            return null
        }

        val deadline = SystemClock.elapsedRealtime() + 3000L
        while (SystemClock.elapsedRealtime() < deadline) {
            ShizukuManagerProvider.systemBinder()?.takeIf { it.isBinderAlive }?.let {
                LOGGER.i("TokenX SYSTEM Binder published after on-demand UID-1000 start")
                return it
            }
            SystemClock.sleep(50L)
        }
        LOGGER.w("TokenX UID-1000 Shizuku started but SYSTEM Binder was not published within 3000ms")
        return null
    }

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
            ?: intent.getBundleExtra("data")?.getString("tokenx_backend")
        val sessionBackend = when (requestedBackend) {
            "sserver", "system" -> TokenXBackend.SYSTEM_SERVER
            "root" -> TokenXBackend.ROOT
            "shell" -> TokenXBackend.SHELL
            else -> null
        }

        // Explicit routes must never fall back to the app's selected compatibility route.
        val selectedBinder = when (requestedBackend) {
            "sserver", "system" -> ensureSystemBinder(context).also {
                if (it == null) LOGGER.w("TokenX System Shizuku Binder requested but could not be started/published")
            }
            "root" -> ShizukuManagerProvider.rootBinder()?.takeIf { it.isBinderAlive }.also {
                if (it == null) LOGGER.w("TokenX Root Binder requested but no verified ROOT Binder is alive")
            }
            "shell" -> ShizukuManagerProvider.shellBinder()?.takeIf { it.isBinderAlive }.also {
                if (it == null) LOGGER.w("TokenX Shell Binder requested but not published/alive")
            }
            else -> Shizuku.getBinder()?.takeIf { it.isBinderAlive }.also {
                if (it == null) LOGGER.w("Binder not received or Shizuku service not running")
            }
        }

        val backendUid = selectedBinder?.let {
            runCatching { moe.shizuku.server.IShizukuService.Stub.asInterface(it).getUid() }.getOrDefault(-1)
        } ?: -1
        val shizukuBinder = selectedBinder?.takeIf {
            sessionBackend == null || backendUid == sessionBackend.uid
        }
        if (sessionBackend != null && selectedBinder != null && shizukuBinder == null) {
            LOGGER.w("TokenX rejected wrong-UID rish Binder requested=%s actualUid=%d", requestedBackend, backendUid)
        }

        // Never deliver a null/dead explicit backend binder. This turns the failure into
        // a precise manager-side routing error instead of letting the client wait for the
        // generic Shizuku request timeout.
        if (sessionBackend != null && shizukuBinder == null) {
            LOGGER.w("TokenX explicit rish route unavailable backend=%s requested=%s; replying with null Binder instead of allowing client timeout",
                sessionBackend.name, requestedBackend ?: "default")
            // The rish client understands a null Binder as an immediate unavailable
            // backend response. A silent return leaves it waiting five seconds and
            // misleadingly suggests a manager package or battery optimization issue.
            val unavailableReply = Parcel.obtain()
            return try {
                unavailableReply.writeStrongBinder(null)
                unavailableReply.writeString(context.applicationInfo.sourceDir)
                unavailableReply.writeLong(0L)
                unavailableReply.writeString(sessionBackend.name)
                unavailableReply.writeInt(-1)
                binder.transact(1, unavailableReply, null, IBinder.FLAG_ONEWAY)
            } catch (e: Throwable) {
                LOGGER.w(e, "TokenX unavailable-backend reply failed backend=%s", sessionBackend.name)
                false
            } finally {
                unavailableReply.recycle()
            }
        }

        val data = Parcel.obtain()
        return try {
            val packageName = intent.getStringExtra("tokenx_package")
                ?: intent.`package`
                ?: "rish"

            // Do not add a second manager-side authorization gate here. The returned
            // Shizuku backend Binder performs the canonical package/UID permission check
            // itself. The extra TokenX gate caused valid Termux clients to be dropped
            // without a callback, which surfaced as a misleading five-second timeout for
            // --root/--system while the default route remained healthy.
            //
            // Explicit routing is still constrained to a live, selected backend above,
            // and the client verifies the backend UID before opening Shell.

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
            // Report the selected Binder's actual UID, not the requested route's label.
            data.writeInt(backendUid)
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
