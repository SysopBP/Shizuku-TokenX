package moe.shizuku.manager.tokenx

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Process
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/** App-side client for the proven BridgeTest IdentityService hosted by system_server. */
object TokenXSystemServerBridge {
    private const val TAG = "TokenX/Bridge"
    private const val PACKAGE = "com.tokenx.bridgetest"
    private const val SERVICE = "com.tokenx.bridgetest.IdentityService"
    private val binding = AtomicBoolean(false)
    @Volatile var connected = false; private set
    @Volatile var binderDescriptor: String? = null; private set
    private var appContext: Context? = null
    private var binder: IBinder? = null

    private val deathRecipient = IBinder.DeathRecipient {
        connected = false
        binderDescriptor = null
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
        }
        override fun onServiceDisconnected(name: ComponentName) {
            connected = false; binderDescriptor = null; binder = null; binding.set(false)
            Log.w(TAG, "BRIDGE_DISCONNECTED component=$name")
        }
        override fun onBindingDied(name: ComponentName) {
            onServiceDisconnected(name); appContext?.let(::connect)
        }
        override fun onNullBinding(name: ComponentName) {
            onServiceDisconnected(name); Log.e(TAG, "BRIDGE_NULL_BINDING component=$name")
        }
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
