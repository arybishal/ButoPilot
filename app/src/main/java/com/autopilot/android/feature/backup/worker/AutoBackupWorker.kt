package com.autopilot.android.feature.backup.worker

import android.content.Context
import androidx.work.*
import com.autopilot.android.AutoPilotApplication
import com.autopilot.android.feature.backup.AutoBackupSettings
import com.autopilot.android.feature.backup.BackupCategory
import com.autopilot.android.feature.backup.BackupManager
import com.autopilot.android.feature.backup.BackupPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

class AutoBackupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "autopilot_periodic_backup_worker"

        fun schedule(context: Context, settings: AutoBackupSettings) {
            val workManager = WorkManager.getInstance(context)
            if (!settings.isEnabled) {
                workManager.cancelUniqueWork(WORK_NAME)
                return
            }

            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .setRequiresStorageNotLow(true)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<AutoBackupWorker>(
                settings.frequency.intervalHours,
                TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .setInitialDelay(settings.frequency.intervalHours.coerceAtMost(6), TimeUnit.HOURS)
                .build()

            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext as? AutoPilotApplication ?: return@withContext Result.failure()
        val prefs = BackupPreferences(app)
        val settings = prefs.getSettings()

        if (!settings.isEnabled) {
            return@withContext Result.success()
        }

        try {
            val backupManager = app.backupManager

            val autoBackupDir = File(app.filesDir, "backups/auto")
            if (!autoBackupDir.exists()) {
                autoBackupDir.mkdirs()
            }

            val categories = mutableSetOf(
                BackupCategory.PROFILES,
                BackupCategory.SCHEDULES,
                BackupCategory.PROXIES,
                BackupCategory.PROXY_SOURCES
            )
            if (settings.includeHistory) {
                categories.add(BackupCategory.EXECUTION_HISTORY)
            }

            // Atomic creation: write backup
            backupManager.createBackup(
                categories = categories,
                outputDir = autoBackupDir
            )

            // Enforce configured retention count
            pruneOldBackups(autoBackupDir, settings.retentionCount)

            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    private fun pruneOldBackups(directory: File, keepCount: Int) {
        val files = directory.listFiles { file ->
            file.isFile && file.name.endsWith(BackupManager.BACKUP_EXTENSION)
        } ?: return

        if (files.size > keepCount) {
            val sorted = files.sortedByDescending { it.lastModified() }
            val toDelete = sorted.drop(keepCount)
            for (f in toDelete) {
                try {
                    f.delete()
                } catch (e: Exception) {
                    // Non-fatal
                }
            }
        }
    }
}
