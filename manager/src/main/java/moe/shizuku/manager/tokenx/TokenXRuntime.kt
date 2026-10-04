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

        // The standalone headless bridge is authoritative for SYSTEM_SERVER.
        // Package presence and the legacy Serv association are not accepted as proof.
        TokenXBridgeClient.ensureBound(context)
        val bridgeIdentity = TokenXBridgeClient.identity()
        val bridgeAttached = bridgeIdentity != null
        val bridgeActive = bridgeIdentity?.verifiedSystemServer == true
        // Identity/health is intentionally the only contract in this first integration.
        val bridgeCapabilities = 0
        val servUid1000 = isServUid1000()
        val nativeUid1000 = isNativeUid1000Verified()
        // Sserver is READY only when the live Shizuku Binder itself belongs to UID 1000.
        // Serv.apk being UID 1000 means the environment is provisioned, not that the
        // UID-1000 Shizuku server has actually published a usable Binder.
        val sserverBinderReady = running && uid == 1000 && servUid1000
        val state = TokenXBackendState(
            serverRunning = running,
            serverUid = uid,
            rootAvailable = root || uid == 0,
            nativeUid1000Available = nativeUid1000,
            systemServerBridgeAvailable = bridgeActive,
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
            systemServerReady = bridgeActive,
            shellReady = running && uid == 2000,
        )

        return TokenXRuntimeState(
            backendState = state,
            nativeUid1000Verified = nativeUid1000,
            systemServerBridgeAttached = bridgeAttached,
            systemServerBridgeActive = bridgeActive,
            systemServerCapabilities = bridgeCapabilities,
            backendRegistry = registry,
            xposedFrameworkDetected = xposedDetected,
            corePatchDetected = corePatchDetected,
            androidApiLevel = android.os.Build.VERSION.SDK_INT,
            oneUiVersion = oneUiVersion,
            xposedBridgeActive = bridgeActive,
            routes = TokenXCapability.entries.associateWith { TokenXRouter.route(it, state, preferredBackend) },
        )
    }

    /** Serv.apk is the Sserver backend. Package presence alone is insufficient: it must
     * resolve to Android's system UID before TokenX advertises UID 1000 as available. */
    private fun isServUid1000(): Boolean = runCatching {
        val result = Shell.cmd("cmd package list packages -U | grep -F 'package:com.vikram.exp uid:1000'").exec()
        result.isSuccess && result.out.any { it.contains("package:com.vikram.exp uid:1000") }
    }.getOrDefault(false)

    /**
     * Native UID1000 is deliberately stricter than package presence: both PackageManager's
     * assigned UID and the shared-user record must agree before TokenX advertises it.
     */
    private fun isNativeUid1000Verified(): Boolean = runCatching {
        val uidCheck = Shell.cmd("cmd package list packages -U | grep -F 'package:com.tokenx.uidtest uid:1000'").exec()
        if (!uidCheck.isSuccess || uidCheck.out.none { it.contains("package:com.tokenx.uidtest uid:1000") }) return false
        val sharedCheck = Shell.cmd("dumpsys package com.tokenx.uidtest | grep -E 'appId=1000|sharedUser=.*android.uid.system/1000'").exec()
        sharedCheck.isSuccess &&
            sharedCheck.out.any { it.contains("appId=1000") } &&
            sharedCheck.out.any { it.contains("android.uid.system/1000") }
    }.getOrDefault(false)

    private fun readOneUiVersion(): String = runCatching {
        val direct = Shell.cmd("getprop ro.build.version.oneui").exec().out.firstOrNull()?.trim().orEmpty()
        if (direct.isNotBlank()) return direct
        val sep = Shell.cmd("getprop ro.build.version.sep").exec().out.firstOrNull()?.trim()?.toIntOrNull()
        if (sep != null && sep >= 90000) {
            val encoded = sep - 90000
            return "${encoded / 10000}.${(encoded % 10000) / 100}"
        }
        "Unknown"
    }.getOrDefault("Unknown")

    private fun isInstalled(pm: PackageManager, packageName: String): Boolean =
        runCatching { pm.getPackageInfo(packageName, 0) }.isSuccess

}
