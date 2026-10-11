package moe.shizuku.manager.tokenx.transport

import android.os.SystemClock
import moe.shizuku.manager.ShizukuManagerProvider
import moe.shizuku.manager.tokenx.TokenXBackend
import moe.shizuku.manager.tokenx.TokenXSessionRegistry
import java.util.concurrent.CopyOnWriteArrayList

/**
 * TokenX-aware health supervisor.
 *
 * This deliberately does not own backend lifecycle. Root/Shell recovery remains with the
 * existing start machinery and SYSTEM is never killed/restarted here. The supervisor provides
 * one authoritative health snapshot for the watchdog and UI and exercises the real TokenX
 * HELLO transaction instead of treating isBinderAlive as a heartbeat.
 */
object TokenXWatchdog {

    enum class State { ONLINE, STALE, OFFLINE }

    data class BackendHealth(
        val state: State,
        val uid: Int,
        val clients: Int,
        val binderAlive: Boolean,
    )

    data class Snapshot(
        val checkedAtElapsedMs: Long,
        val lastHealthyElapsedMs: Long,
        val generation: Long,
        val protocolVersion: Int,
        val transport: State,
        val root: BackendHealth,
        val system: BackendHealth,
        val shell: BackendHealth,
        val consecutiveFailures: Int,
    )

    private val listeners = CopyOnWriteArrayList<(Snapshot) -> Unit>()

    @Volatile private var failures = 0
    @Volatile private var lastHealthy = 0L
    @Volatile private var current = emptySnapshot()

    fun snapshot(): Snapshot = current

    fun addListener(listener: (Snapshot) -> Unit) {
        listeners += listener
        listener(current)
    }

    fun removeListener(listener: (Snapshot) -> Unit) {
        listeners -= listener
    }

    @Synchronized
    fun probe(): Snapshot {
        val now = SystemClock.elapsedRealtime()
        val rendezvous = TokenXRendezvous.snapshot()
        val transport = rendezvous.binder?.takeIf { it.isBinderAlive }
        val hello = transport?.let { runCatching { TokenXBinderClient(it).hello() }.getOrNull() }
        val transportHealthy = hello == TokenXBinderProtocol.VERSION

        if (transportHealthy) {
            failures = 0
            lastHealthy = now
        } else {
            failures++
        }

        val transportState = when {
            transportHealthy -> State.ONLINE
            failures < STALE_AFTER_FAILURES && lastHealthy != 0L -> State.STALE
            else -> State.OFFLINE
        }

        val sessions = TokenXSessionRegistry.snapshot()
        fun backend(backend: TokenXBackend, uid: Int, binderAlive: Boolean) =
            BackendHealth(
                state = if (binderAlive) State.ONLINE else State.OFFLINE,
                uid = uid,
                clients = sessions.count { it.backend == backend },
                binderAlive = binderAlive,
            )

        current = Snapshot(
            checkedAtElapsedMs = now,
            lastHealthyElapsedMs = lastHealthy,
            generation = rendezvous.generation,
            protocolVersion = hello ?: -1,
            transport = transportState,
            root = backend(TokenXBackend.ROOT, 0, ShizukuManagerProvider.rootBinder()?.pingBinder() == true),
            system = backend(TokenXBackend.SYSTEM_UID, 1000, ShizukuManagerProvider.systemBinder()?.pingBinder() == true),
            shell = backend(TokenXBackend.SHELL, 2000, ShizukuManagerProvider.shellBinder()?.pingBinder() == true),
            consecutiveFailures = failures,
        )
        listeners.forEach { runCatching { it(current) } }
        return current
    }

    private fun emptySnapshot() = Snapshot(
        checkedAtElapsedMs = 0L,
        lastHealthyElapsedMs = 0L,
        generation = 0L,
        protocolVersion = -1,
        transport = State.OFFLINE,
        root = BackendHealth(State.OFFLINE, 0, 0, false),
        system = BackendHealth(State.OFFLINE, 1000, 0, false),
        shell = BackendHealth(State.OFFLINE, 2000, 0, false),
        consecutiveFailures = 0,
    )

    private const val STALE_AFTER_FAILURES = 2
}
