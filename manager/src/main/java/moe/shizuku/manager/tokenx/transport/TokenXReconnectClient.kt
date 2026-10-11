package moe.shizuku.manager.tokenx.transport

import android.os.IBinder

class TokenXReconnectClient {
    @Volatile private var generation:Long=-1
    @Volatile private var binder:IBinder?=null
    fun current():IBinder? {
        val s=TokenXRendezvous.snapshot(); val cached=binder
        if(s.generation!=generation||cached==null||!cached.isBinderAlive) {
            generation=s.generation; binder=s.binder?.takeIf{it.isBinderAlive}
        }
        return binder
    }
    fun invalidate(){binder=null;generation=-1}
}