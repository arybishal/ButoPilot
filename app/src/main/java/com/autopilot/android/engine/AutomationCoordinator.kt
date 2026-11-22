package com.autopilot.android.engine

import android.content.Context
import com.autopilot.android.data.repository.HistoryRepository
import com.autopilot.android.data.repository.ProfileRepository
import com.autopilot.android.data.repository.ProxyRepository
import com.autopilot.android.domain.model.AutomationProfile
import com.autopilot.android.domain.model.ExecutionHistoryItem
import com.autopilot.android.domain.model.ExecutionMode
import com.autopilot.android.domain.model.ProfileStatus
import com.autopilot.android.domain.model.TabState
import com.autopilot.android.domain.validation.IntervalCalculator
import com.autopilot.android.domain.validation.UrlValidator
import com.autopilot.android.feature.proxy.ProxyExecutionCoordinator
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class EngineState(
    val status: ProfileStatus = ProfileStatus.IDLE,
    val profile: AutomationProfile? = null,
    val activeTabIndex: Int = 0,
    val totalTabs: Int = 1,
    val tabStates: List<TabState> = emptyList(),
    val totalActions: Int = 0,
    val elapsedSeconds: Long = 0,
    val statusMessage: String = "Idle",
    val memoryMb: Long = 0,
    val errorMessage: String? = null
)

