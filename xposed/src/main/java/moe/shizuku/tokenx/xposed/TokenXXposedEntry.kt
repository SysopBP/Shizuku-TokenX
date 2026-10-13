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
import rikka.shizuku.server.ShizukuService
import java.util.concurrent.atomic.AtomicBoolean

/** TokenX modern LSPosed system_server RPC and OEM compatibility layer. */
// Root Shizuku and the UID-1000 TokenX bridge intentionally keep independent lifecycles.
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
            log(Log.INFO, TAG, "RPC_INSTALL_CALL_BEGIN")
            installSystemServerBridge()
            log(Log.INFO, TAG, "RPC_INSTALL_CALL_RETURN")

            // Restore the earlier UID-1000 provider handoff while keeping the root
            // backend and protocol-4 RPC transport independent and available.
            if (embeddedStartScheduled.compareAndSet(false, true)) {
                Thread({
                    log(Log.INFO, TAG, "EMBEDDED_SHIZUKU_START_ASYNC_BEGIN")
                    runCatching { ShizukuService.startEmbeddedSystemServer() }
                        .onSuccess {
                            log(Log.INFO, TAG, "EMBEDDED_SHIZUKU_START_ASYNC_OK pid=${Process.myPid()} uid=${Process.myUid()}")
                        }
                        .onFailure {
                            log(Log.ERROR, TAG, "EMBEDDED_SHIZUKU_START_ASYNC_FAIL_OPEN: ${it.javaClass.name}: ${it.message}")
                        }
                }, "TokenX-BinderPublish").apply {
                    isDaemon = true
                    start()
                }
            } else {
                log(Log.WARN, TAG, "EMBEDDED_SHIZUKU_START_SKIP already_scheduled")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "SYSTEM_SERVER_CALLBACK_THROWABLE", t)
            runCatching {
                log(Log.ERROR, TAG, "SYSTEM_SERVER_CALLBACK_THROWABLE: ${t.javaClass.name}: ${t.message}\n${Log.getStackTraceString(t)}")
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
                            ACTION_SYSTEM_PROBE -> {
                                // Functional UID-1000 proof. Deliberately read-only: no shell,
                                // fork, setuid or arbitrary command execution in system_server.
                                val services = listOf("activity", "package", "power", "window")
                                val available = services.filter { android.os.ServiceManager.getService(it) != null }
                                reply?.writeNoException()
                                reply?.writeInt(Process.myUid())
                                reply?.writeInt(Process.myPid())
                                reply?.writeString(readSelf("/proc/self/attr/current"))
                                reply?.writeString(available.joinToString(","))
                                log(Log.INFO, TAG, "SYSTEM_SERVER_RPC_FUNCTIONAL uid=${Process.myUid()} pid=${Process.myPid()} services=${available.joinToString(",")}")
                                consumed = true
                            }
                            ACTION_RESOLVE_PACKAGE_UID -> {
                                val packageName = data.readString().orEmpty()
                                val callingUserId = Binder.getCallingUid() / 100000
                                val resolvedUid = resolvePackageUidFromSystem(packageName, callingUserId)
                                reply?.writeNoException()
                                reply?.writeInt(resolvedUid)
                                log(Log.INFO, TAG, "SYSTEM_SERVER_RPC_PACKAGE_UID package=$packageName user=$callingUserId uid=$resolvedUid callingUid=${Binder.getCallingUid()}")
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

    private fun resolvePackageUidFromSystem(packageName: String, userId: Int): Int {
        if (packageName.isBlank()) return -1

        // This code already executes inside real system_server. Use the framework
        // PackageManager API directly instead of reflecting into Samsung's
        // ApplicationPackageManager implementation. On Android 17 / One UI 9 the
        // reflected invocation can throw InvocationTargetException even though the
        // package is installed (observed with com.termux on run 555).
        val direct = runCatching {
            val activityThread = Class.forName("android.app.ActivityThread")
            val current = activityThread.getDeclaredMethod("currentActivityThread").invoke(null)
                ?: return@runCatching -1
            val systemContext = activityThread.getDeclaredMethod("getSystemContext").invoke(current) as? Context
                ?: return@runCatching -1
            val pm = systemContext.packageManager
            val getApplicationInfoAsUser = pm.javaClass.methods.firstOrNull {
                it.name == "getApplicationInfoAsUser" &&
                    it.parameterTypes.size == 3 &&
                    it.parameterTypes[0] == String::class.java
            } ?: return@runCatching -1
            val appInfo = getApplicationInfoAsUser.invoke(pm, packageName, 0, userId)
                as? android.content.pm.ApplicationInfo
            appInfo?.uid ?: -1
        }.onFailure {
            log(Log.WARN, TAG, "SYSTEM_SERVER_RPC_PACKAGE_UID_DIRECT_FAILED package=$packageName user=$userId: ${it.javaClass.name}: ${it.message}")
        }.getOrDefault(-1)

        if (direct >= 0) {
            log(Log.INFO, TAG, "SYSTEM_SERVER_RPC_PACKAGE_UID_OK package=$packageName user=$userId uid=$direct source=PackageManager")
            return direct
        }

        // Last-resort lookup through PackageManager's binder service. This keeps
        // authorization fail-closed while avoiding app-process visibility rules.
        return runCatching {
            val packageManagerBinder = android.os.ServiceManager.getService("package")
                ?: return@runCatching -1
            val stub = Class.forName("android.content.pm.IPackageManager" + "$" + "Stub")
            val asInterface = stub.getDeclaredMethod("asInterface", IBinder::class.java)
            val ipm = asInterface.invoke(null, packageManagerBinder) ?: return@runCatching -1
            val method = ipm.javaClass.methods.firstOrNull {
                it.name == "getPackageUid" &&
                    it.parameterTypes.size == 3 &&
                    it.parameterTypes[0] == String::class.java
            } ?: return@runCatching -1
            val uid = (method.invoke(ipm, packageName, 0L, userId) as? Int) ?: -1
            if (uid >= 0) {
                log(Log.INFO, TAG, "SYSTEM_SERVER_RPC_PACKAGE_UID_OK package=$packageName user=$userId uid=$uid source=IPackageManager")
            }
            uid
        }.onFailure {
            log(Log.WARN, TAG, "SYSTEM_SERVER_RPC_PACKAGE_UID_FALLBACK_FAILED package=$packageName user=$userId: ${it.javaClass.name}: ${it.message}")
        }.getOrDefault(-1)
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
        const val ACTION_SYSTEM_PROBE = 4
        const val ACTION_RESOLVE_PACKAGE_UID = 5
        const val RETAIL_MODE_PACKAGE = "com.samsung.sea.rm"
        const val SYSTEM_UI_PACKAGE = "com.android.systemui"
        const val PROP_ONEUIX_LABS = "persist.tokenx.labs.oneuix"
        const val PROP_STATUS_BAR_LABS = "persist.tokenx.labs.statusbar"
        val RECEIVER_COMPAT_PACKAGES = setOf(RETAIL_MODE_PACKAGE)
        val embeddedStartScheduled = AtomicBoolean(false)
    }
}
