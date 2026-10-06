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

/**
 * Sui-style TokenX rendezvous through Android's existing activity Binder.
 *
 * LSPosed intercepts the private TokenX transaction at Binder.execTransact in
 * system_server, before ActivityManagerService.onTransact sees the request.
 */
object TokenXXposedSystemServerClient {
    private const val TRANSACTION =
        ('_'.code shl 24) or ('T'.code shl 16) or ('K'.code shl 8) or 'N'.code
    private const val DESCRIPTOR = "android.app.IActivityManager"
    private const val ACTION_GET_IDENTITY = 1

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
