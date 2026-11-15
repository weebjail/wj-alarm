package com.example.simplealarm.widget

import android.content.Context
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.simplealarm.MainActivity
import com.example.simplealarm.R
import com.example.simplealarm.data.Alarm
import com.example.simplealarm.data.AppDatabase
import com.example.simplealarm.service.AlarmScheduler
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SimpleAlarmWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val nextAlarmText = getNextAlarmFormatted(context)
        // Date + time are system-driven TextClocks so they stay accurate between widget updates.
        val clockViews = RemoteViews(context.packageName, R.layout.widget_clock)

        provideContent {
            GlanceTheme {
                WidgetContent(
                    clockViews = clockViews,
                    nextAlarmText = nextAlarmText
                )
            }
        }
    }

    @Composable
    private fun WidgetContent(
        clockViews: RemoteViews,
        nextAlarmText: String
    ) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(Color.Transparent)
                .padding(2.dp)
                .clickable(actionStartActivity<MainActivity>()),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1 + 2. Current date above the large current time (live TextClocks)
                AndroidRemoteViews(remoteViews = clockViews, modifier = GlanceModifier.fillMaxWidth())

                // 3. Below Time: Pure white monochrome alarm glyph + day + time
                Text(
                    text = nextAlarmText,
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                        color = androidx.glance.unit.ColorProvider(Color.White)
                    )
                )
            }
        }
    }

    private suspend fun getNextAlarmFormatted(context: Context): String {
        return try {
            val db = AppDatabase.getDatabase(context)
            val enabledAlarms = db.alarmDao().getEnabledAlarms()
            // \u23F0\uFE0E uses Variation Selector-15 to force pure white text glyph
            val alarmSymbol = "\u23F0\uFE0E"
            val noAlarms = "$alarmSymbol ${context.getString(R.string.widget_no_alarms)}"
            if (enabledAlarms.isEmpty()) return noAlarms

            var earliestAlarm: Alarm? = null
            var minTriggerTime = Long.MAX_VALUE

            enabledAlarms.forEach { alarm ->
                val trigger = AlarmScheduler.calculateNextTriggerTime(alarm)
                if (trigger != null && trigger < minTriggerTime) {
                    minTriggerTime = trigger
                    earliestAlarm = alarm
                }
            }

            if (earliestAlarm != null) {
                val sdf = context.resources.configuration.locales[0].let { SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(it, "EEEhmma"), it) }
                "$alarmSymbol ${sdf.format(Date(minTriggerTime))}"
            } else {
                noAlarms
            }
        } catch (e: Exception) {
            "\u23F0\uFE0E ${context.getString(R.string.widget_no_alarms)}"
        }
    }
}

class SimpleAlarmWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SimpleAlarmWidget()
}
