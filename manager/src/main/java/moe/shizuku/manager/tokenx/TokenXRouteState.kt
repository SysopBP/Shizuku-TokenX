package moe.shizuku.manager.tokenx

import java.util.ArrayDeque

data class TokenXRouteEvent(
    val timestampMs: Long,
    val from: TokenXBackend,
    val to: TokenXBackend,
    val message: String,
)

object TokenXRouteState {
    private const val MAX_EVENTS = 20
    @Volatile private var selected: TokenXBackend? = null
    private val events = ArrayDeque<TokenXRouteEvent>()

    fun selected(fallback: TokenXBackend): TokenXBackend = selected ?: fallback

    @Synchronized
    fun select(backend: TokenXBackend, message: String = "Route selected") {
        val previous = selected ?: TokenXBackend.UNAVAILABLE
        selected = backend
        events.addFirst(TokenXRouteEvent(System.currentTimeMillis(), previous, backend, message))
        while (events.size > MAX_EVENTS) events.removeLast()
    }

    @Synchronized
    fun history(): List<TokenXRouteEvent> = events.toList()
}
