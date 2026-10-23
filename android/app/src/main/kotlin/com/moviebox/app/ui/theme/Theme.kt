package com.moviebox.app.ui.theme

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val MovieBoxTypography = Typography(
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
)

/** One row of the theme picker. A null palette means "system". */
data class ThemeEntry(val key: String, val palette: MovieBoxPalette?)

/** The picker's order: the app's own themes, then the generated Omarchy set. */
val ThemeCatalog: List<ThemeEntry> = listOf(
    ThemeEntry("system", null),
    ThemeEntry("light", LightPalette),
    ThemeEntry("dark", DarkPalette),
    ThemeEntry("oled", OledPalette),
    ThemeEntry("blue", BluePalette),
    ThemeEntry("green", GreenPalette),
    ThemeEntry("rose", RosePalette),
) + OmarchyPalettes.entries.sortedBy { it.key }.map { ThemeEntry(it.key, it.value) }

private val ThemeKeys: Set<String> = ThemeCatalog.mapTo(mutableSetOf()) { it.key }

/** Maps a stored name to a key. Unknown names fall back to the system theme. */
fun themeKeyFromName(name: String): String =
    name.trim().lowercase().takeIf { it in ThemeKeys } ?: "system"

/** Resolves a key to its palette; null means "system", which may be dynamic. */
fun paletteFor(key: String): MovieBoxPalette? =
    ThemeCatalog.firstOrNull { it.key == key }?.palette

/** Whether a key paints a dark surface, for the bars and the root background. */
fun themeIsDark(key: String, systemDark: Boolean): Boolean =
    paletteFor(key)?.dark ?: systemDark

/**
 * The Material projection of a palette.
 *
 * Screens read [MaterialTheme.colorScheme], so filling it from the roles keeps
 * every existing component in the theme without touching its call site. The
 * surface-container family matters as much as `surface`: sheets, dialogs and
 * the navigation bar are drawn from it, and leaving it at Material's defaults
 * is what makes a themed app still look half grey.
 */
private fun MovieBoxPalette.toColorScheme() = if (dark) {
    darkColorScheme(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accentWash,
        onPrimaryContainer = inkBright,
        inversePrimary = accentBevel,
        secondary = inkSecondary,
        onSecondary = ground,
        secondaryContainer = accentWash,
        onSecondaryContainer = inkBright,
        tertiary = amber,
        onTertiary = ground,
        tertiaryContainer = panelRaised,
        onTertiaryContainer = ink,
        background = ground,
        onBackground = ink,
        surface = panel,
        onSurface = ink,
        surfaceVariant = panelRaised,
        onSurfaceVariant = inkSecondary,
        surfaceTint = accent,
        inverseSurface = ink,
        inverseOnSurface = ground,
        error = destructive,
        onError = onDestructive,
        errorContainer = panelRaised,
        onErrorContainer = destructive,
        outline = hairlineRegion,
        outlineVariant = hairline,
        scrim = Color.Black,
        surfaceBright = panelRaised,
        surfaceDim = groundScope,
        surfaceContainerLowest = ground,
        surfaceContainerLow = ground,
        surfaceContainer = panel,
        surfaceContainerHigh = panelRaised,
        surfaceContainerHighest = panelRaised,
    )
} else {
    lightColorScheme(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accentWash,
        onPrimaryContainer = inkBright,
        inversePrimary = accentBevel,
        secondary = inkSecondary,
        onSecondary = ground,
        secondaryContainer = accentWash,
        onSecondaryContainer = inkBright,
        tertiary = amber,
        onTertiary = ground,
        tertiaryContainer = panelRaised,
        onTertiaryContainer = ink,
        background = ground,
        onBackground = ink,
        surface = panel,
        onSurface = ink,
        surfaceVariant = panelRaised,
        onSurfaceVariant = inkSecondary,
        surfaceTint = accent,
        inverseSurface = ink,
        inverseOnSurface = ground,
        error = destructive,
        onError = onDestructive,
        errorContainer = panelRaised,
        onErrorContainer = destructive,
        outline = hairlineRegion,
        outlineVariant = hairline,
        scrim = Color.Black,
        surfaceBright = panelRaised,
        surfaceDim = groundScope,
        surfaceContainerLowest = ground,
        surfaceContainerLow = ground,
        surfaceContainer = panel,
        surfaceContainerHigh = panelRaised,
        surfaceContainerHighest = panelRaised,
    )
}

@Composable
fun MovieBoxTheme(
    themeKey: String = "system",
    darkTheme: Boolean = isSystemInDarkTheme(),
    useDynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val palette = paletteFor(themeKey)
    val effectiveDark = palette?.dark ?: darkTheme
    val context = LocalContext.current
    val colors = when {
        palette == null && useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (effectiveDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        else -> (palette ?: if (effectiveDark) DarkPalette else LightPalette).toColorScheme()
    }
    MaterialTheme(colorScheme = colors, typography = MovieBoxTypography) {
        // Edge-to-edge leaves the window background visible between screens and
        // during navigation transitions, so the theme paints the root itself.
        Box(Modifier.fillMaxSize().background(colors.background)) { content() }
    }
}
