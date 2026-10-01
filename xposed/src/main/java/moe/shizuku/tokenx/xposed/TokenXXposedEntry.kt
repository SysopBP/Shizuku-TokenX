package moe.shizuku.tokenx.xposed

import android.os.Binder
import android.os.Process
import android.os.ServiceManager
import android.util.Log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

/** TokenX modern LSPosed UID 1000 bridge. */
class TokenXXposedEntry : XposedModule() {
    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        if (!param.isSystemServer) return
        log(Log.INFO, TAG, "BOOT_TOKEN CLAIMED: XPOSED/SYSTEM_SERVER UID ${Process.myUid()}")
    }

    override fun onSystemServerStarting(param: XposedModuleInterface.SystemServerStartingParam) {
        if (Process.myUid() != Process.SYSTEM_UID) {
            log(Log.WARN, TAG, "refusing bridge outside UID 1000 (uid=${Process.myUid()})")
            return
        }
        runCatching {
            if (ServiceManager.getService(SERVICE_NAME) == null) {
                ServiceManager.addService(SERVICE_NAME, TokenXSystemServerBridge())
                log(Log.INFO, TAG, "BOOT_TOKEN CONFIRMED: system_server bridge registered")
            }
        }.onFailure {
            log(Log.ERROR, TAG, "system_server bridge registration failed: ${it.javaClass.simpleName}: ${it.message}")
        }
    }

    private class TokenXSystemServerBridge : Binder() {
        override fun getInterfaceDescriptor(): String = DESCRIPTOR
        override fun onTransact(code: Int, data: android.os.Parcel, reply: android.os.Parcel?, flags: Int): Boolean {
            if (code == TRANSACTION_PING) {
                data.enforceInterface(DESCRIPTOR)
                reply?.writeNoException()
                reply?.writeInt(Process.myUid())
                reply?.writeInt(Process.myPid())
                return true
            }
            return super.onTransact(code, data, reply, flags)
        }
    }

    private companion object {
        const val TAG = "TokenX/Xposed"
        const val SERVICE_NAME = "tokenx_system_server"
        const val DESCRIPTOR = "moe.shizuku.tokenx.ISystemServerBridge"
        const val TRANSACTION_PING = Binder.FIRST_CALL_TRANSACTION
    }
}
