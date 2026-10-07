package moe.shizuku.manager.tokenx

import android.os.IBinder
import android.os.SystemClock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Live TokenX client sessions.
 *
 * The client-owned Binder is the lifetime token. No persistent/reusable secret is
 * stored here: when the client process dies Binder death removes the session.
 */
data class TokenXSession(
    val id: Long,
    val packageName: String,
    val backend: TokenXBackend,
    val backendUid: Int?,
    val createdAtElapsedMs: Long,
)

object TokenXSessionRegistry {
    private data class Entry(
        val session: TokenXSession,
        val client: IBinder,
        val deathRecipient: IBinder.DeathRecipient,
    )

    private val nextId = AtomicLong(1)
    private val sessions = ConcurrentHashMap<IBinder, Entry>()

    fun register(client: IBinder, packageName: String, backend: TokenXBackend): TokenXSession? {
        remove(client)

        val session = TokenXSession(
            id = nextId.getAndIncrement(),
            packageName = packageName,
            backend = backend,
            backendUid = backend.uid,
            createdAtElapsedMs = SystemClock.elapsedRealtime(),
        )
        val deathRecipient = IBinder.DeathRecipient { remove(client, unlink = false) }

        return try {
            client.linkToDeath(deathRecipient, 0)
            sessions[client] = Entry(session, client, deathRecipient)
            // Close the race where the client dies between linkToDeath and publication.
            if (!client.isBinderAlive) {
                remove(client)
                null
            } else {
                session
            }
        } catch (_: Throwable) {
            null
        }
    }

    fun remove(client: IBinder, unlink: Boolean = true): TokenXSession? {
        val entry = sessions.remove(client) ?: return null
        if (unlink) runCatching { entry.client.unlinkToDeath(entry.deathRecipient, 0) }
        return entry.session
    }

    fun snapshot(): List<TokenXSession> =
        sessions.values.map { it.session }.sortedBy { it.id }

    fun count(backend: TokenXBackend): Int =
        sessions.values.count { it.session.backend == backend }

    fun hasSystemSession(): Boolean =
        sessions.values.any {
            it.session.backend == TokenXBackend.SYSTEM_UID ||
                it.session.backend == TokenXBackend.SYSTEM_SERVER ||
                it.session.backend == TokenXBackend.NATIVE_UID
        }
}
