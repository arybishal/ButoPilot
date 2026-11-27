package com.autopilot.android

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.autopilot.android.core.system.DeviceSpoofManager
import com.autopilot.android.core.system.SharedPrefsSpoofSnapshotStore
import com.autopilot.android.core.system.SuShellRunner
import com.autopilot.android.data.local.AutoPilotDatabase
import com.autopilot.android.data.local.DefaultProxySeedData
import com.autopilot.android.data.repository.*
import com.autopilot.android.domain.model.ProfileStatus
import com.autopilot.android.engine.AutomationCoordinator
import com.autopilot.android.feature.backup.BackupManager
import com.autopilot.android.feature.backup.BackupPreferences
import com.autopilot.android.feature.backup.RestoreCoordinator
import com.autopilot.android.feature.backup.worker.AutoBackupWorker
import com.autopilot.android.feature.diagnostics.DiagnosticsRepository
import com.autopilot.android.feature.diagnostics.DiagnosticsRepositoryImpl
import com.autopilot.android.feature.diagnostics.execution.ExecutionEventTracker
import com.autopilot.android.feature.proxy.AutomaticProxyGeneratorManager
import com.autopilot.android.feature.proxy.ProxyExecutionCoordinator
import com.autopilot.android.feature.proxy.ProxyHealthChecker
import com.autopilot.android.feature.recovery.RecoveryManager
import com.autopilot.android.feature.scheduling.notification.ScheduleNotificationManager
import com.autopilot.android.feature.scheduling.service.AutomationScheduler
import com.autopilot.android.feature.scheduling.worker.ScheduleReconciliationWorker
import com.autopilot.android.feature.traffic.TrafficController
import com.autopilot.android.feature.traffic.TrafficExecutor
import com.autopilot.android.feature.update.UpdateManager
import com.autopilot.android.data.repository.TrafficRepositoryImpl
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

    lateinit var proxyGeneratorManager: AutomaticProxyGeneratorManager
        private set

    lateinit var diagnosticsRepository: DiagnosticsRepository
        private set

    lateinit var executionEventTracker: ExecutionEventTracker
        private set

    lateinit var backupManager: BackupManager
        private set

    lateinit var restoreCoordinator: RestoreCoordinator
        private set

    lateinit var recoveryManager: RecoveryManager
        private set

    lateinit var automationCoordinator: AutomationCoordinator
        private set

    lateinit var deviceSpoofManager: DeviceSpoofManager
        private set

    lateinit var trafficRepository: com.autopilot.android.data.repository.TrafficRepository
        private set

    lateinit var trafficExecutor: TrafficExecutor
        private set

    lateinit var trafficController: TrafficController
        private set

    lateinit var updateManager: UpdateManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = AutoPilotDatabase.getInstance(this)
        profileRepository = ProfileRepositoryImpl(database.profileDao())
        historyRepository = HistoryRepositoryImpl(database.executionHistoryDao())
        trafficRepository = TrafficRepositoryImpl(database.trafficDao())
        scheduleRepository = ScheduleRepositoryImpl(database.scheduleDao())
        proxyRepository = ProxyRepositoryImpl(database.proxyDao(), database.proxySourceDao())

        proxyExecutionCoordinator = ProxyExecutionCoordinator()
        proxyHealthChecker = ProxyHealthChecker(verifyExit = true)
        proxyGeneratorManager = AutomaticProxyGeneratorManager(proxyRepository, proxyHealthChecker)
        trafficExecutor = TrafficExecutor(
            trafficRepository,
            context = this,
            proxyRepository = proxyRepository,
            proxyHealthChecker = proxyHealthChecker,
            proxyExecutionCoordinator = proxyExecutionCoordinator
        )
        trafficController = TrafficController(this, trafficRepository, trafficExecutor)

        diagnosticsRepository = DiagnosticsRepositoryImpl(database.diagnosticDao())
        executionEventTracker = ExecutionEventTracker(database.executionEventDao())
        backupManager = BackupManager(this, database, database.backupRecordDao())
        restoreCoordinator = RestoreCoordinator(this, database, backupManager)

        deviceSpoofManager = DeviceSpoofManager(
            shell = SuShellRunner(),
            snapshots = SharedPrefsSpoofSnapshotStore(this)
        )
        automationCoordinator = AutomationCoordinator(
            context = this,
            profileRepository = profileRepository,
            historyRepository = historyRepository,
            proxyRepository = proxyRepository,
            proxyExecutionCoordinator = proxyExecutionCoordinator,
            eventTracker = executionEventTracker,
            deviceSpoofManager = deviceSpoofManager,
            proxyHealthChecker = proxyHealthChecker
        )

        recoveryManager = RecoveryManager(
            context = this,
            database = database,
            profileRepository = profileRepository,
            scheduleRepository = scheduleRepository,
            proxyRepository = proxyRepository,
            proxyExecutionCoordinator = proxyExecutionCoordinator,
            coordinator = automationCoordinator
        )

        automationScheduler = AutomationScheduler(this)
        scheduleNotificationManager = ScheduleNotificationManager(this)
        updateManager = UpdateManager(this)

        createNotificationChannels()

        // Enqueue WorkManager periodic background reconciliation
        ScheduleReconciliationWorker.enqueuePeriodic(this)

        // Enqueue WorkManager periodic automatic backups according to user preferences
        val backupPrefs = BackupPreferences(this).getSettings()
        AutoBackupWorker.schedule(this, backupPrefs)

        // Process death recovery: reset lingering proxy routing and reconcile stale RUNNING profiles to IDLE
        CoroutineScope(Dispatchers.IO).launch {
            try {
                proxyExecutionCoordinator.resetAndRelease()
            } catch (e: Exception) {
                // Ignore during init
            }
            try {
                deviceSpoofManager.restore()
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
            try {
                val all = proxyRepository.getAllProxies()
                if (all.isEmpty()) {
                    proxyRepository.saveProxies(DefaultProxySeedData.getSeedProxies())
                }
            } catch (e: Exception) {
                // Ignore during init
            }
            trafficController.resumeActiveRuns()
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
