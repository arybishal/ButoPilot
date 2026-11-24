package com.autopilot.android.feature.traffic

import androidx.test.platform.app.InstrumentationRegistry
import com.autopilot.android.data.repository.TrafficRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket

class RealBrowserTrafficInstrumentedTest {

    @Test
    fun realBrowser_executesJavaScriptBeaconAndCountsVisit() = runBlocking {
        val server = BeaconServer()
        try {
            val repository = FakeTrafficRepository(
                TrafficRun(
                    id = "browser-run",
                    websiteUrl = "http://127.0.0.1:${server.port}/",
                    plannedEvents = 1,
                    mode = TrafficMode.REAL_BROWSER,
                    speed = TrafficSpeed.SLOW,
                    concurrency = 1,
                    pagePaths = emptyList(),
                    status = TrafficStatus.QUEUED
                )
            )
            val context = InstrumentationRegistry.getInstrumentation().targetContext

            withTimeout(30_000) {
                TrafficExecutor(repository, context = context).execute("browser-run", 0)
            }

            val result = repository.getRun("browser-run")!!
            assertEquals(1, result.submitted)
            assertEquals(0, result.failed)
            assertTrue("analytics beacon never fired", server.beaconCount > 0)
        } finally {
            server.close()
        }
    }

    private class BeaconServer {
        private val socket = ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))
        val port: Int = socket.localPort

        @Volatile
        var beaconCount: Int = 0
            private set

        private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        init {
            scope.launch {
                while (!socket.isClosed) {
                    try {
                        val client = socket.accept()
                        launch {
                            client.use { connection ->
                                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                                val requestLine = reader.readLine().orEmpty()
                                if (requestLine.startsWith("GET /beacon")) beaconCount++
                                val body = if (requestLine.startsWith("GET / ")) PAGE else ""
                                val bytes = body.toByteArray()
                                connection.getOutputStream().use { out ->
                                    out.write("HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                                    out.write(bytes)
                                    out.flush()
                                }
                            }
                        }
                    } catch (_: Exception) {
                        break
                    }
                }
            }
        }

        fun close() {
            socket.close()
            scope.cancel()
        }

        private companion object {
            const val PAGE = "<html><body><h1>traffic test</h1><script>" +
                "fetch('/beacon?t=' + Date.now(), {mode: 'no-cors'});" +
                "</script></body></html>"
        }
    }

    private class FakeTrafficRepository(initial: TrafficRun) : TrafficRepository {
        private val state = MutableStateFlow(initial)
        private val lock = Mutex()

        override fun observeRuns(): Flow<List<TrafficRun>> =
            kotlinx.coroutines.flow.flowOf(listOf(state.value))

        override suspend fun getRun(id: String): TrafficRun? = state.value.takeIf { it.id == id }
        override suspend fun getActiveRuns(): List<TrafficRun> = listOf(state.value)
        override suspend fun createRun(run: TrafficRun) = lock.withLock { state.value = run }
        override suspend fun updateProgress(run: TrafficRun) = lock.withLock { state.value = run }
        override suspend fun setStatus(id: String, status: TrafficStatus, markCompleted: Boolean) {
            lock.withLock { state.value = state.value.copy(status = status) }
        }
        override suspend fun deleteRun(id: String) = Unit
    }
}
