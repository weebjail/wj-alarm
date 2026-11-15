package com.example.simplealarm.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromAudioOutputOption(value: AudioOutputOption): String {
        return value.name
    }

    @TypeConverter
    fun toAudioOutputOption(value: String): AudioOutputOption {
        return try {
            AudioOutputOption.valueOf(value)
        } catch (e: Exception) {
            AudioOutputOption.BLUETOOTH_WITH_FALLBACK
        }
    }
}
