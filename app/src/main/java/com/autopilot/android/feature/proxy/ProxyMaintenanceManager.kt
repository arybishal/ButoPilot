package com.autopilot.android.feature.proxy

import android.content.Context
import com.autopilot.android.data.repository.ProxyRepository
import com.autopilot.android.domain.model.proxy.ProxyConfiguration
import com.autopilot.android.domain.model.proxy.ProxyHealthStatus
import com.autopilot.android.domain.model.proxy.ProxyTestResult
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

data class DeletedProxyLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val host: String,
    val port: Int,
    val protocol: String,
    val region: String?,
    val reason: String,
    val deletedAt: Long = System.currentTimeMillis()
)

data class ProxyMaintenanceSettings(
    val autoBackgroundHealthCheck: Boolean = true,
    val autoDeleteDeadProxies: Boolean = true,
    val autoFetchHealthyProxies: Boolean = true,
    val targetHealthyPoolSize: Int = 100
)

data class BackgroundMaintenanceStatus(
    val isRunningCycle: Boolean = false,
    val statusMessage: String = "Background proxy monitor idle",
    val lastCycleAt: Long? = null,
    val deletedThisSession: Int = 0,
    val addedThisSession: Int = 0
)

/**
 * Continuously monitors proxy health in the background without disturbing active automations,
 * automatically drops & deletes dead proxies with a persistent log, and searches/adds
 * verified healthy proxies when the healthy pool falls below the configured target.
 */
