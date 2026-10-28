package com.cambiopro.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.cambiopro.app.data.local.ThemeMode

private val DarkColors = darkColorScheme(
    primary = Color(0xFF2ECC71),
    onPrimary = Color(0xFF05210F),
    secondary = Color(0xFF4DA3FF),
    onSecondary = Color(0xFF00203D),
    background = Color(0xFF121417),
    onBackground = Color(0xFFE8EAED),
    surface = Color(0xFF181B20),
    onSurface = Color(0xFFE8EAED),
    surfaceVariant = Color(0xFF23272E),
    onSurfaceVariant = Color(0xFFB4BAC4),
    surfaceContainer = Color(0xFF1E2228),
    surfaceContainerHigh = Color(0xFF262B33),
    outline = Color(0xFF3A404A),
    error = Color(0xFFFF6B6B),
    errorContainer = Color(0xFF4A1F1F),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0B8F4D),
    onPrimary = Color.White,
    secondary = Color(0xFF1565C0),
    onSecondary = Color.White,
    background = Color(0xFFF5F7FA),
    onBackground = Color(0xFF14181D),
    surface = Color.White,
    onSurface = Color(0xFF14181D),
    surfaceVariant = Color(0xFFE6EAEF),
    onSurfaceVariant = Color(0xFF434A54),
    surfaceContainer = Color(0xFFEDF0F4),
    surfaceContainerHigh = Color(0xFFE3E7EC),
    outline = Color(0xFFB8BFC9),
    error = Color(0xFFC62828),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

@Composable
fun CambioTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val c = WindowCompat.getInsetsController(window, view)
                c.isAppearanceLightStatusBars = !dark
                c.isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}

@Composable
private fun isDark(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

@Composable
fun upColor(): Color = if (isDark()) Color(0xFF2ECC71) else Color(0xFF0B8F4D)

@Composable
fun downColor(): Color = if (isDark()) Color(0xFFFF6B6B) else Color(0xFFC62828)

@Composable
fun changeColor(p: Double?): Color = when {
    p == null -> MaterialTheme.colorScheme.onSurfaceVariant
    p > 0 -> upColor()
    p < 0 -> downColor()
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
