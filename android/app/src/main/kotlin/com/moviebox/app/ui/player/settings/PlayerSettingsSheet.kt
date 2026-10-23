package com.moviebox.app.ui.player.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.moviebox.app.R
import com.moviebox.app.core.SubtitleOption
import com.moviebox.app.player.features.PlayerFeatures
import com.moviebox.app.ui.player.PlayerDialogType
import com.moviebox.app.ui.player.PlayerUiState
import com.moviebox.app.ui.player.settings.pages.PlaybackPage
import com.moviebox.app.ui.player.settings.pages.QualityPage
import com.moviebox.app.ui.player.settings.pages.SubtitlesPage
import kotlinx.coroutines.launch

/** One page of the player settings sheet. */
enum class PlayerSettingsPage {
    PLAYBACK,
    SUBTITLES,
    QUALITY,
}

/**
 * The single settings surface of the player: one bottom sheet with a page per
 * concern, so new player settings do not add another dialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSettingsSheet(
    uiState: PlayerUiState,
    features: PlayerFeatures,
    onDismiss: () -> Unit,
    onSelectSpeed: (Float) -> Unit,
    onSelectResolution: (label: String, height: Int?) -> Unit,
    onSelectSubtitle: (SubtitleOption?) -> Unit,
    onUpdateSubtitleStyle: (textSizeSp: Float, textColor: Int, backgroundColor: Int) -> Unit,
    onOpenExternal: () -> Unit = {},
) {
    val pages = listOf(
        PlayerSettingsPage.PLAYBACK,
        PlayerSettingsPage.SUBTITLES,
        PlayerSettingsPage.QUALITY,
    )
    val initialPage = when (uiState.activeDialog) {
        PlayerDialogType.SUBTITLES, PlayerDialogType.SUBTITLE_STYLE -> pages.indexOf(PlayerSettingsPage.SUBTITLES)
        PlayerDialogType.QUALITY -> pages.indexOf(PlayerSettingsPage.QUALITY)
        else -> 0
    }
    val pagerState = rememberPagerState(initialPage = initialPage.coerceAtLeast(0)) { pages.size }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val initialStyle = uiState.activeDialog == PlayerDialogType.SUBTITLE_STYLE

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Text(
            text = stringResource(R.string.player_settings),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
        )

        TabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = Color.Transparent,
        ) {
            pages.forEachIndexed { index, page ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = {
                        scope.launch { pagerState.animateScrollToPage(index) }
                    },
                    icon = {
                        Icon(
                            imageVector = page.icon,
                            contentDescription = stringResource(page.label),
                        )
                    },
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 240.dp, max = 520.dp),
        ) { index ->
            when (pages[index]) {
                PlayerSettingsPage.PLAYBACK -> PlaybackPage(
                    uiState = uiState,
                    features = features,
                    onSelectSpeed = onSelectSpeed,
                    onDismiss = onDismiss,
                    onOpenExternal = onOpenExternal,
                )

                PlayerSettingsPage.SUBTITLES -> SubtitlesPage(
                    uiState = uiState,
                    features = features,
                    initialStyle = initialStyle,
                    onSelectSubtitle = onSelectSubtitle,
                    onUpdateSubtitleStyle = onUpdateSubtitleStyle,
                    onDismiss = onDismiss,
                )

                PlayerSettingsPage.QUALITY -> QualityPage(
                    uiState = uiState,
                    onSelectResolution = onSelectResolution,
                    onDismiss = onDismiss,
                )
            }
        }
    }
}

private val PlayerSettingsPage.icon: ImageVector
    get() = when (this) {
        PlayerSettingsPage.PLAYBACK -> Icons.Filled.Speed
        PlayerSettingsPage.SUBTITLES -> Icons.Filled.Subtitles
        PlayerSettingsPage.QUALITY -> Icons.Filled.HighQuality
    }

private val PlayerSettingsPage.label: Int
    get() = when (this) {
        PlayerSettingsPage.PLAYBACK -> R.string.playback
        PlayerSettingsPage.SUBTITLES -> R.string.subtitles
        PlayerSettingsPage.QUALITY -> R.string.quality_resolution
    }
