package com.rerun.tv.data

import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PlexApiTest {
    @Test
    fun completedResponsesAreReturnedAndHttpErrorsAreReported() = runBlocking {
        StalledServer(sendHeaders = true, completeBody = true).use { server ->
            val api = PlexApi({ "test-client" }, OkHttpClient())
            assertEquals("{}", api.get(server.base, "/metadata", "test-only-placeholder"))
        }
        StalledServer(sendHeaders = true, completeBody = true, status = 503).use { server ->
            val api = PlexApi({ "test-client" }, OkHttpClient())
            try {
                api.get(server.base, "/metadata", "test-only-placeholder")
                fail("HTTP errors must be reported")
            } catch (e: IOException) {
                assertEquals("HTTP 503 for /metadata", e.message)
            }
        }
    }

    @Test
    fun coroutineTimeoutClosesAStalledConnection() = runBlocking {
        StalledServer(sendHeaders = false).use { server ->
            val api = PlexApi({ "test-client" }, OkHttpClient())
            val result = withTimeoutOrNull(500) { api.get(server.base, "/metadata", "test-only-placeholder") }
            assertNull(result)
            assertTrue(server.disconnected.await(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun cancellationClosesAStalledResponseBody() = runBlocking {
        StalledServer(sendHeaders = true).use { server ->
            val api = PlexApi({ "test-client" }, OkHttpClient())
            val request = async { api.get(server.base, "/metadata", "test-only-placeholder") }
            withContext(Dispatchers.IO) { assertTrue(server.ready.await(5, TimeUnit.SECONDS)) }
            request.cancelAndJoin()
            assertTrue(server.disconnected.await(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun httpDeadlineClosesAStalledBodyWithoutCoroutineTimeout() = runBlocking {
        StalledServer(sendHeaders = true).use { server ->
            val api = PlexApi({ "test-client" }, OkHttpClient())
            try {
                api.getWithTimeout(server.base, "/metadata", "test-only-placeholder", 500)
                fail("The HTTP deadline must fail the stalled request")
            } catch (_: IOException) {
                assertTrue(server.disconnected.await(5, TimeUnit.SECONDS))
            }
        }
    }

    private class StalledServer(sendHeaders: Boolean, completeBody: Boolean = false, status: Int = 200) : AutoCloseable {
        private val listener = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        @Volatile private var socket: Socket? = null
        val base = "http://127.0.0.1:${listener.localPort}"
        val ready = CountDownLatch(1)
        val disconnected = CountDownLatch(1)
        private val thread = Thread {
            try {
                listener.accept().use { connection ->
                    socket = connection
                    val reader = connection.getInputStream().bufferedReader()
                    while (!reader.readLine().isNullOrEmpty()) { /* Consume request headers. */ }
                    if (sendHeaders) {
                        connection.getOutputStream().apply {
                            val body = if (completeBody) "{}" else "x"
                            val length = if (completeBody) body.length else 100
                            write("HTTP/1.1 $status Response\r\nContent-Length: $length\r\n\r\n$body".toByteArray())
                            flush()
                        }
                    }
                    ready.countDown()
                    if (reader.read() == -1) disconnected.countDown()
                }
            } catch (e: IOException) {
                if (!listener.isClosed) throw e
            }
        }.apply { isDaemon = true; start() }

        override fun close() {
            listener.close()
            socket?.close()
            thread.join(5_000)
            check(!thread.isAlive) { "Test server did not shut down" }
        }
    }
}
