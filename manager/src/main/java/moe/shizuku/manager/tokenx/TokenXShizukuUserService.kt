package moe.shizuku.manager.tokenx

import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.Process

/**
 * Minimal TokenX Shizuku UserService proof.
 *
 * Shizuku instantiates this class in its privileged user-service process.
 * Keep this Binder intentionally small: it proves the app can bind to code
 * running with the Shizuku backend identity without changing TokenX routing.
 */
class TokenXShizukuUserService : Binder() {

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        return when (code) {
            TRANSACTION_GET_IDENTITY -> {
                reply?.writeNoException()
                reply?.writeInt(Process.myPid())
                reply?.writeInt(Process.myUid())
                reply?.writeString(readSelinuxContext())
                true
            }
            else -> super.onTransact(code, data, reply, flags)
        }
    }

    private fun readSelinuxContext(): String = runCatching {
        java.io.File("/proc/self/attr/current").readText().trim()
    }.getOrDefault("unknown")

    companion object {
        const val TRANSACTION_GET_IDENTITY = IBinder.FIRST_CALL_TRANSACTION
    }
}
