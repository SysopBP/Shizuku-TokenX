package moe.shizuku.manager.tokenx

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Parcel
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import moe.shizuku.manager.ShizukuApplication
import rikka.shizuku.Shizuku

data class TokenXShizukuUserServiceIdentity(
    val pid: Int,
    val uid: Int,
    val selinux: String,
)

/** Owns exactly one app -> Shizuku TokenX UserService generation. */
object TokenXShizukuUserServiceClient {
    private const val TAG = "TokenXUserService"
    private const val SERVICE_TAG = "tokenx-user-service"
    private const val SERVICE_VERSION = 2

    @Volatile private var binder: IBinder? = null
    @Volatile private var lastIdentity: TokenXShizukuUserServiceIdentity? = null
    private val binding = AtomicBoolean(false)
    private val generationPrepared = AtomicBoolean(false)

    private val args: Shizuku.UserServiceArgs
        get() = Shizuku.UserServiceArgs(
            ComponentName(
                ShizukuApplication.appContext.packageName,
                TokenXShizukuUserService::class.java.name,
            )
        )
            .processNameSuffix("tokenx_service")
            .tag(SERVICE_TAG)
            .version(SERVICE_VERSION)
            .daemon(false)
            .debuggable(true)

    private val deathRecipient = IBinder.DeathRecipient {
        Log.w(TAG, "UserService binder died")
        binder = null
        lastIdentity = null
        binding.set(false)
        generationPrepared.set(false)
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            binding.set(false)
            binder = service
            if (service == null) {
                lastIdentity = null
                generationPrepared.set(false)
                Log.e(TAG, "Shizuku UserService returned null binder")
                return
            }
            runCatching { service.linkToDeath(deathRecipient, 0) }
            lastIdentity = queryIdentity(service)
            Log.i(TAG, "CONNECTED identity=$lastIdentity")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.w(TAG, "DISCONNECTED")
            binder = null
            lastIdentity = null
            binding.set(false)
            generationPrepared.set(false)
        }
    }

    fun ensureBound() {
        if (!runCatching { Shizuku.pingBinder() }.getOrDefault(false)) return

        val current = binder
        if (current?.isBinderAlive == true) {
            lastIdentity = queryIdentity(current) ?: lastIdentity
            return
        }
        if (!binding.compareAndSet(false, true)) return

        runCatching {
            // One cleanup per local Shizuku generation. This removes stale remote
            // :tokenx_service instances left by a previous manager process before
            // creating the single service owned by this process.
            if (generationPrepared.compareAndSet(false, true)) {
                runCatching { Shizuku.unbindUserService(args, null, true) }
                    .onSuccess { Log.i(TAG, "stale UserService generation removed") }
                    .onFailure { Log.w(TAG, "stale UserService cleanup failed; continuing", it) }
            }
            Shizuku.bindUserService(args, connection)
            Log.i(TAG, "bindUserService requested")
        }.onFailure {
            binding.set(false)
            generationPrepared.set(false)
            Log.e(TAG, "bindUserService failed", it)
        }
    }

    fun onShizukuBinderDead() {
        val current = binder
        if (current != null) runCatching { current.unlinkToDeath(deathRecipient, 0) }
        binder = null
        lastIdentity = null
        binding.set(false)
        generationPrepared.set(false)
    }

    fun isConnected(): Boolean = binder?.isBinderAlive == true

    fun identity(): TokenXShizukuUserServiceIdentity? {
        val current = binder ?: return null
        if (!current.isBinderAlive) {
            onShizukuBinderDead()
            return null
        }
        return queryIdentity(current)?.also { lastIdentity = it } ?: lastIdentity
    }

    private fun queryIdentity(remote: IBinder): TokenXShizukuUserServiceIdentity? = runCatching {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            if (!remote.transact(TokenXShizukuUserService.TRANSACTION_GET_IDENTITY, data, reply, 0)) return null
            reply.readException()
            TokenXShizukuUserServiceIdentity(
                pid = reply.readInt(),
                uid = reply.readInt(),
                selinux = reply.readString().orEmpty(),
            )
        } finally {
            data.recycle()
            reply.recycle()
        }
    }.getOrNull()
}
