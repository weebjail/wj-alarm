package com.example.simplealarm.ui.alarm

import android.app.Application
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.simplealarm.data.Alarm
import com.example.simplealarm.data.AppDatabase
import com.example.simplealarm.service.AlarmScheduler
import com.example.simplealarm.widget.SimpleAlarmWidget
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AlarmViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val alarmDao = db.alarmDao()
    private val scheduler = AlarmScheduler(application)

    val alarms: StateFlow<List<Alarm>> = alarmDao.getAllAlarms().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun saveAlarm(alarm: Alarm) {
        viewModelScope.launch {
            if (alarm.id == 0) {
                val newId = alarmDao.insertAlarm(alarm).toInt()
                val savedAlarm = alarm.copy(id = newId)
                scheduler.schedule(savedAlarm)
            } else {
                alarmDao.updateAlarm(alarm)
                scheduler.schedule(alarm)
            }
            updateWidget()
        }
    }

    fun toggleAlarm(alarm: Alarm, isEnabled: Boolean) {
        viewModelScope.launch {
            // Toggling clears any "dismissed early" skip so the alarm rings at its next time
            val updated = alarm.copy(isEnabled = isEnabled, skippedTriggerTime = null)
            alarmDao.updateAlarm(updated)
            if (isEnabled) {
                scheduler.schedule(updated)
            } else {
                scheduler.cancel(updated)
            }
            updateWidget()
        }
    }

    fun deleteAlarm(alarm: Alarm) {
        viewModelScope.launch {
            scheduler.cancel(alarm)
            alarmDao.deleteAlarm(alarm)
            updateWidget()
        }
    }

    private suspend fun updateWidget() {
        try {
            SimpleAlarmWidget().updateAll(getApplication())
        } catch (e: Exception) {
            // Ignore widget update errors if any
        }
    }
}
