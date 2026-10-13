package moe.shizuku.manager.tokenx

import android.content.Context
import android.content.pm.PackageManager
import com.topjohnwu.superuser.Shell
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.utils.ShizukuStateMachine
import rikka.shizuku.Shizuku

/**
 * Runtime backend discovery. This never treats package presence as an active
 * bridge: Xposed/LSPosed can be installed while TokenX's system_server hook is
 * not loaded, so those are separate signals.
 */
data class TokenXRuntimeState(
    val backendState: TokenXBackendState,
    /** Controlled TokenX package was admitted by PackageManager to android.uid.system/1000. */
    val nativeUid1000Verified: Boolean,
    /** Standalone headless TokenX bridge returned a verified system_server identity. */
    val systemServerBridgeAttached: Boolean,
    /** A TokenX Binder transaction completed inside system_server as UID 1000. */
    val systemServerBridgeActive: Boolean,
    /** Capability bits reported by the live TokenX system_server Binder. */
    val systemServerCapabilities: Int,
    /** Read-only operations executed by the headless Binder endpoint in system_server. */
    val systemServerFunctionalResult: TokenXBridgeFunctionalResult?,
    val backendRegistry: TokenXBackendRegistry,
    val xposedFrameworkDetected: Boolean,
    /** CorePatch package presence; hook effectiveness is inferred separately from PM UID1000 verification. */
    val corePatchDetected: Boolean,
    val androidApiLevel: Int,
    val oneUiVersion: String,
    val xposedBridgeActive: Boolean,
    val routes: Map<TokenXCapability, TokenXRoute>,
)

object TokenXRuntime {
    const val REFRESH_INTERVAL_MS = 1000L
    private const val XPOSED_RPC_VERIFY_INTERVAL_MS = 15_000L

    @Volatile private var lastXposedRpcVerifyAt = 0L
    @Volatile private var cachedXposedIdentity: TokenXXposedIdentity? = null
    @Volatile private var cachedXposedBridgeActive = false

    /**
     * The UI refreshes once per second, but a full _TKN verification is a three-transaction
     * Binder round trip. Cache that proof for a short window so opening the manager does not
     * continuously hammer ActivityManager/system_server. A dead Shizuku binder invalidates
     * the active result immediately; otherwise verification is refreshed every 15 seconds.
     */
    @Synchronized
    private fun verifyXposedRoute(running: Boolean): Pair<TokenXXposedIdentity?, Boolean> {
        val now = android.os.SystemClock.elapsedRealtime()
        val due = now - lastXposedRpcVerifyAt >= XPOSED_RPC_VERIFY_INTERVAL_MS
        if (!due) {
            return cachedXposedIdentity to (running && cachedXposedBridgeActive)
        }

        val identity = TokenXXposedSystemServerClient.identity()
        val identityVerified = identity?.verifiedSystemServer == true
        val liveShizukuBinder = if (running) runCatching { Shizuku.getBinder() }.getOrNull() else null
        val published = identityVerified &&
            liveShizukuBinder?.let { TokenXXposedSystemServerClient.publishBinder(it) } == true
        val returned = if (published) TokenXXposedSystemServerClient.binder() else null
        val active = identityVerified && returned?.isBinderAlive == true && returned.pingBinder()

        cachedXposedIdentity = identity
        cachedXposedBridgeActive = active
        lastXposedRpcVerifyAt = now
        return identity to active
    }
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
        val corePatchDetected = isInstalled(context.packageManager, "org.lsposed.corepatch")
        val oneUiVersion = readOneUiVersion()

