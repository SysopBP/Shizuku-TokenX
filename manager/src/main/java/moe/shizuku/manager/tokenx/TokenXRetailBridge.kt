package moe.shizuku.manager.tokenx

import com.topjohnwu.superuser.Shell
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

enum class TokenXRetailState {
    NOT_INSTALLED,
    READY,
    CONNECTING,
    UID1000_VERIFIED,
    ERROR,
}

data class TokenXRetailSnapshot(
    val state: TokenXRetailState,
    val installed: Boolean,
    val connected: Boolean,
    val uid: Int?,
    val detail: String,
)

object TokenXRetailBridge {
    const val PACKAGE_NAME = "com.samsung.sea.rm"
    const val ACTION_COMMAND = "com.samsung.sea.rm.ACTION_COMMAND"
    private const val PORT = 9999
    private const val ACCEPT_TIMEOUT_MS = 8000
    private const val READ_TIMEOUT_MS = 4000
    private const val COMMAND_PAYLOAD = "MwUr0y8/QCW6mZN9TKTGfM2w1FwSGvhvquwc6nAAuokp6agtY05P2hvNfmbk5CDR6QcnRrOf6lSk9dDrLn7S05Ki0SNw4JtQxKNUkfQXVQRncYEYXnaL01XBo/vVZnkJ"

    @Volatile private var socket: Socket? = null
    @Volatile private var state = TokenXRetailState.READY
    @Volatile private var detail = "Ready • listener idle"
    private val connecting = AtomicBoolean(false)

    fun snapshot(installed: Boolean): TokenXRetailSnapshot {
        if (!installed) return TokenXRetailSnapshot(
            TokenXRetailState.NOT_INSTALLED, false, false, null, "Samsung Retail Mode not installed"
        )
        val live = socket?.let { it.isConnected && !it.isClosed } == true
        val current = if (live && state == TokenXRetailState.UID1000_VERIFIED) state
            else if (state == TokenXRetailState.UID1000_VERIFIED) TokenXRetailState.READY else state
        return TokenXRetailSnapshot(current, true, live, if (live) 1000 else null, if (live) detail else when (current) {
            TokenXRetailState.READY -> "Ready • UID 1000 session not connected"
            else -> detail
        })
    }

    @Synchronized
    fun disconnect() {
        runCatching { socket?.close() }
        socket = null
        connecting.set(false)
        state = TokenXRetailState.READY
        detail = "Ready • disconnected"
    }

    /**
     * Starts the listener before ACTION_COMMAND to avoid the race in the original
     * launcher. The session is accepted only after a harmless identity probe proves
     * that the peer is the Retail Mode UID-1000 shell.
     */
    fun connect(onComplete: (TokenXRetailSnapshot) -> Unit) {
        if (!connecting.compareAndSet(false, true)) {
            onComplete(snapshot(true))
            return
        }
        state = TokenXRetailState.CONNECTING
        detail = "Connecting • preparing local port $PORT"

        Thread({
            var server: ServerSocket? = null
            try {
                disconnectSocketOnly()
                server = ServerSocket(PORT, 1, InetAddress.getLoopbackAddress()).apply {
                    soTimeout = ACCEPT_TIMEOUT_MS
                    reuseAddress = true
                }
                detail = "Connecting • listener ready; requesting Retail session"

                val escaped = COMMAND_PAYLOAD.replace("'", "'\\''")
                val broadcast = Shell.cmd(
                    "am broadcast -a $ACTION_COMMAND --es cmd '$escaped'"
                ).exec()
                if (!broadcast.isSuccess) error("ACTION_COMMAND broadcast failed")

                val candidate = server.accept().apply { soTimeout = READ_TIMEOUT_MS }
                val writer = BufferedWriter(OutputStreamWriter(candidate.getOutputStream()))
                val reader = BufferedReader(InputStreamReader(candidate.getInputStream()))
                writer.write("id\\n")
                writer.flush()

                val identity = buildString {
                    val deadline = System.currentTimeMillis() + READ_TIMEOUT_MS
                    while (System.currentTimeMillis() < deadline) {
                        if (reader.ready()) {
                            val line = reader.readLine() ?: break
                            append(line).append('\\n')
                            if (line.contains("uid=")) break
                        } else Thread.sleep(40)
                    }
                }.trim()

                if (!identity.contains("uid=1000")) {
                    candidate.close()
                    error("Retail peer identity was not UID 1000: ${identity.ifBlank { "no identity response" }}")
                }

                socket = candidate
                state = TokenXRetailState.UID1000_VERIFIED
                detail = "UID 1000 ✓ • Retail System socket verified"
            } catch (t: Throwable) {
                disconnectSocketOnly()
                state = TokenXRetailState.ERROR
                detail = "Retail connection failed • ${t.message ?: t.javaClass.simpleName}"
            } finally {
                runCatching { server?.close() }
                connecting.set(false)
                onComplete(snapshot(true))
            }
        }, "TokenX-RetailSystem").apply {
            isDaemon = true
            start()
        }
    }

    @Synchronized
    fun execute(command: String): Result<String> = runCatching {
        check(state == TokenXRetailState.UID1000_VERIFIED) { "Retail UID 1000 session is not verified" }
        val active = socket ?: error("Retail socket unavailable")
        check(active.isConnected && !active.isClosed) { "Retail socket disconnected" }

        val writer = BufferedWriter(OutputStreamWriter(active.getOutputStream()))
        val reader = BufferedReader(InputStreamReader(active.getInputStream()))
        val marker = "__TOKENX_RETAIL_DONE_${System.nanoTime()}__"
        writer.write(command)
        writer.write("\\nprintf '$marker:%s\\\\n' \"$?\"\\n")
        writer.flush()

        buildString {
            val deadline = System.currentTimeMillis() + 10000
            while (System.currentTimeMillis() < deadline) {
                if (!reader.ready()) {
                    Thread.sleep(30)
                    continue
                }
                val line = reader.readLine() ?: break
                if (line.startsWith(marker)) break
                append(line).append('\\n')
            }
        }.trimEnd()
    }

    private fun disconnectSocketOnly() {
        runCatching { socket?.close() }
        socket = null
    }
}
