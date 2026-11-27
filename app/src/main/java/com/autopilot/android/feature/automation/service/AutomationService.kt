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
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.launch

class AutomationService : Service() {

    companion object {
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.autopilot.android.action.START"
        const val ACTION_PAUSE = "com.autopilot.android.action.PAUSE"
        const val ACTION_RESUME = "com.autopilot.android.action.RESUME"
        const val ACTION_STOP = "com.autopilot.android.action.STOP"
        const val ACTION_KEEP_ALIVE = "com.autopilot.android.action.KEEP_ALIVE"
        const val EXTRA_PROFILE_ID = "extra_profile_id"

        fun startKeepAlive(context: Context) {
            val intent = Intent(context, AutomationService::class.java).apply {
                action = ACTION_KEEP_ALIVE
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {
                // Ignore if background FGS launch is restricted prior to user interaction
            }
        }

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
    private var wakeLock: android.os.PowerManager.WakeLock? = null
    private var keepAliveMode: Boolean = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as AutoPilotApplication
        val coordinator = app.automationCoordinator
        acquireWakeLock()

        when (intent?.action) {
            ACTION_KEEP_ALIVE -> {
                keepAliveMode = true
                startAsForeground("Background Engine & Proxy Monitor Active")
                observeEngineState()
                return START_STICKY
            }
            ACTION_START -> {
                keepAliveMode = true
                val profileId = intent.getStringExtra(EXTRA_PROFILE_ID)
                startAsForeground("Starting Automation...")
                if (profileId != null) {
                    serviceScope.launch {
                        val profile = app.profileRepository.getProfileById(profileId)
                        if (profile != null) {
                            coordinator.start(profile)
                        } else if (!keepAliveMode) {
                            stopSelf()
                        }
                    }
                }
                observeEngineState()
                return START_STICKY
            }
            ACTION_PAUSE -> {
                coordinator.pause()
            }
            ACTION_RESUME -> {
                coordinator.resume()
            }
            ACTION_STOP -> {
                keepAliveMode = false
                coordinator.stop()
                releaseWakeLock()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }

        return if (keepAliveMode) START_STICKY else START_NOT_STICKY
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock?.isHeld == true) return
            val pm = getSystemService(POWER_SERVICE) as android.os.PowerManager
            wakeLock = pm.newWakeLock(
                android.os.PowerManager.PARTIAL_WAKE_LOCK,
                "ButoPilot::BackgroundKeepAliveWakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (_: Exception) {
            // Ignore wake lock failures on restricted devices
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
        wakeLock = null
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
            app.automationCoordinator.engineState.dropWhile { it.status == ProfileStatus.IDLE }.collectLatest { state ->
                if (state.status == ProfileStatus.IDLE && !keepAliveMode) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    val content = if (state.status == ProfileStatus.IDLE) {
                        "Background Engine & Proxy Monitor Active"
                    } else {
                        "${state.profile?.name ?: "Automation"} - ${state.statusMessage}"
                    }
                    val notif = buildNotification(content, state.status)
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
        releaseWakeLock()
        super.onDestroy()
    }
}
