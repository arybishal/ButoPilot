package com.autopilot.android.engine

import androidx.test.platform.app.InstrumentationRegistry
import com.autopilot.android.data.repository.HistoryRepository
import com.autopilot.android.data.repository.ProfileRepository
import com.autopilot.android.domain.model.AutomationProfile
import com.autopilot.android.domain.model.ExecutionHistoryItem
import com.autopilot.android.domain.model.ExecutionMode
import com.autopilot.android.domain.model.ProfileStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicInteger

class AutomationCoordinatorInstrumentedTest {

    @Test
    fun opensEveryConfiguredTab() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val server = CountingServer()
        val coordinator = AutomationCoordinator(
            context = context,
            profileRepository = FakeProfileRepository(),
            historyRepository = FakeHistoryRepository()
        )
        val requestedTabs = 30
        val profile = AutomationProfile(
            id = "tabs",
            name = "Tabs",
            urls = listOf("http://127.0.0.1:${server.port}/"),
            tabCount = requestedTabs,
            executionMode = ExecutionMode.SIMPLE_SCROLL_REFRESH,
            refreshIntervalSec = 120,
            scrollIntervalSec = 120
        )

        try {
            coordinator.start(profile)
            val deadline = System.currentTimeMillis() + 30_000
            while (server.requestCount.get() < requestedTabs && System.currentTimeMillis() < deadline) {
                kotlinx.coroutines.delay(250)
            }

            assertEquals(requestedTabs, coordinator.engineState.value.totalTabs)
            assertEquals(requestedTabs, coordinator.webViewManager.getAllTabStates().size)
            assertTrue(
                "only ${server.requestCount.get()} of $requestedTabs tabs loaded",
                server.requestCount.get() >= requestedTabs
            )
        } finally {
            coordinator.stop()
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

    private class FakeProfileRepository : ProfileRepository {
        override fun getAllProfilesFlow(): Flow<List<AutomationProfile>> = flowOf(emptyList())
        override suspend fun getAllProfiles(): List<AutomationProfile> = emptyList()
        override suspend fun getProfileById(id: String): AutomationProfile? = null
        override fun getProfileByIdFlow(id: String): Flow<AutomationProfile?> = flowOf(null)
        override suspend fun saveProfile(profile: AutomationProfile) = Unit
        override suspend fun duplicateProfile(profileId: String): AutomationProfile? = null
        override suspend fun deleteProfile(id: String) = Unit
        override suspend fun updateStatus(id: String, status: ProfileStatus) = Unit
    }

    private class FakeHistoryRepository : HistoryRepository {
        override fun getAllHistoryFlow(): Flow<List<ExecutionHistoryItem>> = flowOf(emptyList())
        override fun getHistoryForProfile(profileId: String): Flow<List<ExecutionHistoryItem>> = flowOf(emptyList())
        override suspend fun recordSession(item: ExecutionHistoryItem) = Unit
        override suspend fun clearHistory() = Unit
    }
}
