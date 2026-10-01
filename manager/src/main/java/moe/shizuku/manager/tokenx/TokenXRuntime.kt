package moe.shizuku.manager.tokenx

import android.content.Context
import android.content.pm.PackageManager
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

        // Deliberately false until the actual system_server hook performs a
        // handshake. Installation/detection alone must never grant capability.
        val bridgeActive = false
        val state = TokenXBackendState(
            serverRunning = running,
            serverUid = uid,
            rootAvailable = root || uid == 0,
            systemServerBridgeAvailable = bridgeActive || uid == 1000,
            shellAvailable = uid == 2000 || running,
        )

        return TokenXRuntimeState(
            backendState = state,
            xposedFrameworkDetected = xposedDetected,
            xposedBridgeActive = bridgeActive,
            routes = TokenXCapability.entries.associateWith { TokenXRouter.route(it, state) },
        )
    }

    private fun isInstalled(pm: PackageManager, packageName: String): Boolean =
        runCatching { pm.getPackageInfo(packageName, 0) }.isSuccess
}
