package moe.shizuku.manager.tokenx

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

data class TokenXBridgeIdentity(
    val pid: Int,
    val uid: Int,
    val selinux: String,
    val cmdline: String,
) {
    val verifiedSystemServer: Boolean
        get() = uid == 1000 && cmdline == "system_server" && selinux.startsWith("u:r:system_server:s0")
}

data class TokenXBridgeFunctionalResult(
    val pid: Int,
    val uid: Int,
    val selinux: String,
    val checks: List<String>,
) {
    val passCount: Int get() = checks.count { it.startsWith("PASS ") }
    val denyCount: Int get() = checks.count { it.startsWith("DENY ") }
    val verified: Boolean
        get() = uid == 1000 &&
            selinux.startsWith("u:r:system_server:s0") &&
            passCount > 0 &&
            denyCount == 0
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
    private const val TRANSACTION_RUN_FUNCTIONAL_CHECKS = IBinder.FIRST_CALL_TRANSACTION + 1
    private const val TAG = "TokenX/BridgeClient"
    private const val BIND_TIMEOUT_MS = 5_000L

    @Volatile private var binder: IBinder? = null
    @Volatile private var identity: TokenXBridgeIdentity? = null
    // null = not probed on this Binder, true = v2 supported, false = v1 identity-only backend.
    @Volatile private var functionalProbeSupported: Boolean? = null
    private val binding = AtomicBoolean(false)
    private val handler = Handler(Looper.getMainLooper())

    private val bindTimeout = Runnable {
        if (binding.compareAndSet(true, false)) {
            Log.w(TAG, "BRIDGE_BIND_TIMEOUT stale_binding_cleared")
        }
    }

    private val deathRecipient = IBinder.DeathRecipient {
        binder = null
        identity = null
        functionalProbeSupported = null
        binding.set(false)
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            handler.removeCallbacks(bindTimeout)
            binding.set(false)
            binder = service
            functionalProbeSupported = null
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
            functionalProbeSupported = null
            binding.set(false)
        }

        override fun onBindingDied(name: ComponentName?) {
            binder = null
            identity = null
            binding.set(false)
        }

        override fun onNullBinding(name: ComponentName?) {
            binder = null
            identity = null
            binding.set(false)
        }
    }

    fun ensureBound(context: Context) {
        val current = binder
        if (current?.isBinderAlive == true) {
            identity = queryIdentity(current)
            return
        }
        if (!binding.compareAndSet(false, true)) return
        val intent = Intent().setComponent(ComponentName(BRIDGE_PACKAGE, BRIDGE_SERVICE))
        val ok = runCatching {
            context.applicationContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }.getOrDefault(false)
        if (!ok) {
            binding.set(false)
            handler.removeCallbacks(bindTimeout)
            Log.w(TAG, "BRIDGE_BIND_REJECTED stale_state_cleared")
        } else {
            handler.removeCallbacks(bindTimeout)
            handler.postDelayed(bindTimeout, BIND_TIMEOUT_MS)
            Log.i(TAG, "BRIDGE_BIND_REQUESTED timeoutMs=$BIND_TIMEOUT_MS")
        }
    }

    fun identity(): TokenXBridgeIdentity? {
        val current = binder ?: return null
        if (!current.isBinderAlive) {
            binder = null
            identity = null
            binding.set(false)
            return null
        }
        return queryIdentity(current)?.also { identity = it } ?: identity
    }


    /**
     * Optional v2 read-only capability probe. Older BridgeTest backends reject this
     * transaction cleanly, so identity verification remains backward compatible.
     */
    fun functionalResult(): TokenXBridgeFunctionalResult? {
        val remote = binder ?: return null
        if (!remote.isBinderAlive) return null

        // BridgeTest v1 implements identity transaction 1 only. Runtime snapshots run every
        // second, so blindly probing transaction 2 causes a continuous UNKNOWN_TRANSACTION
        // stream in system_server (observed by ActivityManager as Binder error -74).
        // Probe once per Binder connection; if rejected, remember that this is a v1 bridge.
        if (functionalProbeSupported == false) return null

        return try {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                if (!remote.transact(TRANSACTION_RUN_FUNCTIONAL_CHECKS, data, reply, 0)) {
                    functionalProbeSupported = false
                    Log.i(TAG, "BRIDGE_PROTOCOL_V1 identity_only; functional transaction unsupported")
                    return null
                }
                reply.readException()
                val result = TokenXBridgeFunctionalResult(
                    pid = reply.readInt(),
                    uid = reply.readInt(),
                    selinux = reply.readString().orEmpty(),
                    checks = reply.readString().orEmpty()
                        .lineSequence()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .toList(),
                )
                functionalProbeSupported = true
                result
            } finally {
                data.recycle()
                reply.recycle()
            }
        } catch (t: Throwable) {
            functionalProbeSupported = false
            Log.i(TAG, "BRIDGE_PROTOCOL_V1 identity_only; functional probe rejected: ${t.javaClass.simpleName}")
            null
        }
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
