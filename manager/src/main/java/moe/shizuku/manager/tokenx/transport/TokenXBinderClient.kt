package moe.shizuku.manager.tokenx.transport

import android.os.Binder
import android.os.IBinder
import android.os.Parcel

class TokenXBinderClient(private val remote: IBinder) {
    private val lifetime = Binder()
    fun hello(): Int = transact(TokenXBinderProtocol.TX_HELLO) { it.readInt() }
    fun requestSession(backend: Int, packageHint: String? = null): TokenXBinderSession =
        transact(TokenXBinderProtocol.TX_REQUEST_SESSION, { d ->
            d.writeStrongBinder(lifetime); d.writeInt(backend); d.writeString(packageHint)
        }) { readSession(it) }
    fun status(id: Long)=transact(TokenXBinderProtocol.TX_STATUS,{it.writeLong(id)}){readSession(it)}
    fun ping(id: Long)=transact(TokenXBinderProtocol.TX_PING,{it.writeLong(id)}){readSession(it)}
    fun close(id: Long)=transact(TokenXBinderProtocol.TX_CLOSE,{it.writeLong(id)}){it.readInt()!=0}
    private fun readSession(r: Parcel)=TokenXBinderSession(r.readLong(),r.readInt(),r.readInt(),
        r.readInt(),r.readInt(),r.readString().orEmpty()).also {
        check(it.backendUid == TokenXBinderProtocol.expectedUid(it.backend)) { "backend UID mismatch" }
    }
    private inline fun <T> transact(code:Int,write:(Parcel)->Unit={},read:(Parcel)->T):T {
        val d=Parcel.obtain(); val r=Parcel.obtain()
        try {
            d.writeInterfaceToken(TokenXBinderProtocol.DESCRIPTOR); write(d)
            check(remote.transact(code,d,r,0)) { "TokenX Binder transaction rejected" }
            r.readException(); return read(r)
        } finally { r.recycle(); d.recycle() }
    }
}