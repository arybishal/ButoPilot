package com.autopilot.android.data.repository

import com.autopilot.android.core.security.ProxyCredentialEncryptor
import com.autopilot.android.data.local.dao.ExecutionHistoryDao
import com.autopilot.android.data.local.dao.ProfileDao
import com.autopilot.android.data.local.dao.ProxyDao
import com.autopilot.android.data.local.dao.ProxySourceDao
import com.autopilot.android.data.local.dao.ScheduleDao
import com.autopilot.android.data.local.entity.ExecutionHistoryEntity
import com.autopilot.android.data.local.entity.ProfileEntity
import com.autopilot.android.data.local.entity.ProxyEntity
import com.autopilot.android.data.local.entity.ProxySourceEntity
import com.autopilot.android.data.local.entity.ScheduleEntity
import com.autopilot.android.domain.model.*
import com.autopilot.android.domain.model.proxy.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

interface ProfileRepository {
    fun getAllProfilesFlow(): Flow<List<AutomationProfile>>
    suspend fun getAllProfiles(): List<AutomationProfile>
    suspend fun getProfileById(id: String): AutomationProfile?
    fun getProfileByIdFlow(id: String): Flow<AutomationProfile?>
    suspend fun saveProfile(profile: AutomationProfile)
    suspend fun duplicateProfile(profileId: String): AutomationProfile?
    suspend fun deleteProfile(id: String)
    suspend fun updateStatus(id: String, status: ProfileStatus)
}

interface HistoryRepository {
    fun getAllHistoryFlow(): Flow<List<ExecutionHistoryItem>>
    fun getHistoryForProfile(profileId: String): Flow<List<ExecutionHistoryItem>>
    suspend fun recordSession(item: ExecutionHistoryItem)
    suspend fun clearHistory()
}

