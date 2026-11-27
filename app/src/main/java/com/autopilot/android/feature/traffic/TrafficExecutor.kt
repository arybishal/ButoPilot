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
import com.autopilot.android.domain.model.DeviceCatalog
import com.autopilot.android.domain.model.DeviceCategory
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
            executeRun(runId, workAttempt, listOf(client))
            return@withContext
        }

        coordinator.activeProxy.value?.let { active ->
            executeRun(runId, workAttempt, listOf(clientFor(active), client))
            return@withContext
        }

        // Fast parallel probe (max 6 candidates, 2s timeout) prioritizing healthy & HTTPS-capable proxies.
        val isHttpsTarget = run.websiteUrl.startsWith("https://", ignoreCase = true)
        val candidates = repo.getAllProxies()
            .filter { it.enabled }
            .sortedWith(
                compareBy<ProxyConfiguration>(
                    {
                        when (it.lastHealthStatus) {
                            ProxyHealthStatus.HEALTHY -> 0
                            ProxyHealthStatus.UNTESTED -> 1
                            else -> 2
                        }
                    },
                    {
                        if (isHttpsTarget && it.protocol == ProxyProtocol.HTTP) 1 else 0
                    },
                    { it.lastLatencyMs ?: Long.MAX_VALUE }
                )
            )
            .take(6)

        val healthyProxies: List<ProxyConfiguration> = if (candidates.isEmpty()) {
            emptyList()
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
                }.awaitAll().filterNotNull()
            }
        }

        if (healthyProxies.isEmpty()) {
            // Fall back to direct connection so Traffic Generator still runs when free proxies are offline.
            executeRun(
                runId = runId,
                workAttempt = workAttempt,
                clientPool = listOf(client),
                initialNote = "No verified proxy available; running with direct connection"
            )
            return@withContext
        }

        val clientPool = healthyProxies.map { clientFor(it) } + listOf(client)

        if (run.mode == TrafficMode.PAGE_REQUEST) {
            // OkHttp rotates across healthy proxy clients and falls back to direct on Connection reset.
            executeRun(runId, workAttempt, clientPool)
            return@withContext
        }

        val primaryProxy = healthyProxies.first()
        coordinator.acquireAndApply(primaryProxy)
        if (!coordinator.isProxyActive.value) {
            coordinator.resetAndRelease()
            // WebView proxy override unsupported on this device; still route HTTP requests via proxy pool.
            executeRun(
                runId = runId,
                workAttempt = workAttempt,
                clientPool = clientPool,
                initialNote = "WebView proxy override unavailable; using HTTP proxy engine"
            )
            return@withContext
        }
        try {
            executeRun(runId, workAttempt, clientPool)
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
            // Disable keep-alive reuse for free proxies that abruptly reset idle sockets
            .connectionPool(okhttp3.ConnectionPool(0, 1, TimeUnit.MILLISECONDS))
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
        clientPool: List<OkHttpClient>,
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
        val clientCursor = AtomicInteger(0)
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
                            val outcome = sendWithRetry(run, url, clientPool, clientCursor)

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

    private suspend fun sendWithRetry(
        run: TrafficRun,
        url: String,
        clientPool: List<OkHttpClient>,
        clientCursor: AtomicInteger
    ): Attempt {
        val poolSize = clientPool.size.coerceAtLeast(1)
        val startClient = clientPool[Math.floorMod(clientCursor.get(), poolSize)]

        if ((run.mode == TrafficMode.REAL_BROWSER || run.mode == TrafficMode.ORGANIC_SEARCH) && context != null) {
            return executeBrowserVisit(
                url = url,
                client = startClient,
                category = run.deviceCategory,
                organicSearch = run.mode == TrafficMode.ORGANIC_SEARCH
            )
        }
        var attempt = 0
        var result = perform(url, startClient, run.deviceCategory, run.mode == TrafficMode.ORGANIC_SEARCH)
        while (!result.success && result.transient && attempt < 2) {
            attempt++
            val nextIdx = if (poolSize > 1) clientCursor.incrementAndGet() else 0
            val nextClient = clientPool[Math.floorMod(nextIdx, poolSize)]
            delay(300L * attempt)
            result = perform(url, nextClient, run.deviceCategory, run.mode == TrafficMode.ORGANIC_SEARCH)
        }
        return result
    }

    /**
     * Executes the page visit in an Android WebView with stealth spoofing, human-like scrolling,
     * and realistic dwell time so client-side analytics (GA4, GTM, Meta Pixel) register engaged visits.
     */
    private suspend fun executeBrowserVisit(
        url: String,
        client: OkHttpClient,
        category: DeviceCategory,
        organicSearch: Boolean = false
    ): Attempt {
        if (context == null) {
            return perform(url, client, category, organicSearch)
        }

        val browserUserAgent = DeviceCatalog.random(category).userAgent
        val searchReferer = if (organicSearch) buildSearchReferrer(url) else getRandomReferrer()
        val stealthScript = buildStealthScript()
        val humanScrollScript = buildHumanBehaviorScript()
        val webViewAttempt = withTimeoutOrNull(22_000L) {
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
                                userAgentString = browserUserAgent
                            }

                            val cookieManager = CookieManager.getInstance()
                            cookieManager.setAcceptCookie(true)
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                                cookieManager.setAcceptThirdPartyCookies(this, true)
                            }

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, startedUrl: String?, favicon: android.graphics.Bitmap?) {
                                    super.onPageStarted(view, startedUrl, favicon)
                                    view?.evaluateJavascript(stealthScript, null)
                                }

                                override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                                    super.onPageFinished(view, finishedUrl)
                                    if (!isFinished) {
                                        isFinished = true
                                        mainHandler.removeCallbacks(timeout)
                                        view?.evaluateJavascript(stealthScript, null)
                                        view?.evaluateJavascript(humanScrollScript, null)
                                        // Dwell 2.5s..4.0s for human scroll + GA4/GTM engagement beacons
                                        val dwellMs = Random.nextLong(2500L, 4000L)
                                        mainHandler.postDelayed({
                                            try {
                                                destroy()
                                            } catch (_: Exception) {}
                                            if (continuation.isActive) {
                                                continuation.resume(Attempt(true))
                                            }
                                        }, dwellMs)
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
                        if (searchReferer.isNotBlank()) {
                            webView.loadUrl(url, mapOf("Referer" to searchReferer))
                        } else {
                            webView.loadUrl(url)
                        }
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
            return perform(url, client, category, organicSearch)
        }
        return webViewAttempt
    }

    private suspend fun perform(
        url: String,
        client: OkHttpClient,
        category: DeviceCategory,
        organicSearch: Boolean = false
    ): Attempt = withContext(Dispatchers.IO) {
        try {
            val device = DeviceCatalog.random(category)
            val userAgent = device.userAgent
            val referer = if (organicSearch) buildSearchReferrer(url) else getRandomReferrer()

            val requestBuilder = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Sec-Ch-Ua-Mobile", if (device.isMobile) "?1" else "?0")
                .header("Sec-Ch-Ua-Platform", "\"${device.platformHint}\"")
                .header("Sec-Fetch-Dest", "document")
                .header("Sec-Fetch-Mode", "navigate")
                .header("Sec-Fetch-Site", "cross-site")
                .header("Sec-Fetch-User", "?1")
                .header("Upgrade-Insecure-Requests", "1")
                .header("Cache-Control", "no-cache")

            if (userAgent.contains("Chrome/", ignoreCase = true)) {
                requestBuilder.header("Sec-Ch-Ua", "\"Chromium\";v=\"124\", \"Google Chrome\";v=\"124\", \"Not-A.Brand\";v=\"99\"")
            }
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

        fun getRandomReferrer(): String = REFERRERS[Random.nextInt(REFERRERS.size)]

        fun buildSearchReferrer(url: String): String {
            val host = try {
                URI(url).host?.removePrefix("www.") ?: "website"
            } catch (_: Exception) {
                "website"
            }
            val encoded = java.net.URLEncoder.encode(host, "UTF-8")
            val engines = listOf(
                "https://www.google.com/search?q=$encoded",
                "https://www.bing.com/search?q=$encoded",
                "https://duckduckgo.com/?q=$encoded"
            )
            return engines[Random.nextInt(engines.size)]
        }

        private fun buildStealthScript(): String = """
            (function() {
                try {
                    Object.defineProperty(navigator, 'webdriver', { get: function() { return undefined; } });
                    Object.defineProperty(navigator, 'languages', { get: function() { return ['en-US', 'en']; } });
                    Object.defineProperty(navigator, 'hardwareConcurrency', { get: function() { return 8; } });
                    Object.defineProperty(navigator, 'deviceMemory', { get: function() { return 8; } });
                } catch (e) {}
            })();
        """.trimIndent()

        private fun buildHumanBehaviorScript(): String = """
            (function() {
                try {
                    window.dispatchEvent(new Event('focus'));
                    var maxScroll = Math.max(document.body ? document.body.scrollHeight : 0, document.documentElement ? document.documentElement.scrollHeight : 0);
                    var step1 = Math.min(350, Math.floor(maxScroll * 0.3));
                    var step2 = Math.min(800, Math.floor(maxScroll * 0.65));
                    setTimeout(function() { window.scrollBy({ top: step1, behavior: 'smooth' }); }, 400);
                    setTimeout(function() { window.scrollBy({ top: step2, behavior: 'smooth' }); }, 1300);
                } catch (e) {}
            })();
        """.trimIndent()

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
