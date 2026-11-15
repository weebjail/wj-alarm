package com.example.simplealarm.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.simplealarm.R
import com.example.simplealarm.data.Alarm
import com.example.simplealarm.data.AudioOutputOption
import java.util.Calendar
import com.example.simplealarm.ui.ringing.AlarmRingingActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AlarmService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var fallbackJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    private var alarmId: Int = -1
    private var alarmLabel: String = "Alarm"
    private var alarmVolume: Int = 50
    private var alarmVibrate: Boolean = true
    private var alarmRingtoneUri: String? = null
    private var audioOutputOption: AudioOutputOption = AudioOutputOption.BLUETOOTH_WITH_FALLBACK

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START_ALARM

        when (action) {
            ACTION_START_ALARM -> {
                // A new alarm replacing one that's still ringing: stop the old sound/timer first
                fallbackJob?.cancel()
                stopAudioPlayback()
                vibrator?.cancel()
                vibrator = null

                alarmId = intent?.getIntExtra("ALARM_ID", -1) ?: -1
                val label = intent?.getStringExtra("ALARM_LABEL") ?: getString(R.string.default_alarm_label)
                alarmLabel = label
                alarmVolume = intent?.getIntExtra("ALARM_VOLUME", 50) ?: 50
                val audioOutputStr = intent?.getStringExtra("AUDIO_OUTPUT") ?: AudioOutputOption.BLUETOOTH_WITH_FALLBACK.name
                audioOutputOption = try {
                    AudioOutputOption.valueOf(audioOutputStr)
                } catch (e: Exception) {
                    AudioOutputOption.BLUETOOTH_WITH_FALLBACK
                }
                val vibrate = intent?.getBooleanExtra("VIBRATE", true) ?: true
                val ringtoneUriStr = intent?.getStringExtra("RINGTONE_URI")
                alarmVibrate = vibrate
                alarmRingtoneUri = ringtoneUriStr

                startForegroundServiceWithNotification(label)
                acquireWakeLock()
                startRingingAndVibrating(ringtoneUriStr, vibrate)
            }
            ACTION_SNOOZE_ALARM -> {
                snoozeAlarm()
                stopAlarm()
            }
            ACTION_STOP_ALARM -> {
                stopAlarm()
            }
        }

        return START_NOT_STICKY
    }

    /**
     * Re-rings in [SNOOZE_MS]. For "Bluetooth with speaker fallback" alarms the second ring is
     * the fallback: it plays on the speaker (and vibrates), not only in the headphones.
     */
    private fun snoozeAlarm() {
        if (alarmId == -1) return
        val snoozeCal = Calendar.getInstance().apply { timeInMillis = System.currentTimeMillis() + SNOOZE_MS }
        val snoozeOutput = if (audioOutputOption == AudioOutputOption.BLUETOOTH_WITH_FALLBACK) {
            AudioOutputOption.SPEAKER_ONLY
        } else {
            audioOutputOption
        }
        val snoozedAlarm = Alarm(
            // Snoozes use their own ID range (they aren't stored in the database)
            id = if (alarmId >= SNOOZE_ID_OFFSET) alarmId else alarmId + SNOOZE_ID_OFFSET,
            hour = snoozeCal.get(Calendar.HOUR_OF_DAY),
            minute = snoozeCal.get(Calendar.MINUTE),
            label = getString(R.string.snoozed_suffix).let { suffix -> if (alarmLabel.endsWith(suffix)) alarmLabel else "$alarmLabel $suffix" },
            isEnabled = true,
            daysOfWeek = "", // one-time
            ringtoneUri = alarmRingtoneUri,
            volume = alarmVolume,
            audioOutput = snoozeOutput,
            vibrate = alarmVibrate
        )
        AlarmScheduler(this).schedule(snoozedAlarm)
        Log.d("AlarmService", "Alarm $alarmId snoozed; next ring on $snoozeOutput")
    }

    private fun startForegroundServiceWithNotification(label: String) {
        createNotificationChannel()

        val fullScreenIntent = Intent(this, AlarmRingingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("ALARM_ID", alarmId)
            putExtra("ALARM_LABEL", label)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            alarmId,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, AlarmService::class.java).apply {
            action = ACTION_STOP_ALARM
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            alarmId + 10000,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_alarm_ringing, label))
            .setContentText(getString(R.string.notif_tap_to_stop))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setOngoing(true)
            .addAction(R.drawable.ic_launcher_foreground, getString(R.string.notif_turn_off), stopPendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startRingingAndVibrating(ringtoneUriStr: String?, vibrate: Boolean) {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // Unmute and set max volumes
        setVolumes(audioManager)

        val soundUri = resolveSoundUri(ringtoneUriStr)
        val bluetoothDevice = findConnectedBluetoothAudioDevice(audioManager)

        val isBluetoothRoute = (audioOutputOption == AudioOutputOption.BLUETOOTH_WITH_FALLBACK ||
                audioOutputOption == AudioOutputOption.BLUETOOTH_ONLY)

        // In Bluetooth-with-fallback mode the first ring is headphones only (no speaker, no
        // vibration). The speaker + vibration fallback is the second ring, after a snooze.
        var deferVibration = false

        if (isBluetoothRoute && bluetoothDevice != null) {
            Log.d("AlarmService", "Headphones connected: ${bluetoothDevice.productName} (device type ${bluetoothDevice.type}). Routing alarm audio to them.")
            val playingOnBluetooth = playAudioBluetooth(soundUri, bluetoothDevice)

            if (playingOnBluetooth && audioOutputOption == AudioOutputOption.BLUETOOTH_WITH_FALLBACK) {
                deferVibration = true
                // No response at all: auto-snooze, so the alarm comes back on the speaker
                fallbackJob = serviceScope.launch {
                    Log.d("AlarmService", "Started auto-snooze timer for Bluetooth alarm")
                    delay(AUTO_SNOOZE_MS)
                    Log.d("AlarmService", "No response on Bluetooth; auto-snoozing, next ring on speaker")
                    snoozeAlarm()
                    stopAlarm()
                }
            }
        } else if (audioOutputOption == AudioOutputOption.BLUETOOTH_ONLY) {
            Log.w("AlarmService", "Bluetooth Only requested but no Bluetooth headset connected. Muted.")
        } else {
            Log.d("AlarmService", "Routing alarm directly to Speaker")
            playAudioSpeaker(soundUri)
        }

        if (vibrate && !deferVibration) {
            startVibration()
        }
    }

    private fun startVibration() {
        if (vibrator != null) return // already vibrating
        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            val pattern = longArrayOf(0, 800, 400, 800)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            Log.e("AlarmService", "Failed to start vibration", e)
        }
    }

    /** Returns true if audio is playing on Bluetooth, false if it had to fall back to the speaker. */
    private fun playAudioBluetooth(soundUri: Uri, bluetoothDevice: AudioDeviceInfo): Boolean {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        if (tryPlayWithMediaPlayer(soundUri, attributes, bluetoothDevice)) return true
        if (tryPlayWithRingtone(soundUri, attributes)) return true

        Log.w("AlarmService", "Bluetooth playback failed for $soundUri; falling back to speaker")
        playAudioSpeaker(soundUri)
        return false
    }

    private fun playAudioSpeaker(soundUri: Uri) {
        val alarmAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val mediaAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        // 1. MediaPlayer with ALARM usage (throws on failure, so we can reliably fall back)
        if (tryPlayWithMediaPlayer(soundUri, alarmAttributes, null)) return

        // 2. RingtoneManager (verified to actually be playing)
        if (tryPlayWithRingtone(soundUri, alarmAttributes)) return

        // 3. MediaPlayer with MEDIA usage
        if (tryPlayWithMediaPlayer(soundUri, mediaAttributes, null)) return

        // 4. Selected sound is unplayable (e.g. file deleted / permission lost): use the system alarm sound
        Log.w("AlarmService", "Could not play $soundUri; falling back to default alarm sound")
        val defaultAlarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        if (tryPlayWithMediaPlayer(defaultAlarmUri, alarmAttributes, null)) return
        if (tryPlayWithRingtone(defaultAlarmUri, alarmAttributes)) return

        Log.e("AlarmService", "All audio playback attempts failed")
    }

    private fun tryPlayWithMediaPlayer(soundUri: Uri, attributes: AudioAttributes, preferredDevice: AudioDeviceInfo?): Boolean {
        stopAudioPlayback()
        val mp = MediaPlayer()
        return try {
            mp.setAudioAttributes(attributes)

            // Open Content URIs properly via AssetFileDescriptor for Android 10+
            val afd = try {
                applicationContext.contentResolver.openAssetFileDescriptor(soundUri, "r")
            } catch (e: Exception) {
                null
            }

            if (afd != null) {
                mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
            } else {
                mp.setDataSource(applicationContext, soundUri)
            }

            mp.setVolume(1.0f, 1.0f)
            mp.isLooping = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && preferredDevice != null) {
                mp.setPreferredDevice(preferredDevice)
            }
            mp.prepare()
            mp.start()
            mediaPlayer = mp
            Log.d("AlarmService", "Audio playing successfully via MediaPlayer ($soundUri)")
            true
        } catch (e: Exception) {
            Log.e("AlarmService", "Failed to play audio with MediaPlayer for $soundUri", e)
            try { mp.release() } catch (_: Exception) {}
            false
        }
    }

    private fun tryPlayWithRingtone(soundUri: Uri, attributes: AudioAttributes): Boolean {
        return try {
            stopAudioPlayback()
            val r = RingtoneManager.getRingtone(applicationContext, soundUri) ?: return false
            r.audioAttributes = attributes
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                r.isLooping = true
            }
            r.play()
            // Ringtone.play() fails silently (no exception) when the URI can't be opened,
            // so verify it actually started before reporting success.
            if (!r.isPlaying) {
                Log.w("AlarmService", "Ringtone did not start playing for $soundUri")
                r.stop()
                return false
            }
            ringtone = r
            Log.d("AlarmService", "Audio playing successfully via RingtoneManager ($soundUri)")
            true
        } catch (e: Exception) {
            Log.e("AlarmService", "Failed to play audio with RingtoneManager", e)
            false
        }
    }


    private fun setVolumes(audioManager: AudioManager) {
        // Each stream in its own try: a SecurityException on one (e.g. under Do Not Disturb)
        // must not prevent the other from being set.
        try {
            audioManager.mode = AudioManager.MODE_NORMAL
        } catch (e: Exception) {
            Log.e("AlarmService", "Error setting audio mode", e)
        }
        try {
            val maxAlarmVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val targetAlarmVol = (maxAlarmVol * (alarmVolume / 100f)).toInt().coerceIn(1, maxAlarmVol)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, targetAlarmVol, 0)
        } catch (e: Exception) {
            Log.e("AlarmService", "Error setting alarm stream volume", e)
        }
        try {
            val maxMediaVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val targetMediaVol = (maxMediaVol * (alarmVolume / 100f)).toInt().coerceIn(1, maxMediaVol)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetMediaVol, 0)
        } catch (e: Exception) {
            Log.e("AlarmService", "Error setting media stream volume", e)
        }
    }

    private fun resolveSoundUri(ringtoneUriStr: String?): Uri {
        if (!ringtoneUriStr.isNull_or_empty()) {
            try {
                return Uri.parse(ringtoneUriStr)
            } catch (e: Exception) {
                Log.e("AlarmService", "Invalid ringtone URI string: $ringtoneUriStr", e)
            }
        }

        return RingtoneManager.getActualDefaultRingtoneUri(applicationContext, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: Settings.System.DEFAULT_ALARM_ALERT_URI
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    }

    /**
     * Finds the headphones' MEDIA output. Bluetooth earbuds expose two outputs: A2DP (music) and
     * SCO (phone calls). SCO must never be used here: routing media to it while no call is
     * active is silent (seen on device as AUDIO_DEVICE_OUT_BLUETOOTH_SCO "@:SUPPRESSED").
     */
    private fun findConnectedBluetoothAudioDevice(audioManager: AudioManager): AudioDeviceInfo? {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        // In order of preference
        val mediaHeadphoneTypes = listOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_HEARING_AID,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_USB_HEADSET
        )
        for (type in mediaHeadphoneTypes) {
            devices.firstOrNull { it.type == type }?.let { return it }
        }
        return null
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "SimpleAlarm:AlarmServiceWakeLock"
        ).apply {
            acquire(10 * 60 * 1000L) // 10 minutes max timeout
        }
    }

    private fun stopAudioPlayback() {
        try {
            ringtone?.stop()
            ringtone = null
        } catch (e: Exception) {
            Log.e("AlarmService", "Error stopping ringtone", e)
        }

        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {
            Log.e("AlarmService", "Error stopping media player", e)
        }
    }

    private fun stopAlarm() {
        fallbackJob?.cancel()
        fallbackJob = null

        stopAudioPlayback()

        try {
            vibrator?.cancel()
            vibrator = null
        } catch (e: Exception) {
            Log.e("AlarmService", "Error stopping vibrator", e)
        }

        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
            wakeLock = null
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()

        // Close the full-screen ringing UI (e.g. after an auto-snooze or "Turn Off" in the notification)
        sendBroadcast(Intent(ACTION_ALARM_FINISHED).setPackage(packageName))
    }

    override fun onDestroy() {
        stopAlarm()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            // Delete old notification channels so Android OS updates channel settings
            try {
                manager.deleteNotificationChannel("alarm_service_channel")
                manager.deleteNotificationChannel("alarm_service_v2")
                manager.deleteNotificationChannel("alarm_service_v3")
            } catch (e: Exception) {
                // Ignore if not present
            }

            // The channel must be SILENT: the service itself plays the sound and vibration.
            // A channel sound plays on the alarm stream, which Android sends to the speaker
            // and headphones at once, defeating Bluetooth-only routing.
            // (A new channel ID is required; Android won't change an existing channel's sound.)
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_alarm_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.channel_alarm_description)
                setSound(null, null)
                enableVibration(false)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    setBypassDnd(true)
                }
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun String?.isNull_or_empty(): Boolean = this == null || this.trim().isEmpty()

    companion object {
        const val CHANNEL_ID = "alarm_service_v4"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START_ALARM = "com.example.simplealarm.ACTION_START_ALARM"
        const val ACTION_STOP_ALARM = "com.example.simplealarm.ACTION_STOP_ALARM"
        const val ACTION_SNOOZE_ALARM = "com.example.simplealarm.ACTION_SNOOZE_ALARM"
        const val ACTION_ALARM_FINISHED = "com.example.simplealarm.ACTION_ALARM_FINISHED"
        const val SNOOZE_MS = 5 * 60 * 1000L
        /** How long a Bluetooth-with-fallback alarm rings in the headphones before auto-snoozing. */
        const val AUTO_SNOOZE_MS = 5 * 60 * 1000L
        const val SNOOZE_ID_OFFSET = 99000
    }
}
