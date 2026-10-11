package moe.shizuku.manager.tokenx

import android.os.IBinder
import android.os.Parcel
import android.os.Process
import android.os.ServiceManager

data class TokenXXposedIdentity(
    val pid: Int,
    val uid: Int,
    val selinux: String,
    val process: String,
) {
    val verifiedSystemServer: Boolean
        get() = pid > 0 &&
            uid == Process.SYSTEM_UID &&
            process == "system_server" &&
            selinux.startsWith("u:r:system_server:s0")
}


data class TokenXXposedBridgeSelfTest(
    val identity: TokenXXposedIdentity?,
    val identityVerified: Boolean,
    val publishSucceeded: Boolean,
    val roundTripSucceeded: Boolean,
    val detail: String,
) {
    val verified: Boolean
        get() = identityVerified && publishSucceeded && roundTripSucceeded
}

data class TokenXSystemProbe(
    val uid: Int,
    val pid: Int,
    val selinux: String,
    val services: Set<String>,
) {
    val verified: Boolean
        get() = uid == Process.SYSTEM_UID && pid > 0 &&
            selinux.startsWith("u:r:system_server:s0") &&
            services.containsAll(setOf("activity", "package", "power", "window"))
}

object TokenXXposedSystemServerClient {
    @Volatile private var injectedBinder: IBinder? = null
    fun isInjectionAlive(): Boolean = injectedBinder?.isBinderAlive == true
    private fun transport(): IBinder? = injectedBinder?.takeIf { it.isBinderAlive }
        ?: ServiceManager.getService("activity")

    /** Only the UID-1000-authenticated provider handoff may install this slot. */
    fun acceptInjectedBinder(candidate: IBinder): Boolean {
        val identity = identity(candidate)
        val probe = systemProbe(candidate)
        if (identity?.verifiedSystemServer != true || probe?.verified != true ||
            identity.pid != probe.pid || !challenge(candidate, moe.shizuku.manager.BuildConfig.VERSION_CODE)) return false
        return runCatching {
            candidate.linkToDeath({ if (injectedBinder === candidate) injectedBinder = null }, 0)
            injectedBinder = candidate
            true
        }.getOrDefault(false)
    }

    fun verifyInjection(expectedBuild: Int, previousPid: Int): TokenXXposedIdentity? {
        val candidate = injectedBinder?.takeIf { it.isBinderAlive } ?: return null
        val identity = identity(candidate) ?: return null
        val probe = systemProbe(candidate) ?: return null
        return identity.takeIf {
            it.verifiedSystemServer && probe.verified && probe.pid == it.pid &&
                it.pid != previousPid && challenge(candidate, expectedBuild)
        }
    }

