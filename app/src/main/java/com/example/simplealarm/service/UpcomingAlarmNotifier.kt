package com.example.simplealarm.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.simplealarm.MainActivity
import com.example.simplealarm.R
import java.util.Date

/**
 * The silent "Upcoming alarm" notification shown 30 minutes before an alarm, with a
 * "Dismiss now" action for turning the alarm off early.
 *
 * It uses a LOW-importance channel: it sits in the status bar and notification shade only,
 * with no sound, no vibration, no pop-up and no screen wake. It is tagged CATEGORY_ALARM so
 * Do Not Disturb / Bedtime mode (which let alarms through by default) don't hide it.
 */
object UpcomingAlarmNotifier {
    private const val CHANNEL_ID = "upcoming_alarm"
    private const val TAG = "upcoming_alarm"

    fun show(context: Context, alarmId: Int, label: String, triggerTime: Long) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val remainingMs = triggerTime - System.currentTimeMillis()
        if (remainingMs <= 0) return

        val manager = context.getSystemService(NotificationManager::class.java)
        ensureChannel(context, manager)

        val dismissIntent = Intent(context, UpcomingAlarmReceiver::class.java).apply {
            action = UpcomingAlarmReceiver.ACTION_DISMISS_EARLY
            putExtra("ALARM_ID", alarmId)
            putExtra("TRIGGER_TIME", triggerTime)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            alarmId,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            alarmId,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val timeText = DateFormat.getTimeFormat(context).format(Date(triggerTime))

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.notif_upcoming_alarm))
            .setContentText("$timeText · $label")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(openAppPendingIntent)
            .addAction(R.drawable.ic_launcher_foreground, context.getString(R.string.notif_dismiss_now), dismissPendingIntent)
            .setTimeoutAfter(remainingMs) // disappears by itself when the alarm starts ringing
            .build()

        manager.notify(TAG, alarmId, notification)
    }

    fun cancel(context: Context, alarmId: Int) {
        context.getSystemService(NotificationManager::class.java).cancel(TAG, alarmId)
    }

    private fun ensureChannel(context: Context, manager: NotificationManager) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_upcoming_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.channel_upcoming_description)
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }
}
