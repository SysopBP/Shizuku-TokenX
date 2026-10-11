package moe.shizuku.manager.shell

/** A persisted installation is never reported ready until a new process answers. */
object ProvisionPolicy {
    fun accepts(previousPid: Int, currentPid: Int, expectedBuild: Int, currentBuild: Int,
                identityVerified: Boolean, functionalVerified: Boolean): Boolean =
        previousPid > 0 && currentPid > 0 && previousPid != currentPid &&
            expectedBuild == currentBuild && identityVerified && functionalVerified

    fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}