class AutomationCoordinator(
    private val context: Context,
    private val profileRepository: ProfileRepository,
    private val historyRepository: HistoryRepository,
    private val proxyRepository: ProxyRepository? = null,
    private val proxyExecutionCoordinator: ProxyExecutionCoordinator? = null
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val resourceManager = ResourceManager(context)

    private val _engineState = MutableStateFlow(EngineState())
    val engineState: StateFlow<EngineState> = _engineState.asStateFlow()

    private var executionJob: Job? = null
    private var timerJob: Job? = null
    private var isPaused = false

    private var activeProfile: AutomationProfile? = null
    private var startTimeMillis: Long = 0
    private var totalActionsExecuted = 0

    val webViewManager = WebViewManager(
        context = context,
        onProgressUpdate = { tabIndex, progress ->
            updateTabStates()
        },
        onNavigationStateChange = { tabIndex, isNavigating, title, url ->
            updateTabStates()
        },
        onErrorEncountered = { tabIndex, errorCode, description, failingUrl ->
            _engineState.value = _engineState.value.copy(
                errorMessage = "Tab $tabIndex error: $description on $failingUrl"
            )
            updateTabStates()
        }
    )

    private fun updateTabStates() {
        val currentStates = webViewManager.getAllTabStates()
        _engineState.value = _engineState.value.copy(
            tabStates = currentStates,
            memoryMb = resourceManager.getAvailableMemoryMb()
        )
    }

    /**
     * Starts execution for the given profile.
     */
    fun start(profile: AutomationProfile) {
        if (_engineState.value.status == ProfileStatus.RUNNING) {
            stop()
        }

        activeProfile = profile
        isPaused = false
        startTimeMillis = System.currentTimeMillis()
        totalActionsExecuted = 0

        _engineState.value = EngineState(
            status = ProfileStatus.RUNNING,
            profile = profile,
            activeTabIndex = 0,
            totalTabs = profile.tabCount.coerceIn(AutomationProfile.MIN_TABS, AutomationProfile.MAX_TABS),
            statusMessage = "Initializing tabs and loading URLs...",
            memoryMb = resourceManager.getAvailableMemoryMb()
        )

        // Start elapsed seconds timer
        timerJob?.cancel()
        timerJob = scope.launch {
            while (isActive) {
                delay(1000)
                if (!isPaused && _engineState.value.status == ProfileStatus.RUNNING) {
                    val elapsed = (System.currentTimeMillis() - startTimeMillis) / 1000
                    _engineState.value = _engineState.value.copy(
                        elapsedSeconds = elapsed,
                        memoryMb = resourceManager.getAvailableMemoryMb()
                    )
                }
            }
        }

        // Start automation coroutine loop
        executionJob?.cancel()
        executionJob = scope.launch {
            var proxyLocked = false
            try {
                // Network isolation check: enforce proxy configuration if set
                val configuredProxyId = profile.proxyId
                if (!configuredProxyId.isNullOrBlank()) {
                    val proxy = proxyRepository?.getProxyById(configuredProxyId)
                    if (proxy == null || !proxy.enabled) {
                        val errMsg = "Configured proxy [ID: $configuredProxyId] is unavailable or disabled. Execution aborted to protect network isolation."
                        _engineState.value = _engineState.value.copy(
                            status = ProfileStatus.ERROR,
                            statusMessage = errMsg,
                            errorMessage = errMsg
                        )
                        profileRepository.updateStatus(profile.id, ProfileStatus.ERROR)
                        return@launch
                    }

                    _engineState.value = _engineState.value.copy(
                        statusMessage = "Applying network isolation: ${proxy.name} (${proxy.protocol.displayName} ${proxy.host}:${proxy.port})..."
                    )
                    proxyExecutionCoordinator?.acquireAndApply(proxy)
                    proxyLocked = true
                } else {
                    proxyExecutionCoordinator?.acquireAndApply(null)
                    proxyLocked = true
                }

                // Update profile status in DB
                profileRepository.updateStatus(profile.id, ProfileStatus.RUNNING)

                runAutomationLoop(profile)

            } catch (e: CancellationException) {
                // Normal cancellation on stop
            } catch (e: Exception) {
                _engineState.value = _engineState.value.copy(
                    status = ProfileStatus.ERROR,
                    statusMessage = "Error: ${e.message}",
                    errorMessage = e.message
                )
                profileRepository.updateStatus(profile.id, ProfileStatus.ERROR)
            } finally {
                if (proxyLocked) {
                    proxyExecutionCoordinator?.resetAndRelease()
                }
                recordSessionHistory(ProfileStatus.COMPLETED)
            }
        }
    }

    /**
     * Pauses the active automation loop.
     */
    fun pause() {
        if (_engineState.value.status == ProfileStatus.RUNNING) {
            isPaused = true
            _engineState.value = _engineState.value.copy(
                status = ProfileStatus.PAUSED,
                statusMessage = "Automation Paused"
            )
            activeProfile?.let {
                scope.launch { profileRepository.updateStatus(it.id, ProfileStatus.PAUSED) }
            }
        }
    }

    /**
     * Resumes the paused automation loop.
     */
    fun resume() {
        if (_engineState.value.status == ProfileStatus.PAUSED) {
            isPaused = false
            _engineState.value = _engineState.value.copy(
                status = ProfileStatus.RUNNING,
                statusMessage = "Automation Resumed"
            )
            activeProfile?.let {
                scope.launch { profileRepository.updateStatus(it.id, ProfileStatus.RUNNING) }
            }
        }
    }

    /**
     * Stops the automation, clears resources, and destroys WebViews.
     */
    fun stop() {
        isPaused = false
        executionJob?.cancel()
        timerJob?.cancel()
        executionJob = null
        timerJob = null

        val prof = activeProfile
        val status = _engineState.value.status

        _engineState.value = _engineState.value.copy(
            status = ProfileStatus.IDLE,
            statusMessage = "Automation Stopped",
            activeTabIndex = 0
        )

        webViewManager.destroyAll()

        scope.launch {
            try {
                proxyExecutionCoordinator?.resetAndRelease()
            } catch (e: Exception) {
                // Safe cleanup
            }
            if (prof != null) {
                profileRepository.updateStatus(prof.id, ProfileStatus.IDLE)
                recordSessionHistory(if (status == ProfileStatus.ERROR) ProfileStatus.ERROR else ProfileStatus.COMPLETED)
            }
        }
    }

    private suspend fun runAutomationLoop(profile: AutomationProfile) {
        val totalTabs = profile.tabCount.coerceIn(AutomationProfile.MIN_TABS, AutomationProfile.MAX_TABS)
        val urls = if (profile.urls.isNotEmpty()) profile.urls else listOf("https://example.com")

        // Step 1: Initialize all configured tabs on UI thread
        withContext(Dispatchers.Main) {
            for (tabIdx in 0 until totalTabs) {
                val targetUrl = urls[tabIdx % urls.size]
                webViewManager.createOrGetTab(tabIdx, profile)
                webViewManager.loadUrl(tabIdx, targetUrl)
            }
            webViewManager.selectTab(0)
        }

        _engineState.value = _engineState.value.copy(
            totalTabs = totalTabs,
            statusMessage = "All $totalTabs tabs loaded. Starting ${profile.executionMode.displayName}..."
        )

        // Step 2: Main execution loop
        var currentTab = 0
        while (scope.isActive) {
            // Check memory condition
            if (resourceManager.isMemoryCriticallyLow()) {
                _engineState.value = _engineState.value.copy(
                    statusMessage = "Memory low (<150MB). Pausing briefly for GC..."
                )
                System.gc()
                delay(5000)
                continue
            }

            // Check pause condition
            while (isPaused && scope.isActive) {
                delay(500)
            }

            if (!scope.isActive) break

            // Execute according to mode
            when (profile.executionMode) {
                ExecutionMode.SIMPLE_SCROLL_REFRESH -> {
                    executeSimpleScrollRefresh(profile, currentTab)
                }
                ExecutionMode.DEEP_SCROLL -> {
                    executeDeepScroll(profile, currentTab)
                }
                ExecutionMode.TAB_SWITCHING -> {
                    executeTabSwitching(profile, currentTab)
                    currentTab = (currentTab + 1) % totalTabs
                }
                ExecutionMode.CUSTOM_JAVASCRIPT -> {
                    executeSandboxedJavaScript(profile, currentTab)
                }
            }

            totalActionsExecuted++
            _engineState.value = _engineState.value.copy(
                totalActions = totalActionsExecuted,
                activeTabIndex = currentTab
            )
        }
    }

    private suspend fun executeSimpleScrollRefresh(profile: AutomationProfile, tabIndex: Int) {
        val scrollDelayMs = IntervalCalculator.getNextScrollDelayMs(
            profile.scrollIntervalSec,
            profile.randomizeIntervals,
            profile.minScrollIntervalSec,
            profile.maxScrollIntervalSec
        )

        _engineState.value = _engineState.value.copy(
            statusMessage = "Tab $tabIndex: Scrolling (waiting ${scrollDelayMs / 1000}s)..."
        )
        delay(scrollDelayMs)

        // Perform scroll down
        withContext(Dispatchers.Main) {
            webViewManager.performScrollBy(tabIndex, 400)
        }

        val refreshDelayMs = IntervalCalculator.getNextRefreshDelayMs(
            profile.refreshIntervalSec,
            profile.randomizeIntervals,
            profile.minRefreshIntervalSec,
            profile.maxRefreshIntervalSec
        )

        _engineState.value = _engineState.value.copy(
            statusMessage = "Tab $tabIndex: Refreshing in ${refreshDelayMs / 1000}s..."
        )
        delay(refreshDelayMs)

        withContext(Dispatchers.Main) {
            webViewManager.reloadTab(tabIndex)
        }
    }

    private suspend fun executeDeepScroll(profile: AutomationProfile, tabIndex: Int) {
        val scrollIntervalMs = 2000L
        for (step in 1..5) {
            if (isPaused || !scope.isActive) break

            withContext(Dispatchers.Main) {
                webViewManager.performScrollBy(tabIndex, 600)
                webViewManager.evaluateScrollDepth(tabIndex) { depth ->
                    _engineState.value = _engineState.value.copy(
                        statusMessage = "Tab $tabIndex: Deep Scroll Depth $depth%"
                    )
                }
            }
            delay(scrollIntervalMs)
        }

        // Delay then refresh if interval reached
        val refreshDelayMs = IntervalCalculator.getNextRefreshDelayMs(
            profile.refreshIntervalSec,
            profile.randomizeIntervals,
            profile.minRefreshIntervalSec,
            profile.maxRefreshIntervalSec
        )
        delay(refreshDelayMs)
        withContext(Dispatchers.Main) {
            webViewManager.reloadTab(tabIndex)
        }
    }

    private suspend fun executeTabSwitching(profile: AutomationProfile, tabIndex: Int) {
        withContext(Dispatchers.Main) {
            webViewManager.selectTab(tabIndex)
            webViewManager.performScrollBy(tabIndex, 300)
        }

        val switchDelayMs = IntervalCalculator.getNextScrollDelayMs(
            profile.scrollIntervalSec,
            profile.randomizeIntervals,
            profile.minScrollIntervalSec,
            profile.maxScrollIntervalSec
        )

        _engineState.value = _engineState.value.copy(
            activeTabIndex = tabIndex,
            statusMessage = "Active: Tab ${tabIndex + 1} of ${profile.tabCount} (next switch in ${switchDelayMs / 1000}s)"
        )

        delay(switchDelayMs)
    }

    private suspend fun executeSandboxedJavaScript(profile: AutomationProfile, tabIndex: Int) {
        val script = profile.customScript
        if (script.isNullOrBlank()) {
            _engineState.value = _engineState.value.copy(
                statusMessage = "No custom script provided. Scrolling..."
            )
            executeSimpleScrollRefresh(profile, tabIndex)
            return
        }

        val currentState = webViewManager.getTabState(tabIndex)
        val currentUrl = currentState?.currentUrl ?: ""

        // Security check: Domain permissions
        if (!UrlValidator.isDomainAllowed(currentUrl, profile.allowedDomains)) {
            _engineState.value = _engineState.value.copy(
                statusMessage = "Script execution blocked: Domain not in allowed domains list ($currentUrl)"
            )
            delay(3000)
            return
        }

        _engineState.value = _engineState.value.copy(
            statusMessage = "Executing sandboxed JavaScript on Tab $tabIndex..."
        )

        try {
            // Enforce timeout
            withTimeout(5000L) {
                withContext(Dispatchers.Main) {
                    webViewManager.executeJavaScript(tabIndex, script) { result ->
                        _engineState.value = _engineState.value.copy(
                            statusMessage = "Script result on Tab $tabIndex: ${result?.take(50)}"
                        )
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            _engineState.value = _engineState.value.copy(
                errorMessage = "Script execution timed out (>5s) on Tab $tabIndex"
            )
        }

        val delayMs = IntervalCalculator.getNextScrollDelayMs(
            profile.scrollIntervalSec,
            profile.randomizeIntervals,
            profile.minScrollIntervalSec,
            profile.maxScrollIntervalSec
        )
        delay(delayMs)
    }

    private suspend fun recordSessionHistory(status: ProfileStatus) {
        val prof = activeProfile ?: return
        val duration = (System.currentTimeMillis() - startTimeMillis) / 1000
        val historyItem = ExecutionHistoryItem(
            id = UUID.randomUUID().toString(),
            profileId = prof.id,
            profileName = prof.name,
            startedAt = startTimeMillis,
            endedAt = System.currentTimeMillis(),
            durationSeconds = duration,
            totalActions = totalActionsExecuted,
            status = status,
            errorMessage = _engineState.value.errorMessage
        )
        historyRepository.recordSession(historyItem)
    }
}
