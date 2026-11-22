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
    private val onErrorEncountered: (tabIndex: Int, errorCode: Int, description: String, failingUrl: String) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val webViewMap = ConcurrentHashMap<Int, WebView>()
    private val tabStateMap = ConcurrentHashMap<Int, TabState>()

    private val _activeTabIndex = MutableStateFlow(0)
    val activeTabIndex: StateFlow<Int> = _activeTabIndex.asStateFlow()

    /**
     * Initializes a tab WebView with proper security and engine settings.
     */
    fun createOrGetTab(tabIndex: Int, profile: AutomationProfile): WebView {
        return webViewMap.getOrPut(tabIndex) {
            val webView = WebView(context).apply {
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
                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    allowFileAccess = false
                    allowContentAccess = false

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
                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
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
                        updateTabState(tabIndex) {
                            it.copy(
                                isNavigating = false,
                                currentUrl = url ?: it.currentUrl,
                                pageTitle = view?.title ?: "",
                                loadProgress = 100
                            )
                        }
                        onNavigationStateChange(tabIndex, false, view?.title, url)
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        errorCode: Int,
                        description: String?,
                        failingUrl: String?
                    ) {
                        super.onReceivedError(view, errorCode, description, failingUrl)
                        val errDesc = description ?: "Navigation Error ($errorCode)"
                        updateTabState(tabIndex) {
                            it.copy(
                                isNavigating = false,
                                lastError = errDesc
                            )
                        }
                        onErrorEncountered(tabIndex, errorCode, errDesc, failingUrl ?: "")
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
                        handler?.cancel() // Strict security: reject invalid SSL
                        val msg = "SSL Error: ${error?.primaryError}"
                        updateTabState(tabIndex) { it.copy(lastError = msg) }
                        onErrorEncountered(tabIndex, error?.primaryError ?: -1, msg, view?.url ?: "")
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
            webView
        }
    }

    fun selectTab(tabIndex: Int) {
        _activeTabIndex.value = tabIndex
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
        mainHandler.post {
            val wv = webViewMap[tabIndex] ?: run {
                callback(null)
                return@post
            }
            wv.evaluateJavascript(script) { result ->
                callback(result)
            }
        }
    }

    /**
     * Fully destroys all WebViews and releases memory.
     */
    fun destroyAll() {
        mainHandler.post {
            webViewMap.forEach { (index, wv) ->
                try {
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
        }
    }
}
