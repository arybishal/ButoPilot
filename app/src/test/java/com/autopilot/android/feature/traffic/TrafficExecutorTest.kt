package com.autopilot.android.feature.traffic

import com.autopilot.android.data.repository.TrafficRepository
import com.autopilot.android.domain.model.DeviceCatalog
import com.autopilot.android.domain.model.DeviceCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class TrafficExecutorTest {

    @Test
    fun executor_countsActualResponsesAndCompletes() = runBlocking {
        var calls = 0
        val client = fakeClient { 200 }
        val repository = FakeTrafficRepository(newRun(planned = 3))

        TrafficExecutor(repository, client = client).execute("run", 0)

        val result = repository.getRun("run")!!
        assertEquals(3, result.submitted)
        assertEquals(0, result.failed)
        assertEquals(TrafficStatus.COMPLETED, result.status)
        assertNotNull(result.startedAt)
        assertNotNull(result.completedAt)
    }

    @Test
    fun executor_retriesTransientFailuresOnlyOncePerEvent() = runBlocking {
        var calls = 0
        val client = fakeClient {
            calls++
            if (calls <= 2) 500 else 200
        }
        val repository = FakeTrafficRepository(newRun(planned = 1))

        TrafficExecutor(repository, client = client).execute("run", 0)

        val result = repository.getRun("run")!!
        assertEquals(3, calls)
        assertEquals(1, result.submitted)
        assertEquals(0, result.failed)
        assertEquals(TrafficStatus.COMPLETED, result.status)
    }

    @Test
    fun executor_doesNotRetryForbiddenResponses() = runBlocking {
        var calls = 0
        val client = fakeClient {
            calls++
            403
        }
        val repository = FakeTrafficRepository(newRun(planned = 1))

        TrafficExecutor(repository, client = client).execute("run", 0)

        val result = repository.getRun("run")!!
        assertEquals(1, calls)
        assertEquals(0, result.submitted)
        assertEquals(1, result.failed)
        assertEquals(TrafficStatus.FAILED, result.status)
        assertTrue(result.lastError!!.contains("403"))
    }

    @Test
    fun executor_fallsBackToDirectWhenNoHealthyProxyAvailable() = runBlocking {
        val client = fakeClient { 200 }
        val repository = FakeTrafficRepository(newRun(planned = 2))

        TrafficExecutor(repository, client = client).execute("run", 0)

        val result = repository.getRun("run")!!
        assertEquals(2, result.submitted)
        assertEquals(0, result.failed)
        assertEquals(TrafficStatus.COMPLETED, result.status)
    }

    @Test
    fun executor_organicSearchModeSendsSearchReferrer() = runBlocking {
        var capturedReferer: String? = null
        val interceptor = Interceptor { chain ->
            capturedReferer = chain.request().header("Referer")
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("ok")
                .body("".toResponseBody("text/plain".toMediaType()))
                .build()
        }
        val client = OkHttpClient.Builder().addInterceptor(interceptor).build()
        val repository = FakeTrafficRepository(
            newRun(planned = 1).copy(mode = TrafficMode.ORGANIC_SEARCH)
        )

        TrafficExecutor(repository, client = client).execute("run", 0)

        val result = repository.getRun("run")!!
        assertEquals(1, result.submitted)
        assertNotNull(capturedReferer)
        assertTrue(
            capturedReferer!!.contains("google.com/search") ||
                capturedReferer!!.contains("bing.com/search") ||
                capturedReferer!!.contains("duckduckgo.com/")
        )
    }

    @Test
    fun executor_usesRandomDeviceFromSelectedCategoryForEveryRequest() = runBlocking {
        val userAgents = mutableListOf<String>()
        val client = fakeClient(
            onRequest = { request -> userAgents.add(request.header("User-Agent").orEmpty()) },
            code = { 200 }
        )
        val repository = FakeTrafficRepository(
            newRun(planned = 12).copy(deviceCategory = DeviceCategory.MACBOOK_SILICON)
        )

        TrafficExecutor(repository, client = client).execute("run", 0)

        assertEquals(12, userAgents.size)
        assertTrue(userAgents.all { it.isNotBlank() })
        assertTrue(userAgents.all { ua -> DeviceCatalog.forCategory(DeviceCategory.MACBOOK_SILICON).any { it.userAgent == ua } })
        assertTrue(userAgents.toSet().size > 1)
    }

    private fun newRun(planned: Int) = TrafficRun(
        id = "run",
        websiteUrl = "https://example.com",
        plannedEvents = planned,
        mode = TrafficMode.PAGE_REQUEST,
        speed = TrafficSpeed.FAST,
        concurrency = 1,
        pagePaths = emptyList(),
        status = TrafficStatus.QUEUED
    )

    private fun fakeClient(onRequest: (Request) -> Unit = {}, code: () -> Int): OkHttpClient {
        val interceptor = Interceptor { chain: Interceptor.Chain ->
            onRequest(chain.request())
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(code())
                .message("test")
                .body("".toResponseBody("text/plain".toMediaType()))
                .build()
        }
        return OkHttpClient.Builder().addInterceptor(interceptor).build()
    }

    private class FakeTrafficRepository(initial: TrafficRun) : TrafficRepository {
        private var current = initial
        private val lock = Mutex()

        override fun observeRuns(): Flow<List<TrafficRun>> = flowOf(listOf(current))
        override suspend fun getRun(id: String): TrafficRun? = lock.withLock { current.takeIf { it.id == id } }
        override suspend fun getActiveRuns(): List<TrafficRun> = listOf(current)
        override suspend fun createRun(run: TrafficRun) = lock.withLock { current = run }
        override suspend fun updateProgress(run: TrafficRun) = lock.withLock { current = run }
        override suspend fun setStatus(id: String, status: TrafficStatus, markCompleted: Boolean) {
            lock.withLock { current = current.copy(status = status) }
        }
        override suspend fun deleteRun(id: String) = Unit
    }
}
