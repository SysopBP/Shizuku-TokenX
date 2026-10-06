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
 * Sui-inspired rendezvous over Android's existing activity Binder.
 *
 * No Shizuku lifecycle is involved: LSPosed installs a private identity-only
 * transaction in ActivityManagerService and this client verifies that the reply
 * came from the real system_server execution context.
 */
object TokenXXposedSystemServerClient {
    private const val TRANSACTION = 0x00f54b4e // private TokenX code; must stay within Binder LAST_CALL_TRANSACTION (0x00ffffff)
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