class ProfileRepositoryImpl(
    private val profileDao: ProfileDao
) : ProfileRepository {

    private val gson = Gson()
    private val stringListType = object : TypeToken<List<String>>() {}.type

    override fun getAllProfilesFlow(): Flow<List<AutomationProfile>> {
        return profileDao.getAllFlow().map { list -> list.map { it.toDomain() } }
    }

    override suspend fun getAllProfiles(): List<AutomationProfile> {
        return profileDao.getAll().map { it.toDomain() }
    }

    override suspend fun getProfileById(id: String): AutomationProfile? {
        return profileDao.getById(id)?.toDomain()
    }

    override fun getProfileByIdFlow(id: String): Flow<AutomationProfile?> {
        return profileDao.getByIdFlow(id).map { it?.toDomain() }
    }

    override suspend fun saveProfile(profile: AutomationProfile) {
        profileDao.insertOrUpdate(profile.toEntity())
    }

    override suspend fun duplicateProfile(profileId: String): AutomationProfile? {
        val original = getProfileById(profileId) ?: return null
        val duplicated = original.copy(
            id = UUID.randomUUID().toString(),
            name = "${original.name} (Copy)",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            status = ProfileStatus.IDLE
        )
        saveProfile(duplicated)
        return duplicated
    }

    override suspend fun deleteProfile(id: String) {
        profileDao.deleteById(id)
    }

    override suspend fun updateStatus(id: String, status: ProfileStatus) {
        profileDao.updateStatus(id, status.name)
    }

    private fun ProfileEntity.toDomain(): AutomationProfile {
        val urls: List<String> = try {
            gson.fromJson(urlsJson, stringListType) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        val allowedDomains: List<String> = try {
            gson.fromJson(allowedDomainsJson, stringListType) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        return AutomationProfile(
            id = id,
            name = name,
            description = description,
            urls = urls,
            tabCount = tabCount,
            executionMode = try { ExecutionMode.valueOf(executionMode) } catch (e: Exception) { ExecutionMode.SIMPLE_SCROLL_REFRESH },
            refreshIntervalSec = refreshIntervalSec,
            scrollIntervalSec = scrollIntervalSec,
            randomizeIntervals = randomizeIntervals,
            minRefreshIntervalSec = minRefreshIntervalSec,
            maxRefreshIntervalSec = maxRefreshIntervalSec,
            minScrollIntervalSec = minScrollIntervalSec,
            maxScrollIntervalSec = maxScrollIntervalSec,
            enableSpoofing = enableSpoofing,
            enableFreeProxy = enableFreeProxy,
            customScript = customScript,
            allowedDomains = allowedDomains,
            userAgent = userAgent,
            clearCookiesOnStart = clearCookiesOnStart,
            clearCacheOnStart = clearCacheOnStart,
            timeoutSeconds = timeoutSeconds,
            createdAt = createdAt,
            updatedAt = updatedAt,
            status = try { ProfileStatus.valueOf(status) } catch (e: Exception) { ProfileStatus.IDLE },
            proxyId = proxyId
        )
    }

    private fun AutomationProfile.toEntity(): ProfileEntity {
        return ProfileEntity(
            id = id,
            name = name,
            description = description,
            urlsJson = gson.toJson(urls),
            tabCount = tabCount,
            executionMode = executionMode.name,
            refreshIntervalSec = refreshIntervalSec,
            scrollIntervalSec = scrollIntervalSec,
            randomizeIntervals = randomizeIntervals,
            minRefreshIntervalSec = minRefreshIntervalSec,
            maxRefreshIntervalSec = maxRefreshIntervalSec,
            minScrollIntervalSec = minScrollIntervalSec,
            maxScrollIntervalSec = maxScrollIntervalSec,
            enableSpoofing = enableSpoofing,
            enableFreeProxy = enableFreeProxy,
            customScript = customScript,
            allowedDomainsJson = gson.toJson(allowedDomains),
            userAgent = userAgent,
            clearCookiesOnStart = clearCookiesOnStart,
            clearCacheOnStart = clearCacheOnStart,
            timeoutSeconds = timeoutSeconds,
            createdAt = createdAt,
            updatedAt = updatedAt,
            status = status.name,
            proxyId = proxyId
        )
    }
}

class HistoryRepositoryImpl(
    private val historyDao: ExecutionHistoryDao
) : HistoryRepository {

    override fun getAllHistoryFlow(): Flow<List<ExecutionHistoryItem>> {
        return historyDao.getAllFlow().map { list -> list.map { it.toDomain() } }
    }

    override fun getHistoryForProfile(profileId: String): Flow<List<ExecutionHistoryItem>> {
        return historyDao.getForProfile(profileId).map { list -> list.map { it.toDomain() } }
    }

    override suspend fun recordSession(item: ExecutionHistoryItem) {
        historyDao.insert(item.toEntity())
    }

    override suspend fun clearHistory() {
        historyDao.clearAll()
    }

    private fun ExecutionHistoryEntity.toDomain(): ExecutionHistoryItem {
        return ExecutionHistoryItem(
            id = id,
            profileId = profileId,
            profileName = profileName,
            startedAt = startedAt,
            endedAt = endedAt,
            durationSeconds = durationSeconds,
            totalActions = totalActions,
            status = try { ProfileStatus.valueOf(status) } catch (e: Exception) { ProfileStatus.COMPLETED },
            errorMessage = errorMessage
        )
    }

    private fun ExecutionHistoryItem.toEntity(): ExecutionHistoryEntity {
        return ExecutionHistoryEntity(
            id = id,
            profileId = profileId,
            profileName = profileName,
            startedAt = startedAt,
            endedAt = endedAt,
            durationSeconds = durationSeconds,
            totalActions = totalActions,
            status = status.name,
            errorMessage = errorMessage
        )
    }
}

interface ScheduleRepository {
    fun getAllSchedulesFlow(): Flow<List<AutomationSchedule>>
    suspend fun getAllSchedules(): List<AutomationSchedule>
    suspend fun getScheduleById(id: String): AutomationSchedule?
    fun getScheduleByIdFlow(id: String): Flow<AutomationSchedule?>
    suspend fun getActiveSchedules(): List<AutomationSchedule>
    suspend fun getPendingExecutions(untilEpochMillis: Long): List<AutomationSchedule>
    suspend fun getRunningOrStartingSchedules(): List<AutomationSchedule>
    suspend fun saveSchedule(schedule: AutomationSchedule)
    suspend fun deleteSchedule(id: String)
    suspend fun updateExecutionState(
        id: String,
        status: ScheduleStatus,
        nextExecutionAt: Long?,
        lastExecutionAt: Long?,
        error: String?
    )
    suspend fun updateEnabled(id: String, enabled: Boolean)
}

class ScheduleRepositoryImpl(
    private val scheduleDao: ScheduleDao
) : ScheduleRepository {

    override fun getAllSchedulesFlow(): Flow<List<AutomationSchedule>> {
        return scheduleDao.getAllFlow().map { list -> list.map { it.toDomain() } }
    }

    override suspend fun getAllSchedules(): List<AutomationSchedule> {
        return scheduleDao.getAll().map { it.toDomain() }
    }

    override suspend fun getScheduleById(id: String): AutomationSchedule? {
        return scheduleDao.getById(id)?.toDomain()
    }

    override fun getScheduleByIdFlow(id: String): Flow<AutomationSchedule?> {
        return scheduleDao.getByIdFlow(id).map { it?.toDomain() }
    }

    override suspend fun getActiveSchedules(): List<AutomationSchedule> {
        return scheduleDao.getActiveScheduled().map { it.toDomain() }
    }

    override suspend fun getPendingExecutions(untilEpochMillis: Long): List<AutomationSchedule> {
        return scheduleDao.getPendingExecution(untilEpochMillis).map { it.toDomain() }
    }

    override suspend fun getRunningOrStartingSchedules(): List<AutomationSchedule> {
        return scheduleDao.getRunningOrStarting().map { it.toDomain() }
    }

    override suspend fun saveSchedule(schedule: AutomationSchedule) {
        scheduleDao.insertOrUpdate(schedule.toEntity())
    }

    override suspend fun deleteSchedule(id: String) {
        scheduleDao.deleteById(id)
    }

    override suspend fun updateExecutionState(
        id: String,
        status: ScheduleStatus,
        nextExecutionAt: Long?,
        lastExecutionAt: Long?,
        error: String?
    ) {
        scheduleDao.updateExecutionState(
            id = id,
            status = status.name,
            nextExecutionAt = nextExecutionAt,
            lastExecutionAt = lastExecutionAt,
            error = error
        )
    }

    override suspend fun updateEnabled(id: String, enabled: Boolean) {
        scheduleDao.updateEnabled(id, enabled)
    }

    private fun ScheduleEntity.toDomain(): AutomationSchedule {
        return AutomationSchedule(
            id = id,
            profileId = profileId,
            profileName = profileName,
            scheduleName = scheduleName,
            scheduleType = try { ScheduleType.valueOf(scheduleType) } catch (e: Exception) { ScheduleType.ONE_TIME },
            recurrenceRule = try { RecurrenceRule.valueOf(recurrenceRule) } catch (e: Exception) { RecurrenceRule.NONE },
            targetEpochMillis = targetEpochMillis,
            nextExecutionAt = nextExecutionAt,
            lastExecutionAt = lastExecutionAt,
            timezoneId = timezoneId,
            enabled = enabled,
            maxDurationMinutes = maxDurationMinutes,
            status = try { ScheduleStatus.valueOf(status) } catch (e: Exception) { ScheduleStatus.SCHEDULED },
            lastError = lastError,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun AutomationSchedule.toEntity(): ScheduleEntity {
        return ScheduleEntity(
            id = id,
            profileId = profileId,
            profileName = profileName,
            scheduleName = scheduleName,
            scheduleType = scheduleType.name,
            recurrenceRule = recurrenceRule.name,
            targetEpochMillis = targetEpochMillis,
            nextExecutionAt = nextExecutionAt,
            lastExecutionAt = lastExecutionAt,
            timezoneId = timezoneId,
            enabled = enabled,
            maxDurationMinutes = maxDurationMinutes,
            status = status.name,
            lastError = lastError,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}

interface ProxyRepository {
    fun getAllProxiesFlow(): Flow<List<ProxyConfiguration>>
    suspend fun getAllProxies(): List<ProxyConfiguration>
    suspend fun getProxyById(id: String): ProxyConfiguration?
    fun getProxyByIdFlow(id: String): Flow<ProxyConfiguration?>
    suspend fun getProxyByHostAndPort(host: String, port: Int): ProxyConfiguration?
    suspend fun getEnabledProxies(): List<ProxyConfiguration>
    suspend fun saveProxy(proxy: ProxyConfiguration)
    suspend fun saveProxies(proxies: List<ProxyConfiguration>)
    suspend fun deleteProxy(id: String)
    suspend fun deleteUnhealthyProxies(): Int
    suspend fun updateHealthStatus(id: String, status: ProxyHealthStatus, latencyMs: Long?, error: String?)
    suspend fun updateEnabled(id: String, enabled: Boolean)
    fun getAllSourcesFlow(): Flow<List<ProxySource>>
    suspend fun getSourceById(id: String): ProxySource?
    suspend fun saveSource(source: ProxySource)
    suspend fun deleteSource(id: String)
}

class ProxyRepositoryImpl(
    private val proxyDao: ProxyDao,
    private val proxySourceDao: ProxySourceDao,
    private val encryptor: ProxyCredentialEncryptor = ProxyCredentialEncryptor()
) : ProxyRepository {

    override fun getAllProxiesFlow(): Flow<List<ProxyConfiguration>> {
        return proxyDao.getAllFlow().map { list -> list.map { it.toDomain() } }
    }

    override suspend fun getAllProxies(): List<ProxyConfiguration> {
        return proxyDao.getAll().map { it.toDomain() }
    }

    override suspend fun getProxyById(id: String): ProxyConfiguration? {
        return proxyDao.getById(id)?.toDomain()
    }

    override fun getProxyByIdFlow(id: String): Flow<ProxyConfiguration?> {
        return proxyDao.getByIdFlow(id).map { it?.toDomain() }
    }

    override suspend fun getProxyByHostAndPort(host: String, port: Int): ProxyConfiguration? {
        return proxyDao.getByHostAndPort(host, port)?.toDomain()
    }

    override suspend fun getEnabledProxies(): List<ProxyConfiguration> {
        return proxyDao.getEnabled().map { it.toDomain() }
    }

    override suspend fun saveProxy(proxy: ProxyConfiguration) {
        proxyDao.insertOrUpdate(proxy.toEntity())
    }

    override suspend fun saveProxies(proxies: List<ProxyConfiguration>) {
        proxyDao.insertAll(proxies.map { it.toEntity() })
    }

    override suspend fun deleteProxy(id: String) {
        proxyDao.deleteById(id)
    }

    override suspend fun deleteUnhealthyProxies(): Int {
        return proxyDao.deleteUnhealthy()
    }

    override suspend fun updateHealthStatus(
        id: String,
        status: ProxyHealthStatus,
        latencyMs: Long?,
        error: String?
    ) {
        proxyDao.updateHealthStatus(
            id = id,
            status = status.name,
            latencyMs = latencyMs,
            testedAt = System.currentTimeMillis(),
            error = error
        )
    }

    override suspend fun updateEnabled(id: String, enabled: Boolean) {
        proxyDao.updateEnabled(id, enabled)
    }

    override fun getAllSourcesFlow(): Flow<List<ProxySource>> {
        return proxySourceDao.getAllFlow().map { list -> list.map { it.toDomain() } }
    }

    override suspend fun getSourceById(id: String): ProxySource? {
        return proxySourceDao.getById(id)?.toDomain()
    }

    override suspend fun saveSource(source: ProxySource) {
        proxySourceDao.insertOrUpdate(source.toEntity())
    }

    override suspend fun deleteSource(id: String) {
        proxySourceDao.deleteById(id)
    }

    private fun ProxyEntity.toDomain(): ProxyConfiguration {
        val user = encryptor.decrypt(encryptedUsername, usernameIv)
        val pass = encryptor.decrypt(encryptedPassword, passwordIv)
        val credentials = if (!user.isNullOrEmpty() || !pass.isNullOrEmpty()) {
            ProxyCredentials(username = user, password = pass)
        } else {
            null
        }

        return ProxyConfiguration(
            id = id,
            name = name,
            protocol = ProxyProtocol.fromString(protocol),
            host = host,
            port = port,
            credentials = credentials,
            region = region,
            notes = notes,
            sourceId = sourceId,
            enabled = enabled,
            lastHealthStatus = ProxyHealthStatus.fromString(lastHealthStatus),
            lastLatencyMs = lastLatencyMs,
            lastTestedAt = lastTestedAt,
            lastErrorMessage = lastErrorMessage,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun ProxyConfiguration.toEntity(): ProxyEntity {
        val usernamePayload = encryptor.encrypt(credentials?.username)
        val passwordPayload = encryptor.encrypt(credentials?.password)

        return ProxyEntity(
            id = id,
            name = name,
            protocol = protocol.name,
            host = host,
            port = port,
            encryptedUsername = usernamePayload?.ciphertextBase64,
            encryptedPassword = passwordPayload?.ciphertextBase64,
            usernameIv = usernamePayload?.ivBase64,
            passwordIv = passwordPayload?.ivBase64,
            region = region,
            notes = notes,
            sourceId = sourceId,
            enabled = enabled,
            lastHealthStatus = lastHealthStatus.name,
            lastLatencyMs = lastLatencyMs,
            lastTestedAt = lastTestedAt,
            lastErrorMessage = lastErrorMessage,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun ProxySourceEntity.toDomain(): ProxySource {
        return ProxySource(
            id = id,
            name = name,
            sourceType = try { ProxySourceType.valueOf(sourceType) } catch (e: Exception) { ProxySourceType.MANUAL },
            urlOrPath = urlOrPath,
            lastRefreshedAt = lastRefreshedAt,
            proxyCount = proxyCount,
            createdAt = createdAt
        )
    }

    private fun ProxySource.toEntity(): ProxySourceEntity {
        return ProxySourceEntity(
            id = id,
            name = name,
            sourceType = sourceType.name,
            urlOrPath = urlOrPath,
            lastRefreshedAt = lastRefreshedAt,
            proxyCount = proxyCount,
            createdAt = createdAt
        )
    }
}
