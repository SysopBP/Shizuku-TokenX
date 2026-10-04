package moe.shizuku.tokenx.xposed

import android.content.Context
import android.os.Process
import android.util.Log
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import rikka.shizuku.server.ShizukuService
import rikka.shizuku.server.util.Android17Compat
import java.util.concurrent.atomic.AtomicBoolean

/** TokenX modern LSPosed UID 1000 bridge and Receiver Compatibility layer. */
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
                log(Log.WARN, TAG, "TOKENX_RECEIVER_COMPAT_SKIP: no compatible ContextImpl.registerReceiverInternal overload")
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
                            // Receiver Compatibility: preserve legacy OEM receiver semantics only when
                            // the app omitted both modern export flags. Existing explicit flags
                            // are never rewritten, and the hook remains scoped to FOTA.
                            args[flagsIndex] = oldFlags or Context.RECEIVER_EXPORTED
                            log(Log.INFO, TAG, "TOKENX_RECEIVER_COMPAT_APPLIED: flags=$oldFlags -> ${args[flagsIndex]}")
                            chain.proceed(args)
                        } else {
                            chain.proceed()
                        }
                    }
            }
            log(Log.INFO, TAG, "TOKENX_RECEIVER_COMPAT_READY: hooked ${methods.size} receiver overload(s)")
        }.onFailure {
            log(Log.ERROR, TAG, "TOKENX_RECEIVER_COMPAT_FAIL_OPEN: ${it.javaClass.simpleName}: ${it.message}")
        }
    }

    override fun onSystemServerStarting(param: XposedModuleInterface.SystemServerStartingParam) {
        log(Log.INFO, TAG, "BINDER_HOOK_ENTERED: onSystemServerStarting uid=${Process.myUid()} pid=${Process.myPid()}")
        if (Process.myUid() != Process.SYSTEM_UID) {
            log(Log.WARN, TAG, "refusing bridge outside UID 1000 (uid=${Process.myUid()})")
            return
        }
        runCatching {
            // The embedded system_server backend is pure Binder/Java and must not depend on
            // librish.so. Rish remains configured by the standalone root/shell server path.
            log(Log.INFO, TAG, "EMBEDDED_NATIVE_BYPASS: system_server backend does not require librish.so")

            // Never perform the embedded Binder/provider handoff inline on LSPosed's
            // system_server startup callback. Samsung A17 can stall this path while package/
            // provider services are still coming up, which pins system_server and the boot logo.
            if (!embeddedStartScheduled.compareAndSet(false, true)) {
                log(Log.WARN, TAG, "PUBLISH_SKIP: embedded backend start already scheduled")
                return@runCatching
            }

            Thread({
                log(Log.INFO, TAG, "PUBLISH_BEGIN: async embedded backend startup")
                log(Log.INFO, TAG, "BINDER_CREATE_BEGIN: dispatching embedded ShizukuService startup")
                runCatching {
                    ShizukuService.startEmbeddedSystemServer()
                }.onSuccess {
                    log(Log.INFO, TAG, "BINDER_CREATE_RETURNED: embedded startup completed without exception")
                    log(
                        Log.INFO,
                        TAG,
                        "PUBLISH_OK: BOOT_TOKEN CONFIRMED: embedded Shizuku backend started in system_server UID ${Process.myUid()} PID ${Process.myPid()} via provider binder handoff"
                    )
                }.onFailure {
                    log(Log.ERROR, TAG, "BINDER_CREATE_FAILED: ${it.javaClass.simpleName}: ${it.message}")
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

    private companion object {
        const val TAG = "TokenX/Xposed"
        const val MANAGER_PACKAGE = "com.vikram.exp"
        const val FOTA_PACKAGE = "com.sdet.fotaagent"
        val embeddedStartScheduled = AtomicBoolean(false)
    }
}
