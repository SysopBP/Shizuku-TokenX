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
            val nativeDir = Android17Compat.getApplicationInfo(MANAGER_PACKAGE, 0, 0)
                ?.nativeLibraryDir
                ?.takeIf { it.isNotBlank() }
                ?: findExtractedNativeLibraryDir()
                ?: throw IllegalStateException(
                    "TokenX nativeLibraryDir is unavailable during early system_server startup"
                )
            val rish = File(nativeDir, "librish.so")
            if (!rish.isFile) {
                throw UnsatisfiedLinkError("librish.so missing from extracted nativeLibraryDir: $nativeDir")
            }
            // Preload by absolute path while we are still in TokenX's module context. This avoids
            // LspModuleClassLoader falling back to System.loadLibrary("rish"), which cannot
            // reliably discover APK JNI libraries from injected system_server on Samsung A17.
            System.load(rish.absolutePath)
            RishConfig.setLibraryPath(nativeDir)
            log(Log.INFO, TAG, "NATIVE_READY: librish.so preloaded from ${rish.absolutePath}")

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
            log(Log.ERROR, TAG, "embedded system_server startup failed: ${it.javaClass.simpleName}: ${it.message}\n${Log.getStackTraceString(it)}")
        }
    }

    /**
     * PackageManager can legitimately be unavailable this early in system_server startup.
     * The manager APK uses legacy JNI packaging, so Android extracts librish.so below its
     * /data/app install directory. Resolve that directory without requiring PackageManager
     * to be ready, then let later Shizuku startup use the normal package/provider path.
     */
    private fun findExtractedNativeLibraryDir(): String? {
        val dataApp = File("/data/app")
        return runCatching {
            dataApp.walkTopDown()
                .maxDepth(6)
                .firstOrNull { file ->
                    file.isFile &&
                        file.name == "librish.so" &&
                        file.absolutePath.contains(MANAGER_PACKAGE)
                }
                ?.parentFile
                ?.absolutePath
        }.onFailure {
            log(Log.WARN, TAG, "early native library scan failed: ${it.javaClass.simpleName}: ${it.message}")
        }.getOrNull()
    }

    private companion object {
        const val TAG = "TokenX/Xposed"
        const val MANAGER_PACKAGE = "moe.shizuku.privileged.api"
    }
}
