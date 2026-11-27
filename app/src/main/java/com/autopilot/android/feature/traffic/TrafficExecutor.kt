package com.autopilot.android.feature.traffic

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.autopilot.android.data.repository.ProxyRepository
import com.autopilot.android.data.repository.TrafficRepository
import com.autopilot.android.domain.model.proxy.ProxyConfiguration
import com.autopilot.android.domain.model.proxy.ProxyHealthStatus
import com.autopilot.android.domain.model.proxy.ProxyProtocol
import com.autopilot.android.feature.proxy.ProxyExecutionCoordinator
import com.autopilot.android.feature.proxy.ProxyHealthChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import java.io.IOException
import java.net.InetSocketAddress
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.random.Random

/** Spaces request starts so the configured events-per-minute cap is never exceeded. */
class RateLimiter(private val eventsPerMinute: Int) {
    private val mutex = Mutex()
    private var nextSlotAt = 0L

    suspend fun acquire() = mutex.withLock {
        val now = System.currentTimeMillis()
        val waitMs = delayForSlot(now, nextSlotAt, eventsPerMinute)
        nextSlotAt = max(now, nextSlotAt) + intervalMs(eventsPerMinute)
        if (waitMs > 0) kotlinx.coroutines.delay(waitMs)
    }
}

internal fun intervalMs(eventsPerMinute: Int): Long = 60_000L / eventsPerMinute.coerceAtLeast(1)

internal fun delayForSlot(now: Long, nextSlotAt: Long, eventsPerMinute: Int): Long =
    (max(now, nextSlotAt) - now).coerceAtLeast(0)

private data class Attempt(val success: Boolean, val error: String? = null, val transient: Boolean = false)

/**
 * Executes high-volume simulated visitors and real browser sessions.
 * Supports authentic browser headers, User-Agent rotation, Google search referrers,
 * and WebView execution for full JavaScript and Google Analytics / tracker trigger.
 */
