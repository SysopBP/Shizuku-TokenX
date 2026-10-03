package moe.shizuku.manager.tokenx

import android.content.Context
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.Parcel
import android.os.ServiceManager
import com.topjohnwu.superuser.Shell
import moe.shizuku.manager.utils.ShizukuStateMachine
import rikka.shizuku.Shizuku

/**
 * Runtime backend discovery. This never treats package presence as an active
 * bridge: Xposed/LSPosed can be installed while TokenX's system_server hook is
 * not loaded, so those are separate signals.
 */
data class TokenXRuntimeState(
    val backendState: TokenXBackendState,
    val xposedFrameworkDetected: Boolean,
    val xposedBridgeActive: Boolean,
    val routes: Map<TokenXCapability, TokenXRoute>,
)

object TokenXRuntime {
    const val REFRESH_INTERVAL_MS = 1000L
    private val knownXposedManagers = listOf(
        "org.lsposed.manager",
        "org.meowcat.edxposed.manager",
        "de.robv.android.xposed.installer",
    )

    fun snapshot(context: Context): TokenXRuntimeState {
        val running = ShizukuStateMachine.isRunning() && runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        val uid = if (running) runCatching { Shizuku.getUid() }.getOrDefault(-1) else -1
        val root = runCatching { Shell.getCachedShell()?.isRoot == true }.getOrDefault(false)
        val xposedDetected = knownXposedManagers.any { isInstalled(context.packageManager, it) }

        // A package/manager being installed is not enough. Trust the UID 1000
        // backend only after the Binder service in system_server answers our ping.
        val bridgeActive = pingSystemServerBridge()
        val servUid1000 = isServUid1000()
        val state = TokenXBackendState(
            serverRunning = running,
            serverUid = uid,
            rootAvailable = root || uid == 0,
            systemServerBridgeAvailable = servUid1000 || uid == 1000,
            shellAvailable = uid == 2000,
        )

        return TokenXRuntimeState(
            backendState = state,
            xposedFrameworkDetected = xposedDetected,
            xposedBridgeActive = bridgeActive,
            routes = TokenXCapability.entries.associateWith { TokenXRouter.route(it, state) },
        )
    }

    private fun pingSystemServerBridge(): Boolean = runCatching {
        val binder = ServiceManager.getService(SYSTEM_SERVER_SERVICE) ?: return false
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(SYSTEM_SERVER_DESCRIPTOR)
            if (!binder.transact(IBinder.FIRST_CALL_TRANSACTION, data, reply, 0)) return false
            reply.readException()
            reply.readInt() == 1000
        } finally {
            data.recycle()
            reply.recycle()
        }
    }.getOrDefault(false)

    /** Serv.apk is the Sserver backend. Package presence alone is insufficient: it must
     * resolve to Android's system UID before TokenX advertises UID 1000 as available. */
    private fun isServUid1000(): Boolean = runCatching {
        val result = Shell.cmd("cmd package list packages -U | grep -F 'package:com.vikram.exp uid:1000'").exec()
        result.isSuccess && result.out.any { it.contains("package:com.vikram.exp uid:1000") }
    }.getOrDefault(false)

    private fun isInstalled(pm: PackageManager, packageName: String): Boolean =
        runCatching { pm.getPackageInfo(packageName, 0) }.isSuccess

    private const val SYSTEM_SERVER_SERVICE = "tokenx_system_server"
    private const val SYSTEM_SERVER_DESCRIPTOR = "moe.shizuku.tokenx.ISystemServerBridge"
}
