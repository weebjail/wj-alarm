package com.example.simplealarm.ui.alarm

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import com.example.simplealarm.R
import com.example.simplealarm.data.Alarm
import com.example.simplealarm.ui.components.labelRes
import com.example.simplealarm.data.AudioOutputOption
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditDialog(
    alarm: Alarm?,
    onDismiss: () -> Unit,
    onSave: (Alarm) -> Unit,
    onDelete: (Alarm) -> Unit
) {
    val context = LocalContext.current

    val initialHour = alarm?.hour ?: 7
    val initialMinute = alarm?.minute ?: 0
    val timePickerState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = false
    )

    var label by remember { mutableStateOf(alarm?.label ?: context.getString(R.string.default_alarm_label)) }
    var isRepeat by remember { mutableStateOf(alarm?.isRepeating() ?: true) }
    var selectedDays by remember {
        mutableStateOf(alarm?.getActiveDaysList()?.toSet() ?: setOf(1, 2, 3, 4, 5))
    }
    var ringtoneUriStr by remember { mutableStateOf(alarm?.ringtoneUri) }
    var ringtoneTitle by remember {
        val title = if (!ringtoneUriStr.isNullOrBlank()) {
            val uri = Uri.parse(ringtoneUriStr)
            getFileNameFromUri(context, uri)
        } else {
            context.getString(R.string.default_alarm_sound)
        }
        mutableStateOf(title)
    }

    var showSoundSourceDialog by remember { mutableStateOf(false) }

    var audioOutput by remember {
        mutableStateOf(alarm?.audioOutput ?: AudioOutputOption.BLUETOOTH_WITH_FALLBACK)
    }
    var volume by remember { mutableFloatStateOf(alarm?.volume?.toFloat() ?: 50f) }
    var vibrate by remember { mutableStateOf(alarm?.vibrate ?: true) }

    var audioDropdownExpanded by remember { mutableStateOf(false) }

    // Launcher 1: System Alarm Sound Picker
    val ringtonePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val pickedUri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }

            if (pickedUri != null) {
                ringtoneUriStr = pickedUri.toString()
                val r = RingtoneManager.getRingtone(context, pickedUri)
                ringtoneTitle = r?.getTitle(context) ?: context.getString(R.string.system_sound)
                playPreviewSound(context, pickedUri)
            }
        }
    }

    // Launcher 2: Device File Picker (for audio files)
    val filePickerLauncher = rememberLauncherForActivityResult(
        // OpenDocument (not GetContent) so the read permission can be persisted;
        // GetContent grants are temporary and are gone by the time the alarm fires.
        contract = ActivityResultContracts.OpenDocument()
    ) { pickedUri: Uri? ->
        if (pickedUri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    pickedUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                Log.w("AlarmEdit", "Persistable URI permission not granted: ${e.message}")
            }
            ringtoneUriStr = pickedUri.toString()
            ringtoneTitle = getFileNameFromUri(context, pickedUri)
            playPreviewSound(context, pickedUri)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(if (alarm == null) R.string.add_alarm else R.string.edit_alarm),
                    fontWeight = FontWeight.Bold
                )
                if (alarm != null) {
                    IconButton(onClick = { onDelete(alarm); onDismiss() }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.delete_alarm),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TimePicker(state = timePickerState)

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(stringResource(R.string.alarm_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Repeat Checkbox
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isRepeat = !isRepeat },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isRepeat,
                        onCheckedChange = { isRepeat = it }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.repeat_alarm),
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Days of week selector
                AnimatedVisibility(visible = isRepeat) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val dayLabels = stringArrayResource(R.array.day_letters)
                            dayLabels.forEachIndexed { index, dayName ->
                                val dayIso = index + 1
                                val isSelected = selectedDays.contains(dayIso)

                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        .clickable {
                                            selectedDays = if (isSelected) {
                                                selectedDays - dayIso
                                            } else {
                                                selectedDays + dayIso
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = dayName,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Ringtone / Sound Field
                Text(
                    text = stringResource(R.string.alarm_sound),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = ringtoneTitle,
                    onValueChange = {},
                    readOnly = true,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingIcon = {
                        TextButton(onClick = { showSoundSourceDialog = true }) {
                            Text(stringResource(R.string.change))
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showSoundSourceDialog = true }
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Audio Output Selection
                Text(
                    text = stringResource(R.string.audio_output_route),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(6.dp))

                ExposedDropdownMenuBox(
                    expanded = audioDropdownExpanded,
                    onExpandedChange = { audioDropdownExpanded = !audioDropdownExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = stringResource(audioOutput.labelRes()),
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = audioDropdownExpanded) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Bluetooth,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )

                    ExposedDropdownMenu(
                        expanded = audioDropdownExpanded,
                        onDismissRequest = { audioDropdownExpanded = false }
                    ) {
                        AudioOutputOption.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(stringResource(option.labelRes())) },
                                onClick = {
                                    audioOutput = option
                                    audioDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Volume slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.volume_percent, volume.toInt()))
                }
                Slider(
                    value = volume,
                    onValueChange = { volume = it },
                    valueRange = 0f..100f,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Vibrate toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = stringResource(R.string.vibrate), fontWeight = FontWeight.Medium)
                    Switch(checked = vibrate, onCheckedChange = { vibrate = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val daysString = if (isRepeat) selectedDays.sorted().joinToString(",") else ""
                    val updatedAlarm = Alarm(
                        id = alarm?.id ?: 0,
                        hour = timePickerState.hour,
                        minute = timePickerState.minute,
                        label = label.ifBlank { context.getString(R.string.default_alarm_label) },
                        isEnabled = true,
                        daysOfWeek = daysString,
                        ringtoneUri = ringtoneUriStr,
                        volume = volume.toInt(),
                        audioOutput = audioOutput,
                        vibrate = vibrate
                    )
                    onSave(updatedAlarm)
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.save), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )

    // Choice Dialog: System Sounds vs Audio File
    if (showSoundSourceDialog) {
        AlertDialog(
            onDismissRequest = { showSoundSourceDialog = false },
            title = { Text(stringResource(R.string.select_sound_source), fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            showSoundSourceDialog = false
                            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, context.getString(R.string.system_sounds))
                                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, ringtoneUriStr?.let { Uri.parse(it) })
                                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                            }
                            ringtonePickerLauncher.launch(intent)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Icon(imageVector = Icons.Default.LibraryMusic, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.system_sounds), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = {
                            showSoundSourceDialog = false
                            filePickerLauncher.launch(arrayOf("audio/*"))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Icon(imageVector = Icons.Default.AudioFile, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.audio_file), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSoundSourceDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

private fun playPreviewSound(context: Context, soundUri: Uri) {
    try {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val devices = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        } else emptyArray()

        val bluetoothDevice = devices.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
        }

        val usage = if (bluetoothDevice != null) AudioAttributes.USAGE_MEDIA else AudioAttributes.USAGE_ALARM
        val attr = AudioAttributes.Builder()
            .setUsage(usage)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        val mp = MediaPlayer().apply {
            setDataSource(context, soundUri)
            setAudioAttributes(attr)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && bluetoothDevice != null) {
                setPreferredDevice(bluetoothDevice)
            }
            prepare()
            start()
        }

        CoroutineScope(Dispatchers.Main).launch {
            delay(2500L)
            try {
                if (mp.isPlaying) mp.stop()
                mp.release()
            } catch (e: Exception) {
                Log.e("SoundPreview", "Error stopping preview", e)
            }
        }
    } catch (e: Exception) {
        Log.e("SoundPreview", "Failed to play preview sound", e)
    }
}

private fun getFileNameFromUri(context: Context, uri: Uri): String {
    var name = context.getString(R.string.sound_file)
    try {
        val r = RingtoneManager.getRingtone(context, uri)
        val ringtoneName = r?.getTitle(context)
        if (ringtoneName != null && ringtoneName.isNotBlank()) {
            return ringtoneName
        }

        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1 && cursor.moveToFirst()) {
                name = cursor.getString(nameIndex)
            }
        }
    } catch (e: Exception) {
        Log.e("AlarmEdit", "Error resolving Uri file name", e)
    }
    return name
}

private fun String?.isNull_or_empty(): Boolean = this == null || this.trim().isEmpty()
