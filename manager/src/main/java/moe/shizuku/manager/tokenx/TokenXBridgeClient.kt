package moe.shizuku.manager.tokenx

/**
 * Generic result container retained for dashboard compatibility.
 *
 * Legacy helper-package Binder clients have been removed; live framework identity comes from the _TKN RPC.
 * Live system_server identity is provided exclusively by TokenXXposedSystemServerClient.
 */
data class TokenXBridgeFunctionalResult(
    val pid: Int,
    val uid: Int,
    val selinux: String,
    val checks: List<String>,
) {
    val passCount: Int get() = checks.count { it.startsWith("PASS ") }
    val denyCount: Int get() = checks.count { it.startsWith("DENY ") }
    val verified: Boolean
        get() = uid == 1000 &&
            selinux.startsWith("u:r:system_server:s0") &&
            passCount > 0 &&
            denyCount == 0
}