    private fun challenge(candidate: IBinder, expectedBuild: Int): Boolean = runCatching {
        val nonce = java.security.SecureRandom().nextLong()
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(DESCRIPTOR)
            data.writeInt(6)
            data.writeLong(nonce)
            if (!candidate.transact(TRANSACTION, data, reply, 0)) return false
            reply.readException()
            reply.readInt() == 4 && reply.readInt() == expectedBuild && reply.readLong() == nonce
        } finally { data.recycle(); reply.recycle() }
    }.getOrDefault(false)
    private const val TRANSACTION =
        ('_'.code shl 24) or ('T'.code shl 16) or ('K'.code shl 8) or 'N'.code
    private const val DESCRIPTOR = "android.app.IActivityManager"
    private const val ACTION_GET_IDENTITY = 1
    private const val ACTION_SET_BINDER = 2
    private const val ACTION_GET_BINDER = 3
    private const val ACTION_SYSTEM_PROBE = 4
    private const val ACTION_RESOLVE_PACKAGE_UID = 5

    /**
     * End-to-end, non-destructive proof of the modern LSPosed system_server route.
     * It verifies real system_server identity, publishes a temporary local Binder,
     * reads it back through the _TKN rendezvous, and confirms the returned Binder is alive.
     * Root/Shizuku fallback remains untouched if any stage fails.
     */
    fun selfTest(): TokenXXposedBridgeSelfTest {
        val identity = identity()
        val identityVerified = identity?.verifiedSystemServer == true
        if (!identityVerified) {
            return TokenXXposedBridgeSelfTest(
                identity = identity,
                identityVerified = false,
                publishSucceeded = false,
                roundTripSucceeded = false,
                detail = "GET_IDENTITY did not verify real system_server",
            )
        }

        val probe = android.os.Binder()
        val publishSucceeded = publishBinder(probe)
        if (!publishSucceeded) {
            return TokenXXposedBridgeSelfTest(
                identity = identity,
                identityVerified = true,
                publishSucceeded = false,
                roundTripSucceeded = false,
                detail = "SET_BINDER failed; root fallback remains available",
            )
        }

        val returned = binder()
        val roundTripSucceeded = returned != null && returned.isBinderAlive && returned.pingBinder()
        return TokenXXposedBridgeSelfTest(
            identity = identity,
            identityVerified = true,
            publishSucceeded = true,
            roundTripSucceeded = roundTripSucceeded,
            detail = if (roundTripSucceeded) {
                "_TKN GET_IDENTITY + SET_BINDER + GET_BINDER verified"
            } else {
                "GET_BINDER did not return a live Binder; root fallback remains available"
            },
        )
    }

    fun publishBinder(value: IBinder): Boolean = runCatching {
        val activity = transport() ?: return false
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(DESCRIPTOR)
            data.writeInt(ACTION_SET_BINDER)
            data.writeStrongBinder(value)
            if (!activity.transact(TRANSACTION, data, reply, 0)) return false
            reply.readException()
            true
        } finally {
            data.recycle()
            reply.recycle()
        }
    }.getOrDefault(false)

    fun binder(): IBinder? = runCatching {
        val activity = transport() ?: return null
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(DESCRIPTOR)
            data.writeInt(ACTION_GET_BINDER)
            if (!activity.transact(TRANSACTION, data, reply, 0)) return null
            reply.readException()
            reply.readStrongBinder()
        } finally {
            data.recycle()
            reply.recycle()
        }
    }.getOrNull()

    fun systemProbe(bridge: IBinder? = transport()): TokenXSystemProbe? = runCatching {
        val activity = bridge ?: return null
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(DESCRIPTOR)
            data.writeInt(ACTION_SYSTEM_PROBE)
            if (!activity.transact(TRANSACTION, data, reply, 0)) return null
            reply.readException()
            TokenXSystemProbe(
                uid = reply.readInt(),
                pid = reply.readInt(),
                selinux = reply.readString().orEmpty(),
                services = reply.readString().orEmpty().split(',').filter { it.isNotBlank() }.toSet(),
            )
        } finally {
            data.recycle()
            reply.recycle()
        }
    }.getOrNull()

    fun packageUid(packageName: String): Int = runCatching {
        if (packageName.isBlank()) return -1
        val activity = transport() ?: return -1
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(DESCRIPTOR)
            data.writeInt(ACTION_RESOLVE_PACKAGE_UID)
            data.writeString(packageName)
            if (!activity.transact(TRANSACTION, data, reply, 0)) return -1
            reply.readException()
            reply.readInt()
        } finally {
            data.recycle()
            reply.recycle()
        }
    }.getOrDefault(-1)

    fun identity(bridge: IBinder? = transport()): TokenXXposedIdentity? = runCatching {
        val activity: IBinder = bridge ?: return null
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(DESCRIPTOR)
            data.writeInt(ACTION_GET_IDENTITY)
            if (!activity.transact(TRANSACTION, data, reply, 0)) return null
            reply.readException()
            TokenXXposedIdentity(
                pid = reply.readInt(),
                uid = reply.readInt(),
                selinux = reply.readString().orEmpty(),
                process = reply.readString().orEmpty(),
            )
        } finally {
            data.recycle()
            reply.recycle()
        }
    }.getOrNull()
}

