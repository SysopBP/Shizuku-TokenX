package moe.shizuku.manager

import android.os.Bundle
import android.os.IBinder
import android.os.Parcel
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

        @Volatile private var rootBackendBinder: IBinder? = null
        @Volatile private var systemBackendBinder: IBinder? = null

        private fun remoteUid(binder: IBinder): Int {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            return try {
                data.writeInterfaceToken("moe.shizuku.server.IShizukuService")
                if (!binder.transact(IBinder.FIRST_CALL_TRANSACTION + 1, data, reply, 0)) -1
                else { reply.readException(); reply.readInt() }
            } catch (_: Throwable) { -1 }
            finally { data.recycle(); reply.recycle() }
        }

        fun backendBinder(route: String): IBinder? = when (route) {
            ShizukuSettings.BACKEND_SYSTEM -> systemBackendBinder?.takeIf { it.isBinderAlive }
            else -> rootBackendBinder?.takeIf { it.isBinderAlive }
        }
    }

    override fun onCreate(): Boolean {
        disableAutomaticSuiInitialization()
        return super.onCreate()
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (extras == null) return null

        return if (method == METHOD_GET_BACKEND_ROUTE) {
            val packageName = extras.getString(EXTRA_PACKAGE) ?: return null
            Bundle().apply { putString(EXTRA_ROUTE, ShizukuSettings.getBackendRoute(packageName)) }
        } else if (method == METHOD_SEND_BINDER) {
            extras.classLoader = BinderContainer::class.java.classLoader
            val incoming = extras.getParcelable<BinderContainer>(EXTRA_BINDER)?.binder
            if (incoming != null) {
                when (val uid = remoteUid(incoming)) {
                    0 -> rootBackendBinder = incoming
                    1000 -> systemBackendBinder = incoming
                    else -> LOGGER.w("Ignoring TokenX backend binder with unexpected UID %d", uid)
                }
            }
            LOGGER.i("Receiving Shizuku binder handoff through manager provider")
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
