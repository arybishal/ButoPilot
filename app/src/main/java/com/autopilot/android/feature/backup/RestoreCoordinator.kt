package com.autopilot.android.feature.backup

import android.content.Context
import androidx.room.withTransaction
import com.autopilot.android.data.local.AutoPilotDatabase
import com.autopilot.android.data.local.entity.BackupRecordEntity
import com.autopilot.android.feature.scheduling.worker.ScheduleReconciliationWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class RestoreCoordinator(
    private val context: Context,
    private val database: AutoPilotDatabase,
    private val backupManager: BackupManager
) {
    private val serializer = BackupSerializer()

    suspend fun executeRestore(
        archiveFile: File,
        password: String? = null,
        mode: RestoreMode = RestoreMode.MERGE
    ): RestoreResult = withContext(Dispatchers.IO) {
        // 1. Read manifest and extract payloads
        val manifest = backupManager.readManifest(archiveFile)
        val payloadMap = backupManager.extractPayloads(archiveFile, manifest, password)

        // 2. Deserialize data
        val profilesJson = payloadMap[BackupManager.PROFILES_ENTRY_NAME]?.let { String(it, Charsets.UTF_8) } ?: ""
        val schedulesJson = payloadMap[BackupManager.SCHEDULES_ENTRY_NAME]?.let { String(it, Charsets.UTF_8) } ?: ""
        val proxiesJson = payloadMap[BackupManager.PROXIES_ENTRY_NAME]?.let { String(it, Charsets.UTF_8) } ?: ""
        val sourcesJson = payloadMap[BackupManager.SOURCES_ENTRY_NAME]?.let { String(it, Charsets.UTF_8) } ?: ""
        val historyJson = payloadMap[BackupManager.HISTORY_ENTRY_NAME]?.let { String(it, Charsets.UTF_8) } ?: ""

        val profiles = serializer.deserializeProfiles(profilesJson)
        val schedules = serializer.deserializeSchedules(schedulesJson)
        val proxies = serializer.deserializeProxies(proxiesJson)
        val sources = serializer.deserializeProxySources(sourcesJson)
        val history = serializer.deserializeHistory(historyJson)

        // 2b. Pre-transaction validation: reject corrupted/malformed entities before modifying database
        try {
            for (profile in profiles) {
                require(profile.id.isNotBlank()) { "Corrupt backup: Profile contains blank ID" }
                require(profile.name.isNotBlank()) { "Corrupt backup: Profile contains blank name" }
            }
            for (schedule in schedules) {
                require(schedule.id.isNotBlank()) { "Corrupt backup: Schedule contains blank ID" }
                require(schedule.profileId.isNotBlank()) { "Corrupt backup: Schedule contains blank profile ID" }
            }
            for (proxy in proxies) {
                require(proxy.id.isNotBlank()) { "Corrupt backup: Proxy contains blank ID" }
                require(proxy.host.isNotBlank()) { "Corrupt backup: Proxy contains blank host" }
                require(proxy.port in 1..65535) { "Corrupt backup: Proxy port out of range (${proxy.port})" }
            }
        } catch (e: Exception) {
            return@withContext RestoreResult(
                success = false,
                message = "Validation failed: ${e.message}. Restoration aborted with zero database changes."
            )
        }

        var safetyBackupPath: String? = null

        // 3. If REPLACE mode, create safety backup first before modifying database!
        if (mode == RestoreMode.REPLACE) {
            try {
                val safetyDir = File(context.filesDir, "backups/safety")
                val safetyBackup = backupManager.createBackup(
                    categories = BackupCategory.values().toSet(),
                    outputDir = safetyDir
                )
                safetyBackupPath = safetyBackup.absolutePath
            } catch (e: Exception) {
                // If safety backup fails, fail the replace operation to prevent data loss
                return@withContext RestoreResult(
                    success = false,
                    message = "Safety backup creation failed before replace: ${e.message}. Aborted to prevent data loss."
                )
            }
        }

        // 4. Execute atomic database transaction
        try {
            database.withTransaction {
                if (mode == RestoreMode.REPLACE) {
                    // Delete in proper order respecting foreign keys
                    database.scheduleDao().clearAll()
                    database.profileDao().clearAll()
                    database.proxyDao().deleteUnhealthy()
                    // Delete remaining proxies
                    database.openHelper.writableDatabase.execSQL("DELETE FROM proxy_configurations")
                    database.openHelper.writableDatabase.execSQL("DELETE FROM proxy_sources")
                    database.executionHistoryDao().clearAll()
                }

                // Insert proxy sources first
                for (source in sources) {
                    database.proxySourceDao().insertOrUpdate(source)
                }

                // Insert proxies
                if (proxies.isNotEmpty()) {
                    database.proxyDao().insertAll(proxies)
                }

                // Insert profiles
                for (profile in profiles) {
                    database.profileDao().insertOrUpdate(profile)
                }

                // Insert schedules (all disabled by default!)
                for (schedule in schedules) {
                    database.scheduleDao().insertOrUpdate(schedule)
                }

                // Insert execution history
                if (history.isNotEmpty()) {
                    database.executionHistoryDao().insertAll(history)
                }
            }
        } catch (e: Exception) {
            return@withContext RestoreResult(
                success = false,
                message = "Database transaction failed during restore: ${e.message}",
                safetyBackupPath = safetyBackupPath
            )
        }

        // 5. Post-restore reconciliation: enqueue WorkManager reconciliation
        try {
            ScheduleReconciliationWorker.enqueueOneTime(context)
        } catch (e: Exception) {
            // Non-fatal
        }

        // 6. Record restore event in backup_records
        try {
            val record = BackupRecordEntity(
                id = UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis(),
                filePath = archiveFile.absolutePath,
                sizeBytes = archiveFile.length(),
                isEncrypted = manifest.isEncrypted,
                itemCount = profiles.size + schedules.size + proxies.size,
                status = "RESTORED_${mode.name}"
            )
            database.backupRecordDao().insert(record)
        } catch (e: Exception) {
            // Non-fatal
        }

        RestoreResult(
            success = true,
            importedProfiles = profiles.size,
            importedSchedules = schedules.size,
            importedProxies = proxies.size,
            importedSources = sources.size,
            importedHistory = history.size,
            message = "Restoration completed successfully via ${mode.displayName}. Note: Restored schedules are paused by default and proxy passwords must be re-entered.",
            safetyBackupPath = safetyBackupPath
        )
    }
}
