package com.moviebox.app.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moviebox.app.core.SubtitleOption
import com.moviebox.app.player.PlayerSessionPreferences
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PlayerDialogType {
    NONE,
    SUBTITLES,
    QUALITY,
    SPEED,
    CHAPTERS,
    SUBTITLE_STYLE,
}

data class PlayerUiState(
    val title: String = "",
    val season: Int = 0,
    val episode: Int = 0,
    val coverUrl: String? = null,
    val controlsVisible: Boolean = true,
    val isInPipMode: Boolean = false,
    val gestureHudState: GestureHudState? = null,
    val activeDialog: PlayerDialogType = PlayerDialogType.NONE,
    val playbackSpeed: Float = PlayerSessionPreferences.playbackSpeed,
    val selectedResolution: String = "Auto",
    val availableSubtitles: List<SubtitleOption> = emptyList(),
    val selectedSubtitle: String? = "None",
    val chapters: List<PlayerChapter> = emptyList(),
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isPlaying: Boolean = false,
    val seekScrubPositionMs: Long? = null,
)

data class PlayerChapter(
    val id: String,
    val title: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
)

class PlayerViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var hudDismissJob: Job? = null

    fun setMetadata(title: String, season: Int, episode: Int, coverUrl: String?) {
        _uiState.update {
            it.copy(
                title = title,
                season = season,
                episode = episode,
                coverUrl = coverUrl,
            )
        }
    }

    fun setControlsVisible(visible: Boolean) {
        _uiState.update { it.copy(controlsVisible = visible) }
    }

    fun toggleControls() {
        _uiState.update { it.copy(controlsVisible = !it.controlsVisible) }
    }

    fun setInPipMode(inPip: Boolean) {
        _uiState.update {
            it.copy(
                isInPipMode = inPip,
                controlsVisible = if (inPip) false else it.controlsVisible,
                activeDialog = if (inPip) PlayerDialogType.NONE else it.activeDialog,
                gestureHudState = if (inPip) null else it.gestureHudState,
            )
        }
    }

    fun showGestureHud(hudState: GestureHudState) {
        hudDismissJob?.cancel()
        _uiState.update { it.copy(gestureHudState = hudState) }
    }

    fun hideGestureHudDelayed(delayMs: Long = 800L) {
        hudDismissJob?.cancel()
        hudDismissJob = viewModelScope.launch {
            delay(delayMs)
            _uiState.update { it.copy(gestureHudState = null) }
        }
    }

    fun setDialog(dialog: PlayerDialogType) {
        _uiState.update { it.copy(activeDialog = dialog) }
    }

    fun dismissDialog() {
        _uiState.update { it.copy(activeDialog = PlayerDialogType.NONE) }
    }

    fun setPlaybackSpeed(speed: Float) {
        PlayerSessionPreferences.playbackSpeed = speed
        _uiState.update { it.copy(playbackSpeed = speed) }
    }

    fun setResolution(label: String) {
        _uiState.update { it.copy(selectedResolution = label) }
    }

    fun setSubtitles(list: List<SubtitleOption>) {
        _uiState.update { it.copy(availableSubtitles = list) }
    }

    fun setSelectedSubtitle(name: String?) {
        _uiState.update { it.copy(selectedSubtitle = name) }
    }

    fun setChapters(chapters: List<PlayerChapter>) {
        _uiState.update { it.copy(chapters = chapters) }
    }

    fun updatePlaybackProgress(positionMs: Long, durationMs: Long, isPlaying: Boolean) {
        _uiState.update {
            it.copy(
                currentPositionMs = positionMs,
                durationMs = durationMs,
                isPlaying = isPlaying,
            )
        }
    }

    fun setSeekScrubPosition(positionMs: Long?) {
        _uiState.update { it.copy(seekScrubPositionMs = positionMs) }
    }
}
