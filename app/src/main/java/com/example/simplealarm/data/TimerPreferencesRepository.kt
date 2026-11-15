package com.example.simplealarm.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "timer_preferences")

class TimerPreferencesRepository(private val context: Context) {
    companion object {
        private val LAST_TIMER_DURATION_KEY = longPreferencesKey("last_timer_duration_ms")
    }

    val lastTimerDurationMs: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[LAST_TIMER_DURATION_KEY] ?: (5 * 60 * 1000L) // default 5 minutes
    }

    suspend fun saveLastTimerDuration(durationMs: Long) {
        context.dataStore.edit { preferences ->
            preferences[LAST_TIMER_DURATION_KEY] = durationMs
        }
    }
}
