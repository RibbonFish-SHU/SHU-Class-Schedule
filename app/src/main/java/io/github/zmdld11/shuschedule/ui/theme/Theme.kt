package io.github.zmdld11.shuschedule.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val ShuBlue = Color(0xFF1E5AA8)
private val ShuBlueDark = Color(0xFFA8C8F0)

private val LightScheme = lightColorScheme(
    primary = ShuBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E3FF),
    onPrimaryContainer = Color(0xFF001B3F),
)

private val DarkScheme = darkColorScheme(
    primary = ShuBlueDark,
    onPrimary = Color(0xFF00315F),
    primaryContainer = Color(0xFF10477F),
    onPrimaryContainer = Color(0xFFD6E3FF),
)

@Composable
fun ShuScheduleTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkScheme
        else -> LightScheme
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
