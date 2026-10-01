package moe.shizuku.manager.tokenx

import android.os.SystemClock
import java.util.UUID

/**
 * Coordinates ownership of one TokenX startup generation.
 *
 * This is intentionally engine-agnostic: receivers/bridges can claim a session,
 * but the existing Shizuku startup path remains untouched until each producer
 * is wired and tested.
 */
enum class TokenXBootState {
    NEW, CLAIMED, SERVER_STARTING, BINDER_READY, CONFIRMED, FAILED, RECOVERY_CLAIMED
}

enum class TokenXBootOwner {
    SYSTEM_SERVER, ROOT, SHELL, NONE
}

data class TokenXBootSnapshot(
    val token: String,
    val generation: Long,
    val state: TokenXBootState,
    val owner: TokenXBootOwner,
    val createdElapsedMs: Long,
    val updatedElapsedMs: Long,
    val failure: String? = null,
)

object TokenXBootSession {
    private val lock = Any()
    private var generation = 0L
    private var snapshot = fresh()

    fun current(): TokenXBootSnapshot = synchronized(lock) { snapshot }

    fun newGeneration(): TokenXBootSnapshot = synchronized(lock) {
        generation += 1
        snapshot = fresh()
        snapshot
    }

    fun claim(owner: TokenXBootOwner): Boolean = synchronized(lock) {
        if (owner == TokenXBootOwner.NONE || snapshot.state != TokenXBootState.NEW) return false
        snapshot = snapshot.copy(
            state = TokenXBootState.CLAIMED,
            owner = owner,
            updatedElapsedMs = SystemClock.elapsedRealtime(),
        )
        true
    }

    fun markServerStarting(owner: TokenXBootOwner): Boolean =
        transition(owner, setOf(TokenXBootState.CLAIMED), TokenXBootState.SERVER_STARTING)

    fun markBinderReady(owner: TokenXBootOwner): Boolean =
        transition(owner, setOf(TokenXBootState.SERVER_STARTING), TokenXBootState.BINDER_READY)

    fun confirm(owner: TokenXBootOwner): Boolean =
        transition(owner, setOf(TokenXBootState.BINDER_READY), TokenXBootState.CONFIRMED)

    fun fail(owner: TokenXBootOwner, reason: String): Boolean = synchronized(lock) {
        if (snapshot.owner != owner || snapshot.state == TokenXBootState.CONFIRMED) return false
        snapshot = snapshot.copy(
            state = TokenXBootState.FAILED,
            updatedElapsedMs = SystemClock.elapsedRealtime(),
            failure = reason.take(160),
        )
        true
    }

    fun claimRecovery(owner: TokenXBootOwner): Boolean = synchronized(lock) {
        if (owner == TokenXBootOwner.NONE || snapshot.state != TokenXBootState.FAILED) return false
        snapshot = snapshot.copy(
            state = TokenXBootState.RECOVERY_CLAIMED,
            owner = owner,
            updatedElapsedMs = SystemClock.elapsedRealtime(),
        )
        true
    }

    fun confirmRecovery(owner: TokenXBootOwner): Boolean =
        transition(owner, setOf(TokenXBootState.RECOVERY_CLAIMED), TokenXBootState.CONFIRMED)

    private fun transition(
        owner: TokenXBootOwner,
        allowed: Set<TokenXBootState>,
        target: TokenXBootState,
    ): Boolean = synchronized(lock) {
        if (snapshot.owner != owner || snapshot.state !in allowed) return false
        snapshot = snapshot.copy(state = target, updatedElapsedMs = SystemClock.elapsedRealtime())
        true
    }

    private fun fresh(): TokenXBootSnapshot {
        val now = SystemClock.elapsedRealtime()
        return TokenXBootSnapshot(
            token = UUID.randomUUID().toString(),
            generation = generation,
            state = TokenXBootState.NEW,
            owner = TokenXBootOwner.NONE,
            createdElapsedMs = now,
            updatedElapsedMs = now,
        )
    }
}
