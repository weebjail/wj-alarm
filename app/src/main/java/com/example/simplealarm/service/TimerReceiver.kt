package com.example.simplealarm.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.simplealarm.R
import com.example.simplealarm.data.AudioOutputOption
import com.example.simplealarm.ui.ringing.AlarmRingingActivity

/** Fired by AlarmManager when the countdown timer reaches zero. */
class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("TimerReceiver", "Timer finished")
        TimerScheduler.clear(context)

        val label = context.getString(R.string.timer_finished)
        val serviceIntent = Intent(context, AlarmService::class.java).apply {
            action = AlarmService.ACTION_START_ALARM
            putExtra("ALARM_ID", TimerScheduler.TIMER_ALARM_ID)
            putExtra("ALARM_LABEL", label)
            putExtra("ALARM_VOLUME", 50)
            putExtra("AUDIO_OUTPUT", AudioOutputOption.SPEAKER_ONLY.name) // timers always ring on the speaker
            putExtra("VIBRATE", true)
            // Classic timer beeps (res/raw/timer_beep.wav) rather than the alarm ringtone
            putExtra("RINGTONE_URI", "android.resource://${context.packageName}/${R.raw.timer_beep}")
        }
        context.startForegroundService(serviceIntent)

        val activityIntent = Intent(context, AlarmRingingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("ALARM_ID", TimerScheduler.TIMER_ALARM_ID)
            putExtra("ALARM_LABEL", label)
        }
        try {
            context.startActivity(activityIntent)
        } catch (e: Exception) {
            // The service's full-screen notification shows the ringing screen instead
            Log.w("TimerReceiver", "Could not open ringing screen directly", e)
        }
    }
}