class TrafficExecutor(
    private val repository: TrafficRepository,
    private val context: Context? = null,
    private val client: OkHttpClient = createClient(),
    private val proxyRepository: ProxyRepository? = null,
    private val proxyHealthChecker: ProxyHealthChecker? = null,
    private val proxyExecutionCoordinator: ProxyExecutionCoordinator? = null
) {

    suspend fun execute(runId: String, workAttempt: Int) = withContext(Dispatchers.IO) {
        val run = repository.getRun(runId) ?: return@withContext
        if (run.status !in setOf(
                TrafficStatus.QUEUED, TrafficStatus.RUNNING,
                TrafficStatus.INTERRUPTED
            )
        ) return@withContext

        // Transition to RUNNING immediately so UI never appears stuck on QUEUED at 0.
        repository.updateProgress(
            run.copy(
                status = TrafficStatus.RUNNING,
                startedAt = run.startedAt ?: System.currentTimeMillis(),
                lastError = "Connecting..."
            )
        )

        val repo = proxyRepository
        val checker = proxyHealthChecker
        val coordinator = proxyExecutionCoordinator
        if (repo == null || checker == null || coordinator == null) {
            executeRun(runId, workAttempt, client)
            return@withContext
        }

        coordinator.activeProxy.value?.let { active ->
            executeRun(runId, workAttempt, clientFor(active))
            return@withContext
        }

        // Fast parallel probe (max 3 candidates, 2s timeout) prioritizing already-healthy proxies.
        val candidates = repo.getAllProxies()
            .filter { it.enabled }
            .sortedBy {
                when (it.lastHealthStatus) {
                    ProxyHealthStatus.HEALTHY -> 0
                    ProxyHealthStatus.UNTESTED -> 1
                    else -> 2
                }
            }
            .take(3)

        val proxy: ProxyConfiguration? = if (candidates.isEmpty()) {
            null
        } else {
            coroutineScope {
                candidates.map { candidate ->
                    async {
                        val result = try {
                            withTimeoutOrNull(2000L) { checker.checkHealth(candidate) }
                        } catch (_: Exception) {
                            null
                        }
                        repo.updateHealthStatus(
                            candidate.id,
                            result?.status ?: ProxyHealthStatus.TIMEOUT,
                            result?.latencyMs,
                            result?.errorMessage ?: "Proxy verification timed out"
                        )
                        if (result?.status == ProxyHealthStatus.HEALTHY) candidate else null
                    }
                }.awaitAll().firstOrNull { it != null }
            }
        }

        if (proxy == null) {
            // Fall back to direct connection so Traffic Generator still runs when free proxies are offline.
            executeRun(
                runId = runId,
                workAttempt = workAttempt,
                client = client,
                initialNote = "No verified proxy available; running with direct connection"
            )
            return@withContext
        }

        if (run.mode == TrafficMode.PAGE_REQUEST) {
            // OkHttp uses clientFor(proxy) directly without needing WebView ProxyController.
            executeRun(runId, workAttempt, clientFor(proxy))
            return@withContext
        }

        coordinator.acquireAndApply(proxy)
        if (!coordinator.isProxyActive.value) {
            coordinator.resetAndRelease()
            // WebView proxy override unsupported on this device; still route HTTP requests via proxy client.
            executeRun(
                runId = runId,
                workAttempt = workAttempt,
                client = clientFor(proxy),
                initialNote = "WebView proxy override unavailable; using HTTP proxy engine"
            )
            return@withContext
        }
        try {
            executeRun(runId, workAttempt, clientFor(proxy))
        } finally {
            coordinator.resetAndRelease()
        }
    }

    private suspend fun failRun(runId: String, error: String) {
        repository.getRun(runId)?.let {
            repository.updateProgress(
                it.copy(
                    status = TrafficStatus.FAILED,
                    lastError = error,
                    completedAt = System.currentTimeMillis()
                )
            )
        }
    }

    private fun clientFor(proxy: ProxyConfiguration): OkHttpClient {
        val type = if (proxy.protocol == ProxyProtocol.SOCKS5) java.net.Proxy.Type.SOCKS else java.net.Proxy.Type.HTTP
        val builder = client.newBuilder()
            .proxy(java.net.Proxy(type, InetSocketAddress(proxy.host, proxy.port)))
        val username = proxy.credentials?.username
        val password = proxy.credentials?.password
        if (!username.isNullOrBlank() && !password.isNullOrBlank()) {
            builder.proxyAuthenticator(object : Authenticator {
                override fun authenticate(route: Route?, response: Response): Request? =
                    response.request.newBuilder()
                        .header("Proxy-Authorization", Credentials.basic(username, password))
                        .build()
            })
        }
        return builder.build()
    }

    private suspend fun executeRun(
        runId: String,
        workAttempt: Int,
        client: OkHttpClient,
        initialNote: String? = null
    ) = withContext(Dispatchers.IO) {
        var run = repository.getRun(runId) ?: return@withContext
        if (run.status !in setOf(
                TrafficStatus.QUEUED, TrafficStatus.RUNNING,
                TrafficStatus.INTERRUPTED
            )
        ) return@withContext

        run = run.copy(
            status = TrafficStatus.RUNNING,
            startedAt = run.startedAt ?: System.currentTimeMillis(),
            lastError = when {
                workAttempt > 0 -> "Resumed after interruption (attempt ${workAttempt + 1})"
                initialNote != null -> initialNote
                else -> null
            }
        )
        repository.updateProgress(run)

        val currentStatus = MutableStateFlow(run.status)
        val watcher = launch {
            while (isActive) {
                currentStatus.value = repository.getRun(runId)?.status ?: TrafficStatus.STOPPED
                delay(500)
            }
        }

        var submitted = run.submitted
        var failed = run.failed
        var lastError = run.lastError
        val limiter = RateLimiter(run.speed.eventsPerMinute)
        val nextIndex = AtomicInteger(submitted + failed)
        val counterLock = Mutex()

        try {
            coroutineScope {
                repeat(run.concurrency.coerceIn(1, 4)) {
                    launch {
                        while (currentStatus.value == TrafficStatus.RUNNING) {
                            val index = nextIndex.getAndIncrement()
                            if (index >= run.plannedEvents) break

                            val url = buildUrl(run, index)
                            limiter.acquire()
                            val outcome = sendWithRetry(run, url, client)

                            counterLock.withLock {
                                if (outcome.success) {
                                    submitted++
                                } else {
                                    failed++
                                    lastError = outcome.error ?: "Request failed"
                                }
                                repository.updateProgress(
                                    run.copy(submitted = submitted, failed = failed, lastError = lastError)
                                )
                            }
                        }
                    }
                }
            }

            val finalStatus = when (repository.getRun(runId)?.status ?: currentStatus.value) {
                TrafficStatus.PAUSED -> TrafficStatus.PAUSED
                TrafficStatus.STOPPED -> TrafficStatus.STOPPED
                else -> if (submitted == 0 && failed > 0) TrafficStatus.FAILED else TrafficStatus.COMPLETED
            }
            repository.updateProgress(
                run.copy(
                    status = finalStatus,
                    submitted = submitted,
                    failed = failed,
                    lastError = lastError,
                    completedAt = if (finalStatus in setOf(
                            TrafficStatus.COMPLETED, TrafficStatus.FAILED, TrafficStatus.STOPPED
                        )
                    ) System.currentTimeMillis() else null
                )
            )
        } finally {
            watcher.cancel()
        }
    }

    private fun buildUrl(run: TrafficRun, index: Int): String {
        if (run.pagePaths.isEmpty()) return run.websiteUrl
        val path = run.pagePaths[index % run.pagePaths.size]
        if (path.isEmpty()) return run.websiteUrl
        return try {
            val base = URI(run.websiteUrl)
            URI(base.scheme, base.userInfo, base.host, base.port, path, base.query, null).toString()
        } catch (e: Exception) {
            run.websiteUrl
        }
    }

    private suspend fun sendWithRetry(run: TrafficRun, url: String, client: OkHttpClient): Attempt {
        if (run.mode == TrafficMode.REAL_BROWSER && context != null) {
            return executeBrowserVisit(url, client)
        }
        var attempt = 0
        var result = perform(url, client)
        while (!result.success && result.transient && attempt < 2) {
            attempt++
            delay(500L * (1 shl (attempt - 1)))
            result = perform(url, client)
        }
        return result
    }

    /**
     * Executes the page visit in an Android WebView with JavaScript enabled
     * so that client-side analytics (Google Analytics, GTM, Meta Pixel, Cloudflare) fire properly.
     */
    private suspend fun executeBrowserVisit(url: String, client: OkHttpClient): Attempt {
        if (context == null) {
            return perform(url, client)
        }

        val webViewAttempt = withTimeoutOrNull(18_000L) {
            suspendCancellableCoroutine<Attempt> { continuation ->
                val mainHandler = Handler(Looper.getMainLooper())
                var webViewRef: WebView? = null
                var timeoutRunnable: Runnable? = null

                continuation.invokeOnCancellation {
                    mainHandler.post {
                        timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
                        try {
                            webViewRef?.stopLoading()
                            webViewRef?.destroy()
                        } catch (_: Exception) {}
                    }
                }

                mainHandler.post {
                    try {
                        var isFinished = false
                        val webView = WebView(context)
                        webViewRef = webView

                        val timeout = Runnable {
                            if (!isFinished) {
                                isFinished = true
                                try {
                                    webView.stopLoading()
                                    webView.destroy()
                                } catch (_: Exception) {}
                                if (continuation.isActive) {
                                    continuation.resume(Attempt(false, "Browser page load timed out", transient = true))
                                }
                            }
                        }
                        timeoutRunnable = timeout
                        mainHandler.postDelayed(timeout, 15_000L)

                        webView.apply {
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = true
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                loadsImagesAutomatically = true
                                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                userAgentString = getRandomUserAgent()
                            }

                            val cookieManager = CookieManager.getInstance()
                            cookieManager.setAcceptCookie(true)

                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                                    super.onPageFinished(view, finishedUrl)
                                    if (!isFinished) {
                                        isFinished = true
                                        mainHandler.removeCallbacks(timeout)
                                        // Allow 1.2s for analytics scripts to execute HTTP beacons
                                        mainHandler.postDelayed({
                                            try {
                                                destroy()
                                            } catch (_: Exception) {}
                                            if (continuation.isActive) {
                                                continuation.resume(Attempt(true))
                                            }
                                        }, 1200)
                                    }
                                }

                                override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                                    super.onReceivedError(view, errorCode, description, failingUrl)
                                    if (!isFinished && failingUrl == url) {
                                        isFinished = true
                                        mainHandler.removeCallbacks(timeout)
                                        try { destroy() } catch (_: Exception) {}
                                        if (continuation.isActive) {
                                            continuation.resume(Attempt(false, description ?: "Error $errorCode", transient = true))
                                        }
                                    }
                                }
                            }
                        }
                        webView.loadUrl(url)
                    } catch (e: Exception) {
                        timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
                        if (continuation.isActive) {
                            continuation.resume(Attempt(false, "WEBVIEW_UNAVAILABLE: ${e.message}"))
                        }
                    }
                }
            }
        } ?: Attempt(false, "Browser visit timed out", transient = true)

        // If WebView could not initialize on this context, fall back to HTTP engine so traffic still runs.
        if (!webViewAttempt.success && webViewAttempt.error?.startsWith("WEBVIEW_UNAVAILABLE:") == true) {
            return perform(url, client)
        }
        return webViewAttempt
    }

    private suspend fun perform(url: String, client: OkHttpClient): Attempt = withContext(Dispatchers.IO) {
        try {
            val userAgent = getRandomUserAgent()
            val referer = getRandomReferrer()

            val requestBuilder = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Sec-Ch-Ua", "\"Chromium\";v=\"124\", \"Google Chrome\";v=\"124\", \"Not-A.Brand\";v=\"99\"")
                .header("Sec-Ch-Ua-Mobile", if (userAgent.contains("Mobile")) "?1" else "?0")
                .header("Sec-Ch-Ua-Platform", if (userAgent.contains("Android")) "\"Android\"" else if (userAgent.contains("iPhone")) "\"iOS\"" else "\"Windows\"")
                .header("Sec-Fetch-Dest", "document")
                .header("Sec-Fetch-Mode", "navigate")
                .header("Sec-Fetch-Site", "cross-site")
                .header("Sec-Fetch-User", "?1")
                .header("Upgrade-Insecure-Requests", "1")
                .header("Cache-Control", "no-cache")

            if (referer.isNotBlank()) {
                requestBuilder.header("Referer", referer)
            }

            val request = requestBuilder.get().build()
            client.newCall(request).execute().use { response ->
                val code = response.code
                when {
                    code in 200..399 -> Attempt(true)
                    code == 401 -> Attempt(false, "HTTP 401: Target URL requires authentication/password protection")
                    code == 403 -> Attempt(false, "HTTP 403: Access forbidden by target server")
                    code == 408 || code in 500..599 -> Attempt(false, "HTTP $code", transient = true)
                    else -> Attempt(false, "HTTP $code")
                }
            }
        } catch (e: IOException) {
            Attempt(false, e.message?.take(160) ?: "Network error", transient = true)
        } catch (e: IllegalArgumentException) {
            Attempt(false, "Invalid target URL: ${e.message}")
        }
    }

    companion object {
        private val USER_AGENTS = listOf(
            "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro Build/UQ1A.240205.004) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36",
            "Mozilla/5.0 (Linux; Android 13; SM-S918B Build/TP1A.220624.014) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.6312.118 Mobile Safari/537.36",
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4.1 Mobile/15E148 Safari/604.1",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Safari/605.1.15",
            "Mozilla/5.0 (Linux; Android 14; SM-A546B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:125.0) Gecko/20100101 Firefox/125.0"
        )

        private val REFERRERS = listOf(
            "https://www.google.com/",
            "https://www.google.com/search?q=website",
            "https://www.bing.com/",
            "https://t.co/",
            "https://www.facebook.com/",
            "https://duckduckgo.com/",
            "https://www.linkedin.com/",
            ""
        )

        fun getRandomUserAgent(): String = USER_AGENTS[Random.nextInt(USER_AGENTS.size)]
        fun getRandomReferrer(): String = REFERRERS[Random.nextInt(REFERRERS.size)]

        fun createClient(): OkHttpClient = OkHttpClient.Builder()
            .dns(PublicOnlyDns())
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}
