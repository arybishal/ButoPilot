package com.autopilot.android.feature.recovery

import android.content.Context
import com.autopilot.android.data.local.AutoPilotDatabase
import com.autopilot.android.data.repository.ProfileRepository
import com.autopilot.android.data.repository.ProxyRepository
import com.autopilot.android.data.repository.ScheduleRepository
import com.autopilot.android.domain.model.ProfileStatus
import com.autopilot.android.engine.AutomationCoordinator
import com.autopilot.android.feature.proxy.ProxyExecutionCoordinator
import com.autopilot.android.feature.scheduling.worker.ScheduleReconciliationWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class RecoverySummary(
    val stuckProfilesReset: Int = 0,
    val orphanedProxiesRepaired: Int = 0,
    val cacheBytesFreed: Long = 0,
    val schedulesReconciled: Int = 0,
    val databaseIntegrityMessage: String = "OK"
)

class RecoveryManager(
    private val context: Context,
    private val database: AutoPilotDatabase,
    private val profileRepository: ProfileRepository,
    private val scheduleRepository: ScheduleRepository,
    private val proxyRepository: ProxyRepository,
    private val proxyExecutionCoordinator: ProxyExecutionCoordinator? = null,
    private val coordinator: AutomationCoordinator? = null
) {

    suspend fun resetStuckAutomations(): Int = withContext(Dispatchers.IO) {
        val activeState = coordinator?.engineState?.value
        val activeProfileId = activeState?.profile?.id
        val isCoordinatorActive = activeState?.status == ProfileStatus.RUNNING || activeState?.status == ProfileStatus.PAUSED

        val allProfiles = profileRepository.getAllProfiles()
        var resetCount = 0

        for (p in allProfiles) {
            // CRITICAL AUDIT SAFETY RULE:
            // Do NOT reset legitimately active or user-paused profiles currently held by the active coordinator!
            if (isCoordinatorActive && p.id == activeProfileId) {
                continue
            }

            // Only reset if profile is marked RUNNING or PAUSED but coordinator is NOT actively executing/holding it (zombie state from process death)
            if (p.status == ProfileStatus.RUNNING || p.status == ProfileStatus.PAUSED) {
                profileRepository.updateStatus(p.id, ProfileStatus.IDLE)
                resetCount++
            }
        }

        // Only release proxy locks if the coordinator is NOT actively running an isolated session!
        if (!isCoordinatorActive) {
            try {
                proxyExecutionCoordinator?.resetAndRelease()
            } catch (e: Exception) {
                // Safe cleanup
            }
        }

        resetCount
    }

    suspend fun repairOrphanedProxyReferences(): Int = withContext(Dispatchers.IO) {
        val allProfiles = profileRepository.getAllProfiles()
        val allProxies = proxyRepository.getAllProxies()
        val validProxyIds = allProxies.map { it.id }.toSet()

        var repairedCount = 0
        for (profile in allProfiles) {
            if (profile.proxyId != null && !validProxyIds.contains(profile.proxyId)) {
                val repaired = profile.copy(proxyId = null, updatedAt = System.currentTimeMillis())
                profileRepository.saveProfile(repaired)
                repairedCount++
            }
        }
        repairedCount
    }

    suspend fun cleanTemporaryAndCacheFiles(): Long = withContext(Dispatchers.IO) {
        var freedBytes = 0L

        // Clean app cache directory
        freedBytes += deleteDirectoryContents(context.cacheDir)

        // Clean temp backups directory if any
        val tempBackupsDir = File(context.filesDir, "backups/temp")
        if (tempBackupsDir.exists()) {
            freedBytes += deleteDirectoryContents(tempBackupsDir)
        }

        freedBytes
    }

    suspend fun reconcileSchedules(): Int = withContext(Dispatchers.IO) {
        val activeSchedules = scheduleRepository.getActiveSchedules()
        try {
            ScheduleReconciliationWorker.enqueueOneTime(context)
        } catch (e: Exception) {
            // Non-fatal
        }
        activeSchedules.size
    }

    suspend fun verifyAndOptimizeDatabase(): String = withContext(Dispatchers.IO) {
        val db = database.openHelper.writableDatabase
        var resultMessage = "OK"
        try {
            val cursor = db.query("PRAGMA integrity_check")
            if (cursor.moveToFirst()) {
                resultMessage = cursor.getString(0)
            }
            cursor.close()

            // Run VACUUM
            db.execSQL("VACUUM")
        } catch (e: Exception) {
            resultMessage = "Database optimization error: ${e.message}"
        }
        resultMessage
    }

    suspend fun performFullRecovery(): RecoverySummary = withContext(Dispatchers.IO) {
        val stuck = resetStuckAutomations()
        val orphaned = repairOrphanedProxyReferences()
        val freed = cleanTemporaryAndCacheFiles()
        val schedules = reconcileSchedules()
        val dbMsg = verifyAndOptimizeDatabase()

        RecoverySummary(
            stuckProfilesReset = stuck,
            orphanedProxiesRepaired = orphaned,
            cacheBytesFreed = freed,
            schedulesReconciled = schedules,
            databaseIntegrityMessage = dbMsg
        )
    }

    private fun deleteDirectoryContents(dir: File): Long {
        if (!dir.exists()) return 0L
        var bytesDeleted = 0L
        val files = dir.listFiles() ?: return 0L
        for (f in files) {
            bytesDeleted += if (f.isDirectory) {
                deleteDirectoryContents(f).also { f.delete() }
            } else {
                val len = f.length()
                if (f.delete()) len else 0L
            }
        }
        return bytesDeleted
    }
}
