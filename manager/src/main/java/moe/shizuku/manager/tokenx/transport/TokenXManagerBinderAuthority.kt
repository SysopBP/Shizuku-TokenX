package moe.shizuku.manager.tokenx.transport

import android.content.Context
import android.os.IBinder
import moe.shizuku.manager.ShizukuManagerProvider
import moe.shizuku.manager.tokenx.TokenXBackend
import moe.shizuku.manager.tokenx.TokenXSessionRegistry

class TokenXManagerBinderAuthority(
    private val context:Context,
    private val isAuthorized:(String,Int)->Boolean,
):TokenXBinderAuthority {
    private data class Owned(val wire:TokenXBinderSession,val lifetime:IBinder)
    private val lock=Any()
    private val sessions=LinkedHashMap<Long,Owned>()

    override fun openSession(lifetime:IBinder,requestedBackend:Int,callerUid:Int,
        callerPid:Int,packageHint:String?):TokenXBinderSession? {
        val packageName=resolvePackage(callerUid,packageHint)?:return null
        if(!isAuthorized(packageName,callerUid)||!backendAvailable(requestedBackend)) return null
        val backend=requestedBackend.toBackend()?:return null
        val registered=TokenXSessionRegistry.register(lifetime,packageName,backend)?:return null
        val wire=TokenXBinderSession(registered.id,requestedBackend,
            TokenXBinderProtocol.expectedUid(requestedBackend),callerUid,callerPid,packageName)
        synchronized(lock){sessions[wire.id]=Owned(wire,lifetime)}
        return wire
    }
    override fun session(sessionId:Long,callerUid:Int,callerPid:Int):TokenXBinderSession? {
        val owned=synchronized(lock){sessions[sessionId]}?:return null
        if(owned.wire.callerUid!=callerUid||owned.wire.callerPid!=callerPid)return null
        if(TokenXSessionRegistry.snapshot().none{it.id==sessionId}) {
            synchronized(lock){sessions.remove(sessionId)}; return null
        }
        return owned.wire
    }
    override fun closeSession(sessionId:Long,callerUid:Int,callerPid:Int):Boolean {
        val owned=synchronized(lock){sessions[sessionId]}?:return false
        if(owned.wire.callerUid!=callerUid||owned.wire.callerPid!=callerPid)return false
        synchronized(lock){sessions.remove(sessionId)}
        return TokenXSessionRegistry.remove(owned.lifetime)!=null
    }
    private fun resolvePackage(uid:Int,hint:String?):String? {
        val packages=context.packageManager.getPackagesForUid(uid)?.distinct().orEmpty()
        if(!hint.isNullOrBlank()&&hint in packages)return hint
        return packages.singleOrNull()
    }
    private fun backendAvailable(b:Int)=when(b) {
        TokenXBinderProtocol.BACKEND_ROOT->ShizukuManagerProvider.rootBinder()!=null
        TokenXBinderProtocol.BACKEND_SYSTEM->ShizukuManagerProvider.systemBinder()!=null
        TokenXBinderProtocol.BACKEND_SHELL->ShizukuManagerProvider.shellBinder()!=null
        else->false
    }
    private fun Int.toBackend()=when(this) {
        TokenXBinderProtocol.BACKEND_ROOT->TokenXBackend.ROOT
        TokenXBinderProtocol.BACKEND_SYSTEM->TokenXBackend.SYSTEM_UID
        TokenXBinderProtocol.BACKEND_SHELL->TokenXBackend.SHELL
        else->null
    }
}