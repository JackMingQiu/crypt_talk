package com.crypttalk.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF006C4C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF89F8C7),
    onPrimaryContainer = Color(0xFF002114),
    secondary = Color(0xFF4D6358),
    secondaryContainer = Color(0xFFCFE9DA),
    onSecondaryContainer = Color(0xFF0A1F16),
    background = Color(0xFFF5FBF5),
    surface = Color(0xFFF5FBF5),
    surfaceVariant = Color(0xFFDBE5DD),
    onSurfaceVariant = Color(0xFF404943),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6CDBAC),
    onPrimary = Color(0xFF003825),
    primaryContainer = Color(0xFF005236),
    onPrimaryContainer = Color(0xFF89F8C7),
    secondary = Color(0xFFB3CCBE),
    secondaryContainer = Color(0xFF354B40),
    onSecondaryContainer = Color(0xFFCFE9DA),
    background = Color(0xFF0F1511),
    surface = Color(0xFF0F1511),
    surfaceVariant = Color(0xFF404943),
    onSurfaceVariant = Color(0xFFBFC9C2),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

@Composable
fun CryptTalkTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content
    )
}
