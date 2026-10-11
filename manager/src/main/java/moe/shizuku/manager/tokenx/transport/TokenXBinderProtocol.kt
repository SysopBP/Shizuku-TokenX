package moe.shizuku.manager.tokenx.transport

import android.os.Binder
import android.os.IBinder
import android.os.Parcel

object TokenXBinderProtocol {
    const val DESCRIPTOR = "moe.shizuku.tokenx.ITransport"
    const val VERSION = 1
    const val TX_HELLO = IBinder.FIRST_CALL_TRANSACTION
    const val TX_REQUEST_SESSION = IBinder.FIRST_CALL_TRANSACTION + 1
    const val TX_PING = IBinder.FIRST_CALL_TRANSACTION + 2
    const val TX_STATUS = IBinder.FIRST_CALL_TRANSACTION + 3
    const val TX_CLOSE = IBinder.FIRST_CALL_TRANSACTION + 4
    const val BACKEND_ROOT = 0
    const val BACKEND_SYSTEM = 1
    const val BACKEND_SHELL = 2
    fun expectedUid(backend: Int): Int = when (backend) {
        BACKEND_ROOT -> 0
        BACKEND_SYSTEM -> 1000
        BACKEND_SHELL -> 2000
        else -> -1
    }
}

data class TokenXBinderSession(
    val id: Long, val backend: Int, val backendUid: Int,
    val callerUid: Int, val callerPid: Int, val packageName: String,
)

interface TokenXBinderAuthority {
    fun openSession(lifetime: IBinder, requestedBackend: Int, callerUid: Int,
        callerPid: Int, packageHint: String?): TokenXBinderSession?
    fun session(sessionId: Long, callerUid: Int, callerPid: Int): TokenXBinderSession?
    fun closeSession(sessionId: Long, callerUid: Int, callerPid: Int): Boolean
}

class TokenXTransportBinder(private val authority: TokenXBinderAuthority) : Binder() {
    init { attachInterface(null, TokenXBinderProtocol.DESCRIPTOR) }
    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        if (code == INTERFACE_TRANSACTION) { reply?.writeString(TokenXBinderProtocol.DESCRIPTOR); return true }
        data.enforceInterface(TokenXBinderProtocol.DESCRIPTOR)
        val uid = getCallingUid()
        val pid = getCallingPid()
        when (code) {
            TokenXBinderProtocol.TX_HELLO -> { reply?.writeNoException(); reply?.writeInt(TokenXBinderProtocol.VERSION); return true }
            TokenXBinderProtocol.TX_REQUEST_SESSION -> {
                val lifetime=data.readStrongBinder(); val backend=data.readInt(); val hint=data.readString()
                if (lifetime == null || TokenXBinderProtocol.expectedUid(backend) < 0) {
                    reply?.writeException(SecurityException("invalid TokenX session request")); return true
                }
                val session=authority.openSession(lifetime,backend,uid,pid,hint)
                if (session == null) { reply?.writeException(SecurityException("TokenX session denied")); return true }
                reply?.writeNoException(); writeSession(reply,session); return true
            }
            TokenXBinderProtocol.TX_PING, TokenXBinderProtocol.TX_STATUS -> {
                val session=authority.session(data.readLong(),uid,pid)
                if (session == null) { reply?.writeException(SecurityException("unknown or foreign TokenX session")); return true }
                reply?.writeNoException(); writeSession(reply,session); return true
            }
            TokenXBinderProtocol.TX_CLOSE -> {
                val closed=authority.closeSession(data.readLong(),uid,pid)
                reply?.writeNoException(); reply?.writeInt(if(closed) 1 else 0); return true
            }
        }
        return super.onTransact(code,data,reply,flags)
    }
    private fun writeSession(reply: Parcel?, s: TokenXBinderSession) {
        reply ?: return
        reply.writeLong(s.id); reply.writeInt(s.backend); reply.writeInt(s.backendUid)
        reply.writeInt(s.callerUid); reply.writeInt(s.callerPid); reply.writeString(s.packageName)
    }
}