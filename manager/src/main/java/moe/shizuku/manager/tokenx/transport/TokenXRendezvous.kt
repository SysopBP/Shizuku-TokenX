package moe.shizuku.manager.tokenx.transport

import android.os.IBinder
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

object TokenXRendezvous {
    data class Snapshot(val generation:Long,val binder:IBinder?,val alive:Boolean)
    private val generation=AtomicLong(0)
    private val listeners=CopyOnWriteArrayList<(Snapshot)->Unit>()
    @Volatile private var published:IBinder?=null
    @Volatile private var death:IBinder.DeathRecipient?=null

    @Synchronized fun publish(binder:IBinder):Snapshot {
        unpublishLocked()
        val recipient=IBinder.DeathRecipient {
            synchronized(this) {
                if(published===binder) {
                    published=null; death=null; generation.incrementAndGet(); notifySnapshot()
                }
            }
        }
        return try {
            binder.linkToDeath(recipient,0); published=binder; death=recipient
            generation.incrementAndGet(); notifySnapshot()
        } catch (_:Throwable) {
            published=null; death=null; generation.incrementAndGet(); notifySnapshot()
        }
    }
    @Synchronized fun unpublish():Snapshot { unpublishLocked(); generation.incrementAndGet(); return notifySnapshot() }
    fun snapshot():Snapshot {
        val b=published; val alive=b?.isBinderAlive==true
        return Snapshot(generation.get(),b?.takeIf{alive},alive)
    }
    fun addListener(l:(Snapshot)->Unit){listeners+=l;l(snapshot())}
    fun removeListener(l:(Snapshot)->Unit){listeners-=l}
    @Synchronized private fun unpublishLocked(){
        val b=published; val d=death; published=null; death=null
        if(b!=null&&d!=null) runCatching{b.unlinkToDeath(d,0)}
    }
    private fun notifySnapshot():Snapshot { val s=snapshot(); listeners.forEach{runCatching{it(s)}}; return s }
}