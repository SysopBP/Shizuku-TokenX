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
    private const val TRANSACTION =
        ('_'.code shl 24) or ('T'.code shl 16) or ('K'.code shl 8) or 'N'.code
    private const val DESCRIPTOR = "android.app.IActivityManager"
    private const val ACTION_GET_IDENTITY = 1
    private const val ACTION_SET_BINDER = 2
    private const val ACTION_GET_BINDER = 3
    private const val ACTION_SYSTEM_PROBE = 4

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
        val activity = ServiceManager.getService("activity") ?: return false
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
        val activity = ServiceManager.getService("activity") ?: return null
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

    fun systemProbe(): TokenXSystemProbe? = runCatching {
        val activity = ServiceManager.getService("activity") ?: return null
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

    fun identity(): TokenXXposedIdentity? = runCatching {
        val activity: IBinder = ServiceManager.getService("activity") ?: return null
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
