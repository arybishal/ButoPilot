package com.autopilot.android

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.autopilot.android.data.local.AutoPilotDatabase
import com.autopilot.android.data.repository.*
import com.autopilot.android.domain.model.ProfileStatus
import com.autopilot.android.engine.AutomationCoordinator
import com.autopilot.android.feature.proxy.ProxyExecutionCoordinator
import com.autopilot.android.feature.proxy.ProxyHealthChecker
import com.autopilot.android.feature.scheduling.notification.ScheduleNotificationManager
import com.autopilot.android.feature.scheduling.service.AutomationScheduler
import com.autopilot.android.feature.scheduling.worker.ScheduleReconciliationWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AutoPilotApplication : Application() {

    companion object {
        const val CHANNEL_ID = "autopilot_automation_channel"
        const val CHANNEL_NAME = "AutoPilot Automation Service"
        lateinit var instance: AutoPilotApplication
            private set
    }

    lateinit var database: AutoPilotDatabase
        private set

    lateinit var profileRepository: ProfileRepository
        private set

    lateinit var historyRepository: HistoryRepository
        private set

    lateinit var scheduleRepository: ScheduleRepository
        private set

    lateinit var automationScheduler: AutomationScheduler
        private set

    lateinit var scheduleNotificationManager: ScheduleNotificationManager
        private set

    lateinit var proxyRepository: ProxyRepository
        private set

    lateinit var proxyExecutionCoordinator: ProxyExecutionCoordinator
        private set

    lateinit var proxyHealthChecker: ProxyHealthChecker
        private set

    lateinit var automationCoordinator: AutomationCoordinator
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = AutoPilotDatabase.getInstance(this)
        profileRepository = ProfileRepositoryImpl(database.profileDao())
        historyRepository = HistoryRepositoryImpl(database.executionHistoryDao())
        scheduleRepository = ScheduleRepositoryImpl(database.scheduleDao())
        proxyRepository = ProxyRepositoryImpl(database.proxyDao(), database.proxySourceDao())

        proxyExecutionCoordinator = ProxyExecutionCoordinator()
        proxyHealthChecker = ProxyHealthChecker()

        automationCoordinator = AutomationCoordinator(
            context = this,
            profileRepository = profileRepository,
            historyRepository = historyRepository,
            proxyRepository = proxyRepository,
            proxyExecutionCoordinator = proxyExecutionCoordinator
        )
        automationScheduler = AutomationScheduler(this)
        scheduleNotificationManager = ScheduleNotificationManager(this)

        createNotificationChannels()

        // Enqueue WorkManager periodic background reconciliation
        ScheduleReconciliationWorker.enqueuePeriodic(this)

        // Process death recovery: reset lingering proxy routing and reconcile stale RUNNING profiles to IDLE
        CoroutineScope(Dispatchers.IO).launch {
            try {
                proxyExecutionCoordinator.resetAndRelease()
            } catch (e: Exception) {
                // Ignore during init
            }
            try {
                val runningProfiles = profileRepository.getAllProfiles().filter { it.status == ProfileStatus.RUNNING }
                for (p in runningProfiles) {
                    profileRepository.updateStatus(p.id, ProfileStatus.IDLE)
                }
            } catch (e: Exception) {
                // Ignore during init
            }
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val automationChannel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows real-time status of running browser automations"
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(automationChannel)

            scheduleNotificationManager.createChannels()
        }
    }
}
