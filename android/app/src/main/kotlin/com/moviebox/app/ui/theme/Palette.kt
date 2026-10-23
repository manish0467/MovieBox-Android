package com.moviebox.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * The colours a theme fills, named by role rather than by what they look like.
 *
 * Screens read a role and never a literal colour, so a theme swap is total: the
 * hairlines, the panel behind a sheet and the selected-row wash all move
 * together. The Material scheme in [MovieBoxTheme] is a projection of this, so
 * existing components keep working while new ones can reach for the role they
 * actually mean.
 *
 * [dark] is part of the contract because it decides the system-bar icons and
 * the default background, not just a tone.
 */
@Immutable
data class MovieBoxPalette(
    val dark: Boolean,
    // Grounds, from the page down to the deepest scope.
    val ground: Color,
    val groundScope: Color,
    val panel: Color,
    val panelRaised: Color,
    // Lines: between rows, between regions, and around a framed surface.
    val hairline: Color,
    val hairlineRegion: Color,
    val frameBorder: Color,
    // Ink, from a headline down to metadata.
    val ink: Color,
    val inkBright: Color,
    val inkSecondary: Color,
    val inkFaint: Color,
    // The one accent hue, its text colour and the wash behind a selection.
    val accent: Color,
    val onAccent: Color,
    val accentWash: Color,
    val accentBevel: Color,
    // Semantic: amber means "live / remote", destructive is only ever an error.
    val amber: Color,
    val destructive: Color,
    val onDestructive: Color,
)

/** The app's own dark palette: Catppuccin-adjacent violet on a blue-black page. */
val DarkPalette = MovieBoxPalette(
    dark = true,
    ground = Color(0xFF11111B),
    groundScope = Color(0xFF0B0B12),
    panel = Color(0xFF181825),
    panelRaised = Color(0xFF313244),
    hairline = Color(0xFF262637),
    hairlineRegion = Color(0xFF313244),
    frameBorder = Color(0xFF45475A),
    ink = Color(0xFFE6E6F0),
    inkBright = Color(0xFFF7F7FB),
    inkSecondary = Color(0xFFC6C6D5),
    inkFaint = Color(0xFF8E8EA3),
    accent = Color(0xFFCBA6F7),
    onAccent = Color(0xFF1E1E2E),
    accentWash = Color(0xFF2E2640),
    accentBevel = Color(0xFF4C3F6E),
    amber = Color(0xFFF9E2AF),
    destructive = Color(0xFFF38BA8),
    onDestructive = Color(0xFF11111B),
)

/** The app's own light palette: the same violet, darkened for contrast on paper. */
val LightPalette = MovieBoxPalette(
    dark = false,
    ground = Color(0xFFFFFBFE),
    groundScope = Color(0xFFF3EDF7),
    panel = Color(0xFFF7F2FA),
    panelRaised = Color(0xFFE7E0EC),
    hairline = Color(0xFFE3DCEA),
    hairlineRegion = Color(0xFFCFC6DB),
    frameBorder = Color(0xFFB9AEC9),
    ink = Color(0xFF1D1B20),
    inkBright = Color(0xFF111014),
    inkSecondary = Color(0xFF49454F),
    inkFaint = Color(0xFF79747E),
    accent = Color(0xFF6750A4),
    onAccent = Color(0xFFFFFFFF),
    accentWash = Color(0xFFEADDFF),
    accentBevel = Color(0xFF4F378B),
    amber = Color(0xFF8A5300),
    destructive = Color(0xFFB3261E),
    onDestructive = Color(0xFFFFFFFF),
)

/** [DarkPalette] with the grounds pulled to true black for OLED panels. */
val OledPalette = DarkPalette.copy(
    ground = Color(0xFF000000),
    groundScope = Color(0xFF000000),
    panel = Color(0xFF060608),
    panelRaised = Color(0xFF16161F),
    hairline = Color(0xFF1C1C26),
    hairlineRegion = Color(0xFF262632),
    frameBorder = Color(0xFF3C3C4A),
    accentWash = Color(0xFF1C1528),
)

/** [DarkPalette] with the accent rotated to blue. */
val BluePalette = DarkPalette.copy(
    accent = Color(0xFF90CAF9),
    onAccent = Color(0xFF11131B),
    accentWash = Color(0xFF1E2A38),
    accentBevel = Color(0xFF1565C0),
)

/** [DarkPalette] with the accent rotated to green. */
val GreenPalette = DarkPalette.copy(
    accent = Color(0xFFA5D6A7),
    onAccent = Color(0xFF11131B),
    accentWash = Color(0xFF1D2B20),
    accentBevel = Color(0xFF2E7D32),
)

/** [DarkPalette] with the accent rotated to rose. */
val RosePalette = DarkPalette.copy(
    accent = Color(0xFFFFB1C8),
    onAccent = Color(0xFF11131B),
    accentWash = Color(0xFF35202A),
    accentBevel = Color(0xFF8E3154),
)
