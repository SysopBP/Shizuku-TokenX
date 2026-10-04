package moe.shizuku.manager.tokenx

/**
 * TokenX privilege routing foundation.
 *
 * Root is the primary engine when available. System Server is a specialized
 * framework backend rather than a global replacement for root. Shell remains
 * the recovery/fallback path.
 */
enum class TokenXBackend(val uid: Int?) {
    ROOT(0),
    NATIVE_UID(1000),
    SYSTEM_UID(1000),
    SYSTEM_SERVER(1000),
    SHELL(2000),
    UNAVAILABLE(null),
}

enum class TokenXCapability {
    GENERAL,
    FILESYSTEM,
    PROCESS,
    FRAMEWORK,
    SHELL_COMMAND,
}

data class TokenXBackendState(
    val serverRunning: Boolean,
    val serverUid: Int,
    val rootAvailable: Boolean,
    val nativeUid1000Available: Boolean = false,
    val systemServerBridgeAvailable: Boolean = false,
    val shellAvailable: Boolean = true,
) {
    val activeBackend: TokenXBackend
        get() = when (serverUid) {
            0 -> TokenXBackend.ROOT
            1000 -> TokenXBackend.SYSTEM_SERVER
            2000 -> TokenXBackend.SHELL
            else -> TokenXBackend.UNAVAILABLE
        }
}

data class TokenXRoute(
    val capability: TokenXCapability,
    val backend: TokenXBackend,
    val reason: String,
)

object TokenXRouter {

    fun route(
        capability: TokenXCapability,
        state: TokenXBackendState,
        preferredBackend: TokenXBackend? = null,
    ): TokenXRoute {
        val preferredAvailable = when (preferredBackend) {
            TokenXBackend.ROOT -> state.rootAvailable
            TokenXBackend.NATIVE_UID -> state.nativeUid1000Available
            TokenXBackend.SYSTEM_UID -> state.systemServerBridgeAvailable
            TokenXBackend.SYSTEM_SERVER -> state.systemServerBridgeAvailable
            TokenXBackend.SHELL -> state.shellAvailable
            else -> false
        }
        val backend = if (preferredAvailable) preferredBackend!! else when (capability) {
            TokenXCapability.FRAMEWORK -> when {
                state.systemServerBridgeAvailable -> TokenXBackend.SYSTEM_SERVER
                state.rootAvailable -> TokenXBackend.ROOT
                state.shellAvailable -> TokenXBackend.SHELL
                else -> TokenXBackend.UNAVAILABLE
            }

            TokenXCapability.FILESYSTEM,
            TokenXCapability.PROCESS,
            TokenXCapability.GENERAL -> when {
                state.rootAvailable -> TokenXBackend.ROOT
                state.nativeUid1000Available -> TokenXBackend.NATIVE_UID
                state.systemServerBridgeAvailable -> TokenXBackend.SYSTEM_SERVER
                state.shellAvailable -> TokenXBackend.SHELL
                else -> TokenXBackend.UNAVAILABLE
            }

            TokenXCapability.SHELL_COMMAND -> when {
                state.rootAvailable -> TokenXBackend.ROOT
                state.shellAvailable -> TokenXBackend.SHELL
                state.systemServerBridgeAvailable -> TokenXBackend.SYSTEM_SERVER
                else -> TokenXBackend.UNAVAILABLE
            }
        }

        return TokenXRoute(
            capability = capability,
            backend = backend,
            reason = if (preferredAvailable) "Explicit runtime preference: ${backend.name}" else routeReason(capability, backend),
        )
    }

    private fun routeReason(
        capability: TokenXCapability,
        backend: TokenXBackend,
    ): String = when (backend) {
        TokenXBackend.ROOT -> "Root/UID 0 selected as the primary privilege engine"
        TokenXBackend.NATIVE_UID -> "PackageManager-verified android.uid.system/UID 1000 selected"
        TokenXBackend.SYSTEM_UID -> "Serv.apk/System UID 1000 selected for privileged execution"
        TokenXBackend.SYSTEM_SERVER ->
            if (capability == TokenXCapability.FRAMEWORK) {
                "System Server selected for Android framework access"
            } else {
                "System Server selected because the root backend is unavailable"
            }
        TokenXBackend.SHELL -> "Shell/UID 2000 selected as the fallback backend"
        TokenXBackend.UNAVAILABLE -> "No TokenX backend is currently available"
    }
}
