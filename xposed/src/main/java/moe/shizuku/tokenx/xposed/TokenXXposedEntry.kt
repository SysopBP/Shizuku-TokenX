package moe.shizuku.tokenx.xposed

import android.content.Context
import android.os.Process
import android.util.Log
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import rikka.rish.RishConfig
import rikka.shizuku.server.ShizukuService
import rikka.shizuku.server.util.Android17Compat
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/** TokenX modern LSPosed UID 1000 bridge. */
class TokenXXposedEntry : XposedModule() {
    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        if (!param.isSystemServer) return
        log(Log.INFO, TAG, "BOOT_TOKEN CLAIMED: XPOSED/SYSTEM_SERVER UID ${Process.myUid()}")
    }

    override fun onPackageLoaded(param: XposedModuleInterface.PackageLoadedParam) {
        if (param.packageName != FOTA_PACKAGE || !param.isFirstPackage) return

        runCatching {
            val contextImpl = Class.forName("android.app.ContextImpl", false, param.defaultClassLoader)
            val methods = contextImpl.declaredMethods.filter { method ->
                method.name == "registerReceiverInternal" &&
                    method.parameterTypes.isNotEmpty() &&
                    method.parameterTypes.last() == Int::class.javaPrimitiveType
            }

            if (methods.isEmpty()) {
                log(Log.WARN, TAG, "FOTA_RX_SHIM_SKIP: no compatible ContextImpl.registerReceiverInternal overload")
                return@runCatching
            }

            methods.forEach { method ->
                hook(method)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept { chain ->
                        val args = chain.args.toTypedArray()
                        val flagsIndex = args.lastIndex
                        val oldFlags = args[flagsIndex] as? Int ?: return@intercept chain.proceed()
                        val hasExportFlag = oldFlags and (Context.RECEIVER_EXPORTED or Context.RECEIVER_NOT_EXPORTED) != 0
                        if (!hasExportFlag) {
                            // FOTA is an OEM/system broadcast consumer. Preserve legacy exported
                            // receiver semantics only inside com.sdet.fotaagent; never rewrite
                            // receiver flags globally.
                            args[flagsIndex] = oldFlags or Context.RECEIVER_EXPORTED
                            log(Log.INFO, TAG, "FOTA_RX_SHIM_APPLIED: flags=$oldFlags -> ${args[flagsIndex]}")
                            chain.proceed(*args)
                        } else {
                            chain.proceed()
                        }
                    }
            }
            log(Log.INFO, TAG, "FOTA_RX_SHIM_READY: hooked ${methods.size} receiver overload(s)")
        }.onFailure {
            log(Log.ERROR, TAG, "FOTA_RX_SHIM_FAIL_OPEN: ${it.javaClass.simpleName}: ${it.message}")
        }
    }

    override fun onSystemServerStarting(param: XposedModuleInterface.SystemServerStartingParam) {
        if (Process.myUid() != Process.SYSTEM_UID) {
            log(Log.WARN, TAG, "refusing bridge outside UID 1000 (uid=${Process.myUid()})")
            return
        }
        runCatching {
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
            System.load(rish.absolutePath)
            RishConfig.setLibraryPath(nativeDir)
            log(Log.INFO, TAG, "NATIVE_READY: librish.so preloaded from ${rish.absolutePath}")

            // Never perform the embedded Binder/provider handoff inline on LSPosed's
            // system_server startup callback. Samsung A17 can stall this path while package/
            // provider services are still coming up, which pins system_server and the boot logo.
            if (!embeddedStartScheduled.compareAndSet(false, true)) {
                log(Log.WARN, TAG, "PUBLISH_SKIP: embedded backend start already scheduled")
                return@runCatching
            }

            Thread({
                log(Log.INFO, TAG, "PUBLISH_BEGIN: async embedded backend startup")
                runCatching {
                    ShizukuService.startEmbeddedSystemServer()
                }.onSuccess {
                    log(
                        Log.INFO,
                        TAG,
                        "PUBLISH_OK: BOOT_TOKEN CONFIRMED: embedded Shizuku backend started in system_server UID ${Process.myUid()} PID ${Process.myPid()} via provider binder handoff"
                    )
                }.onFailure {
                    // Fail open: never crash/terminate system_server because TokenX could not
                    // publish its Binder during early boot. Root/shell can recover after boot.
                    log(
                        Log.ERROR,
                        TAG,
                        "PUBLISH_FAIL_OPEN: embedded backend unavailable; leaving system_server boot path alive: ${it.javaClass.simpleName}: ${it.message}\n${Log.getStackTraceString(it)}"
                    )
                }
            }, "TokenX-BinderPublish").apply {
                isDaemon = true
                start()
            }

            log(Log.INFO, TAG, "PUBLISH_ASYNC: system_server startup callback released")
        }.onFailure {
            log(Log.ERROR, TAG, "embedded system_server preparation failed open: ${it.javaClass.simpleName}: ${it.message}\n${Log.getStackTraceString(it)}")
        }
    }

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
        const val FOTA_PACKAGE = "com.sdet.fotaagent"
        val embeddedStartScheduled = AtomicBoolean(false)
    }
}
