package moe.shizuku.manager.shell

/** A persisted installation is never reported ready until a new process answers. */
object ProvisionPolicy {
    fun acceptsDirect(targetPid: Int, currentPid: Int, expectedBuild: Int, currentBuild: Int,
                      expectedRequest: Long, actualRequest: Long,
                      identityVerified: Boolean, functionalVerified: Boolean): Boolean =
        targetPid > 0 && targetPid == currentPid && expectedBuild == currentBuild &&
            expectedRequest > 0 && expectedRequest == actualRequest && identityVerified && functionalVerified

    fun accepts(previousPid: Int, currentPid: Int, expectedBuild: Int, currentBuild: Int,
                identityVerified: Boolean, functionalVerified: Boolean): Boolean =
        previousPid > 0 && currentPid > 0 && previousPid != currentPid &&
            expectedBuild == currentBuild && identityVerified && functionalVerified

    fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}
