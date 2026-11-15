package com.example.simplealarm.ui.timer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.simplealarm.R
import com.example.simplealarm.ui.components.LanguageSwitcher
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerScreen(
    viewModel: TimerViewModel
) {
    val remainingTimeMs by viewModel.remainingTimeMs.collectAsState()
    val totalTimeMs by viewModel.totalTimeMs.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()
    val isPaused by viewModel.isPaused.collectAsState()
    val lastUsedDurationMs by viewModel.lastUsedDurationMs.collectAsState()

    val context = LocalContext.current
    var rawInput by remember { mutableStateOf("") }

    val isActive = isRunning || isPaused

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.timer_title), fontWeight = FontWeight.Bold) },
                actions = { LanguageSwitcher() }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            if (!isActive) {
                // SET TIMER MODE (Fits 100% of screen without scrolling)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // 1. Digital Time Display (hh:mm:ss format)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = formatRawInputToTime(context, rawInput),
                            fontSize = 54.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (rawInput.isEmpty()) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // 2. Large Rounded Keypad (Takes majority of screen)
                    KeypadGrid(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        onDigitClick = { digit ->
                            if (rawInput.length < 6) {
                                rawInput += digit
                            }
                        },
                        onDoubleZeroClick = {
                            if (rawInput.length <= 4) {
                                rawInput += "00"
                            } else if (rawInput.length == 5) {
                                rawInput += "0"
                            }
                        },
                        onClearClick = {
                            rawInput = ""
                        }
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // 3. Start Timer Button
                    Button(
                        onClick = {
                            val durationMs = parseRawInputToMs(rawInput)
                            if (durationMs > 0) {
                                viewModel.startTimer(durationMs)
                                rawInput = ""
                            }
                        },
                        enabled = rawInput.isNotEmpty() && parseRawInputToMs(rawInput) > 0,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.start_timer), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // 4. Preset Buttons Row (Big text, zero padding to prevent truncation)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        viewModel.defaultPresets.forEach { preset ->
                            Button(
                                onClick = {
                                    viewModel.startTimer(preset.durationMs)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp),
                                contentPadding = PaddingValues(horizontal = 1.dp, vertical = 0.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            ) {
                                Text(
                                    text = stringResource(R.string.preset_minutes, (preset.durationMs / 60_000L).toInt()),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // 5. Last Used Timer Button (Bottom of screen)
                    AnimatedVisibility(
                        visible = lastUsedDurationMs > 0,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ElevatedButton(
                            onClick = {
                                viewModel.startTimer(lastUsedDurationMs)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            colors = ButtonDefaults.elevatedButtonColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        ) {
                            Icon(imageVector = Icons.Default.History, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.use_last_used, formatDurationMs(context, lastUsedDurationMs)),
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            } else {
                // COUNTDOWN MODE
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 30.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(260.dp)
                    ) {
                        val progress = if (totalTimeMs > 0) remainingTimeMs.toFloat() / totalTimeMs.toFloat() else 0f

                        CircularProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxSize(),
                            strokeWidth = 12.dp,
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )

                        Text(
                            text = formatDurationMs(context, remainingTimeMs),
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(50.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Button(
                            onClick = { viewModel.resetTimer() },
                            modifier = Modifier.height(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) {
                            Icon(imageVector = Icons.Default.Stop, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.cancel), fontWeight = FontWeight.Bold)
                        }

                        if (isRunning) {
                            Button(
                                onClick = { viewModel.pauseTimer() },
                                modifier = Modifier.height(56.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Pause, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.pause), fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = { viewModel.resumeTimer() },
                                modifier = Modifier.height(56.dp)
                            ) {
                                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.resume), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun KeypadGrid(
    modifier: Modifier = Modifier,
    onDigitClick: (String) -> Unit,
    onDoubleZeroClick: () -> Unit,
    onClearClick: () -> Unit
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("00", "0", "C")
        )

        val mutedRedBg = androidx.compose.ui.graphics.Color(0xFFD32F2F).copy(alpha = 0.18f)
        val mutedRedText = androidx.compose.ui.graphics.Color(0xFFE57373)

        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                row.forEach { key ->
                    val isClear = key == "C"
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(68.dp)
                            .clip(CircleShape)
                            .background(
                                if (isClear) mutedRedBg
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable {
                                when (key) {
                                    "00" -> onDoubleZeroClick()
                                    "C" -> onClearClick()
                                    else -> onDigitClick(key)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = key,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isClear) mutedRedText
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun formatRawInputToTime(context: Context, raw: String): String {
    val padded = raw.padStart(6, '0')
    val h = padded.substring(0, 2)
    val m = padded.substring(2, 4)
    val s = padded.substring(4, 6)
    return context.getString(R.string.countdown_hms, h.toInt(), m.toInt(), s.toInt())
}

private fun parseRawInputToMs(raw: String): Long {
    if (raw.isBlank()) return 0L
    val padded = raw.padStart(6, '0')
    val h = padded.substring(0, 2).toLongOrNull() ?: 0L
    val m = padded.substring(2, 4).toLongOrNull() ?: 0L
    val s = padded.substring(4, 6).toLongOrNull() ?: 0L
    return (h * 3600 + m * 60 + s) * 1000L
}

private fun formatDurationMs(context: Context, ms: Long): String {
    val totalSecs = ms / 1000
    val h = totalSecs / 3600
    val m = (totalSecs % 3600) / 60
    val s = totalSecs % 60

    return if (h > 0) {
        context.getString(R.string.countdown_hms, h.toInt(), m.toInt(), s.toInt())
    } else {
        context.getString(R.string.countdown_ms, m.toInt(), s.toInt())
    }
}
