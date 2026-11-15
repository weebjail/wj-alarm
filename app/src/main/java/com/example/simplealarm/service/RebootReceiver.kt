package com.example.simplealarm.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.glance.appwidget.updateAll
import com.example.simplealarm.data.AppDatabase
import com.example.simplealarm.widget.SimpleAlarmWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RebootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            Log.d("RebootReceiver", "Boot completed - rescheduling alarms")
            TimerScheduler.restoreAfterBoot(context)
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getDatabase(context)
                    val scheduler = AlarmScheduler(context)
                    val enabledAlarms = db.alarmDao().getEnabledAlarms()
                    enabledAlarms.forEach { alarm ->
                        scheduler.schedule(alarm)
                    }
                    SimpleAlarmWidget().updateAll(context)
                } catch (e: Exception) {
                    Log.e("RebootReceiver", "Failed to reschedule alarms after reboot", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