        // LSPosed is the live SYSTEM_SERVER backend. BridgeTest remains diagnostic-only:
        // runtime must never bind its IdentityService or retry it as an application backend.
        // The private _TKN transaction rides ActivityManager's existing Binder and is
        // accepted only when the reply proves the real system_server PID/UID/SELinux/cmdline.
        // Full Xposed RPC verification is deliberately decoupled from the 1-second UI
        // refresh. Repeating GET_IDENTITY + SET_BINDER + GET_BINDER every refresh created
        // unnecessary Binder traffic even after the route was already proven healthy.
        val (xposedIdentity, xposedSystemServerActive) = verifyXposedRoute(running)
        val bridgeAttached = xposedIdentity != null
        val bridgeActive = xposedSystemServerActive
        val bridgeFunctional: TokenXBridgeFunctionalResult? = null
        val bridgeCapabilities = 0
        // Native PM UID1000 remains separate from the Xposed system_server route.
        // SYSTEM_UID is ready only after a functional read-only RPC executes in real
        // system_server and verifies UID 1000, SELinux and core framework services.
        val nativeUid1000 = running && uid == 1000
        val systemProbe = if (xposedSystemServerActive) TokenXXposedSystemServerClient.systemProbe() else null
        val sserverBinderReady = systemProbe?.verified == true
        val state = TokenXBackendState(
            serverRunning = running,
            serverUid = uid,
            rootAvailable = root || uid == 0,
            nativeUid1000Available = nativeUid1000,
            systemServerBridgeAvailable = xposedSystemServerActive,
            shellAvailable = uid == 2000,
        )

        val preferredBackend = when (ShizukuSettings.getStartMethod()) {
            ShizukuSettings.StartMethod.ROOT -> TokenXBackend.ROOT
            ShizukuSettings.StartMethod.SYSTEM -> TokenXBackend.SYSTEM_UID
            ShizukuSettings.StartMethod.WIRELESS,
            ShizukuSettings.StartMethod.USB,
            ShizukuSettings.StartMethod.WIRELESS_NO_NETWORK -> TokenXBackend.SHELL
            else -> null
        }

        val selectedBackend = TokenXRouteState.selected(preferredBackend ?: state.activeBackend)
        val registry = TokenXBackendRegistryBuilder.build(
            selected = selectedBackend,
            rootReady = state.rootAvailable,
            nativeUidReady = nativeUid1000,
            systemUidReady = sserverBinderReady,
            systemServerReady = xposedSystemServerActive,
            shellReady = running && uid == 2000,
        )

        return TokenXRuntimeState(
            backendState = state,
            nativeUid1000Verified = nativeUid1000,
            systemServerBridgeAttached = bridgeAttached,
            systemServerBridgeActive = bridgeActive,
            systemServerCapabilities = bridgeCapabilities,
            systemServerFunctionalResult = bridgeFunctional,
            backendRegistry = registry,
            xposedFrameworkDetected = xposedDetected,
            corePatchDetected = corePatchDetected,
            androidApiLevel = android.os.Build.VERSION.SDK_INT,
            oneUiVersion = oneUiVersion,
            xposedBridgeActive = xposedSystemServerActive,
            routes = TokenXCapability.entries.associateWith { TokenXRouter.route(it, state, preferredBackend) },
        )
    }


    private fun readOneUiVersion(): String = runCatching {
        fun formatSamsungVersion(raw: String): String? {
            if (raw.isBlank()) return null
            val numeric = raw.toIntOrNull() ?: return raw
            // Samsung exposes One UI either as a friendly value or as its SEP-style
            // encoded integer (for example 90000 for One UI 9). Never print the raw
            // encoded property in the UI.
            if (numeric >= 90000) {
                val encoded = numeric - 90000
                val major = 9 + (encoded / 10000)
                val minor = (encoded % 10000) / 100
                return if (minor == 0) major.toString() else "$major.$minor"
            }
            return raw
        }

        val direct = Shell.cmd("getprop ro.build.version.oneui").exec().out.firstOrNull()?.trim().orEmpty()
        formatSamsungVersion(direct)?.let { return it }

        val sep = Shell.cmd("getprop ro.build.version.sep").exec().out.firstOrNull()?.trim().orEmpty()
        formatSamsungVersion(sep) ?: "Unknown"
    }.getOrDefault("Unknown")

    private fun isInstalled(pm: PackageManager, packageName: String): Boolean =
        runCatching { pm.getPackageInfo(packageName, 0) }.isSuccess

}
