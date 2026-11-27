package com.autopilot.android.feature.proxy

import com.autopilot.android.data.repository.ProxyRepository
import com.autopilot.android.domain.model.proxy.ProxyConfiguration
import com.autopilot.android.domain.model.proxy.ProxyHealthStatus
import com.autopilot.android.domain.model.proxy.ProxyTestResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class BackgroundProxyGenerationState(
    val isRunning: Boolean = false,
    val isDialogVisible: Boolean = false,
    val runInBackground: Boolean = false,
    val checkedCount: Int = 0,
    val totalToCheck: Int = 0,
    val healthyFoundCount: Int = 0,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val completedSummary: String? = null
) {
    val progressFraction: Float
        get() = if (totalToCheck > 0) (checkedCount.toFloat() / totalToCheck).coerceIn(0f, 1f) else 0f
}

/**
 * Manages Automatic Proxy Generator execution in an application-scoped coroutine so
 * users can view progress in the pop-up dialog OR minimize it to run in the background
 * while performing other automations.
 */
class AutomaticProxyGeneratorManager(
    private val proxyRepository: ProxyRepository,
    private val healthChecker: ProxyHealthChecker,
    private val generator: AutomaticProxyGenerator = AutomaticProxyGenerator()
) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeJob: Job? = null

    private val _state = MutableStateFlow(BackgroundProxyGenerationState())
    val state: StateFlow<BackgroundProxyGenerationState> = _state.asStateFlow()

    fun openDialog() {
        _state.value = _state.value.copy(
            isDialogVisible = true,
            runInBackground = false,
            errorMessage = null
        )
    }

    fun minimizeToBackground() {
        _state.value = _state.value.copy(
            isDialogVisible = false,
            runInBackground = true
        )
    }

    fun dismissDialog() {
        if (_state.value.isRunning) {
            minimizeToBackground()
        } else {
            _state.value = _state.value.copy(
                isDialogVisible = false,
                errorMessage = null
            )
        }
    }

    fun clearCompletedSummary() {
        _state.value = _state.value.copy(completedSummary = null)
    }

    fun cancel() {
        activeJob?.cancel()
        activeJob = null
        _state.value = _state.value.copy(
            isRunning = false,
            statusMessage = null,
            checkedCount = 0,
            totalToCheck = 0,
            completedSummary = "Proxy generation stopped."
        )
    }

    fun startGeneration(
        request: ProxyGenerationRequest,
        onlyAddHealthy: Boolean,
        autoTestAfterAdd: Boolean,
        existingProxies: List<ProxyConfiguration>,
        startInBackground: Boolean = false
    ) {
        activeJob?.cancel()
        _state.value = BackgroundProxyGenerationState(
            isRunning = true,
            isDialogVisible = !startInBackground,
            runInBackground = startInBackground,
            statusMessage = "Fetching proxy sources..."
        )

        activeJob = appScope.launch {
            try {
                val res = generator.generate(
                    request = request,
                    existingProxies = existingProxies
                )

                if (!res.success || res.generatedProxies.isEmpty()) {
                    _state.value = _state.value.copy(
                        isRunning = false,
                        statusMessage = null,
                        errorMessage = res.errorMessage ?: "No new proxies matched your selection."
                    )
                    return@launch
                }

                val candidates = res.generatedProxies
                val sourcesLabel = if (res.sourcesUsed.isNotEmpty()) {
                    " from ${res.sourcesUsed.joinToString(" & ")}"
                } else {
                    ""
                }

                if (onlyAddHealthy) {
                    var checked = 0
                    var healthySaved = 0
                    _state.value = _state.value.copy(
                        totalToCheck = candidates.size,
                        checkedCount = 0,
                        healthyFoundCount = 0,
                        statusMessage = "Live checking proxies (0/${candidates.size})..."
                    )

                    for (chunk in candidates.chunked(8)) {
                        if (!isActive) break
                        val results = mutableMapOf<String, ProxyTestResult>()
                        coroutineScope {
                            chunk.map { proxy ->
                                async { proxy.id to healthChecker.checkHealth(proxy) }
                            }.awaitAll().forEach { (id, result) -> results[id] = result }
                        }

                        val healthyChunk = keepHealthyProxies(chunk, results)
                        if (healthyChunk.isNotEmpty()) {
                            proxyRepository.saveProxies(healthyChunk)
                            healthySaved += healthyChunk.size
                        }

                        checked += chunk.size
                        _state.value = _state.value.copy(
                            checkedCount = checked,
                            healthyFoundCount = healthySaved,
                            statusMessage = "Live checking proxies ($checked/${candidates.size}) - $healthySaved healthy"
                        )
                    }

                    if (healthySaved == 0) {
                        _state.value = _state.value.copy(
                            isRunning = false,
                            statusMessage = null,
                            errorMessage = "No healthy proxies found among ${candidates.size} generated. Try another tier, country or quantity."
                        )
                    } else {
                        _state.value = _state.value.copy(
                            isRunning = false,
                            isDialogVisible = false,
                            statusMessage = null,
                            completedSummary = "Added $healthySaved healthy proxies$sourcesLabel."
                        )
                    }
                } else {
                    proxyRepository.saveProxies(candidates)

                    if (autoTestAfterAdd) {
                        var checked = 0
                        var healthyCount = 0
                        _state.value = _state.value.copy(
                            totalToCheck = candidates.size,
                            checkedCount = 0,
                            healthyFoundCount = 0,
                            statusMessage = "Testing added proxies (0/${candidates.size})..."
                        )

                        for (chunk in candidates.chunked(6)) {
                            if (!isActive) break
                            val results = coroutineScope {
                                chunk.map { proxy ->
                                    async { proxy to healthChecker.checkHealth(proxy) }
                                }.awaitAll()
                            }
                            for ((proxy, result) in results) {
                                proxyRepository.updateHealthStatus(
                                    proxy.id,
                                    result.status,
                                    result.latencyMs,
                                    result.errorMessage
                                )
                                if (result.status == ProxyHealthStatus.HEALTHY) {
                                    healthyCount++
                                }
                            }
                            checked += chunk.size
                            _state.value = _state.value.copy(
                                checkedCount = checked,
                                healthyFoundCount = healthyCount,
                                statusMessage = "Testing added proxies ($checked/${candidates.size}) - $healthyCount healthy"
                            )
                        }

                        _state.value = _state.value.copy(
                            isRunning = false,
                            isDialogVisible = false,
                            statusMessage = null,
                            completedSummary = "Generated ${candidates.size} proxies$sourcesLabel ($healthyCount healthy)."
                        )
                    } else {
                        _state.value = _state.value.copy(
                            isRunning = false,
                            isDialogVisible = false,
                            statusMessage = null,
                            completedSummary = "Generated ${candidates.size} proxies$sourcesLabel."
                        )
                    }
                }
            } catch (_: CancellationException) {
                // Handled by cancel()
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isRunning = false,
                    statusMessage = null,
                    errorMessage = e.message ?: "Proxy generation failed."
                )
            } finally {
                activeJob = null
            }
        }
    }
}
