package moe.shizuku.manager.adb

import java.io.DataInputStream
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class AdbClientTest {
    @Test
    fun `closing and reconnecting releases each successful socket`() {
        exerciseConnections(failFirst = false)
    }

    @Test
    fun `failed handshake closes its socket before a fresh successful attempt`() {
        exerciseConnections(failFirst = true)
    }

    @Test
    fun `malformed packet length is rejected before reading or allocating its body`() {
        for (length in listOf(-1, Int.MAX_VALUE)) {
            ServerSocket(0).use { server ->
                val executor = Executors.newSingleThreadExecutor()
                try {
                    val observed = executor.submit<Int> {
                        server.accept().use { socket ->
                            socket.soTimeout = 5000
                            val input = DataInputStream(socket.getInputStream())
                            val header = ByteArray(24)
                            input.readFully(header)
                            val requestLength = java.nio.ByteBuffer.wrap(header)
                                .order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt(12)
                            input.readFully(ByteArray(requestLength))
                            val response = java.nio.ByteBuffer.allocate(24)
                                .order(java.nio.ByteOrder.LITTLE_ENDIAN)
                                .putInt(AdbProtocol.A_CNXN).putInt(0).putInt(0)
                                .putInt(length).putInt(0).putInt(AdbProtocol.A_CNXN xor -1)
                            socket.getOutputStream().write(response.array())
                            input.read()
                        }
                    }
                    AdbClient("127.0.0.1", server.localPort) { error("No auth expected") }.use {
                        try {
                            it.connect()
                            fail("Invalid packet accepted")
                        } catch (expected: IllegalArgumentException) {
                            // No body sent: reading it would block instead of rejecting the header.
                        }
                    }
                    assertEquals(-1, observed.get(5, TimeUnit.SECONDS))
                } finally {
                    executor.shutdownNow()
                }
            }
        }
    }

    private fun exerciseConnections(failFirst: Boolean) {
        ServerSocket(0).use { server ->
            server.soTimeout = 5000
            val executor = Executors.newSingleThreadExecutor()
            try {
                val observed = executor.submit<List<Int>> {
                    (0..1).map { attempt ->
                        server.accept().use { socket ->
                            socket.soTimeout = 5000
                            val input = DataInputStream(socket.getInputStream())
                            val header = ByteArray(24)
                            input.readFully(header)
                            val length = java.nio.ByteBuffer.wrap(header)
                                .order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt(12)
                            input.readFully(ByteArray(length))
                            val command = if (failFirst && attempt == 0) AdbProtocol.A_CLSE else AdbProtocol.A_CNXN
                            socket.getOutputStream().write(AdbMessage(command, 0, 0, null as ByteArray?).toByteArray())
                            // Closing the actual client socket must unblock this read with EOF.
                            input.read()
                        }
                    }
                }
                val client = AdbClient("127.0.0.1", server.localPort) { error("No auth expected") }
                client.use {
                    if (failFirst) {
                        try {
                            it.connect()
                            fail("Invalid handshake accepted")
                        } catch (expected: IllegalStateException) {
                            // The next connect must succeed without another user tap.
                        }
                    } else {
                        it.connect()
                        it.close()
                    }
                    it.connect()
                }
                assertEquals(listOf(-1, -1), observed.get(10, TimeUnit.SECONDS))
            } finally {
                executor.shutdownNow()
            }
        }
    }
}
