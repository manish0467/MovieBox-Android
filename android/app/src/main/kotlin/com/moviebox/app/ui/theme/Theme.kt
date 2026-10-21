package com.moviebox.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Modes are string-backed by the core config so persisted values remain forward compatible. */
enum class MovieBoxThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    OLED,
    DARK_BLUE,
    DARK_GREEN,
    DARK_ROSE,
}

private val MovieBoxTypography = Typography(
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
)

private fun scheme(mode: MovieBoxThemeMode, dark: Boolean) = when {
    mode == MovieBoxThemeMode.OLED -> darkColorScheme(
        primary = Color(0xFFCBA6F7), onPrimary = Color(0xFF1E1E2E),
        primaryContainer = Color(0xFF4C3F6E), onPrimaryContainer = Color(0xFFEADDFF),
        secondary = Color(0xFF89B4FA), onSecondary = Color(0xFF11131B),
        tertiary = Color(0xFF94E2D5), background = Color.Black, onBackground = Color(0xFFE6E6F0),
        surface = Color.Black, onSurface = Color(0xFFE6E6F0), surfaceVariant = Color(0xFF242432),
        onSurfaceVariant = Color(0xFFC6C6D5), error = Color(0xFFF38BA8), onError = Color(0xFF11111B),
    )
    mode == MovieBoxThemeMode.DARK_BLUE -> darkScheme(Color(0xFF90CAF9), Color(0xFF1565C0))
    mode == MovieBoxThemeMode.DARK_GREEN -> darkScheme(Color(0xFFA5D6A7), Color(0xFF2E7D32))
    mode == MovieBoxThemeMode.DARK_ROSE -> darkScheme(Color(0xFFFFB1C8), Color(0xFF8E3154))
    dark -> darkScheme(Color(0xFFCBA6F7), Color(0xFF4C3F6E))
    else -> lightColorScheme(
        primary = Color(0xFF6750A4), onPrimary = Color.White,
        primaryContainer = Color(0xFFEADDFF), onPrimaryContainer = Color(0xFF21005D),
        secondary = Color(0xFF52618D), tertiary = Color(0xFF386A61),
        background = Color(0xFFFFFBFE), surface = Color(0xFFFFFBFE),
        surfaceVariant = Color(0xFFE7E0EC), error = Color(0xFFB3261E),
    )
}

private fun darkScheme(primaryColor: Color, containerColor: Color) = darkColorScheme(
    primary = primaryColor, onPrimary = Color(0xFF1E1E2E),
    primaryContainer = containerColor, onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFF89B4FA), onSecondary = Color(0xFF11131B),
    tertiary = Color(0xFF94E2D5), background = Color(0xFF11111B),
    onBackground = Color(0xFFE6E6F0), surface = Color(0xFF181825),
    onSurface = Color(0xFFE6E6F0), surfaceVariant = Color(0xFF313244),
    onSurfaceVariant = Color(0xFFC6C6D5), error = Color(0xFFF38BA8), onError = Color(0xFF11111B),
)

/** Existing no-argument and darkTheme call sites remain source compatible. */
/** Maps a stored name to a colour scheme. Unknown names fall back to the
 *  system setting. */
fun themeModeFromName(name: String): MovieBoxThemeMode =
    when (name.trim().lowercase()) {
        "light" -> MovieBoxThemeMode.LIGHT
        "dark" -> MovieBoxThemeMode.DARK
        "oled" -> MovieBoxThemeMode.OLED
        "blue" -> MovieBoxThemeMode.DARK_BLUE
        "green" -> MovieBoxThemeMode.DARK_GREEN
        "rose" -> MovieBoxThemeMode.DARK_ROSE
        else -> MovieBoxThemeMode.SYSTEM
    }

@Composable
fun MovieBoxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) = MovieBoxTheme(
    mode = MovieBoxThemeMode.SYSTEM,
    darkTheme = darkTheme,
    useDynamicColor = true,
    content = content,
)

@Composable
fun MovieBoxTheme(
    mode: MovieBoxThemeMode,
    darkTheme: Boolean = isSystemInDarkTheme(),
    useDynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val effectiveDark = when (mode) {
        MovieBoxThemeMode.LIGHT -> false
        MovieBoxThemeMode.DARK, MovieBoxThemeMode.OLED,
        MovieBoxThemeMode.DARK_BLUE, MovieBoxThemeMode.DARK_GREEN,
        MovieBoxThemeMode.DARK_ROSE -> true
        MovieBoxThemeMode.SYSTEM -> darkTheme
    }
    val context = LocalContext.current
    val colors = if (
        mode == MovieBoxThemeMode.SYSTEM && useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    ) {
        if (effectiveDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        scheme(mode, effectiveDark)
    }
    MaterialTheme(colorScheme = colors, typography = MovieBoxTypography, content = content)
}
