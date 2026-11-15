package com.example.simplealarm.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class AudioOutputOption(val displayName: String) {
    BLUETOOTH_WITH_FALLBACK("Bluetooth Headset (5m Speaker Fallback)"),
    BLUETOOTH_ONLY("Bluetooth Headset Only (No Fallback)"),
    SPEAKER_ONLY("Speaker Only"),
    SYSTEM_DEFAULT("System Default")
}

@Entity(tableName = "alarms")
data class Alarm(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val hour: Int,
    val minute: Int,
    val label: String = "Alarm",
    val isEnabled: Boolean = true,
    /**
     * Set of active days of week represented as integers 1 (Monday) through 7 (Sunday).
     * Stored as comma-separated String in DB, e.g. "1,2,3,4,5".
     */
    val daysOfWeek: String = "1,2,3,4,5,6,7",
    val ringtoneUri: String? = null,
    val volume: Int = 50, // 0..100
    val audioOutput: AudioOutputOption = AudioOutputOption.BLUETOOTH_WITH_FALLBACK,
    val vibrate: Boolean = true,
    /** Trigger time (epoch ms) of a single repeating occurrence the user dismissed early; it is skipped. */
    val skippedTriggerTime: Long? = null
) {
    fun getActiveDaysList(): List<Int> {
        if (daysOfWeek.isBlank()) return emptyList()
        return daysOfWeek.split(",").mapNotNull { it.trim().toIntOrNull() }
    }

    fun isRepeating(): Boolean = getActiveDaysList().isNotEmpty()

    fun getFormattedTime(): String {
        val h = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
        val amPm = if (hour >= 12) "PM" else "AM"
        val m = String.format("%02d", minute)
        return "$h:$m $amPm"
    }
}
