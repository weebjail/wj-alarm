package com.example.simplealarm.service

import android.content.BroadcastReceiver
import android.content.Context
import com.example.simplealarm.R
import android.content.Intent
import android.util.Log
import androidx.glance.appwidget.updateAll
import com.example.simplealarm.data.AppDatabase
import com.example.simplealarm.widget.SimpleAlarmWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Shows the "Upcoming alarm" notification, and handles its "Dismiss now" action. */
class UpcomingAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getIntExtra("ALARM_ID", -1)
        val triggerTime = intent.getLongExtra("TRIGGER_TIME", 0L)
        if (alarmId == -1) return

        when (intent.action) {
            ACTION_SHOW_UPCOMING -> {
                val label = intent.getStringExtra("ALARM_LABEL") ?: context.getString(R.string.default_alarm_label)
                UpcomingAlarmNotifier.show(context, alarmId, label, triggerTime)
            }
            ACTION_DISMISS_EARLY -> dismissEarly(context, alarmId, triggerTime)
        }
    }

    private fun dismissEarly(context: Context, alarmId: Int, triggerTime: Long) {
        Log.d("UpcomingAlarmReceiver", "Alarm $alarmId dismissed early (was due at $triggerTime)")
        val scheduler = AlarmScheduler(context)
        // Cancels the pending ring and removes the notification
        scheduler.cancel(alarmId)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = AppDatabase.getDatabase(context).alarmDao()
                val alarm = dao.getAlarmById(alarmId) // null for snoozes, which aren't stored
                if (alarm != null) {
                    if (alarm.isRepeating()) {
                        // Skip just this occurrence; the alarm stays on for its next day
                        val updated = alarm.copy(skippedTriggerTime = triggerTime)
                        dao.updateAlarm(updated)
                        scheduler.schedule(updated)
                    } else {
                        dao.updateAlarm(alarm.copy(isEnabled = false))
                    }
                }
                SimpleAlarmWidget().updateAll(context)
            } catch (e: Exception) {
                Log.e("UpcomingAlarmReceiver", "Error dismissing alarm $alarmId early", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_SHOW_UPCOMING = "com.example.simplealarm.ACTION_SHOW_UPCOMING"
        const val ACTION_DISMISS_EARLY = "com.example.simplealarm.ACTION_DISMISS_EARLY"
    }
}
