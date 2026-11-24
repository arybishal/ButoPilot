package com.autopilot.android.feature.backup

import com.autopilot.android.data.local.entity.*
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.security.MessageDigest

data class BackupDatabasePayload(
    val profiles: List<ProfileEntity> = emptyList(),
    val schedules: List<ScheduleEntity> = emptyList(),
    val proxies: List<ProxyEntity> = emptyList(),
    val proxySources: List<ProxySourceEntity> = emptyList(),
    val history: List<ExecutionHistoryEntity> = emptyList(),
    val settingsJson: String? = null
)

class BackupSerializer {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    private val profileListType = object : TypeToken<List<ProfileEntity>>() {}.type
    private val scheduleListType = object : TypeToken<List<ScheduleEntity>>() {}.type
    private val proxyListType = object : TypeToken<List<ProxyEntity>>() {}.type
    private val proxySourceListType = object : TypeToken<List<ProxySourceEntity>>() {}.type
    private val historyListType = object : TypeToken<List<ExecutionHistoryEntity>>() {}.type

    fun serializeManifest(manifest: BackupManifest): String {
        return gson.toJson(manifest)
    }

    fun deserializeManifest(json: String): BackupManifest {
        return gson.fromJson(json, BackupManifest::class.java)
    }

    fun serializeProfiles(profiles: List<ProfileEntity>): String {
        return gson.toJson(profiles)
    }

    fun deserializeProfiles(json: String): List<ProfileEntity> {
        if (json.isBlank()) return emptyList()
        return gson.fromJson(json, profileListType) ?: emptyList()
    }

    fun serializeSchedules(schedules: List<ScheduleEntity>): String {
        return gson.toJson(schedules)
    }

    fun deserializeSchedules(json: String): List<ScheduleEntity> {
        if (json.isBlank()) return emptyList()
        val list: List<ScheduleEntity> = gson.fromJson(json, scheduleListType) ?: emptyList()
        // MANDATORY SAFETY RULE: Restored schedules MUST be disabled by default pending user review!
        return list.map { schedule ->
            schedule.copy(
                enabled = false,
                status = "IDLE",
                lastError = "Restored from backup (disabled by default)"
            )
        }
    }

    fun serializeProxies(proxies: List<ProxyEntity>): String {
        // MANDATORY SECURITY EXCLUSION: Never export proxy passwords, auth secrets, or Keystore IVs!
        val sanitizedProxies = proxies.map { proxy ->
            proxy.copy(
                encryptedUsername = null,
                encryptedPassword = null,
                usernameIv = null,
                passwordIv = null,
                notes = if (proxy.notes.isNotBlank()) {
                    "${proxy.notes} [Credentials excluded in backup for security]"
                } else {
                    "[Credentials excluded in backup for security]"
                }
            )
        }
        return gson.toJson(sanitizedProxies)
    }

    fun deserializeProxies(json: String): List<ProxyEntity> {
        if (json.isBlank()) return emptyList()
        val list: List<ProxyEntity> = gson.fromJson(json, proxyListType) ?: emptyList()
        // Ensure imported proxies have null credentials so they cannot attempt to decrypt invalid/missing keys
        return list.map { proxy ->
            proxy.copy(
                encryptedUsername = null,
                encryptedPassword = null,
                usernameIv = null,
                passwordIv = null
            )
        }
    }

    fun serializeProxySources(sources: List<ProxySourceEntity>): String {
        return gson.toJson(sources)
    }

    fun deserializeProxySources(json: String): List<ProxySourceEntity> {
        if (json.isBlank()) return emptyList()
        return gson.fromJson(json, proxySourceListType) ?: emptyList()
    }

    fun serializeHistory(history: List<ExecutionHistoryEntity>): String {
        return gson.toJson(history)
    }

    fun deserializeHistory(json: String): List<ExecutionHistoryEntity> {
        if (json.isBlank()) return emptyList()
        return gson.fromJson(json, historyListType) ?: emptyList()
    }

    fun calculateSha256(content: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(content)
        return hash.joinToString("") { "%02x".format(it) }
    }

    fun calculateSha256(content: String): String {
        return calculateSha256(content.toByteArray(Charsets.UTF_8))
    }
}