class ProxyMaintenanceManager(
    context: Context,
    private val proxyRepository: ProxyRepository,
    private val healthChecker: ProxyHealthChecker,
    private val proxyExecutionCoordinator: ProxyExecutionCoordinator,
    private val generator: AutomaticProxyGenerator = AutomaticProxyGenerator(),
    private val gson: Gson = Gson()
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var backgroundJob: Job? = null

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<ProxyMaintenanceSettings> = _settings.asStateFlow()

    private val _deletedLogs = MutableStateFlow(loadLogs())
    val deletedLogs: StateFlow<List<DeletedProxyLogEntry>> = _deletedLogs.asStateFlow()

    private val _status = MutableStateFlow(BackgroundMaintenanceStatus())
    val status: StateFlow<BackgroundMaintenanceStatus> = _status.asStateFlow()

    companion object {
        private const val PREFS_NAME = "proxy_maintenance"
        private const val KEY_AUTO_CHECK = "auto_bg_check"
        private const val KEY_AUTO_DELETE = "auto_delete_dead"
        private const val KEY_AUTO_FETCH = "auto_fetch_healthy"
        private const val KEY_TARGET_POOL = "target_healthy_pool"
        private const val KEY_DELETED_LOGS = "deleted_proxy_logs"
        private const val MAX_LOG_ENTRIES = 300
        private const val CYCLE_INTERVAL_MS = 90_000L
    }

    fun startBackgroundLoop() {
        if (backgroundJob?.isActive == true) return
        backgroundJob = scope.launch {
            delay(8_000L)
            while (isActive) {
                try {
                    runMaintenanceCycle()
                } catch (_: Exception) {
                    // Keep background monitor resilient
                }
                delay(CYCLE_INTERVAL_MS)
            }
        }
    }

    fun triggerImmediateCycle() {
        scope.launch {
            try {
                runMaintenanceCycle()
            } catch (_: Exception) {
                // Ignore transient errors
            }
        }
    }

    fun updateSettings(transform: (ProxyMaintenanceSettings) -> ProxyMaintenanceSettings) {
        val updated = transform(_settings.value)
        _settings.value = updated
        prefs.edit()
            .putBoolean(KEY_AUTO_CHECK, updated.autoBackgroundHealthCheck)
            .putBoolean(KEY_AUTO_DELETE, updated.autoDeleteDeadProxies)
            .putBoolean(KEY_AUTO_FETCH, updated.autoFetchHealthyProxies)
            .putInt(KEY_TARGET_POOL, updated.targetHealthyPoolSize.coerceIn(20, 500))
            .apply()
    }

    suspend fun dropDeadProxy(proxy: ProxyConfiguration, reason: String) {
        if (!_settings.value.autoDeleteDeadProxies) {
            proxyRepository.updateHealthStatus(proxy.id, ProxyHealthStatus.UNHEALTHY, null, reason)
            return
        }
        proxyRepository.deleteProxy(proxy.id)
        recordDeletedProxy(proxy, reason)
    }

    fun recordDeletedProxy(proxy: ProxyConfiguration, reason: String) {
        val entry = DeletedProxyLogEntry(
            host = proxy.host,
            port = proxy.port,
            protocol = proxy.protocol.displayName,
            region = proxy.region,
            reason = reason.ifBlank { "Health check failed" }
        )
        val updated = (listOf(entry) + _deletedLogs.value).take(MAX_LOG_ENTRIES)
        _deletedLogs.value = updated
        saveLogs(updated)
        _status.value = _status.value.copy(
            deletedThisSession = _status.value.deletedThisSession + 1
        )
    }

    fun clearDeletedLogs() {
        _deletedLogs.value = emptyList()
        prefs.edit().remove(KEY_DELETED_LOGS).apply()
    }

    private suspend fun runMaintenanceCycle() {
        val currentSettings = _settings.value
        if (!currentSettings.autoBackgroundHealthCheck && !currentSettings.autoFetchHealthyProxies) {
            _status.value = _status.value.copy(
                isRunningCycle = false,
                statusMessage = "Background proxy maintenance paused"
            )
            return
        }

        _status.value = _status.value.copy(
            isRunningCycle = true,
            statusMessage = "Background: Checking proxy pool..."
        )

        val activeProxyId = proxyExecutionCoordinator.activeProxy.value?.id
        var allProxies = proxyRepository.getAllProxies()

        // 1. Delete any already-known unhealthy/timeout proxies (except active proxy)
        if (currentSettings.autoDeleteDeadProxies) {
            val knownDead = allProxies.filter {
                it.id != activeProxyId &&
                    (it.lastHealthStatus == ProxyHealthStatus.UNHEALTHY ||
                        it.lastHealthStatus == ProxyHealthStatus.TIMEOUT ||
                        it.lastHealthStatus == ProxyHealthStatus.AUTH_FAILURE)
            }
            for (dead in knownDead) {
                proxyRepository.deleteProxy(dead.id)
                recordDeletedProxy(dead, dead.lastErrorMessage ?: dead.lastHealthStatus.displayName)
            }
            if (knownDead.isNotEmpty()) {
                allProxies = proxyRepository.getAllProxies()
            }
        }

        // 2. Background health check on a batch of enabled proxies (never touching activeProxyId)
        if (currentSettings.autoBackgroundHealthCheck) {
            val checkTargets = allProxies
                .filter { it.enabled && it.id != activeProxyId }
                .sortedBy { it.lastTestedAt ?: 0L }
                .take(24)

            for (chunk in checkTargets.chunked(6)) {
                val results = coroutineScope {
                    chunk.map { p ->
                        async { p to healthChecker.checkHealth(p) }
                    }.awaitAll()
                }
                for ((proxy, result) in results) {
                    if (result.status == ProxyHealthStatus.HEALTHY) {
                        proxyRepository.updateHealthStatus(proxy.id, result.status, result.latencyMs, null)
                    } else if (currentSettings.autoDeleteDeadProxies) {
                        proxyRepository.deleteProxy(proxy.id)
                        recordDeletedProxy(proxy, result.errorMessage ?: result.status.displayName)
                    } else {
                        proxyRepository.updateHealthStatus(proxy.id, result.status, result.latencyMs, result.errorMessage)
                    }
                }
            }
            allProxies = proxyRepository.getAllProxies()
        }

        // 3. Auto-search and add verified healthy proxies if below target pool size
        val healthyCount = allProxies.count { it.lastHealthStatus == ProxyHealthStatus.HEALTHY }
        if (currentSettings.autoFetchHealthyProxies && healthyCount < currentSettings.targetHealthyPoolSize) {
            val needed = (currentSettings.targetHealthyPoolSize - healthyCount).coerceIn(10, 150)
            _status.value = _status.value.copy(
                statusMessage = "Background: Searching for healthy proxies ($healthyCount/${currentSettings.targetHealthyPoolSize})..."
            )
            val fetchRes = generator.generate(
                request = ProxyGenerationRequest(
                    countries = emptySet(),
                    tier = ProxyTier.TOP_TIER,
                    quantity = (needed * 3).coerceAtMost(300)
                ),
                existingProxies = allProxies
            )

            if (fetchRes.success && fetchRes.generatedProxies.isNotEmpty()) {
                var addedNow = 0
                for (chunk in fetchRes.generatedProxies.chunked(8)) {
                    if (addedNow >= needed) break
                    val results = mutableMapOf<String, ProxyTestResult>()
                    coroutineScope {
                        chunk.map { p ->
                            async { p.id to healthChecker.checkHealth(p) }
                        }.awaitAll().forEach { (id, res) -> results[id] = res }
                    }
                    val healthyChunk = keepHealthyProxies(chunk, results)
                    if (healthyChunk.isNotEmpty()) {
                        proxyRepository.saveProxies(healthyChunk)
                        addedNow += healthyChunk.size
                        _status.value = _status.value.copy(
                            addedThisSession = _status.value.addedThisSession + healthyChunk.size
                        )
                    }
                }
            }
        }

        val finalHealthy = proxyRepository.getAllProxies().count { it.lastHealthStatus == ProxyHealthStatus.HEALTHY }
        _status.value = _status.value.copy(
            isRunningCycle = false,
            lastCycleAt = System.currentTimeMillis(),
            statusMessage = "Background monitor active: $finalHealthy healthy proxies"
        )
    }

    private fun loadSettings(): ProxyMaintenanceSettings = ProxyMaintenanceSettings(
        autoBackgroundHealthCheck = prefs.getBoolean(KEY_AUTO_CHECK, true),
        autoDeleteDeadProxies = prefs.getBoolean(KEY_AUTO_DELETE, true),
        autoFetchHealthyProxies = prefs.getBoolean(KEY_AUTO_FETCH, true),
        targetHealthyPoolSize = prefs.getInt(KEY_TARGET_POOL, 100).coerceIn(20, 500)
    )

    private fun loadLogs(): List<DeletedProxyLogEntry> {
        val raw = prefs.getString(KEY_DELETED_LOGS, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<DeletedProxyLogEntry>>() {}.type
            gson.fromJson<List<DeletedProxyLogEntry>>(raw, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveLogs(entries: List<DeletedProxyLogEntry>) {
        prefs.edit().putString(KEY_DELETED_LOGS, gson.toJson(entries)).apply()
    }
}
