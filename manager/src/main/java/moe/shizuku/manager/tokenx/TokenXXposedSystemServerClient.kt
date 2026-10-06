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

object TokenXXposedSystemServerClient {
    private const val TRANSACTION =
        ('_'.code shl 24) or ('T'.code shl 16) or ('K'.code shl 8) or 'N'.code
    private const val DESCRIPTOR = "android.app.IActivityManager"
    private const val ACTION_GET_IDENTITY = 1
    private const val ACTION_SET_BINDER = 2
    private const val ACTION_GET_BINDER = 3

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
