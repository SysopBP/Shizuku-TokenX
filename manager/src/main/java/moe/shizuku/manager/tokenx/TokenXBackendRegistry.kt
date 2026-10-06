package moe.shizuku.manager.tokenx

/**
 * One discovered TokenX execution backend. Availability is independent of the
 * currently selected route so multiple privilege services can remain ready at once.
 */
data class TokenXRegisteredBackend(
    val backend: TokenXBackend,
    val uid: Int,
    val ready: Boolean,
    val verified: Boolean,
    val detail: String,
)

data class TokenXBackendRegistry(
    val selected: TokenXBackend,
    val backends: Map<TokenXBackend, TokenXRegisteredBackend>,
) {
    fun entry(backend: TokenXBackend): TokenXRegisteredBackend? = backends[backend]
    fun isReady(backend: TokenXBackend): Boolean = backends[backend]?.ready == true
}

/**
 * Builds the routing view without starting, stopping or replacing any Binder.
 * This is intentionally observation-only: backend lifecycle stays with the proven
 * launch paths while routing can evolve independently.
 */
object TokenXBackendRegistryBuilder {
    fun build(
        selected: TokenXBackend,
        rootReady: Boolean,
        nativeUidReady: Boolean,
        systemUidReady: Boolean,
        systemServerReady: Boolean,
        shellReady: Boolean,
    ): TokenXBackendRegistry = TokenXBackendRegistry(
        selected = selected,
        backends = listOf(
            TokenXRegisteredBackend(TokenXBackend.ROOT, 0, rootReady, rootReady, if (rootReady) "KernelSU/root ready" else "Root unavailable"),
            TokenXRegisteredBackend(TokenXBackend.NATIVE_UID, 1000, nativeUidReady, nativeUidReady, if (nativeUidReady) "PackageManager android.uid.system verified" else "Native PM UID 1000 unavailable"),
            TokenXRegisteredBackend(TokenXBackend.SYSTEM_UID, 1000, systemUidReady, systemUidReady, if (systemUidReady) "TKN Bridge + Shizuku UID 1000 ready" else "System UID backend unavailable"),
            TokenXRegisteredBackend(TokenXBackend.SYSTEM_SERVER, 1000, systemServerReady, systemServerReady, if (systemServerReady) "LSPosed system_server RPC verified" else "LSPosed system_server RPC unavailable"),
            TokenXRegisteredBackend(TokenXBackend.SHELL, 2000, shellReady, shellReady, if (shellReady) "Shizuku shell ready" else "Shell backend unavailable"),
        ).associateBy { it.backend },
    )
}
