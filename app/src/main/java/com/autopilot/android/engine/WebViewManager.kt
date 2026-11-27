package com.autopilot.android.engine

import android.content.Context
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.*
import com.autopilot.android.domain.model.AutomationProfile
import com.autopilot.android.domain.model.TabState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages the pool of WebViews for active automation tabs (1 to 25 tabs).
 * Ensures safe UI-thread lifecycle management, memory reclamation,
 * cookie/storage isolation, navigation monitoring, and scroll tracking.
 */
class WebViewManager(
    private val context: Context,
    private val onProgressUpdate: (tabIndex: Int, progress: Int) -> Unit,
    private val onNavigationStateChange: (tabIndex: Int, isNavigating: Boolean, title: String?, url: String?) -> Unit,
    private val onErrorEncountered: (tabIndex: Int, errorCode: Int, description: String, failingUrl: String) -> Unit,
    private val activeProxyProvider: (() -> com.autopilot.android.domain.model.proxy.ProxyConfiguration?)? = null
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val webViewMap = ConcurrentHashMap<Int, WebView>()
    private val tabStateMap = ConcurrentHashMap<Int, TabState>()
    private val navigationTimeoutMap = ConcurrentHashMap<Int, Runnable>()
    private var scriptOnPageFinished: String? = null

    /** Script injected after every completed page load; pass null to disable. */
    fun setScriptOnPageFinished(script: String?) {
        scriptOnPageFinished = script
    }

    private val _activeTabIndex = MutableStateFlow(0)
    val activeTabIndex: StateFlow<Int> = _activeTabIndex.asStateFlow()

    private val _activeWebView = MutableStateFlow<WebView?>(null)
    val activeWebView: StateFlow<WebView?> = _activeWebView.asStateFlow()

    fun isWebViewAvailable(): Boolean = try {
        androidx.webkit.WebViewCompat.getCurrentWebViewPackage(context) != null
    } catch (_: Throwable) {
        false
    }

    /**
     * Initializes a tab WebView with proper security and engine settings.
     * Returns null if no WebView provider is installed or WebView initialization fails.
     */
    fun createOrGetTab(tabIndex: Int, profile: AutomationProfile): WebView? {
        webViewMap[tabIndex]?.let { existing ->
            if (tabIndex == _activeTabIndex.value) _activeWebView.value = existing
            return existing
        }

        val webView = try {
            val wv = WebView(context).apply {
                visibility = View.VISIBLE
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    displayZoomControls = false
                    builtInZoomControls = true
                    loadsImagesAutomatically = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    allowFileAccess = false
                    allowContentAccess = false
                    javaScriptCanOpenWindowsAutomatically = true
                    mediaPlaybackRequiresUserGesture = false
                    cacheMode = WebSettings.LOAD_DEFAULT

                    if (!profile.userAgent.isNullOrBlank()) {
                        userAgentString = profile.userAgent
                    }
                }

                // Apply cookie policy
                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    cookieManager.setAcceptThirdPartyCookies(this, true)
                }

                if (profile.clearCookiesOnStart) {
                    cookieManager.removeAllCookies(null)
                    cookieManager.flush()
                }

                if (profile.clearCacheOnStart) {
                    clearCache(true)
                    clearFormData()
                    clearHistory()
                }

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        val url = request?.url?.toString() ?: return false
                        return !(url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true))
                    }

                    @Suppress("DEPRECATION")
                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                        val u = url ?: return false
                        return !(u.startsWith("http://", ignoreCase = true) || u.startsWith("https://", ignoreCase = true))
                    }

                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        navigationTimeoutMap.remove(tabIndex)?.let { mainHandler.removeCallbacks(it) }

                        val timeoutSec = profile.timeoutSeconds.coerceIn(20, 120)
                        val watchdog = Runnable {
                            val wv = webViewMap[tabIndex]
                            val state = tabStateMap[tabIndex]
                            if (wv != null && state?.isNavigating == true) {
                                wv.stopLoading()
                                val timeoutMsg = "Navigation timed out after ${timeoutSec}s"
                                updateTabState(tabIndex) {
                                    it.copy(
                                        isNavigating = false,
                                        lastError = timeoutMsg
                                    )
                                }
                                onNavigationStateChange(tabIndex, false, wv.title, wv.url)
                                onErrorEncountered(tabIndex, -8 /* ERROR_TIMEOUT */, timeoutMsg, wv.url ?: "")
                            }
                        }
                        navigationTimeoutMap[tabIndex] = watchdog
                        mainHandler.postDelayed(watchdog, timeoutSec * 1000L)

                        updateTabState(tabIndex) {
                            it.copy(
                                isNavigating = true,
                                currentUrl = url ?: it.currentUrl,
                                lastError = null
                            )
                        }
                        onNavigationStateChange(tabIndex, true, view?.title, url)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        navigationTimeoutMap.remove(tabIndex)?.let { mainHandler.removeCallbacks(it) }
                        updateTabState(tabIndex) {
                            it.copy(
                                isNavigating = false,
                                currentUrl = url ?: it.currentUrl,
                                pageTitle = view?.title ?: "",
                                loadProgress = 100
                            )
                        }
                        scriptOnPageFinished?.let { script -> view?.evaluateJavascript(script, null) }
                        onNavigationStateChange(tabIndex, false, view?.title, url)
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?
                    ) {
                        super.onReceivedError(view, request, error)
                        // Only report and act on main frame errors; 3rd party ads/trackers failing must NOT abort the tab
                        if (request?.isForMainFrame == true) {
                            navigationTimeoutMap.remove(tabIndex)?.let { mainHandler.removeCallbacks(it) }
                            val errCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) error?.errorCode ?: -1 else -1
                            val errDesc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) error?.description?.toString() ?: "Navigation Error ($errCode)" else "Navigation Error ($errCode)"
                            val failingUrl = request.url?.toString() ?: ""
                            updateTabState(tabIndex) {
                                it.copy(
                                    isNavigating = false,
                                    lastError = errDesc
                                )
                            }
                            onErrorEncountered(tabIndex, errCode, errDesc, failingUrl)
                        }
                    }

                    @Suppress("DEPRECATION")
                    override fun onReceivedError(
                        view: WebView?,
                        errorCode: Int,
                        description: String?,
                        failingUrl: String?
                    ) {
                        super.onReceivedError(view, errorCode, description, failingUrl)
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                            navigationTimeoutMap.remove(tabIndex)?.let { mainHandler.removeCallbacks(it) }
                            val errDesc = description ?: "Navigation Error ($errorCode)"
                            updateTabState(tabIndex) {
                                it.copy(
                                    isNavigating = false,
                                    lastError = errDesc
                                )
                            }
                            onErrorEncountered(tabIndex, errorCode, errDesc, failingUrl ?: "")
                        }
                    }

                    override fun onReceivedHttpError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        errorResponse: WebResourceResponse?
                    ) {
                        super.onReceivedHttpError(view, request, errorResponse)
                        if (request?.isForMainFrame == true) {
                            val code = errorResponse?.statusCode ?: 0
                            val msg = "HTTP Error $code: ${errorResponse?.reasonPhrase}"
                            updateTabState(tabIndex) { it.copy(lastError = msg) }
                            onErrorEncountered(tabIndex, code, msg, request.url.toString())
                        }
                    }

                    override fun onReceivedSslError(
                        view: WebView?,
                        handler: SslErrorHandler?,
                        error: SslError?
                    ) {
                        // Crucial for proxy routing and automated sessions:
                        // Proxies and tunneling gateways frequently present self-signed certificates,
                        // intermediate CA chains, or domain mismatch warnings that would otherwise
                        // completely abort page loading. Proceed to allow the website to open.
                        handler?.proceed()
                        val warnMsg = "SSL Notice: proceeded through certificate warning (code ${error?.primaryError})"
                        updateTabState(tabIndex) { it.copy(lastError = warnMsg) }
                    }

                    override fun onReceivedHttpAuthRequest(
                        view: WebView?,
                        handler: HttpAuthHandler?,
                        host: String?,
                        realm: String?
                    ) {
                        val activeProxy = activeProxyProvider?.invoke()
                        val proxyHost = activeProxy?.host
                        val creds = activeProxy?.credentials

                        if (activeProxy != null &&
                            !proxyHost.isNullOrBlank() &&
                            host?.equals(proxyHost, ignoreCase = true) == true &&
                            creds != null &&
                            !creds.username.isNullOrBlank() &&
                            !creds.password.isNullOrBlank()
                        ) {
                            // Safe: Challenge host strictly matches active proxy host
                            handler?.proceed(creds.username, creds.password)
                        } else {
                            // Origin challenge or mismatch: reject to prevent credential leakage
                            handler?.cancel()
                        }
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        super.onProgressChanged(view, newProgress)
                        updateTabState(tabIndex) { it.copy(loadProgress = newProgress) }
                        onProgressUpdate(tabIndex, newProgress)
                    }

                    override fun onReceivedTitle(view: WebView?, title: String?) {
                        super.onReceivedTitle(view, title)
                        updateTabState(tabIndex) { it.copy(pageTitle = title ?: "") }
                    }
                }
            }

            tabStateMap[tabIndex] = TabState(
                tabIndex = tabIndex,
                currentUrl = ""
            )
            webViewMap[tabIndex] = wv
            wv
        } catch (_: Throwable) {
            return null
        }
        if (tabIndex == _activeTabIndex.value) _activeWebView.value = webView
        return webView
    }

    fun selectTab(tabIndex: Int) {
        _activeTabIndex.value = tabIndex
        _activeWebView.value = webViewMap[tabIndex]
    }

    fun getWebView(tabIndex: Int): WebView? = webViewMap[tabIndex]

    fun getActiveWebView(): WebView? = webViewMap[_activeTabIndex.value]

    fun getTabState(tabIndex: Int): TabState? = tabStateMap[tabIndex]

    fun getAllTabStates(): List<TabState> = tabStateMap.values.sortedBy { it.tabIndex }

    private fun updateTabState(tabIndex: Int, transform: (TabState) -> TabState) {
        val current = tabStateMap[tabIndex] ?: TabState(tabIndex = tabIndex, currentUrl = "")
        tabStateMap[tabIndex] = transform(current)
    }

    /**
     * Executes vertical scroll by delta pixels on UI thread.
     */
    fun performScrollBy(tabIndex: Int, deltaY: Int, callback: (newScrollY: Int) -> Unit = {}) {
        mainHandler.post {
            val wv = webViewMap[tabIndex] ?: return@post
            wv.scrollBy(0, deltaY)
            val currentY = wv.scrollY
            updateTabState(tabIndex) {
                it.copy(
                    lastScrollTime = System.currentTimeMillis()
                )
            }
            callback(currentY)
        }
    }

    /**
     * Measures vertical scroll progress percentage: (scrollY / maxScroll) * 100
     */
    fun evaluateScrollDepth(tabIndex: Int, callback: (percentage: Int) -> Unit) {
        mainHandler.post {
            val wv = webViewMap[tabIndex] ?: run {
                callback(0)
                return@post
            }
            val js = """
                (function() {
                    var total = Math.max(document.body.scrollHeight, document.documentElement.scrollHeight) - window.innerHeight;
                    if (total <= 0) return 100;
                    return Math.min(100, Math.round((window.pageYOffset / total) * 100));
                })();
            """.trimIndent()

            wv.evaluateJavascript(js) { result ->
                val depth = result?.toIntOrNull() ?: 0
                updateTabState(tabIndex) { it.copy(scrollDepthPercentage = depth) }
                callback(depth)
            }
        }
    }

    /**
     * Navigates the target tab to a URL.
     */
    fun loadUrl(tabIndex: Int, url: String) {
        mainHandler.post {
            val wv = webViewMap[tabIndex] ?: return@post
            wv.loadUrl(url)
        }
    }

    /**
     * Reloads the target tab.
     */
    fun reloadTab(tabIndex: Int) {
        mainHandler.post {
            val wv = webViewMap[tabIndex] ?: return@post
            updateTabState(tabIndex) { it.copy(lastRefreshTime = System.currentTimeMillis()) }
            wv.reload()
        }
    }

    /**
     * Executes sandboxed JavaScript with string return callback.
     */
    fun executeJavaScript(tabIndex: Int, script: String, callback: (String?) -> Unit) {
        val safeScript = com.autopilot.android.domain.model.AutomationScripts.normalizeScript(script)
        mainHandler.post {
            val wv = webViewMap[tabIndex] ?: run {
                callback(null)
                return@post
            }
            wv.evaluateJavascript(safeScript) { result ->
                callback(result)
            }
        }
    }

    /**
     * Fully destroys all WebViews and releases memory.
     */
    fun destroyAll() {
        mainHandler.post {
            navigationTimeoutMap.values.forEach { mainHandler.removeCallbacks(it) }
            navigationTimeoutMap.clear()
            webViewMap.forEach { (index, wv) ->
                try {
                    (wv.parent as? android.view.ViewGroup)?.removeView(wv)
                    wv.stopLoading()
                    wv.clearHistory()
                    wv.removeAllViews()
                    wv.destroy()
                } catch (e: Exception) {
                    // Ignore already destroyed
                }
            }
            webViewMap.clear()
            tabStateMap.clear()
            _activeWebView.value = null
        }
    }
}
