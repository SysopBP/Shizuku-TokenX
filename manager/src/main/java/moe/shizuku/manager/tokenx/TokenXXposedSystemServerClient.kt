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
 * TokenX system_server rendezvous over a dedicated Binder service.
 *
 * LSPosed creates and registers the Binder from inside system_server. The
 * manager resolves only that service and verifies the returned process identity.
 * Root Shizuku remains independent and is not replaced by this transport.
 */
object TokenXXposedSystemServerClient {
    private const val SERVICE_NAME = "tokenx.system_server"
    private const val TRANSACTION = IBinder.FIRST_CALL_TRANSACTION
    private const val DESCRIPTOR = "moe.shizuku.tokenx.ISystemServerBridge"
    private const val ACTION_GET_IDENTITY = 1

    fun identity(): TokenXXposedIdentity? = runCatching {
        val bridge: IBinder = ServiceManager.checkService(SERVICE_NAME) ?: return null
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(DESCRIPTOR)
            data.writeInt(ACTION_GET_IDENTITY)
            if (!bridge.transact(TRANSACTION, data, reply, 0)) return null
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
