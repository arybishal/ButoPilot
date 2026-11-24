package com.autopilot.android.engine

import android.content.Context
import com.autopilot.android.data.local.DefaultProxySeedData
import com.autopilot.android.data.repository.HistoryRepository
import com.autopilot.android.data.repository.ProfileRepository
import com.autopilot.android.data.repository.ProxyRepository
import com.autopilot.android.domain.model.AutomationProfile
import com.autopilot.android.domain.model.ExecutionHistoryItem
import com.autopilot.android.domain.model.ExecutionMode
import com.autopilot.android.domain.model.ProfileStatus
import com.autopilot.android.domain.model.TabState
import com.autopilot.android.domain.model.proxy.ProxyConfiguration
import com.autopilot.android.domain.model.proxy.ProxyHealthStatus
import com.autopilot.android.domain.model.proxy.ProxyProtocol
import com.autopilot.android.domain.validation.IntervalCalculator
import com.autopilot.android.domain.validation.UrlValidator
import com.autopilot.android.feature.diagnostics.execution.ExecutionErrorCategory
import com.autopilot.android.feature.diagnostics.execution.ExecutionEventTracker
import com.autopilot.android.feature.diagnostics.execution.ExecutionEventType
import com.autopilot.android.feature.scripting.AutoHumanBehaviourScript
import com.autopilot.android.feature.proxy.ProxyExecutionCoordinator
import com.autopilot.android.feature.proxy.ProxyHealthChecker
import com.autopilot.android.feature.proxy.source.GeonodeProxySourceAdapter
import com.autopilot.android.feature.proxy.source.ProxiflyProxySourceAdapter
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class EngineState(
    val status: ProfileStatus = ProfileStatus.IDLE,
    val profile: AutomationProfile? = null,
    val activeProfiles: List<AutomationProfile> = emptyList(),
    val activeTabIndex: Int = 0,
    val totalTabs: Int = 1,
    val tabStates: List<TabState> = emptyList(),
    val totalActions: Int = 0,
    val elapsedSeconds: Long = 0,
    val statusMessage: String = "Idle",
    val memoryMb: Long = 0,
    val errorMessage: String? = null,
    val isMultiRun: Boolean = false,
    val currentProxyName: String? = null,
    val nextProxyRotationSeconds: Long = 600L
)

