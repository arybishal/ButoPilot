package com.autopilot.android

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.autopilot.android.data.local.AutoPilotDatabase
import com.autopilot.android.data.repository.HistoryRepository
import com.autopilot.android.data.repository.HistoryRepositoryImpl
import com.autopilot.android.data.repository.ProfileRepository
import com.autopilot.android.data.repository.ProfileRepositoryImpl
import com.autopilot.android.engine.AutomationCoordinator

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

    lateinit var automationCoordinator: AutomationCoordinator
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = AutoPilotDatabase.getInstance(this)
        profileRepository = ProfileRepositoryImpl(database.profileDao())
        historyRepository = HistoryRepositoryImpl(database.executionHistoryDao())
        automationCoordinator = AutomationCoordinator(this, profileRepository, historyRepository)

        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows real-time status of running browser automations"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
