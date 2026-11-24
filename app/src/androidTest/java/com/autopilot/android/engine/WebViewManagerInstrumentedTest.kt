package com.autopilot.android.engine

import androidx.test.platform.app.InstrumentationRegistry
import com.autopilot.android.domain.model.AutomationProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicInteger

class WebViewManagerInstrumentedTest {

    @Test
    fun createsEveryRequestedTab() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = WebViewManager(
            context = context,
            onProgressUpdate = { _, _ -> },
            onNavigationStateChange = { _, _, _, _ -> },
            onErrorEncountered = { _, _, _, _ -> }
        )
        val profile = AutomationProfile(
            id = "tabs",
            name = "Tabs",
            urls = listOf("about:blank"),
            tabCount = 25
        )

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            repeat(25) { index -> manager.createOrGetTab(index, profile) }
        }

        assertEquals(25, manager.getAllTabStates().size)

        InstrumentationRegistry.getInstrumentation().runOnMainSync { manager.destroyAll() }
    }

    @Test
    fun loadsEveryRequestedTab() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val server = CountingServer()
        val manager = WebViewManager(
            context = context,
            onProgressUpdate = { _, _ -> },
            onNavigationStateChange = { _, _, _, _ -> },
            onErrorEncountered = { _, _, _, _ -> }
        )
        val requestedTabs = 30
        val profile = AutomationProfile(
            id = "tabs",
            name = "Tabs",
            urls = listOf("about:blank"),
            tabCount = requestedTabs
        )

        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                repeat(requestedTabs) { index ->
                    manager.createOrGetTab(index, profile)
                    manager.loadUrl(index, "http://127.0.0.1:${server.port}/tab/$index")
                }
            }

            val deadline = System.currentTimeMillis() + 30_000
            while (server.requestCount.get() < requestedTabs && System.currentTimeMillis() < deadline) {
                Thread.sleep(250)
            }

            assertTrue(
                "only ${server.requestCount.get()} of $requestedTabs tabs loaded",
                server.requestCount.get() >= requestedTabs
            )
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { manager.destroyAll() }
            server.close()
        }
    }

    private class CountingServer {
        private val socket = ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))
        val port: Int = socket.localPort
        val requestCount = AtomicInteger()

        private val thread = Thread {
            while (!socket.isClosed) {
                try {
                    socket.accept().use { connection ->
                        BufferedReader(InputStreamReader(connection.inputStream)).readLine()
                        requestCount.incrementAndGet()
                        val bytes = "<html><body>ok</body></html>".toByteArray()
                        connection.getOutputStream().use { out ->
                            out.write("HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                            out.write(bytes)
                            out.flush()
                        }
                    }
                } catch (_: Exception) {
                    break
                }
            }
        }

        init {
            thread.isDaemon = true
            thread.start()
        }

        fun close() {
            socket.close()
        }
    }
}
