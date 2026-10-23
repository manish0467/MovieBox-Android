package com.moviebox.app.player.features

import androidx.media3.common.C
import androidx.media3.common.Tracks
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackGroup
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AudioTrackOption(
    val groupId: String,
    val trackIndex: Int,
    val trackNumber: Int,
    val label: String?,
    val language: String?,
    val channelCount: Int,
)

internal data class AudioTrackFormat(
    val groupId: String,
    val trackIndex: Int,
    val label: String?,
    val language: String?,
    val channelCount: Int,
    val supported: Boolean,
)

internal data class AudioTrackPresentation(
    val label: String,
    val channelCountSuffix: Int?,
)

data class AudioTracksUiState(
    val options: List<AudioTrackOption> = emptyList(),
    val selectedGroupId: String? = null,
)

/** Maps supported formats to ordered options with one-based fallback numbers. */
internal fun mapAudioTrackOptions(formats: List<AudioTrackFormat>): List<AudioTrackOption> =
    formats.asSequence()
        .filter { it.supported && it.groupId.isNotBlank() }
        .mapIndexed { index, format ->
            AudioTrackOption(
                groupId = format.groupId,
                trackIndex = format.trackIndex,
                trackNumber = index + 1,
                label = format.label?.takeIf(String::isNotBlank),
                language = format.language?.takeIf(String::isNotBlank),
                channelCount = format.channelCount,
            )
        }
        .toList()

/** Applies the display-name precedence and returns channels only when meaningful. */
internal fun presentAudioTrack(
    option: AudioTrackOption,
    fallbackTrackLabel: String,
): AudioTrackPresentation = AudioTrackPresentation(
    label = option.label ?: option.language ?: fallbackTrackLabel,
    channelCountSuffix = option.channelCount.takeIf { it > 1 },
)

internal fun shouldShowAudioTrackSection(usableTrackCount: Int): Boolean = usableTrackCount > 1

/** Matches a session language preference to a track in the current media item. */
internal fun preferredAudioTrack(
    options: List<AudioTrackOption>,
    language: String?,
): AudioTrackOption? {
    val normalizedLanguage = language?.takeIf(String::isNotBlank)?.lowercase() ?: return null
    return options.firstOrNull { it.language?.lowercase() == normalizedLanguage }
}

/** Seam for choosing among the audio tracks embedded in the current media item. */
class AudioTracksFeature(
    /** Supplier for the running player; null before it is created. */
    val player: () -> ExoPlayer?,
) {
    private val mutableUiState = MutableStateFlow(AudioTracksUiState())
    val uiState: StateFlow<AudioTracksUiState> = mutableUiState.asStateFlow()
    private var groupsById: Map<String, TrackGroup> = emptyMap()
    private var preferredLanguage: String? = null

    val available: Boolean = true

    /** Called from Player.Listener.onTracksChanged. */
    fun onTracks(tracks: Tracks) {
        val audioGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        val formats = audioGroups.flatMap { group ->
            val mediaGroup = group.mediaTrackGroup
            (0 until group.length)
                .map { index ->
                    val format = group.getTrackFormat(index)
                    AudioTrackFormat(
                        groupId = mediaGroup.id,
                        trackIndex = index,
                        label = format.label,
                        language = format.language,
                        channelCount = format.channelCount,
                        supported = group.isTrackSupported(index),
                    )
                }
        }
        val options = mapAudioTrackOptions(formats)
        groupsById = audioGroups.associate { it.mediaTrackGroup.id to it.mediaTrackGroup }

        val preferred = preferredAudioTrack(options, preferredLanguage)
        if (preferred != null) {
            val matchingGroup = audioGroups.firstOrNull {
                it.mediaTrackGroup.id == preferred.groupId
            }
            if (matchingGroup?.isTrackSelected(preferred.trackIndex) != true) {
                applySelection(preferred, options)
            }
        }
        mutableUiState.value = AudioTracksUiState(options, preferred?.groupId)
    }

    /** Selects an audio group, or clears the override when null. */
    fun select(trackId: String?) {
        if (trackId == null) {
            reset()
            return
        }
        val option = mutableUiState.value.options.firstOrNull { it.groupId == trackId } ?: return
        preferredLanguage = option.language
        applySelection(option, mutableUiState.value.options)
        mutableUiState.value = mutableUiState.value.copy(selectedGroupId = option.groupId)
    }

    /** Clears audio overrides and returns to the player's automatic choice. */
    fun reset() {
        preferredLanguage = null
        player()?.let { exoPlayer ->
            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                .buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                .build()
        }
        mutableUiState.value = mutableUiState.value.copy(selectedGroupId = null)
    }

    private fun applySelection(option: AudioTrackOption, options: List<AudioTrackOption>) {
        val exoPlayer = player() ?: return
        val group = groupsById[option.groupId] ?: return
        if (options.any { it.groupId == option.groupId }) {
            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                .buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                .addOverride(TrackSelectionOverride(group, listOf(option.trackIndex)))
                .build()
        }
    }
}
