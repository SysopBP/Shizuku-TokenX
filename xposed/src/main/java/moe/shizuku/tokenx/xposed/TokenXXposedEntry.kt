package moe.shizuku.tokenx.xposed

import android.content.Context
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.Process
import android.util.Log
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

/** TokenX modern LSPosed system_server RPC and OEM compatibility layer. */
class TokenXXposedEntry : XposedModule() {
    init {
        // Earliest lifecycle marker: proves LSPosed instantiated the module class.
        Log.i(TAG, "ENTRY_CONSTRUCTOR pid=${Process.myPid()} uid=${Process.myUid()}")
        runCatching { log(Log.INFO, TAG, "ENTRY_CONSTRUCTOR pid=${Process.myPid()} uid=${Process.myUid()}") }
            .onFailure { Log.e(TAG, "ENTRY_CONSTRUCTOR_XPOSED_LOG_FAILED", it) }
    }

    @Volatile private var rendezvousBinder: IBinder? = null
    @Volatile private var rendezvousOwnerUid: Int = -1
    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        Log.i(TAG, "MODULE_LOADED isSystemServer=${param.isSystemServer} pid=${Process.myPid()} uid=${Process.myUid()}")
        log(Log.INFO, TAG, "MODULE_LOADED isSystemServer=${param.isSystemServer} pid=${Process.myPid()} uid=${Process.myUid()}")
        if (!param.isSystemServer) return
        log(Log.INFO, TAG, "BOOT_TOKEN CLAIMED: XPOSED/SYSTEM_SERVER UID ${Process.myUid()}")
    }

    override fun onPackageLoaded(param: XposedModuleInterface.PackageLoadedParam) {
        if (!param.isFirstPackage) return
        if (param.packageName == SYSTEM_UI_PACKAGE) installStatusBarLabs(param)
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
                hook(method).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept { chain ->
                    val args = chain.args.toTypedArray()
                    val flagsIndex = args.lastIndex
                    val oldFlags = args[flagsIndex] as? Int ?: return@intercept chain.proceed()
                    val hasExportFlag = oldFlags and (Context.RECEIVER_EXPORTED or Context.RECEIVER_NOT_EXPORTED) != 0
                    if (!hasExportFlag) {
                        args[flagsIndex] = oldFlags or Context.RECEIVER_EXPORTED
                        log(Log.INFO, TAG, "TOKENX_RECEIVER_COMPAT_APPLIED: package=$receiverCompatPackage flags=$oldFlags -> ${args[flagsIndex]}")
                        chain.proceed(args)
                    } else chain.proceed()
                }
            }
            log(Log.INFO, TAG, "TOKENX_RECEIVER_COMPAT_READY: package=$receiverCompatPackage hooked ${methods.size} receiver overload(s)")
        }.onFailure {
            log(Log.ERROR, TAG, "TOKENX_RECEIVER_COMPAT_FAIL_OPEN: ${it.javaClass.simpleName}: ${it.message}")
        }
    }

    override fun onSystemServerStarting(param: XposedModuleInterface.SystemServerStartingParam) {
        Log.i(TAG, "SYSTEM_SERVER_CALLBACK_ENTER pid=${Process.myPid()} uid=${Process.myUid()}")
        log(Log.INFO, TAG, "SYSTEM_SERVER_CALLBACK_ENTER pid=${Process.myPid()} uid=${Process.myUid()}")
        try {
            val uid = Process.myUid()
            val pid = Process.myPid()
            val selinux = readSelf("/proc/self/attr/current")
            val cmdline = readSelf("/proc/self/cmdline").replace("\u0000", "").trim()
            log(Log.INFO, TAG, "SYSTEM_SERVER_HOOK pid=$pid uid=$uid selinux=$selinux process=$cmdline")
            if (uid != Process.SYSTEM_UID || cmdline != "system_server" || !selinux.startsWith("u:r:system_server:s0")) {
                log(Log.WARN, TAG, "SYSTEM_SERVER_IDENTITY_REJECTED pid=$pid uid=$uid selinux=$selinux process=$cmdline")
                return
            }
            log(Log.INFO, TAG, "SYSTEM_SERVER_IDENTITY_OK backend=XPOSED_SYSTEM_SERVER pid=$pid uid=$uid selinux=$selinux process=$cmdline")
            log(Log.INFO, TAG, "EMBEDDED_SHIZUKU_DISABLED root_shizuku_remains_fallback")
            log(Log.INFO, TAG, "RPC_INSTALL_CALL_BEGIN")
            installSystemServerBridge()
            log(Log.INFO, TAG, "RPC_INSTALL_CALL_RETURN")
        } catch (t: Throwable) {
            Log.e(TAG, "SYSTEM_SERVER_CALLBACK_THROWABLE", t)
            runCatching {
                log(Log.ERROR, TAG, "SYSTEM_SERVER_CALLBACK_THROWABLE: ${t.javaClass.name}: ${t.message}")
                log(t)
            }
        }
    }

    private fun installSystemServerBridge() {
        log(Log.INFO, TAG, "SYSTEM_SERVER_RPC_INSTALL_BEGIN transport=binder_execTransact transaction=0x${TOKENX_BRIDGE_TRANSACTION.toString(16)}")
        runCatching {
            val execTransact = Binder::class.java.getDeclaredMethod(
                "execTransact",
                Int::class.javaPrimitiveType,
                Long::class.javaPrimitiveType,
                Long::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            val obtainNative = Parcel::class.java.getDeclaredMethod("obtain", Long::class.javaPrimitiveType).apply {
                isAccessible = true
            }

            hook(execTransact)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    val code = chain.args.getOrNull(0) as? Int ?: return@intercept chain.proceed()
                    if (code != TOKENX_BRIDGE_TRANSACTION) return@intercept chain.proceed()

                    val dataPtr = chain.args.getOrNull(1) as? Long ?: 0L
                    val replyPtr = chain.args.getOrNull(2) as? Long ?: 0L
                    val flags = chain.args.getOrNull(3) as? Int ?: 0
                    val data = if (dataPtr != 0L) obtainNative.invoke(null, dataPtr) as? Parcel else null
                    val reply = if (replyPtr != 0L) obtainNative.invoke(null, replyPtr) as? Parcel else null

                    if (data == null) {
                        log(Log.ERROR, TAG, "SYSTEM_SERVER_RPC_REJECT reason=null_data code=$code")
                        return@intercept chain.proceed()
                    }

                    var consumed = false
                    try {
                        log(Log.INFO, TAG, "SYSTEM_SERVER_RPC_INTERCEPT code=$code flags=$flags callingUid=${Binder.getCallingUid()} callingPid=${Binder.getCallingPid()}")
                        data.enforceInterface(ACTIVITY_MANAGER_DESCRIPTOR)
                        when (val action = data.readInt()) {
                            ACTION_GET_IDENTITY -> {
                                reply?.writeNoException()
                                reply?.writeInt(Process.myPid())
                                reply?.writeInt(Process.myUid())
                                reply?.writeString(readSelf("/proc/self/attr/current"))
                                reply?.writeString(readSelf("/proc/self/cmdline").replace("\u0000", "").trim())
                                log(Log.INFO, TAG, "SYSTEM_SERVER_RPC_IDENTITY callingUid=${Binder.getCallingUid()} callingPid=${Binder.getCallingPid()}")
                                consumed = true
                            }
                            ACTION_SET_BINDER -> {
                                val callingUid = Binder.getCallingUid()
                                val candidate = data.readStrongBinder()
                                if (candidate == null || !candidate.pingBinder()) {
                                    reply?.writeException(IllegalArgumentException("dead or null rendezvous binder"))
                                } else {
                                    rendezvousBinder = candidate
                                    rendezvousOwnerUid = callingUid
                                    candidate.linkToDeath({
                                        if (rendezvousBinder === candidate) {
                                            rendezvousBinder = null
                                            rendezvousOwnerUid = -1
                                            log(Log.WARN, TAG, "SYSTEM_SERVER_RPC_BINDER_DIED")
                                        }
                                    }, 0)
                                    reply?.writeNoException()
                                    log(Log.INFO, TAG, "SYSTEM_SERVER_RPC_SET_BINDER ownerUid=$callingUid class=${candidate.javaClass.name}")
                                }
                                consumed = true
                            }
                            ACTION_GET_BINDER -> {
                                val callingUid = Binder.getCallingUid()
                                val current = rendezvousBinder?.takeIf { it.isBinderAlive }
                                reply?.writeNoException()
                                // First iteration intentionally limits retrieval to the UID
                                // that published the binder. Permission routing can be
                                // widened later without exposing a root binder globally.
                                reply?.writeStrongBinder(if (callingUid == rendezvousOwnerUid) current else null)
                                log(Log.INFO, TAG, "SYSTEM_SERVER_RPC_GET_BINDER callingUid=$callingUid ownerUid=$rendezvousOwnerUid present=${current != null} allowed=${callingUid == rendezvousOwnerUid}")
                                consumed = true
                            }
                            else -> log(Log.WARN, TAG, "SYSTEM_SERVER_RPC_REJECT reason=unknown_action action=$action")
                        }
                    } catch (t: Throwable) {
                        log(Log.ERROR, TAG, "SYSTEM_SERVER_RPC_HANDLE_FAILED: ${t.javaClass.name}: ${t.message}")
                        if ((flags and IBinder.FLAG_ONEWAY) == 0 && reply != null) {
                            reply.setDataPosition(0)
                            reply.writeException(t as? Exception ?: RuntimeException(t))
                            consumed = true
                        }
                    } finally {
                        // These Parcel wrappers point at native Parcel objects owned by
                        // Binder.execTransact. Do not recycle or rewind them here: doing so
                        // can invalidate framework-owned native state before execTransact
                        // finishes returning the reply to the caller.
                    }

                    if (consumed) {
                        log(Log.INFO, TAG, "SYSTEM_SERVER_RPC_REPLY_OK replyPresent=${reply != null}")
                        true
                    } else {
                        chain.proceed()
                    }
                }
            log(Log.INFO, TAG, "SYSTEM_SERVER_RPC_READY transport=binder_execTransact protocol=4 transaction=0x${TOKENX_BRIDGE_TRANSACTION.toString(16)}")
        }.onFailure {
            log(Log.ERROR, TAG, "SYSTEM_SERVER_RPC_INSTALL_FAILED: ${it.javaClass.name}: ${it.message}")
        }
    }

    private fun readSelf(path: String): String = runCatching { java.io.File(path).readText().trim() }.getOrDefault("unknown")

    private fun systemPropertyEnabled(key: String): Boolean = runCatching {
        val clazz = Class.forName("android.os.SystemProperties")
        val method = clazz.getDeclaredMethod("getBoolean", String::class.java, Boolean::class.javaPrimitiveType)
        method.invoke(null, key, false) as Boolean
    }.getOrDefault(false)

    private fun installStatusBarLabs(param: XposedModuleInterface.PackageLoadedParam) {
        if (!systemPropertyEnabled(PROP_ONEUIX_LABS) || !systemPropertyEnabled(PROP_STATUS_BAR_LABS)) {
            log(Log.INFO, TAG, "ONEUIX_LABS_STATUSBAR_OFF")
            return
        }
        runCatching {
            val controller = Class.forName("com.android.systemui.statusbar.phone.ui.StatusBarIconControllerImpl", false, param.defaultClassLoader)
            val iconManager = Class.forName("com.android.systemui.statusbar.phone.ui.IconManager", false, param.defaultClassLoader)
            val method = controller.getDeclaredMethod("hideBySimplification", iconManager, String::class.java)
            hook(method).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept { chain ->
                val slot = chain.args.getOrNull(1) as? String
                if (slot == "bluetooth" || slot == "bluetooth_connected") false else chain.proceed()
            }
            log(Log.INFO, TAG, "ONEUIX_LABS_STATUSBAR_READY: bluetooth icon simplification hook installed")
        }.onFailure {
            log(Log.WARN, TAG, "ONEUIX_LABS_STATUSBAR_FAIL_OPEN: ${it.javaClass.simpleName}: ${it.message}")
        }
    }

    private companion object {
        const val TAG = "TokenX/Xposed"
        const val TOKENX_BRIDGE_TRANSACTION =
            ('_'.code shl 24) or ('T'.code shl 16) or ('K'.code shl 8) or 'N'.code
        const val ACTIVITY_MANAGER_DESCRIPTOR = "android.app.IActivityManager"
        const val ACTION_GET_IDENTITY = 1
        const val ACTION_SET_BINDER = 2
        const val ACTION_GET_BINDER = 3
        const val RETAIL_MODE_PACKAGE = "com.samsung.sea.rm"
        const val SYSTEM_UI_PACKAGE = "com.android.systemui"
        const val PROP_ONEUIX_LABS = "persist.tokenx.labs.oneuix"
        const val PROP_STATUS_BAR_LABS = "persist.tokenx.labs.statusbar"
        val RECEIVER_COMPAT_PACKAGES = setOf(RETAIL_MODE_PACKAGE)
    }
}
