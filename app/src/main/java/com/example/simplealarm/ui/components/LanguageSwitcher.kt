package com.example.simplealarm.ui.components

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.simplealarm.R
import com.example.simplealarm.data.AudioOutputOption

/**
 * Small "EN / 日本語" toggle for the top-right of each screen. Uses Android's per-app language
 * setting, so the choice is remembered and the whole app (notifications, widget too) follows it.
 */
@Composable
fun LanguageSwitcher(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val isJapanese = LocalConfiguration.current.locales[0].language == "ja"
    val description = stringResource(R.string.language_switch_description)

    TextButton(
        onClick = { setAppLanguage(context, if (isJapanese) null else "ja") },
        modifier = modifier
            .padding(end = 4.dp)
            .semantics { contentDescription = description }
    ) {
        // Shows the language you'd switch TO
        Text(
            text = if (isJapanese) "EN" else "日本語",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/** [languageTag] null = English (the app's base language). */
fun setAppLanguage(context: Context, languageTag: String?) {
    val localeManager = context.getSystemService(LocaleManager::class.java)
    localeManager.applicationLocales =
        if (languageTag == null) LocaleList.forLanguageTags("en") else LocaleList.forLanguageTags(languageTag)
}

@StringRes
fun AudioOutputOption.labelRes(): Int = when (this) {
    AudioOutputOption.BLUETOOTH_WITH_FALLBACK -> R.string.output_bt_fallback
    AudioOutputOption.BLUETOOTH_ONLY -> R.string.output_bt_only
    AudioOutputOption.SPEAKER_ONLY -> R.string.output_speaker
    AudioOutputOption.SYSTEM_DEFAULT -> R.string.output_system
}

@StringRes
fun AudioOutputOption.shortLabelRes(): Int = when (this) {
    AudioOutputOption.BLUETOOTH_WITH_FALLBACK -> R.string.output_bt_fallback_short
    AudioOutputOption.BLUETOOTH_ONLY -> R.string.output_bt_only_short
    AudioOutputOption.SPEAKER_ONLY -> R.string.output_speaker
    AudioOutputOption.SYSTEM_DEFAULT -> R.string.output_system
}

/** "7:05 AM" in English, "午前 7:05" in Japanese. */
fun formatAlarmTime(context: Context, hour: Int, minute: Int): String {
    val h = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
    val amPm = context.getString(if (hour >= 12) R.string.pm else R.string.am)
    val time = String.format("%d:%02d", h, minute)
    return context.getString(R.string.time_with_ampm, time, amPm)
}
