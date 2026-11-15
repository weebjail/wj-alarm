package com.example.simplealarm.ui.alarm

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.example.simplealarm.R
import com.example.simplealarm.ui.components.LanguageSwitcher
import com.example.simplealarm.ui.components.formatAlarmTime
import com.example.simplealarm.ui.components.shortLabelRes
import com.example.simplealarm.data.Alarm
import com.example.simplealarm.data.AudioOutputOption
import com.example.simplealarm.service.AlarmScheduler

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmScreen(
    viewModel: AlarmViewModel
) {
    val context = LocalContext.current
    val alarms by viewModel.alarms.collectAsState()
    var selectedAlarmForEdit by remember { mutableStateOf<Alarm?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.alarms_title), fontWeight = FontWeight.Bold) },
                actions = { LanguageSwitcher() }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(imageVector = Icons.Default.Add, contentDescription = stringResource(R.string.add_alarm))
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (alarms.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.no_alarms),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(alarms, key = { it.id }) { alarm ->
                        AlarmItemCard(
                            alarm = alarm,
                            onToggle = { isEnabled ->
                                viewModel.toggleAlarm(alarm, isEnabled)
                                if (isEnabled) {
                                    showAlarmTimeToast(context, alarm.copy(isEnabled = true))
                                }
                            },
                            onClick = { selectedAlarmForEdit = alarm }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AlarmEditDialog(
            alarm = null,
            onDismiss = { showAddDialog = false },
            onSave = { newAlarm ->
                viewModel.saveAlarm(newAlarm)
                showAlarmTimeToast(context, newAlarm)
            },
            onDelete = {}
        )
    }

    selectedAlarmForEdit?.let { alarm ->
        AlarmEditDialog(
            alarm = alarm,
            onDismiss = { selectedAlarmForEdit = null },
            onSave = { updated ->
                viewModel.saveAlarm(updated)
                showAlarmTimeToast(context, updated)
            },
            onDelete = { toDelete -> viewModel.deleteAlarm(toDelete) }
        )
    }
}

@Composable
fun AlarmItemCard(
    alarm: Alarm,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (alarm.isEnabled) MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatAlarmTime(LocalContext.current, alarm.hour, alarm.minute),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (alarm.isEnabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = alarm.label,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatDaysOfWeekSummary(LocalContext.current, alarm.getActiveDaysList()),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                AssistChip(
                    onClick = onClick,
                    label = {
                        Text(
                            text = stringResource(alarm.audioOutput.shortLabelRes()),
                            fontSize = 11.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (alarm.audioOutput == AudioOutputOption.BLUETOOTH_WITH_FALLBACK || alarm.audioOutput == AudioOutputOption.BLUETOOTH_ONLY)
                                Icons.Default.Bluetooth else Icons.Default.VolumeUp,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                )
            }

            Switch(
                checked = alarm.isEnabled,
                onCheckedChange = onToggle
            )
        }
    }
}

private fun showAlarmTimeToast(context: Context, alarm: Alarm) {
    if (!alarm.isEnabled) return

    val triggerTime = AlarmScheduler.calculateNextTriggerTime(alarm) ?: return
    val diffMs = triggerTime - System.currentTimeMillis()
    if (diffMs <= 0) return

    val totalMinutes = diffMs / (1000 * 60)
    val days = totalMinutes / (24 * 60)
    val hours = (totalMinutes % (24 * 60)) / 60
    val minutes = totalMinutes % 60

    val res = context.resources
    val parts = mutableListOf<String>()
    if (days > 0) parts.add(res.getQuantityString(R.plurals.span_days, days.toInt(), days.toInt()))
    if (hours > 0) parts.add(res.getQuantityString(R.plurals.span_hours, hours.toInt(), hours.toInt()))
    if (minutes > 0 || (days == 0L && hours == 0L)) {
        val minVal = (if (minutes == 0L && days == 0L && hours == 0L) 1L else minutes).toInt()
        parts.add(res.getQuantityString(R.plurals.span_minutes, minVal, minVal))
    }

    val timeSpanStr = when (parts.size) {
        1 -> parts[0]
        2 -> context.getString(R.string.span_join_two, parts[0], parts[1])
        3 -> context.getString(R.string.span_join_three, parts[0], parts[1], parts[2])
        else -> parts.joinToString(", ")
    }

    Toast.makeText(context, context.getString(R.string.alarm_set_toast, timeSpanStr), Toast.LENGTH_LONG).show()
}

private fun formatDaysOfWeekSummary(context: Context, days: List<Int>): String {
    if (days.isEmpty()) return context.getString(R.string.days_once)
    if (days.size == 7) return context.getString(R.string.days_every_day)
    if (days == listOf(1, 2, 3, 4, 5)) return context.getString(R.string.days_weekdays)
    if (days == listOf(6, 7)) return context.getString(R.string.days_weekends)

    val names = context.resources.getStringArray(R.array.day_names_short) // index 0 = Monday
    return days.sorted().mapNotNull { names.getOrNull(it - 1) }
        .joinToString(context.getString(R.string.days_separator))
}
