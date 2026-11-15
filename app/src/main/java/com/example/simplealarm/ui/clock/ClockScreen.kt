package com.example.simplealarm.ui.clock

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.text.format.DateFormat
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.example.simplealarm.R
import com.example.simplealarm.ui.components.LanguageSwitcher
import com.example.simplealarm.data.Alarm
import com.example.simplealarm.service.AlarmScheduler
import com.example.simplealarm.ui.alarm.AlarmViewModel
import com.example.simplealarm.widget.SimpleAlarmWidgetReceiver
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ClockScreen(
    alarmViewModel: AlarmViewModel
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val alarms by alarmViewModel.alarms.collectAsState()

    var currentTimeText by remember { mutableStateOf(getFormattedTime(locale)) }
    var currentDateText by remember { mutableStateOf(getFormattedDate(locale)) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTimeText = getFormattedTime(locale)
            currentDateText = getFormattedDate(locale)
            delay(1000L)
        }
    }

    val nextAlarmInfo = remember(alarms) {
        findEarliestNextAlarm(alarms, locale)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.clock_title), fontWeight = FontWeight.Bold) },
                actions = { LanguageSwitcher() }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = currentTimeText,
                fontSize = 52.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = currentDateText,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(40.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Alarm,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.next_scheduled_alarm),
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = nextAlarmInfo ?: stringResource(R.string.no_active_alarms),
                        textAlign = TextAlign.Center,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ADD WIDGET TO HOME SCREEN BUTTON
            // Tap or press-and-hold: asks the launcher to place the widget. Most launchers'
            // prompt lets you touch & hold the preview to drag it to a spot on the home screen.
            val haptics = LocalHapticFeedback.current
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shadowElevation = 1.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .combinedClickable(
                        onClick = { pinWidgetToHomeScreen(context) },
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            pinWidgetToHomeScreen(context)
                        }
                    )
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Widgets, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.add_widget), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun pinWidgetToHomeScreen(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val widgetProvider = ComponentName(context, SimpleAlarmWidgetReceiver::class.java)

        if (appWidgetManager.isRequestPinAppWidgetSupported) {
            val successCallback = PendingIntent.getBroadcast(
                context,
                0,
                Intent(context, SimpleAlarmWidgetReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            appWidgetManager.requestPinAppWidget(widgetProvider, null, successCallback)
        } else {
            Toast.makeText(
                context,
                context.getString(R.string.add_widget_hint),
                Toast.LENGTH_LONG
            ).show()
        }
    } else {
        Toast.makeText(
            context,
            context.getString(R.string.add_widget_hint),
            Toast.LENGTH_LONG
        ).show()
    }
}

private fun findEarliestNextAlarm(alarms: List<Alarm>, locale: Locale): String? {
    val enabledAlarms = alarms.filter { it.isEnabled }
    if (enabledAlarms.isEmpty()) return null

    var minTime = Long.MAX_VALUE
    var earliest: Alarm? = null

    enabledAlarms.forEach { alarm ->
        val trigger = AlarmScheduler.calculateNextTriggerTime(alarm)
        if (trigger != null && trigger < minTime) {
            minTime = trigger
            earliest = alarm
        }
    }

    return if (earliest != null) {
        val sdf = SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "EEEMMMdhmma"), locale)
        "${earliest.label}: ${sdf.format(Date(minTime))}"
    } else {
        null
    }
}

private fun getFormattedTime(locale: Locale): String {
    val sdf = SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "hmmssa"), locale)
    return sdf.format(Date())
}

private fun getFormattedDate(locale: Locale): String {
    val sdf = SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "EEEEMMMMdyyyy"), locale)
    return sdf.format(Date())
}
