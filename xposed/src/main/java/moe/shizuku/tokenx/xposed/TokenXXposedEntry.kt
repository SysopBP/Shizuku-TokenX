package moe.shizuku.tokenx.xposed

import android.content.Context
import android.os.Process
import android.os.Parcel
import android.util.Log
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

/** TokenX modern LSPosed UID 1000 bridge and Receiver Compatibility layer. */
class TokenXXposedEntry : XposedModule() {
    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        if (!param.isSystemServer) return
        log(Log.INFO, TAG, "BOOT_TOKEN CLAIMED: XPOSED/SYSTEM_SERVER UID ${Process.myUid()}")
    }

    override fun onPackageLoaded(param: XposedModuleInterface.PackageLoadedParam) {
        if (!param.isFirstPackage) return

        if (param.packageName == SYSTEM_UI_PACKAGE) {
            installStatusBarLabs(param)
        }

        if (param.packageName !in RECEIVER_COMPAT_PACKAGES) return

        val receiverCompatPackage = param.packageName

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
                            log(Log.INFO, TAG, "TOKENX_RECEIVER_COMPAT_APPLIED: package=$receiverCompatPackage flags=$oldFlags -> ${args[flagsIndex]}")
                            chain.proceed(args)
                        } else {
                            chain.proceed()
                        }
                    }
            }
            log(Log.INFO, TAG, "TOKENX_RECEIVER_COMPAT_READY: package=$receiverCompatPackage hooked ${methods.size} receiver overload(s)")
        }.onFailure {
            log(Log.ERROR, TAG, "TOKENX_RECEIVER_COMPAT_FAIL_OPEN: ${it.javaClass.simpleName}: ${it.message}")
        }
    }

    override fun onSystemServerStarting(param: XposedModuleInterface.SystemServerStartingParam) {
        val uid = Process.myUid()
        val pid = Process.myPid()
        val selinux = readSelf("/proc/self/attr/current")
        val cmdline = readSelf("/proc/self/cmdline").replace("\u0000", "").trim()

        if (uid != Process.SYSTEM_UID || cmdline != "system_server" ||
            !selinux.startsWith("u:r:system_server:s0")
        ) {
            log(
                Log.WARN,
                TAG,
                "SYSTEM_SERVER_IDENTITY_REJECTED pid=$pid uid=$uid selinux=$selinux process=$cmdline"
            )
            return
        }

        // Phase 1 is deliberately identity-only. Previous builds attempted to start an
        // embedded ShizukuService from this callback; on Samsung A17 that competed with the
        // existing root Shizuku server/provider handoff and could leave Starter waiting for
        // a replacement Binder. Keep Shizuku/root independent and prove the LSPosed
        // system_server execution context before adding an allow-listed RPC surface.
        log(
            Log.INFO,
            TAG,
            "SYSTEM_SERVER_IDENTITY_OK backend=XPOSED_SYSTEM_SERVER pid=$pid uid=$uid selinux=$selinux process=$cmdline"
        )
        log(Log.INFO, TAG, "EMBEDDED_SHIZUKU_DISABLED root_shizuku_remains_fallback")
        installSystemServerBridge()
    }

    private fun installSystemServerBridge() {
        runCatching {
            val ams = Class.forName("com.android.server.am.ActivityManagerService")
            val onTransact = ams.getDeclaredMethod(
                "onTransact",
                Int::class.javaPrimitiveType,
                Parcel::class.java,
                Parcel::class.java,
                Int::class.javaPrimitiveType
            )
            hook(onTransact)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    val code = chain.args.getOrNull(0) as? Int
                    if (code != TOKENX_BRIDGE_TRANSACTION) return@intercept chain.proceed()

                    val data = chain.args.getOrNull(1) as? Parcel
                        ?: return@intercept chain.proceed()
                    val reply = chain.args.getOrNull(2) as? Parcel
                    data.enforceInterface(ACTIVITY_MANAGER_DESCRIPTOR)

                    when (data.readInt()) {
                        ACTION_GET_IDENTITY -> {
                            reply?.writeNoException()
                            reply?.writeInt(Process.myPid())
                            reply?.writeInt(Process.myUid())
                            reply?.writeString(readSelf("/proc/self/attr/current"))
                            reply?.writeString(readSelf("/proc/self/cmdline").replace("\u0000", "").trim())
                            log(Log.INFO, TAG, "SYSTEM_SERVER_RPC_IDENTITY callingUid=${android.os.Binder.getCallingUid()} callingPid=${android.os.Binder.getCallingPid()}")
                            true
                        }
                        else -> false
                    }
                }
            log(Log.INFO, TAG, "SYSTEM_SERVER_RPC_READY transport=activity_binder protocol=1")
        }.onFailure {
            log(Log.ERROR, TAG, "SYSTEM_SERVER_RPC_INSTALL_FAILED: ${it.javaClass.simpleName}: ${it.message}")
        }
    }

    private fun readSelf(path: String): String = runCatching {
        java.io.File(path).readText().trim()
    }.getOrDefault("unknown")

    private fun systemPropertyEnabled(key: String): Boolean = runCatching {
        val clazz = Class.forName("android.os.SystemProperties")
        val method = clazz.getDeclaredMethod("getBoolean", String::class.java, Boolean::class.javaPrimitiveType)
        method.invoke(null, key, false) as Boolean
    }.getOrDefault(false)

    private fun installStatusBarLabs(param: XposedModuleInterface.PackageLoadedParam) {
        if (!systemPropertyEnabled(PROP_ONEUIX_LABS) ||
            !systemPropertyEnabled(PROP_STATUS_BAR_LABS)
        ) {
            log(Log.INFO, TAG, "ONEUIX_LABS_STATUSBAR_OFF")
            return
        }

        // First OneUIX Labs hook: restore Samsung's Bluetooth status-bar icon by
        // bypassing SystemUI icon simplification for Bluetooth slots only.
        // Inspired by SoClear/OneUIX StatusBar.restoreBluetoothStatusBarIcon()
        // (AGPL-3.0). Kept fail-open for One UI version drift.
        runCatching {
            val controller = Class.forName(
                "com.android.systemui.statusbar.phone.ui.StatusBarIconControllerImpl",
                false,
                param.defaultClassLoader
            )
            val iconManager = Class.forName(
                "com.android.systemui.statusbar.phone.ui.IconManager",
                false,
                param.defaultClassLoader
            )
            val method = controller.getDeclaredMethod(
                "hideBySimplification",
                iconManager,
                String::class.java
            )
            hook(method)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    val slot = chain.args.getOrNull(1) as? String
                    if (slot == "bluetooth" || slot == "bluetooth_connected") false
                    else chain.proceed()
                }
            log(Log.INFO, TAG, "ONEUIX_LABS_STATUSBAR_READY: bluetooth icon simplification hook installed")
        }.onFailure {
            log(
                Log.WARN,
                TAG,
                "ONEUIX_LABS_STATUSBAR_FAIL_OPEN: ${it.javaClass.simpleName}: ${it.message}"
            )
        }
    }

    private companion object {
        const val TAG = "TokenX/Xposed"
        const val TOKENX_BRIDGE_TRANSACTION = 0x5f544b4e // "_TKN"
        const val ACTIVITY_MANAGER_DESCRIPTOR = "android.app.IActivityManager"
        const val ACTION_GET_IDENTITY = 1
        const val FOTA_PACKAGE = "com.sdet.fotaagent"
        const val RETAIL_MODE_PACKAGE = "com.samsung.sea.rm"
        const val SYSTEM_UI_PACKAGE = "com.android.systemui"
        const val PROP_ONEUIX_LABS = "persist.tokenx.labs.oneuix"
        const val PROP_STATUS_BAR_LABS = "persist.tokenx.labs.statusbar"
        val RECEIVER_COMPAT_PACKAGES = setOf(FOTA_PACKAGE, RETAIL_MODE_PACKAGE)
    }
}
