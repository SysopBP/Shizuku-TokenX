package moe.shizuku.tokenx.xposed

import android.os.Process
import android.util.Log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import rikka.shizuku.server.ShizukuService

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
            // Samsung Android 17 blocks custom servicemanager registrations from
            // injected system_server code even though this hook already runs as UID 1000.
            // Do not publish a second Binder service name. Start the embedded Shizuku
            // backend directly and let its existing ContentProvider BinderSender hand the
            // Binder to the manager. This is the same transport used by Shizuku itself
            // and avoids the SELinux service_manager add denial entirely.
            ShizukuService.startEmbeddedSystemServer()
            log(
                Log.INFO,
                TAG,
                "BOOT_TOKEN CONFIRMED: embedded Shizuku backend started in system_server UID ${Process.myUid()} PID ${Process.myPid()} via provider binder handoff"
            )
        }.onFailure {
            log(Log.ERROR, TAG, "embedded system_server startup failed: ${it.javaClass.simpleName}: ${it.message}")
        }
    }

    private companion object {
        const val TAG = "TokenX/Xposed"
    }
}
