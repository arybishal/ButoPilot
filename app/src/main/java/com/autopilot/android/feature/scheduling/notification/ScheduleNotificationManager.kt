package com.autopilot.android.feature.scheduling.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.autopilot.android.domain.model.AutomationSchedule
import com.autopilot.android.ui.MainActivity

class ScheduleNotificationManager(private val context: Context) {

    companion object {
        const val CHANNEL_SCHEDULE_ID = "autopilot_schedule_channel"
        const val CHANNEL_SCHEDULE_NAME = "AutoPilot Schedule Alerts"

        const val NOTIFICATION_ID_BASE = 2000
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val scheduleChannel = NotificationChannel(
                CHANNEL_SCHEDULE_ID,
                CHANNEL_SCHEDULE_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifies when scheduled automations start, complete, or require attention."
                enableLights(true)
            }
            notificationManager.createNotificationChannel(scheduleChannel)
        }
    }

    fun notifyScheduleStarted(schedule: AutomationSchedule) {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            schedule.id.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_SCHEDULE_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Scheduled Automation Started")
            .setContentText("${schedule.scheduleName} (${schedule.profileName}) has begun execution.")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(getNotificationId(schedule.id), notification)
    }

    fun notifyScheduleCompleted(schedule: AutomationSchedule, durationSeconds: Long) {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            schedule.id.hashCode() + 1,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_SCHEDULE_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Scheduled Automation Completed")
            .setContentText("${schedule.scheduleName} finished in ${durationSeconds}s.")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        notificationManager.notify(getNotificationId(schedule.id), notification)
    }

    fun notifyScheduleFailed(schedule: AutomationSchedule, reason: String) {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            schedule.id.hashCode() + 2,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_SCHEDULE_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Scheduled Automation Failed")
            .setContentText("${schedule.scheduleName}: $reason")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Failed to execute schedule '${schedule.scheduleName}' for profile '${schedule.profileName}': $reason"))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(getNotificationId(schedule.id), notification)
    }

    fun notifyScheduleConflict(schedule: AutomationSchedule, reason: String) {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            schedule.id.hashCode() + 3,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_SCHEDULE_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Schedule Execution Delayed / Conflict")
            .setContentText("${schedule.scheduleName} could not run: $reason")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(getNotificationId(schedule.id), notification)
    }

    private fun getNotificationId(scheduleId: String): Int {
        return NOTIFICATION_ID_BASE + Math.abs(scheduleId.hashCode() % 5000)
    }
}
