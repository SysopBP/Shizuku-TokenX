package moe.shizuku.manager.tokenx

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.os.Process
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/** App-side client for the proven BridgeTest IdentityService hosted by system_server. */
object TokenXSystemServerBridge {
    private const val TAG = "TokenX/Bridge"
    private const val PACKAGE = "com.tokenx.bridgetest"
    private const val SERVICE = "com.tokenx.bridgetest.IdentityService"
    private const val TRANSACTION_GET_IDENTITY = 1
    private const val RETRY_INITIAL_MS = 1_000L
    private const val RETRY_MAX_MS = 10_000L

    data class BackendIdentity(val pid: Int, val uid: Int, val selinux: String?, val process: String?)

    private val binding = AtomicBoolean(false)
    private val handler = Handler(Looper.getMainLooper())
    @Volatile var connected = false; private set
    @Volatile var binderDescriptor: String? = null; private set
    @Volatile var backendIdentity: BackendIdentity? = null; private set
    @Volatile private var autoConnect = false
    @Volatile private var retryDelayMs = RETRY_INITIAL_MS
    private var appContext: Context? = null
    private var binder: IBinder? = null

    private val retryRunnable = Runnable {
        if (!autoConnect) return@Runnable
        if (verifiedSystemServer()) {
            retryDelayMs = RETRY_INITIAL_MS
            return@Runnable
        }
        connectNow()
        if (!verifiedSystemServer()) scheduleRetry()
    }

    private val deathRecipient = IBinder.DeathRecipient {
        clearConnection("BRIDGE_BINDER_DIED")
        scheduleRetry(immediate = true)
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            binder = service
            connected = service.isBinderAlive
            binderDescriptor = runCatching { service.interfaceDescriptor }.getOrNull()
            binding.set(false)
            runCatching { service.linkToDeath(deathRecipient, 0) }
            Log.i(TAG, "BRIDGE_CONNECTED component=$name clientUid=${Process.myUid()} clientPid=${Process.myPid()} descriptor=$binderDescriptor")
            val identity = queryIdentity(service)
            if (verifiedSystemServer(identity)) {
                retryDelayMs = RETRY_INITIAL_MS
                handler.removeCallbacks(retryRunnable)
                Log.i(TAG, "SYSTEM_SERVER_ATTACHED pid=${identity!!.pid} uid=${identity.uid}")
            } else {
                scheduleRetry()
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            clearConnection("BRIDGE_DISCONNECTED component=$name")
            scheduleRetry()
        }

        override fun onBindingDied(name: ComponentName) {
            clearConnection("BRIDGE_BINDING_DIED component=$name")
            scheduleRetry(immediate = true)
        }

        override fun onNullBinding(name: ComponentName) {
            clearConnection("BRIDGE_NULL_BINDING component=$name")
            scheduleRetry()
        }
    }

    private fun clearConnection(reason: String) {
        connected = false
        binderDescriptor = null
        backendIdentity = null
        binder = null
        binding.set(false)
        Log.w(TAG, reason)
    }

    private fun verifiedSystemServer(identity: BackendIdentity? = backendIdentity): Boolean =
        identity != null &&
            identity.uid == Process.SYSTEM_UID &&
            identity.pid > 0 &&
            identity.process == "system_server" &&
            identity.selinux?.startsWith("u:r:system_server:s0") == true

    private fun scheduleRetry(immediate: Boolean = false) {
        if (!autoConnect || verifiedSystemServer()) return
        handler.removeCallbacks(retryRunnable)
        val delay = if (immediate) 0L else retryDelayMs
        Log.i(TAG, "BRIDGE_RETRY_SCHEDULED delayMs=$delay")
        handler.postDelayed(retryRunnable, delay)
        if (!immediate) retryDelayMs = (retryDelayMs * 2).coerceAtMost(RETRY_MAX_MS)
    }

    private fun queryIdentity(service: IBinder): BackendIdentity? {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            val transacted = service.transact(TRANSACTION_GET_IDENTITY, data, reply, 0)
            if (!transacted) {
                Log.e(TAG, "SYSTEM_SERVER_RPC_REJECTED transaction=$TRANSACTION_GET_IDENTITY")
                null
            } else {
                reply.readException()
                val identity = BackendIdentity(reply.readInt(), reply.readInt(), reply.readString(), reply.readString())
                backendIdentity = identity
                if (verifiedSystemServer(identity)) {
                    Log.i(TAG, "SYSTEM_SERVER_RPC_OK pid=${identity.pid} uid=${identity.uid} selinux=${identity.selinux} process=${identity.process}")
                } else {
                    Log.w(TAG, "SYSTEM_SERVER_RPC_IDENTITY_MISMATCH pid=${identity.pid} uid=${identity.uid} selinux=${identity.selinux} process=${identity.process}")
                }
                identity
            }
        } catch (t: Throwable) {
            backendIdentity = null
            Log.e(TAG, "SYSTEM_SERVER_RPC_FAILED transaction=$TRANSACTION_GET_IDENTITY", t)
            null
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    fun queryIdentity(): BackendIdentity? {
        val service = binder
        if (!connected || service?.isBinderAlive != true) {
            Log.w(TAG, "SYSTEM_SERVER_RPC_SKIPPED bridge_not_connected")
            return null
        }
        return queryIdentity(service)
    }

    /** Lifecycle-safe attachment; safe to call repeatedly from app/Shizuku callbacks. */
    fun startAutoConnect(context: Context) {
        appContext = context.applicationContext
        autoConnect = true
        retryDelayMs = RETRY_INITIAL_MS
        if (verifiedSystemServer()) return
        Log.i(TAG, "BRIDGE_AUTOCONNECT_START")
        connectNow()
        if (!verifiedSystemServer()) scheduleRetry()
    }

    /** Wake the retry loop when Shizuku/server state changes. */
    fun poke() {
        if (!autoConnect || verifiedSystemServer()) return
        scheduleRetry(immediate = true)
    }

    fun connect(context: Context): Boolean {
        appContext = context.applicationContext
        return connectNow()
    }

    private fun connectNow(): Boolean {
        val context = appContext ?: return false
        val live = binder
        if (connected && live?.isBinderAlive == true) {
            val identity = queryIdentity(live)
            if (verifiedSystemServer(identity)) return true
            clearConnection("BRIDGE_IDENTITY_NOT_SYSTEM_SERVER")
        }
        if (!binding.compareAndSet(false, true)) return false

        val intent = Intent().setComponent(ComponentName(PACKAGE, SERVICE))
        val ok = runCatching {
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }.onFailure {
            Log.e(TAG, "BRIDGE_BIND_FAILED", it)
        }.getOrDefault(false)

        if (!ok) binding.set(false)
        Log.i(TAG, if (ok) "BRIDGE_BIND_REQUESTED" else "BRIDGE_BIND_REJECTED")
        return ok
    }
}
