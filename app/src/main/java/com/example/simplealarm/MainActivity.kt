package com.example.simplealarm

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import com.example.simplealarm.ui.alarm.AlarmScreen
import com.example.simplealarm.ui.alarm.AlarmViewModel
import com.example.simplealarm.ui.clock.ClockScreen
import com.example.simplealarm.ui.navigation.Screen
import com.example.simplealarm.ui.theme.SimpleAlarmTheme
import com.example.simplealarm.ui.timer.TimerScreen
import com.example.simplealarm.ui.timer.TimerViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val alarmViewModel: AlarmViewModel by viewModels()
    private val timerViewModel: TimerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestPermissionsIfNeeded()

        setContent {
            SimpleAlarmTheme {
                MainAppScreen(
                    alarmViewModel = alarmViewModel,
                    timerViewModel = timerViewModel
                )
            }
        }
    }

    private fun requestPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                101
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(android.app.AlarmManager::class.java)
            if (!alarmManager.canScheduleExactAlarms()) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                startActivity(intent)
            }
        }
    }
}

@Composable
fun MainAppScreen(
    alarmViewModel: AlarmViewModel,
    timerViewModel: TimerViewModel
) {
    val screens = listOf(Screen.Alarm, Screen.Clock, Screen.Timer)
    // Swipe left/right between tabs; the bottom bar and the pager stay in sync.
    val pagerState = rememberPagerState(pageCount = { screens.size })
    val scope = rememberCoroutineScope()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                screens.forEachIndexed { index, screen ->
                    NavigationBarItem(
                        icon = { Icon(imageVector = screen.icon, contentDescription = stringResource(screen.titleRes)) },
                        label = { Text(stringResource(screen.titleRes)) },
                        selected = pagerState.currentPage == index,
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(index) }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.padding(innerPadding),
            // Keep all tabs composed so their state isn't lost when swiping away
            beyondViewportPageCount = screens.size - 1
        ) { page ->
            when (screens[page]) {
                Screen.Alarm -> AlarmScreen(viewModel = alarmViewModel)
                Screen.Clock -> ClockScreen(alarmViewModel = alarmViewModel)
                Screen.Timer -> TimerScreen(viewModel = timerViewModel)
            }
        }
    }
}
