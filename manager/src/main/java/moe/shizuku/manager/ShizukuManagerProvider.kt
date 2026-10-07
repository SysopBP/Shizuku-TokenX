package moe.shizuku.manager

import android.os.Bundle
import android.os.IBinder
import android.os.Process
import android.util.Log
import androidx.core.os.bundleOf
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import moe.shizuku.api.BinderContainer
import moe.shizuku.manager.utils.Logger.LOGGER
import moe.shizuku.manager.authorization.AuthorizationManager
import moe.shizuku.manager.tokenx.transport.TokenXManagerBinderAuthority
import moe.shizuku.manager.tokenx.transport.TokenXRendezvous
import moe.shizuku.manager.tokenx.transport.TokenXRendezvousContract
import moe.shizuku.manager.tokenx.transport.TokenXTransportBinder
import moe.shizuku.manager.tokenx.transport.TokenXBinderClient
import moe.shizuku.manager.tokenx.transport.TokenXBinderProtocol
import moe.shizuku.manager.tokenx.TokenXXposedSystemServerClient
import moe.shizuku.manager.utils.ShizukuStateMachine
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuApiConstants.USER_SERVICE_ARG_TOKEN
import rikka.shizuku.ShizukuProvider
import rikka.shizuku.server.ktx.workerHandler

class ShizukuManagerProvider : ShizukuProvider() {

    companion object {
        private const val EXTRA_BINDER = "moe.shizuku.privileged.api.intent.extra.BINDER"
        private const val METHOD_SEND_BINDER = "sendBinder"
        private const val METHOD_SEND_USER_SERVICE = "sendUserService"
        private const val METHOD_GET_BACKEND_ROUTE = "getBackendRoute"
        private const val EXTRA_PACKAGE = "packageName"
        private const val EXTRA_ROUTE = "route"

        @Volatile private var tokenXTransportBinder: IBinder? = null

        @Synchronized
        fun ensureTokenXTransport(context: android.content.Context): IBinder {
            tokenXTransportBinder?.takeIf { it.isBinderAlive }?.let { return it }

            val appContext = context.applicationContext
            val authority = TokenXManagerBinderAuthority(appContext) { packageName, uid ->
                runCatching { AuthorizationManager.granted(packageName, uid) }
                    .getOrDefault(false)
            }
            val transport = TokenXTransportBinder(authority)
            tokenXTransportBinder = transport
            val snapshot = TokenXRendezvous.publish(transport)
            LOGGER.i("TokenX transport ensured protocol=%d generation=%d alive=%s pid=%d uid=%d",
                TokenXRendezvousContract.PROTOCOL_VERSION, snapshot.generation, snapshot.alive, Process.myPid(), Process.myUid())
            Log.i("TokenX/Transport", "ensured protocol=" + TokenXRendezvousContract.PROTOCOL_VERSION +
                " generation=" + snapshot.generation + " alive=" + snapshot.alive +
                " pid=" + Process.myPid() + " uid=" + Process.myUid())
            return transport
        }

        @Volatile private var rootBackendBinder: IBinder? = null
        @Volatile private var systemBackendBinder: IBinder? = null
        @Volatile private var shellBackendBinder: IBinder? = null

        fun rootBinder(): IBinder? {
            rootBackendBinder?.takeIf { it.isBinderAlive }?.let { return it }
            // The compatibility Binder can arrive before probeServerUid() can classify it.
            // If Shizuku itself has verified that the live global Binder is UID 0, adopt it
            // into TokenX's independent ROOT slot so explicit rish routing does not time out.
            val global = Shizuku.getBinder()?.takeIf { it.isBinderAlive && Shizuku.pingBinder() }
            val uid = runCatching { if (global != null) Shizuku.getUid() else -1 }.getOrDefault(-1)
            if (global != null && uid == 0) {
                rootBackendBinder = global
                Log.i("TokenX/Transport", "adopted verified ROOT Binder from compatibility route")
                return global
            }
            return null
        }
        fun systemBinder(): IBinder? {
            systemBackendBinder?.takeIf { it.isBinderAlive }?.let { return it }
            val xposed = TokenXXposedSystemServerClient.binder()?.takeIf { it.isBinderAlive && it.pingBinder() }
            if (xposed != null) {
                systemBackendBinder = xposed
                Log.i("TokenX/Transport", "adopted live SYSTEM Binder from Xposed rendezvous")
            }
            return xposed
        }
        fun shellBinder(): IBinder? {
            shellBackendBinder?.takeIf { it.isBinderAlive }?.let { return it }
            val global = Shizuku.getBinder()?.takeIf { it.isBinderAlive && Shizuku.pingBinder() }
            val uid = runCatching { if (global != null) Shizuku.getUid() else -1 }.getOrDefault(-1)
            if (global != null && uid == Process.SHELL_UID) {
                shellBackendBinder = global
                Log.i("TokenX/Transport", "adopted verified SHELL Binder from compatibility route")
                return global
            }
            return null
        }
    }

