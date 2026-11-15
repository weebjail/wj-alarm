package com.example.simplealarm.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.simplealarm.MainActivity
import com.example.simplealarm.data.Alarm
import java.util.Calendar

class AlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(alarm: Alarm) {
        if (!alarm.isEnabled) {
            cancel(alarm)
            return
        }

        val triggerTime = calculateNextTriggerTime(alarm) ?: return

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("ALARM_ID", alarm.id)
            putExtra("ALARM_LABEL", alarm.label)
            putExtra("ALARM_VOLUME", alarm.volume)
            putExtra("AUDIO_OUTPUT", alarm.audioOutput.name)
            putExtra("VIBRATE", alarm.vibrate)
            putExtra("RINGTONE_URI", alarm.ringtoneUri)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Show Intent when user taps the upcoming alarm in system status bar
        val showIntent = Intent(context, MainActivity::class.java)
        val showPendingIntent = PendingIntent.getActivity(
            context,
            alarm.id,
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            Log.w("AlarmScheduler", "Cannot schedule exact alarms - permission missing")
            return
        }

        val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerTime, showPendingIntent)
        alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
        Log.d("AlarmScheduler", "Scheduled alarm ${alarm.id} at $triggerTime (${alarm.getFormattedTime()})")

        scheduleUpcomingNotification(alarm, triggerTime)
    }

    /**
     * Shows the silent "Upcoming alarm" notification [UPCOMING_WINDOW_MS] before the alarm,
     * or right away if the alarm is already closer than that.
     */
    private fun scheduleUpcomingNotification(alarm: Alarm, triggerTime: Long) {
        // Replace any notification / pending notice left over from a previous schedule of this alarm
        UpcomingAlarmNotifier.cancel(context, alarm.id)
        val upcomingIntent = upcomingPendingIntent(alarm.id, alarm.label, triggerTime)
        alarmManager.cancel(upcomingIntent)

        val showAt = triggerTime - UPCOMING_WINDOW_MS
        if (showAt <= System.currentTimeMillis()) {
            UpcomingAlarmNotifier.show(context, alarm.id, alarm.label, triggerTime)
        } else {
            // RTC_WAKEUP wakes the CPU only, never the screen
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, showAt, upcomingIntent)
            Log.d("AlarmScheduler", "Upcoming notification for alarm ${alarm.id} at $showAt")
        }
    }

    private fun upcomingPendingIntent(alarmId: Int, label: String, triggerTime: Long): PendingIntent {
        val intent = Intent(context, UpcomingAlarmReceiver::class.java).apply {
            action = UpcomingAlarmReceiver.ACTION_SHOW_UPCOMING
            putExtra("ALARM_ID", alarmId)
            putExtra("ALARM_LABEL", label)
            putExtra("TRIGGER_TIME", triggerTime)
        }
        return PendingIntent.getBroadcast(
            context,
            alarmId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun cancel(alarm: Alarm) = cancel(alarm.id)

    fun cancel(alarmId: Int) {
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarmId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)

        alarmManager.cancel(upcomingPendingIntent(alarmId, "", 0L))
        UpcomingAlarmNotifier.cancel(context, alarmId)

        Log.d("AlarmScheduler", "Cancelled alarm $alarmId")
    }

    companion object {
        const val UPCOMING_WINDOW_MS = 30 * 60 * 1000L

        /**
         * Next time this alarm will ring after [afterMillis], skipping the occurrence the user
         * dismissed early (if any).
         */
        fun calculateNextTriggerTime(alarm: Alarm, afterMillis: Long = System.currentTimeMillis()): Long? {
            val next = calculateRawNextTriggerTime(alarm, afterMillis) ?: return null
            if (alarm.isRepeating() && alarm.skippedTriggerTime == next) {
                return calculateRawNextTriggerTime(alarm, next)
            }
            return next
        }

        private fun calculateRawNextTriggerTime(alarm: Alarm, afterMillis: Long): Long? {
            val activeDays = alarm.getActiveDaysList()

            val calendar = Calendar.getInstance().apply {
                timeInMillis = afterMillis
                set(Calendar.HOUR_OF_DAY, alarm.hour)
                set(Calendar.MINUTE, alarm.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            if (activeDays.isEmpty()) {
                // One-time alarm
                if (calendar.timeInMillis <= afterMillis) {
                    calendar.add(Calendar.DAY_OF_YEAR, 1)
                }
                return calendar.timeInMillis
            }

            // Repeating alarm
            for (dayOffset in 0..7) {
                val testCalendar = (calendar.clone() as Calendar).apply {
                    add(Calendar.DAY_OF_YEAR, dayOffset)
                }

                if (testCalendar.timeInMillis <= afterMillis) {
                    continue
                }

                val dayOfWeekIso = calendarDayToIso(testCalendar.get(Calendar.DAY_OF_WEEK))
                if (activeDays.contains(dayOfWeekIso)) {
                    return testCalendar.timeInMillis
                }
            }

            return null
        }

        /**
         * Maps Calendar.DAY_OF_WEEK (Sun=1, Mon=2..Sat=7) to ISO day of week (Mon=1..Sun=7)
         */
        private fun calendarDayToIso(calendarDay: Int): Int {
            return when (calendarDay) {
                Calendar.MONDAY -> 1
                Calendar.TUESDAY -> 2
                Calendar.WEDNESDAY -> 3
                Calendar.THURSDAY -> 4
                Calendar.FRIDAY -> 5
                Calendar.SATURDAY -> 6
                Calendar.SUNDAY -> 7
                else -> 1
            }
        }
    }
}
