package com.autopilot.android.feature.backup

enum class BackupCategory(val displayName: String) {
    PROFILES("Automation Profiles"),
    SCHEDULES("Schedules & Recurrence"),
    PROXIES("Proxies & Endpoints"),
    PROXY_SOURCES("Proxy Sources"),
    EXECUTION_HISTORY("Execution History"),
    SETTINGS("User Preferences")
}

data class BackupManifest(
    val formatVersion: Int = 1,
    val appVersion: String,
    val schemaVersion: Int = 4,
    val createdAt: Long = System.currentTimeMillis(),
    val isEncrypted: Boolean = false,
    val encryptionSaltBase64: String? = null,
    val encryptionIvBase64: String? = null,
    val kdfIterations: Int? = null,
    val exportedCategories: List<String>,
    val recordCounts: Map<String, Int>,
    val checksums: Map<String, String> = emptyMap()
)

enum class RestoreMode(val displayName: String) {
    MERGE("Merge with Existing Data"),
    REPLACE("Replace Existing Data (Destructive)")
}

data class RestorePreview(
    val manifest: BackupManifest,
    val profileCount: Int,
    val scheduleCount: Int,
    val proxyCount: Int,
    val sourceCount: Int,
    val historyCount: Int,
    val warnings: List<String> = emptyList(),
    val isCompatible: Boolean = true,
    val requiresPassword: Boolean = false
)

data class RestoreResult(
    val success: Boolean,
    val importedProfiles: Int = 0,
    val importedSchedules: Int = 0,
    val importedProxies: Int = 0,
    val importedSources: Int = 0,
    val importedHistory: Int = 0,
    val message: String,
    val safetyBackupPath: String? = null
)
