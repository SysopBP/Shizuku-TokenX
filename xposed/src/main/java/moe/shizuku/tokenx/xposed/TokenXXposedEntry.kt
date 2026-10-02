package moe.shizuku.tokenx.xposed

import android.os.Process
import android.util.Log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import rikka.rish.RishConfig
import rikka.shizuku.server.ShizukuService
import rikka.shizuku.server.util.Android17Compat
import java.io.File

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
            // Service's constructor initializes Rish. Under LSPosed, system_server uses the
            // module ClassLoader, whose native lookup does not reliably resolve librish.so
            // from the APK. Point Rish at PackageManager's extracted nativeLibraryDir before
            // ShizukuService is constructed so RishConfig uses System.load(absolutePath).
            val ai = Android17Compat.getApplicationInfo(MANAGER_PACKAGE, 0, 0)
                ?: throw IllegalStateException("TokenX manager APK is not installed")
            val nativeDir = ai.nativeLibraryDir
                ?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("TokenX nativeLibraryDir is unavailable")
            val rish = File(nativeDir, "librish.so")
            if (!rish.isFile) {
                throw UnsatisfiedLinkError("librish.so missing from extracted nativeLibraryDir: $nativeDir")
            }
            RishConfig.setLibraryPath(nativeDir)
            log(Log.INFO, TAG, "NATIVE_READY: librish.so resolved from $nativeDir")

            // Samsung Android 17 blocks custom servicemanager registrations from injected
            // system_server code. Start the embedded backend directly and retain Shizuku's
            // existing ContentProvider BinderSender handoff to the manager.
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
        const val MANAGER_PACKAGE = "moe.shizuku.privileged.api"
    }
}
