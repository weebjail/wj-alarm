package com.example.simplealarm.ui.timer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.simplealarm.data.TimerPreferencesRepository
import com.example.simplealarm.service.TimerScheduler
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TimerPreset(val name: String, val durationMs: Long)

/**
 * UI state for the timer. The countdown itself lives in [TimerScheduler] (AlarmManager), which
 * rings even if the screen is locked or the app is closed; this class only mirrors it for display.
 */
class TimerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = TimerPreferencesRepository(application)

    val lastUsedDurationMs: StateFlow<Long> = repository.lastTimerDurationMs.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 5 * 60 * 1000L // 5 mins default
    )

    private val _remainingTimeMs = MutableStateFlow(0L)
    val remainingTimeMs: StateFlow<Long> = _remainingTimeMs.asStateFlow()

    private val _totalTimeMs = MutableStateFlow(0L)
    val totalTimeMs: StateFlow<Long> = _totalTimeMs.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private var tickerJob: Job? = null

    val defaultPresets = listOf(
        TimerPreset("1 min", 1 * 60 * 1000L),
        TimerPreset("5 min", 5 * 60 * 1000L),
        TimerPreset("10 min", 10 * 60 * 1000L),
        TimerPreset("15 min", 15 * 60 * 1000L)
    )

    init {
        // Restore a timer that was started before the app was closed
        syncFromScheduler()
    }

    fun startTimer(durationMs: Long) {
        if (durationMs <= 0) return

        viewModelScope.launch {
            repository.saveLastTimerDuration(durationMs)
        }

        TimerScheduler.start(getApplication(), durationMs)
        syncFromScheduler()
    }

    fun pauseTimer() {
        TimerScheduler.pause(getApplication())
        syncFromScheduler()
    }

    fun resumeTimer() {
        TimerScheduler.resume(getApplication())
        syncFromScheduler()
    }

    fun resetTimer() {
        TimerScheduler.reset(getApplication())
        syncFromScheduler()
    }

    private fun syncFromScheduler() {
        val snap = TimerScheduler.read(getApplication())
        _totalTimeMs.value = snap.totalMs
        _remainingTimeMs.value = snap.remainingNow()
        _isRunning.value = snap.state == TimerScheduler.State.RUNNING
        _isPaused.value = snap.state == TimerScheduler.State.PAUSED

        tickerJob?.cancel()
        if (snap.state == TimerScheduler.State.RUNNING) {
            tickerJob = viewModelScope.launch {
                // Display only: computed from the end time, so it is correct after the screen
                // was off. Ringing is handled by TimerReceiver.
                while (true) {
                    val remaining = snap.remainingNow()
                    _remainingTimeMs.value = remaining
                    if (remaining <= 0L) {
                        _isRunning.value = false
                        _isPaused.value = false
                        _totalTimeMs.value = 0L
                        break
                    }
                    delay(100L)
                }
            }
        }
    }
}
