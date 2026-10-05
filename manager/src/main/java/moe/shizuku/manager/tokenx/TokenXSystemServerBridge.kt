package moe.shizuku.manager.tokenx

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Parcel
import android.os.Process
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/** App-side client for the proven BridgeTest IdentityService hosted by system_server. */
object TokenXSystemServerBridge {
    private const val TAG = "TokenX/Bridge"
    private const val PACKAGE = "com.tokenx.bridgetest"
    private const val SERVICE = "com.tokenx.bridgetest.IdentityService"

    // Contract recovered from the existing BridgeTest APK. Keep this read-only.
    private const val TRANSACTION_GET_IDENTITY = 1

    data class BackendIdentity(
        val pid: Int,
        val uid: Int,
        val selinux: String?,
        val process: String?
    )

    private val binding = AtomicBoolean(false)
    @Volatile var connected = false; private set
    @Volatile var binderDescriptor: String? = null; private set
    @Volatile var backendIdentity: BackendIdentity? = null; private set
    private var appContext: Context? = null
    private var binder: IBinder? = null

    private val deathRecipient = IBinder.DeathRecipient {
        connected = false
        binderDescriptor = null
        backendIdentity = null
        binder = null
        binding.set(false)
        Log.w(TAG, "BRIDGE_BINDER_DIED")
        appContext?.let(::connect)
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            binder = service
            connected = service.isBinderAlive
            binderDescriptor = runCatching { service.interfaceDescriptor }.getOrNull()
            binding.set(false)
            runCatching { service.linkToDeath(deathRecipient, 0) }
            Log.i(TAG, "BRIDGE_CONNECTED component=$name clientUid=${Process.myUid()} clientPid=${Process.myPid()} descriptor=$binderDescriptor")

            // Functional proof: execute the existing read-only RPC through the returned Binder.
            queryIdentity(service)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            connected = false
            binderDescriptor = null
            backendIdentity = null
            binder = null
            binding.set(false)
            Log.w(TAG, "BRIDGE_DISCONNECTED component=$name")
        }

        override fun onBindingDied(name: ComponentName) {
            onServiceDisconnected(name)
            appContext?.let(::connect)
        }

        override fun onNullBinding(name: ComponentName) {
            onServiceDisconnected(name)
            Log.e(TAG, "BRIDGE_NULL_BINDING component=$name")
        }
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
                val identity = BackendIdentity(
                    pid = reply.readInt(),
                    uid = reply.readInt(),
                    selinux = reply.readString(),
                    process = reply.readString()
                )
                backendIdentity = identity

                val isSystemServer =
                    identity.uid == Process.SYSTEM_UID &&
                    identity.selinux?.startsWith("u:r:system_server:") == true

                if (isSystemServer) {
                    Log.i(
                        TAG,
                        "SYSTEM_SERVER_RPC_OK pid=${identity.pid} uid=${identity.uid} " +
                            "selinux=${identity.selinux} process=${identity.process}"
                    )
                } else {
                    Log.w(
                        TAG,
                        "SYSTEM_SERVER_RPC_IDENTITY_MISMATCH pid=${identity.pid} uid=${identity.uid} " +
                            "selinux=${identity.selinux} process=${identity.process}"
                    )
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

    fun connect(context: Context): Boolean {
        appContext = context.applicationContext
        if (connected && binder?.isBinderAlive == true) return true
        if (!binding.compareAndSet(false, true)) return false
        val intent = Intent().setComponent(ComponentName(PACKAGE, SERVICE))
        val ok = runCatching { appContext!!.bindService(intent, connection, Context.BIND_AUTO_CREATE) }
            .onFailure { Log.e(TAG, "BRIDGE_BIND_FAILED", it) }.getOrDefault(false)
        if (!ok) binding.set(false)
        Log.i(TAG, if (ok) "BRIDGE_BIND_REQUESTED" else "BRIDGE_BIND_REJECTED")
        return ok
    }
}
