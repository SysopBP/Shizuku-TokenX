package moe.shizuku.manager.tokenx

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Process
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

object TokenXSystemServerBridge {
    private const val TAG = "TokenX/Bridge"
    private const val PACKAGE = "com.tokenx.bridgetest"
    private const val SERVICE = "com.tokenx.bridgetest.IdentityService"

    private val binding = AtomicBoolean(false)

    @Volatile var connected: Boolean = false
        private set
    @Volatile var binderAlive: Boolean = false
        private set
    @Volatile var binderDescriptor: String? = null
        private set

    private var applicationContext: Context? = null
    private var binder: IBinder? = null

    private val deathRecipient = IBinder.DeathRecipient {
        connected = false
        binderAlive = false
        binderDescriptor = null
        binder = null
        binding.set(false)
        Log.w(TAG, "BRIDGE_BINDER_DIED")
        applicationContext?.let { connect(it) }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            binder = service
            connected = true
            binderAlive = service.isBinderAlive
            binderDescriptor = runCatching { service.interfaceDescriptor }.getOrNull()
            binding.set(false)
            runCatching { service.linkToDeath(deathRecipient, 0) }
            Log.i(TAG, "BRIDGE_CONNECTED component=$name clientUid=${Process.myUid()} clientPid=${Process.myPid()} alive=$binderAlive descriptor=$binderDescriptor")
        }

        override fun onServiceDisconnected(name: ComponentName) {
            connected = false
            binderAlive = false
            binderDescriptor = null
            binder = null
            binding.set(false)
            Log.w(TAG, "BRIDGE_DISCONNECTED component=$name")
        }

        override fun onBindingDied(name: ComponentName) {
            onServiceDisconnected(name)
            applicationContext?.let { connect(it) }
        }

        override fun onNullBinding(name: ComponentName) {
            onServiceDisconnected(name)
            Log.e(TAG, "BRIDGE_NULL_BINDING component=$name")
        }
    }

    fun connect(context: Context): Boolean {
        applicationContext = context.applicationContext
        if (connected && binder?.isBinderAlive == true) return true
        if (!binding.compareAndSet(false, true)) return false

        val intent = Intent().setComponent(ComponentName(PACKAGE, SERVICE))
        val result = runCatching {
            applicationContext!!.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }.onFailure {
            Log.e(TAG, "BRIDGE_BIND_FAILED ${it.javaClass.simpleName}: ${it.message}", it)
        }.getOrDefault(false)

        if (!result) {
            binding.set(false)
            Log.w(TAG, "BRIDGE_BIND_REJECTED component=$PACKAGE/$SERVICE")
        } else {
            Log.i(TAG, "BRIDGE_BIND_REQUESTED component=$PACKAGE/$SERVICE")
        }
        return result
    }
}