    override fun onCreate(): Boolean {
        disableAutomaticSuiInitialization()
        val created = super.onCreate()
        if (created) {
            val appContext = context?.applicationContext
            if (appContext != null) {
                ensureTokenXTransport(appContext)
            }
        }
        return created
    }

    private fun probeServerUid(binder: IBinder): Int = runCatching {
        // Shizuku's public client API already performs the server UID query.
        // Only use it when this is the currently installed compatibility Binder;
        // otherwise leave the incoming backend unclassified rather than relying
        // on a private transaction constant that is not part of this build.
        if (Shizuku.getBinder() === binder || Shizuku.getBinder() == binder) {
            Shizuku.getUid()
        } else {
            -1
        }
    }.getOrDefault(-1)

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (extras == null) return null

        return if (method == "tokenx.transportProbe") {
            val snapshot = TokenXRendezvous.snapshot()
            val transport = tokenXTransportBinder?.takeIf { it.isBinderAlive }
                ?: snapshot.binder?.takeIf { it.isBinderAlive }
            val hello = transport?.let { runCatching { TokenXBinderClient(it).hello() }.getOrNull() }
            Log.i("TokenX/Transport", "probe generation=" + snapshot.generation +
                " alive=" + (transport?.isBinderAlive == true) + " hello=" + hello +
                " root=" + (rootBinder() != null) + " system=" + (systemBinder() != null) +
                " shell=" + (shellBinder() != null))
            Bundle().apply {
                putInt("tokenx.hello", hello ?: -1)
                putInt(TokenXRendezvousContract.EXTRA_PROTOCOL_VERSION, TokenXRendezvousContract.PROTOCOL_VERSION)
                putLong(TokenXRendezvousContract.EXTRA_GENERATION, snapshot.generation)
                putBoolean(TokenXRendezvousContract.EXTRA_BINDER_ALIVE, transport?.isBinderAlive == true)
                putBoolean("tokenx.rootAvailable", rootBinder() != null)
                putBoolean("tokenx.systemAvailable", systemBinder() != null)
                putBoolean("tokenx.shellAvailable", shellBinder() != null)
            }
        } else if (method == TokenXRendezvousContract.METHOD_GET_TRANSPORT_STATUS) {
            val snapshot = TokenXRendezvous.snapshot()
            val transport = tokenXTransportBinder?.takeIf { it.isBinderAlive }
                ?: snapshot.binder?.takeIf { it.isBinderAlive }
            Log.i("TokenX/Transport", "status generation=" + snapshot.generation +
                " published=" + (transport != null) + " alive=" + (transport?.isBinderAlive == true))
            Bundle().apply {
                putInt(TokenXRendezvousContract.EXTRA_PROTOCOL_VERSION, TokenXRendezvousContract.PROTOCOL_VERSION)
                putLong(TokenXRendezvousContract.EXTRA_GENERATION, snapshot.generation)
                putBoolean(TokenXRendezvousContract.EXTRA_PUBLISHED, transport != null)
                putBoolean(TokenXRendezvousContract.EXTRA_BINDER_ALIVE, transport?.isBinderAlive == true)
                putInt(TokenXRendezvousContract.EXTRA_PROVIDER_PID, Process.myPid())
                putInt(TokenXRendezvousContract.EXTRA_PROVIDER_UID, Process.myUid())
            }
        } else if (method == TokenXRendezvousContract.METHOD_GET_TRANSPORT) {
            val snapshot = TokenXRendezvous.snapshot()
            val transport = tokenXTransportBinder?.takeIf { it.isBinderAlive }
                ?: snapshot.binder?.takeIf { it.isBinderAlive }
                ?: return Bundle().apply {
                    putInt(TokenXRendezvousContract.EXTRA_PROTOCOL_VERSION, TokenXRendezvousContract.PROTOCOL_VERSION)
                    putLong(TokenXRendezvousContract.EXTRA_GENERATION, snapshot.generation)
                }
            Log.i("TokenX/Transport", "discovery generation=" + snapshot.generation +
                " alive=" + transport.isBinderAlive + " callerUid=" + android.os.Binder.getCallingUid() +
                " callerPid=" + android.os.Binder.getCallingPid())
            Bundle().apply {
                putInt(TokenXRendezvousContract.EXTRA_PROTOCOL_VERSION, TokenXRendezvousContract.PROTOCOL_VERSION)
                putLong(TokenXRendezvousContract.EXTRA_GENERATION, snapshot.generation)
                putBoolean(TokenXRendezvousContract.EXTRA_PUBLISHED, true)
                putBoolean(TokenXRendezvousContract.EXTRA_BINDER_ALIVE, transport.isBinderAlive)
                putInt(TokenXRendezvousContract.EXTRA_PROVIDER_PID, Process.myPid())
                putInt(TokenXRendezvousContract.EXTRA_PROVIDER_UID, Process.myUid())
                putBinder(TokenXRendezvousContract.EXTRA_BINDER, transport)
            }
        } else if (method == METHOD_GET_BACKEND_ROUTE) {
            val packageName = extras.getString(EXTRA_PACKAGE) ?: return null
            Bundle().apply { putString(EXTRA_ROUTE, ShizukuSettings.getBackendRoute(packageName)) }
        } else if (method == METHOD_SEND_BINDER) {
            // TokenX can keep ROOT and SYSTEM alive concurrently. Classify the
            // incoming Shizuku Binder before deciding whether it should replace
            // Shizuku's global compatibility Binder.
            extras.classLoader = BinderContainer::class.java.classLoader
            val incoming = extras.getParcelable<BinderContainer>(EXTRA_BINDER)?.binder
            val incomingUid = incoming?.let { probeServerUid(it) } ?: -1

            when (incomingUid) {
                0 -> {
                    rootBackendBinder = incoming
                    LOGGER.i("TokenX stored ROOT Binder independently (uid=0)")
                }
                Process.SHELL_UID -> {
                    shellBackendBinder = incoming
                    LOGGER.i("TokenX stored SHELL Binder independently (uid=2000)")
                }
                Process.SYSTEM_UID -> {
                    systemBackendBinder = incoming
                    LOGGER.i("TokenX stored SYSTEM Binder independently (uid=1000)")
                    // Do not let the asynchronous UID-1000 provider handoff replace
                    // an already-live root Shizuku Binder. SYSTEM remains available
                    // through TokenX's dedicated reference/router.
                    if (rootBinder() != null && Shizuku.pingBinder()) {
                        return Bundle()
                    }
                }
            }

            super.call(method, arg, extras).also {
                ShizukuStateMachine.update()
            }
        } else if (method == METHOD_SEND_USER_SERVICE) {
            try {
                extras.classLoader = BinderContainer::class.java.classLoader

                val token = extras.getString(USER_SERVICE_ARG_TOKEN) ?: return null
                val binder = extras.getParcelable<BinderContainer>(EXTRA_BINDER)?.binder ?: return null

                return runBlocking {
                    try {
                        withTimeout(5000) {
                            ShizukuStateMachine.asFlow().first { it == ShizukuStateMachine.State.RUNNING }
                            withContext(workerHandler.asCoroutineDispatcher()) {
                                try {
                                    val reply = Bundle()
                                    Shizuku.attachUserService(binder, bundleOf(USER_SERVICE_ARG_TOKEN to token))
                                    reply!!.putParcelable(EXTRA_BINDER, BinderContainer(Shizuku.getBinder()))
                                    reply
                                } catch (e: Throwable) {
                                    LOGGER.e(e, "attachUserService $token")
                                    null
                                }
                            }
                        }
                    } catch (e: TimeoutCancellationException) {
                        LOGGER.e(e, "Binder not received in 5s")
                        null
                    }
                }
            } catch (e: Throwable) {
                LOGGER.e(e, "sendUserService")
                null
            }
        } else {
            super.call(method, arg, extras)
        }
    }
}