class AutomationCoordinator(
    private val context: Context,
    private val profileRepository: ProfileRepository,
    private val historyRepository: HistoryRepository,
    private val proxyRepository: ProxyRepository? = null,
    private val proxyExecutionCoordinator: ProxyExecutionCoordinator? = null,
    private val eventTracker: ExecutionEventTracker? = null,
    private val deviceSpoofManager: com.autopilot.android.core.system.DeviceSpoofManager? = null,
    private val proxyHealthChecker: ProxyHealthChecker? = null
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val resourceManager = ResourceManager(context)

    private val _engineState = MutableStateFlow(EngineState())
    val engineState: StateFlow<EngineState> = _engineState.asStateFlow()

    private var executionJob: Job? = null
    private var timerJob: Job? = null
    private var proxyRotationJob: Job? = null
    private var isPaused = false

    private var activeProfile: AutomationProfile? = null
    private var activeProfilesList: List<AutomationProfile> = emptyList()
    private var startTimeMillis: Long = 0
    private var totalActionsExecuted = 0
    var currentExecutionId: String = UUID.randomUUID().toString()
        private set

    val webViewManager: WebViewManager = WebViewManager(
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

            // If active proxy causes connection or timeout errors, auto-rotate to another healthy proxy
            val activeProxy = proxyExecutionCoordinator?.activeProxy?.value
            val isProxyRelatedError = errorCode == -8 /* ERROR_TIMEOUT */ ||
                errorCode == -6 /* ERROR_CONNECT */ ||
                errorCode == -15 /* ERROR_PROXY_AUTHENTICATION_FAILED */ ||
                errorCode == -2 /* ERROR_HOST_LOOKUP */ ||
                description.contains("ERR_PROXY", ignoreCase = true) ||
                description.contains("ERR_TUNNEL", ignoreCase = true) ||
                description.contains("timed out", ignoreCase = true)

            if (activeProxy != null && isProxyRelatedError && _engineState.value.status == ProfileStatus.RUNNING) {
                scope.launch {
                    proxyRepository?.updateHealthStatus(
                        activeProxy.id,
                        ProxyHealthStatus.UNHEALTHY,
                        null,
                        "WebView failure $errorCode: $description"
                    )
                    val rotated = rotateToNextHealthyProxy()
                    // Reload tabs immediately so they recover and open the website with updated routing
                    withContext(Dispatchers.Main) {
                        for (i in 0 until _engineState.value.totalTabs) {
                            webViewManager.reloadTab(i)
                        }
                    }
                }
            }
        },
        activeProxyProvider = {
            proxyExecutionCoordinator?.activeProxy?.value
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
     * Starts execution for the given single profile.
     */
    fun start(profile: AutomationProfile) {
        if (_engineState.value.status == ProfileStatus.RUNNING) {
            stop()
        }

        activeProfile = profile
        activeProfilesList = listOf(profile)
        isPaused = false
        startTimeMillis = System.currentTimeMillis()
        totalActionsExecuted = 0
        currentExecutionId = UUID.randomUUID().toString()

        eventTracker?.recordEvent(
            executionId = currentExecutionId,
            profileId = profile.id,
            eventType = ExecutionEventType.EXECUTION_REQUESTED,
            message = "Execution requested for profile '${profile.name}' (${profile.executionMode.displayName})"
        )
        eventTracker?.recordEvent(
            executionId = currentExecutionId,
            profileId = profile.id,
            eventType = ExecutionEventType.PROFILE_VALIDATED,
            message = "Validated profile: ${profile.urls.size} URLs, ${profile.tabCount} tabs"
        )

        _engineState.value = EngineState(
            status = ProfileStatus.RUNNING,
            profile = profile,
            activeProfiles = listOf(profile),
            isMultiRun = false,
            activeTabIndex = 0,
            totalTabs = profile.tabCount.coerceIn(AutomationProfile.MIN_TABS, AutomationProfile.MAX_TABS),
            statusMessage = "Initializing tabs and loading URLs...",
            memoryMb = resourceManager.getAvailableMemoryMb(),
            nextProxyRotationSeconds = 600L
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

        // Start 10-Minute Healthy Proxy Auto-Rotation loop
        startProxyRotationLoop()

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
                        eventTracker?.recordEvent(
                            executionId = currentExecutionId,
                            profileId = profile.id,
                            eventType = ExecutionEventType.EXECUTION_FAILED,
                            errorCategory = ExecutionErrorCategory.PROXY_UNAVAILABLE,
                            message = errMsg
                        )
                        _engineState.value = _engineState.value.copy(
                            status = ProfileStatus.ERROR,
                            statusMessage = errMsg,
                            errorMessage = errMsg
                        )
                        profileRepository.updateStatus(profile.id, ProfileStatus.ERROR)
                        return@launch
                    }

                    val proxyDesc = "${proxy.name} (${proxy.protocol.displayName} ${proxy.host}:${proxy.port})"
                    _engineState.value = _engineState.value.copy(
                        statusMessage = "Applying network isolation: $proxyDesc...",
                        currentProxyName = proxyDesc
                    )
                    eventTracker?.recordEvent(
                        executionId = currentExecutionId,
                        profileId = profile.id,
                        eventType = ExecutionEventType.PROXY_SELECTED,
                        message = "Selected proxy: $proxyDesc"
                    )
                    proxyExecutionCoordinator?.acquireAndApply(proxy)
                    proxyLocked = true
                    eventTracker?.recordEvent(
                        executionId = currentExecutionId,
                        profileId = profile.id,
                        eventType = ExecutionEventType.PROXY_COMPATIBILITY_CHECKED,
                        message = "Proxy routing rules acquired and verified"
                    )
                } else if (profile.enableFreeProxy) {
                    val freeProxy = obtainHealthyFreeProxy()
                    if (freeProxy != null) {
                        val proxyDesc = "${freeProxy.name} (${freeProxy.protocol.displayName} ${freeProxy.host}:${freeProxy.port})"
                        _engineState.value = _engineState.value.copy(
                            statusMessage = "Connected to free proxy: $proxyDesc",
                            currentProxyName = proxyDesc
                        )
                        proxyExecutionCoordinator?.acquireAndApply(freeProxy)
                        proxyLocked = true
                        eventTracker?.recordEvent(
                            executionId = currentExecutionId,
                            profileId = profile.id,
                            eventType = ExecutionEventType.PROXY_SELECTED,
                            message = "Selected free proxy: $proxyDesc"
                        )
                    } else {
                        _engineState.value = _engineState.value.copy(
                            statusMessage = "No working free proxy found, running with direct connection..."
                        )
                        proxyExecutionCoordinator?.acquireAndApply(null)
                        proxyLocked = true
                    }
                } else {
                    proxyExecutionCoordinator?.acquireAndApply(null)
                    proxyLocked = true
                }

                if (profile.enableSpoofing) {
                    val spoofManager = deviceSpoofManager
                    val result = if (spoofManager != null &&
                        com.autopilot.android.core.system.RootChecker.checkRootAccess()
                    ) spoofManager.apply() else null
                    eventTracker?.recordEvent(
                        executionId = currentExecutionId,
                        profileId = profile.id,
                        eventType = ExecutionEventType.PROFILE_VALIDATED,
                        message = when {
                            spoofManager == null -> "Device spoofing unavailable (manager not configured)"
                            result == null -> "Device spoofing requested (root not granted)"
                            result.ok -> "Device spoofing applied to ${result.applied} properties"
                            else -> "Device spoofing partly applied (${result.applied} ok, ${result.failed} failed)"
                        }
                    )
                }

                // Update profile status in DB
                profileRepository.updateStatus(profile.id, ProfileStatus.RUNNING)

                runAutomationLoop(profile)

            } catch (e: CancellationException) {
                // Normal cancellation on stop
            } catch (e: Exception) {
                eventTracker?.recordEvent(
                    executionId = currentExecutionId,
                    profileId = profile.id,
                    eventType = ExecutionEventType.EXECUTION_FAILED,
                    errorCategory = ExecutionErrorCategory.UNKNOWN_ERROR,
                    message = "Execution loop encountered exception: ${e.message}"
                )
                _engineState.value = _engineState.value.copy(
                    status = ProfileStatus.ERROR,
                    statusMessage = "Error: ${e.message}",
                    errorMessage = e.message
                )
                profileRepository.updateStatus(profile.id, ProfileStatus.ERROR)
            } finally {
                proxyRotationJob?.cancel()
                webViewManager.setScriptOnPageFinished(null)
                deviceSpoofManager?.restore()
                if (proxyLocked) {
                    proxyExecutionCoordinator?.resetAndRelease()
                }
                recordSessionHistory(ProfileStatus.COMPLETED)
            }
        }
    }

    /**
     * Starts execution for multiple profiles concurrently.
     */
    fun startMultiple(profiles: List<AutomationProfile>) {
        if (profiles.isEmpty()) return
        if (profiles.size == 1) {
            start(profiles.first())
            return
        }

        if (_engineState.value.status == ProfileStatus.RUNNING) {
            stop()
        }

        activeProfilesList = profiles
        activeProfile = profiles.first()
        isPaused = false
        startTimeMillis = System.currentTimeMillis()
        totalActionsExecuted = 0
        currentExecutionId = UUID.randomUUID().toString()

        val totalTabsSum = profiles.sumOf { it.tabCount.coerceIn(1, 200) }.coerceIn(1, 200)

        _engineState.value = EngineState(
            status = ProfileStatus.RUNNING,
            profile = profiles.first(),
            activeProfiles = profiles,
            isMultiRun = true,
            activeTabIndex = 0,
            totalTabs = totalTabsSum,
            statusMessage = "Starting ${profiles.size} automations concurrently across $totalTabsSum total tabs...",
            memoryMb = resourceManager.getAvailableMemoryMb(),
            nextProxyRotationSeconds = 600L
        )

        // Elapsed seconds timer
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

        // 10-Minute Proxy Auto-Rotation loop
        startProxyRotationLoop()

        // Multi-Profile Execution loop
        executionJob?.cancel()
        executionJob = scope.launch {
            var proxyLocked = false
            try {
                // Initialize proxy if any profile requests proxy or free proxy
                val proxyProfile = profiles.firstOrNull { !it.proxyId.isNullOrBlank() || it.enableFreeProxy }
                if (proxyProfile != null) {
                    val configuredProxyId = proxyProfile.proxyId
                    if (!configuredProxyId.isNullOrBlank()) {
                        val proxy = proxyRepository?.getProxyById(configuredProxyId)
                        if (proxy != null && proxy.enabled) {
                            val desc = "${proxy.name} (${proxy.protocol.displayName} ${proxy.host}:${proxy.port})"
                            _engineState.value = _engineState.value.copy(currentProxyName = desc)
                            proxyExecutionCoordinator?.acquireAndApply(proxy)
                            proxyLocked = true
                        }
                    } else if (proxyProfile.enableFreeProxy) {
                        val freeProxy = obtainHealthyFreeProxy()
                        if (freeProxy != null) {
                            val desc = "${freeProxy.name} (${freeProxy.protocol.displayName} ${freeProxy.host}:${freeProxy.port})"
                            _engineState.value = _engineState.value.copy(
                                statusMessage = "Connected to free proxy: $desc",
                                currentProxyName = desc
                            )
                            proxyExecutionCoordinator?.acquireAndApply(freeProxy)
                            proxyLocked = true
                        } else {
                            proxyExecutionCoordinator?.acquireAndApply(null)
                            proxyLocked = true
                        }
                    }
                } else {
                    proxyExecutionCoordinator?.acquireAndApply(null)
                    proxyLocked = true
                }

                // Mark all profiles running in database
                profiles.forEach { p ->
                    profileRepository.updateStatus(p.id, ProfileStatus.RUNNING)
                }

                // Partition and initialize tabs per profile
                var globalTabOffset = 0
                val profileTabRanges = mutableListOf<Triple<AutomationProfile, Int, Int>>()

                withContext(Dispatchers.Main) {
                    for (prof in profiles) {
                        val tabCount = prof.tabCount.coerceIn(1, 200)
                        val startIdx = globalTabOffset
                        val endIdx = (startIdx + tabCount).coerceAtMost(200)
                        profileTabRanges.add(Triple(prof, startIdx, endIdx))

                        val urls = if (prof.urls.isNotEmpty()) prof.urls else listOf("https://example.com")
                        for (tabIdx in startIdx until endIdx) {
                            val targetUrl = urls[(tabIdx - startIdx) % urls.size]
                            webViewManager.createOrGetTab(tabIdx, prof)
                            webViewManager.loadUrl(tabIdx, targetUrl)
                        }
                        globalTabOffset = endIdx
                        if (globalTabOffset >= 200) break
                    }
                    if (globalTabOffset > 0) {
                        webViewManager.selectTab(0)
                    }
                }

                _engineState.value = _engineState.value.copy(
                    totalTabs = globalTabOffset,
                    statusMessage = "All ${profiles.size} automations running concurrently across $globalTabOffset tabs."
                )

                // Launch parallel worker for each profile
                coroutineScope {
                    profileTabRanges.forEach { (prof, startIdx, endIdx) ->
                        launch {
                            runProfileTabLoop(prof, startIdx, endIdx)
                        }
                    }
                }

            } catch (e: CancellationException) {
                // Stopped normally
            } catch (e: Exception) {
                _engineState.value = _engineState.value.copy(
                    status = ProfileStatus.ERROR,
                    statusMessage = "Multi-Run Error: ${e.message}",
                    errorMessage = e.message
                )
                profiles.forEach { p ->
                    profileRepository.updateStatus(p.id, ProfileStatus.ERROR)
                }
            } finally {
                proxyRotationJob?.cancel()
                webViewManager.setScriptOnPageFinished(null)
                deviceSpoofManager?.restore()
                if (proxyLocked) {
                    proxyExecutionCoordinator?.resetAndRelease()
                }
                recordSessionHistory(ProfileStatus.COMPLETED)
            }
        }
    }

    private suspend fun runProfileTabLoop(profile: AutomationProfile, startTab: Int, endTab: Int) {
        val tabSpan = (endTab - startTab).coerceAtLeast(1)
        var relativeTab = 0

        while (scope.isActive) {
            if (resourceManager.isMemoryCriticallyLow()) {
                delay(5000)
                continue
            }
            while (isPaused && scope.isActive) {
                delay(500)
            }
            if (!scope.isActive) break

            val currentTab = startTab + relativeTab

            when (profile.executionMode) {
                ExecutionMode.SIMPLE_SCROLL_REFRESH -> {
                    executeSimpleScrollRefresh(profile, currentTab)
                }
                ExecutionMode.DEEP_SCROLL -> {
                    executeDeepScroll(profile, currentTab)
                }
                ExecutionMode.AUTO_HUMAN_BEHAVIOUR -> {
                    delay(1000L)
                }
                ExecutionMode.TAB_SWITCHING -> {
                    executeTabSwitching(profile, currentTab)
                    relativeTab = (relativeTab + 1) % tabSpan
                }
                ExecutionMode.CUSTOM_JAVASCRIPT -> {
                    executeSandboxedJavaScript(profile, currentTab)
                }
            }

            totalActionsExecuted++
            _engineState.value = _engineState.value.copy(
                totalActions = totalActionsExecuted
            )
        }
    }

    /**
     * Starts background 10-minute proxy rotation loop.
     */
    private fun startProxyRotationLoop() {
        proxyRotationJob?.cancel()
        proxyRotationJob = scope.launch {
            var seconds = 600L
            while (isActive) {
                delay(1000L)
                if (!isPaused && _engineState.value.status == ProfileStatus.RUNNING) {
                    seconds--
                    _engineState.value = _engineState.value.copy(
                        nextProxyRotationSeconds = seconds
                    )
                    if (seconds <= 0) {
                        rotateToNextHealthyProxy()
                        seconds = 600L
                    }
                }
            }
        }
    }

    /**
     * Resolves and verifies a healthy free proxy.
     * If the local database has no proxies, automatically imports fresh ones from public sources.
     * Verifies proxy health to prevent hanging on dead or broken proxies.
     */
    suspend fun obtainHealthyFreeProxy(): ProxyConfiguration? {
        val repo = proxyRepository ?: return null
        val checker = proxyHealthChecker

        var allProxies = repo.getAllProxies().filter { it.enabled }

        // If repository is empty or only has dead proxies, fetch fresh free proxies
        if (allProxies.isEmpty() || allProxies.all { it.lastHealthStatus == ProxyHealthStatus.UNHEALTHY || it.lastHealthStatus == ProxyHealthStatus.TIMEOUT }) {
            _engineState.value = _engineState.value.copy(
                statusMessage = "Fetching fresh free proxies from network..."
            )
            try {
                val proxifly = ProxiflyProxySourceAdapter()
                val res = proxifly.fetchProxies(protocolFilter = ProxyProtocol.HTTP)
                if (res.success && res.validProxies.isNotEmpty()) {
                    repo.saveProxies(res.validProxies.take(30))
                    allProxies = repo.getAllProxies().filter { it.enabled }
                }
            } catch (e: Exception) {
                try {
                    val geonode = GeonodeProxySourceAdapter()
                    val geoRes = geonode.fetchProxies()
                    if (geoRes.success && geoRes.validProxies.isNotEmpty()) {
                        repo.saveProxies(geoRes.validProxies.take(30))
                        allProxies = repo.getAllProxies().filter { it.enabled }
                    }
                } catch (e2: Exception) {
                    // Ignore fallback failure
                }
            }
            if (allProxies.isEmpty()) {
                repo.saveProxies(DefaultProxySeedData.getSeedProxies())
                allProxies = repo.getAllProxies().filter { it.enabled }
            }
        }

        if (allProxies.isEmpty()) return null

        val prefs = context.getSharedPreferences("proxy_settings", Context.MODE_PRIVATE)
        val preferredRegion = prefs.getString("preferred_proxy_region", "AUTO") ?: "AUTO"

        // Sort: preferred region first, verified HEALTHY first (by lowest latency), then UNTESTED, then others
        val sorted = allProxies.sortedWith(
            compareBy<ProxyConfiguration> {
                if (preferredRegion != "AUTO") {
                    val matches = if (preferredRegion == "UNKNOWN") it.region.isNullOrBlank()
                    else it.region?.equals(preferredRegion, ignoreCase = true) == true
                    if (matches) 0 else 1
                } else 0
            }.thenBy {
                when (it.lastHealthStatus) {
                    ProxyHealthStatus.HEALTHY -> 0
                    ProxyHealthStatus.UNTESTED -> 1
                    else -> 2
                }
            }.thenBy { it.lastLatencyMs ?: Long.MAX_VALUE }
        )

        val now = System.currentTimeMillis()
        // Probe candidates to guarantee the proxy can route HTTP traffic cleanly
        for (candidate in sorted.take(8)) {
            val isRecentlyHealthy = candidate.lastHealthStatus == ProxyHealthStatus.HEALTHY &&
                candidate.lastTestedAt != null &&
                (now - candidate.lastTestedAt) < 180_000L // Verified in last 3 min

            if (isRecentlyHealthy) {
                return candidate
            }

            if (checker != null) {
                _engineState.value = _engineState.value.copy(
                    statusMessage = "Testing free proxy reachability: ${candidate.host}:${candidate.port}..."
                )
                try {
                    val testResult = withTimeoutOrNull(3500L) {
                        checker.checkHealth(candidate)
                    }
                    if (testResult != null) {
                        repo.updateHealthStatus(candidate.id, testResult.status, testResult.latencyMs, testResult.errorMessage)
                        if (testResult.status == ProxyHealthStatus.HEALTHY) {
                            return candidate.copy(
                                lastHealthStatus = ProxyHealthStatus.HEALTHY,
                                lastLatencyMs = testResult.latencyMs,
                                lastTestedAt = System.currentTimeMillis()
                            )
                        }
                    } else {
                        repo.updateHealthStatus(candidate.id, ProxyHealthStatus.TIMEOUT, 3500L, "Health probe timed out")
                    }
                } catch (e: Exception) {
                    repo.updateHealthStatus(candidate.id, ProxyHealthStatus.UNHEALTHY, null, e.message)
                }
            } else {
                return candidate
            }
        }

        // Return null if no proxy is verified healthy, allowing safe fallback to direct connection
        return null
    }

    /**
     * Rotates to the next healthy, verified proxy in the database.
     */
    suspend fun rotateToNextHealthyProxy(): Boolean {
        val repo = proxyRepository ?: return false
        var allProxies = repo.getAllProxies().filter { it.enabled }

        // If proxies are empty or all marked unhealthy, try to fetch fresh proxies
        if (allProxies.isEmpty() || allProxies.all { it.lastHealthStatus == ProxyHealthStatus.UNHEALTHY || it.lastHealthStatus == ProxyHealthStatus.TIMEOUT }) {
            try {
                val proxifly = ProxiflyProxySourceAdapter()
                val res = proxifly.fetchProxies(protocolFilter = ProxyProtocol.HTTP)
                if (res.success && res.validProxies.isNotEmpty()) {
                    repo.saveProxies(res.validProxies.take(30))
                    allProxies = repo.getAllProxies().filter { it.enabled }
                }
            } catch (e: Exception) {
                // Ignore fallback
            }
            if (allProxies.isEmpty()) {
                repo.saveProxies(DefaultProxySeedData.getSeedProxies())
                allProxies = repo.getAllProxies().filter { it.enabled }
            }
        }

        if (allProxies.isEmpty()) return false

        val currentActive = proxyExecutionCoordinator?.activeProxy?.value
        val candidates = if (allProxies.size > 1 && currentActive != null) {
            allProxies.filter { it.id != currentActive.id } + listOf(currentActive)
        } else {
            allProxies
        }

        for (candidate in candidates) {
            val isHealthy = if (proxyHealthChecker != null) {
                try {
                    val result = withTimeoutOrNull(5000L) {
                        proxyHealthChecker.checkHealth(candidate)
                    }
                    if (result != null) {
                        repo.updateHealthStatus(candidate.id, result.status, result.latencyMs, result.errorMessage)
                        result.status == ProxyHealthStatus.HEALTHY
                    } else {
                        repo.updateHealthStatus(candidate.id, ProxyHealthStatus.TIMEOUT, 5000L, "Health check timed out")
                        false
                    }
                } catch (e: Exception) {
                    false
                }
            } else {
                true
            }

            if (isHealthy) {
                proxyExecutionCoordinator?.updateProxy(candidate)
                val proxyDesc = "${candidate.name} (${candidate.protocol.displayName} ${candidate.host}:${candidate.port})"
                _engineState.value = _engineState.value.copy(
                    currentProxyName = proxyDesc,
                    nextProxyRotationSeconds = 600L,
                    statusMessage = "Auto-rotated to healthy proxy: ${candidate.name}"
                )
                eventTracker?.recordEvent(
                    executionId = currentExecutionId,
                    profileId = activeProfile?.id ?: "multi",
                    eventType = ExecutionEventType.PROXY_SELECTED,
                    message = "10-Minute Auto-Rotation: Switched to healthy proxy $proxyDesc"
                )
                // Reload active tabs so that navigation resumes cleanly on the newly active proxy
                withContext(Dispatchers.Main) {
                    for (i in 0 until _engineState.value.totalTabs) {
                        webViewManager.reloadTab(i)
                    }
                }
                return true
            }
        }
        return false
    }

    /**
     * Manually triggers immediate rotation to the next healthy proxy.
     */
    fun rotateProxyNow() {
        scope.launch {
            rotateToNextHealthyProxy()
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
                eventTracker?.recordEvent(
                    executionId = currentExecutionId,
                    profileId = it.id,
                    eventType = ExecutionEventType.EXECUTION_PAUSED,
                    message = "Automation loop paused"
                )
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
                eventTracker?.recordEvent(
                    executionId = currentExecutionId,
                    profileId = it.id,
                    eventType = ExecutionEventType.EXECUTION_RESUMED,
                    message = "Automation loop resumed"
                )
                scope.launch { profileRepository.updateStatus(it.id, ProfileStatus.RUNNING) }
            }
        }
    }

    /**
     * Stops the automation, clears resources, and destroys WebViews.
     */
    fun stop() {
        isPaused = false
        proxyRotationJob?.cancel()
        proxyRotationJob = null
        executionJob?.cancel()
        timerJob?.cancel()
        executionJob = null
        timerJob = null

        val profs = activeProfilesList
        val prof = activeProfile
        val status = _engineState.value.status

        _engineState.value = _engineState.value.copy(
            status = ProfileStatus.IDLE,
            statusMessage = "Automation Stopped",
            activeTabIndex = 0,
            activeProfiles = emptyList(),
            isMultiRun = false
        )

        webViewManager.destroyAll()
        webViewManager.setScriptOnPageFinished(null)

        scope.launch {
            try {
                proxyExecutionCoordinator?.resetAndRelease()
            } catch (e: Exception) {
                // Safe cleanup
            }
            profs.forEach { p ->
                profileRepository.updateStatus(p.id, ProfileStatus.IDLE)
            }
            if (prof != null && prof !in profs) {
                profileRepository.updateStatus(prof.id, ProfileStatus.IDLE)
            }
            recordSessionHistory(if (status == ProfileStatus.ERROR) ProfileStatus.ERROR else ProfileStatus.COMPLETED)
        }
    }

    private suspend fun runAutomationLoop(profile: AutomationProfile) {
        val totalTabs = profile.tabCount.coerceIn(AutomationProfile.MIN_TABS, AutomationProfile.MAX_TABS)
        val urls = if (profile.urls.isNotEmpty()) profile.urls else listOf("https://example.com")

        webViewManager.setScriptOnPageFinished(
            if (profile.executionMode == ExecutionMode.AUTO_HUMAN_BEHAVIOUR) AutoHumanBehaviourScript.SCRIPT else null
        )

        // Step 1: Initialize all configured tabs on UI thread
        withContext(Dispatchers.Main) {
            for (tabIdx in 0 until totalTabs) {
                val targetUrl = urls[tabIdx % urls.size]
                webViewManager.createOrGetTab(tabIdx, profile)
                eventTracker?.recordEvent(
                    executionId = currentExecutionId,
                    profileId = profile.id,
                    eventType = ExecutionEventType.WEBVIEW_INITIALIZED,
                    message = "Tab $tabIdx initialized"
                )
                webViewManager.loadUrl(tabIdx, targetUrl)
                eventTracker?.recordEvent(
                    executionId = currentExecutionId,
                    profileId = profile.id,
                    eventType = ExecutionEventType.URL_NAVIGATION_STARTED,
                    message = "Tab $tabIdx loading: ${ExecutionEventTracker.sanitizeUrl(targetUrl)}"
                )
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
                ExecutionMode.AUTO_HUMAN_BEHAVIOUR -> {
                    delay(1000L)
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

        if (profile.allowedDomains.isNotEmpty() && !UrlValidator.isDomainAllowed(currentUrl, profile.allowedDomains)) {
            _engineState.value = _engineState.value.copy(
                statusMessage = "Script execution blocked: Domain not in allowed domains list ($currentUrl)"
            )
            delay(3000)
            return
        }

        _engineState.value = _engineState.value.copy(
            statusMessage = "Executing JavaScript on Tab $tabIndex (${script.length} chars)..."
        )

        val timeoutMs = (profile.timeoutSeconds.coerceAtLeast(60) * 1000L)
        try {
            withTimeout(timeoutMs) {
                withContext(Dispatchers.Main) {
                    webViewManager.executeJavaScript(tabIndex, script) { result ->
                        val clean = result?.trim('"', ' ', '\n', '\r')
                        val status = when {
                            clean.isNullOrBlank() || clean == "null" || clean == "undefined" ->
                                "Script executed successfully on Tab ${tabIndex + 1}"
                            clean.length > 60 ->
                                "Tab ${tabIndex + 1}: ${clean.take(60)}..."
                            else ->
                                "Tab ${tabIndex + 1}: $clean"
                        }
                        _engineState.value = _engineState.value.copy(
                            statusMessage = status
                        )
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            _engineState.value = _engineState.value.copy(
                errorMessage = "Script execution timed out (>${timeoutMs / 1000}s) on Tab $tabIndex"
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
            id = currentExecutionId,
            profileId = prof.id,
            profileName = if (activeProfilesList.size > 1) "Multi-Run (${activeProfilesList.size} Profiles)" else prof.name,
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
