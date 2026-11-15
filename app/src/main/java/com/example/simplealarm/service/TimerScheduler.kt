package com.example.simplealarm.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.simplealarm.MainActivity

/**
 * The countdown timer, driven by the system AlarmManager instead of an in-app loop, so it keeps
 * running while the screen is locked, the CPU is asleep, or the app process has been killed.
 *
 * State is stored as an absolute end time, so the remaining time is always "end - now".
 */
object TimerScheduler {
    const val TIMER_ALARM_ID = 9999
    private const val PREFS = "timer_state"
    private const val KEY_END_AT = "end_at"           // wall-clock ms the timer finishes (running)
    private const val KEY_REMAINING = "remaining"     // ms left (paused)
    private const val KEY_TOTAL = "total"
    private const val KEY_STATE = "state"

    enum class State { IDLE, RUNNING, PAUSED }

    data class Snapshot(val state: State, val totalMs: Long, val endAt: Long, val remainingMs: Long) {
        fun remainingNow(now: Long = System.currentTimeMillis()): Long = when (state) {
            State.RUNNING -> (endAt - now).coerceAtLeast(0L)
            State.PAUSED -> remainingMs
            State.IDLE -> 0L
        }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun read(context: Context): Snapshot {
        val p = prefs(context)
        val state = try {
            State.valueOf(p.getString(KEY_STATE, State.IDLE.name)!!)
        } catch (_: Exception) {
            State.IDLE
        }
        return Snapshot(
            state = state,
            totalMs = p.getLong(KEY_TOTAL, 0L),
            endAt = p.getLong(KEY_END_AT, 0L),
            remainingMs = p.getLong(KEY_REMAINING, 0L)
        )
    }

    fun start(context: Context, durationMs: Long) {
        val endAt = System.currentTimeMillis() + durationMs
        prefs(context).edit()
            .putString(KEY_STATE, State.RUNNING.name)
            .putLong(KEY_TOTAL, durationMs)
            .putLong(KEY_END_AT, endAt)
            .putLong(KEY_REMAINING, durationMs)
            .apply()
        scheduleAlarm(context, endAt)
    }

    fun pause(context: Context) {
        val snap = read(context)
        if (snap.state != State.RUNNING) return
        cancelAlarm(context)
        prefs(context).edit()
            .putString(KEY_STATE, State.PAUSED.name)
            .putLong(KEY_REMAINING, snap.remainingNow())
            .apply()
    }

    fun resume(context: Context) {
        val snap = read(context)
        if (snap.state != State.PAUSED || snap.remainingMs <= 0) return
        val endAt = System.currentTimeMillis() + snap.remainingMs
        prefs(context).edit()
            .putString(KEY_STATE, State.RUNNING.name)
            .putLong(KEY_END_AT, endAt)
            .apply()
        scheduleAlarm(context, endAt)
    }

    fun reset(context: Context) {
        cancelAlarm(context)
        clear(context)
    }

    /** AlarmManager forgets alarms on reboot: re-arm a running timer (ringing now if it expired). */
    fun restoreAfterBoot(context: Context) {
        val snap = read(context)
        if (snap.state != State.RUNNING) return
        scheduleAlarm(context, maxOf(snap.endAt, System.currentTimeMillis() + 1000L))
    }

    /** Called when the timer has fired. */
    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, TimerReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            TIMER_ALARM_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun scheduleAlarm(context: Context, endAt: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val showIntent = PendingIntent.getActivity(
            context,
            TIMER_ALARM_ID,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            // setAlarmClock fires even in Doze, and lets the receiver start the foreground
            // service / ringing screen from the background.
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(endAt, showIntent), pendingIntent(context))
            Log.d("TimerScheduler", "Timer scheduled for $endAt")
        } catch (e: SecurityException) {
            // Exact-alarm permission missing: fall back to an inexact-but-Doze-safe alarm
            Log.w("TimerScheduler", "Exact alarm not permitted; using inexact timer", e)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAt, pendingIntent(context))
        }
    }

    private fun cancelAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent(context))
    }
}
