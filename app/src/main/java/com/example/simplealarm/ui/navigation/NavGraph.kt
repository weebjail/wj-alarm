package com.example.simplealarm.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Schedule
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.simplealarm.R

sealed class Screen(val route: String, @StringRes val titleRes: Int, val icon: ImageVector) {
    object Alarm : Screen("alarm", R.string.tab_alarm, Icons.Default.Alarm)
    object Clock : Screen("clock", R.string.tab_clock, Icons.Default.Schedule)
    object Timer : Screen("timer", R.string.tab_timer, Icons.Default.HourglassBottom)
}
