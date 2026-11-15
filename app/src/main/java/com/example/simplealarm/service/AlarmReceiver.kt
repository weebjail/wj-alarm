package com.example.simplealarm.service

import android.content.BroadcastReceiver
import android.content.Context
import com.example.simplealarm.R
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.simplealarm.data.AppDatabase
import androidx.glance.appwidget.updateAll
import com.example.simplealarm.ui.ringing.AlarmRingingActivity
import com.example.simplealarm.widget.SimpleAlarmWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getIntExtra("ALARM_ID", -1)
        val label = intent.getStringExtra("ALARM_LABEL") ?: context.getString(R.string.default_alarm_label)
        val volume = intent.getIntExtra("ALARM_VOLUME", 80)
        val audioOutput = intent.getStringExtra("AUDIO_OUTPUT") ?: "BLUETOOTH_WITH_FALLBACK"
        val vibrate = intent.getBooleanExtra("VIBRATE", true)
        val ringtoneUri = intent.getStringExtra("RINGTONE_URI")

        Log.d("AlarmReceiver", "Alarm $alarmId triggered! Output: $audioOutput")

        // The alarm is ringing now, so the "Upcoming alarm" notice is no longer needed
        UpcomingAlarmNotifier.cancel(context, alarmId)

        // 1. Start AlarmService (Foreground Service)
        val serviceIntent = Intent(context, AlarmService::class.java).apply {
            action = AlarmService.ACTION_START_ALARM
            putExtra("ALARM_ID", alarmId)
            putExtra("ALARM_LABEL", label)
            putExtra("ALARM_VOLUME", volume)
            putExtra("AUDIO_OUTPUT", audioOutput)
            putExtra("VIBRATE", vibrate)
            putExtra("RINGTONE_URI", ringtoneUri)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }

        // 2. Open full-screen AlarmRingingActivity
        val activityIntent = Intent(context, AlarmRingingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("ALARM_ID", alarmId)
            putExtra("ALARM_LABEL", label)
        }
        context.startActivity(activityIntent)

        // 3. Handle reschedule / disable in Coroutine
        if (alarmId != -1) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getDatabase(context)
                    val alarm = db.alarmDao().getAlarmById(alarmId)
                    if (alarm != null) {
                        val scheduler = AlarmScheduler(context)
                        if (alarm.isRepeating()) {
                            scheduler.schedule(alarm)
                        } else {
                            val updated = alarm.copy(isEnabled = false)
                            db.alarmDao().updateAlarm(updated)
                        }
                    }
                    // Refresh the widget's "next alarm" line now that this alarm has fired
                    SimpleAlarmWidget().updateAll(context)
                } catch (e: Exception) {
                    Log.e("AlarmReceiver", "Error processing alarm $alarmId", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
