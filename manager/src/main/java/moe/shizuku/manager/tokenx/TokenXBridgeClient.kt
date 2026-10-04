package moe.shizuku.manager.tokenx

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Parcel

data class TokenXBridgeIdentity(
    val pid: Int,
    val uid: Int,
    val selinux: String,
    val cmdline: String,
) {
    val verifiedSystemServer: Boolean
        get() = uid == 1000 && cmdline == "system_server" && selinux.startsWith("u:r:system_server:s0")
}

/**
 * Read-only client for the standalone TokenX headless bridge.
 *
 * The bridge exposes identity/health only. No shell, settings, package or reboot
 * operations cross this Binder.
 */
object TokenXBridgeClient {
    private const val BRIDGE_PACKAGE = "com.tokenx.bridgetest"
    private const val BRIDGE_SERVICE = "com.tokenx.bridgetest.IdentityService"
    private const val TRANSACTION_GET_IDENTITY = IBinder.FIRST_CALL_TRANSACTION

    @Volatile private var binder: IBinder? = null
    @Volatile private var identity: TokenXBridgeIdentity? = null
    @Volatile private var binding = false

    private val deathRecipient = IBinder.DeathRecipient {
        binder = null
        identity = null
        binding = false
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            binding = false
            binder = service
            if (service == null) {
                identity = null
                return
            }
            runCatching { service.linkToDeath(deathRecipient, 0) }
            identity = queryIdentity(service)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            binder = null
            identity = null
            binding = false
        }

        override fun onBindingDied(name: ComponentName?) {
            binder = null
            identity = null
            binding = false
        }

        override fun onNullBinding(name: ComponentName?) {
            binder = null
            identity = null
            binding = false
        }
    }

    fun ensureBound(context: Context) {
        val current = binder
        if (current?.isBinderAlive == true) {
            identity = queryIdentity(current)
            return
        }
        if (binding) return
        binding = true
        val intent = Intent().setComponent(ComponentName(BRIDGE_PACKAGE, BRIDGE_SERVICE))
        val ok = runCatching {
            context.applicationContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }.getOrDefault(false)
        if (!ok) binding = false
    }

    fun identity(): TokenXBridgeIdentity? {
        val current = binder ?: return null
        if (!current.isBinderAlive) {
            binder = null
            identity = null
            binding = false
            return null
        }
        return queryIdentity(current)?.also { identity = it } ?: identity
    }

    private fun queryIdentity(remote: IBinder): TokenXBridgeIdentity? = runCatching {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            if (!remote.transact(TRANSACTION_GET_IDENTITY, data, reply, 0)) return null
            reply.readException()
            TokenXBridgeIdentity(
                pid = reply.readInt(),
                uid = reply.readInt(),
                selinux = reply.readString().orEmpty(),
                cmdline = reply.readString().orEmpty(),
            )
        } finally {
            data.recycle()
            reply.recycle()
        }
    }.getOrNull()
}
