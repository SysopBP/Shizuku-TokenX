package moe.shizuku.manager

import android.os.Bundle
import android.util.Log
import moe.shizuku.manager.tokenx.TokenXSystemServerBridge
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
    }

    override fun onCreate(): Boolean {
        disableAutomaticSuiInitialization()
        val created = super.onCreate()
        // The provider is initialized before Activity creation and is also the actual
        // Shizuku binder handoff entry point. Use it as a second process-start anchor.
        Log.i("TokenX/Bridge", "MANAGER_PROVIDER_AUTOCONNECT_START pid=${android.os.Process.myPid()}")
        context?.let { TokenXSystemServerBridge.startAutoConnect(it.applicationContext) }
        return created
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (extras == null) return null

        return if (method == METHOD_SEND_BINDER) {
            // Keep ShizukuProvider as the single owner of the binder handshake.
            // The UID-1000 backend publishes through this provider method; delegating
            // here avoids a second/competing manager-side binder implementation.
            LOGGER.i("Receiving Shizuku binder handoff through manager provider")
            super.call(method, arg, extras).also {
                ShizukuStateMachine.update()
                Log.i("TokenX/Bridge", "MANAGER_PROVIDER_BINDER_RECEIVED")
                TokenXSystemServerBridge.poke()
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
