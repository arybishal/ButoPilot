package com.autopilot.android.feature.automation.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.autopilot.android.AutoPilotApplication
import com.autopilot.android.R
import com.autopilot.android.domain.model.ProfileStatus
import com.autopilot.android.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AutomationService : Service() {

    companion object {
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.autopilot.android.action.START"
        const val ACTION_PAUSE = "com.autopilot.android.action.PAUSE"
        const val ACTION_RESUME = "com.autopilot.android.action.RESUME"
        const val ACTION_STOP = "com.autopilot.android.action.STOP"
        const val EXTRA_PROFILE_ID = "extra_profile_id"

        fun startAutomation(context: Context, profileId: String) {
            val intent = Intent(context, AutomationService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PROFILE_ID, profileId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun pauseAutomation(context: Context) {
            context.startService(Intent(context, AutomationService::class.java).apply {
                action = ACTION_PAUSE
            })
        }

        fun resumeAutomation(context: Context) {
            context.startService(Intent(context, AutomationService::class.java).apply {
                action = ACTION_RESUME
            })
        }

        fun stopAutomation(context: Context) {
            context.startService(Intent(context, AutomationService::class.java).apply {
                action = ACTION_STOP
            })
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var observerJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as AutoPilotApplication
        val coordinator = app.automationCoordinator

        when (intent?.action) {
            ACTION_START -> {
                val profileId = intent.getStringExtra(EXTRA_PROFILE_ID)
                startAsForeground("Starting Automation...")
                if (profileId != null) {
                    serviceScope.launch {
                        val profile = app.profileRepository.getProfileById(profileId)
                        if (profile != null) {
                            coordinator.start(profile)
                        } else {
                            stopSelf()
                        }
                    }
                }
                observeEngineState()
            }
            ACTION_PAUSE -> {
                coordinator.pause()
            }
            ACTION_RESUME -> {
                coordinator.resume()
            }
            ACTION_STOP -> {
                coordinator.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun startAsForeground(contentText: String) {
        val notification = buildNotification(contentText, ProfileStatus.RUNNING)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun observeEngineState() {
        val app = application as AutoPilotApplication
        observerJob?.cancel()
        observerJob = serviceScope.launch {
            app.automationCoordinator.engineState.collectLatest { state ->
                if (state.status == ProfileStatus.IDLE) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    val notif = buildNotification(
                        "${state.profile?.name ?: "Automation"} - ${state.statusMessage}",
                        state.status
                    )
                    val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
                    manager.notify(NOTIFICATION_ID, notif)
                }
            }
        }
    }

    private fun buildNotification(contentText: String, status: ProfileStatus): Notification {
        val mainIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, AutomationService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )

        val toggleIntent = if (status == ProfileStatus.RUNNING) {
            PendingIntent.getService(
                this,
                2,
                Intent(this, AutomationService::class.java).apply { action = ACTION_PAUSE },
                PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            PendingIntent.getService(
                this,
                3,
                Intent(this, AutomationService::class.java).apply { action = ACTION_RESUME },
                PendingIntent.FLAG_IMMUTABLE
            )
        }

        val toggleActionTitle = if (status == ProfileStatus.RUNNING) "Pause" else "Resume"

        return NotificationCompat.Builder(this, AutoPilotApplication.CHANNEL_ID)
            .setContentTitle("AutoPilot Active")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(mainIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, toggleActionTitle, toggleIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopIntent)
            .build()
    }

    override fun onDestroy() {
        val app = application as? AutoPilotApplication
        app?.automationCoordinator?.stop()
        observerJob?.cancel()
        super.onDestroy()
    }
}
