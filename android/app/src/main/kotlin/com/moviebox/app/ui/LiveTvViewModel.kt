package com.moviebox.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.moviebox.app.core.MovieBoxRepository
import com.moviebox.app.core.TvChannel
import com.moviebox.app.core.TvPlaylist
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LiveTvUiState(
    val playlists: List<TvPlaylist> = emptyList(),
    val channels: List<TvChannel> = emptyList(),
    val query: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val showManager: Boolean = false,
    val newPlaylist: String = "",
)

/** The Live TV state: the playlists, the channel list and the search. */
class LiveTvViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = MovieBoxRepository().also { it.initialize(app) }

    private val _state = MutableStateFlow(LiveTvUiState())
    val state: StateFlow<LiveTvUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    init {
        loadPlaylists()
        loadChannels()
    }

    /** The configured playlists. */
    fun loadPlaylists() {
        viewModelScope.launch {
            runCatching { repository.tvPlaylists() }
                .onSuccess { playlists ->
                    _state.update { it.copy(playlists = playlists) }
                }
                .onFailure { error ->
                    _state.update { it.copy(error = error.message ?: "Failed to load the playlists") }
                }
        }
    }

    /** The channels of every playlist. */
    fun loadChannels() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            runCatching { repository.tvChannels(_state.value.query) }
                .onSuccess { channels ->
                    _state.update { it.copy(channels = channels, loading = false) }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(loading = false, error = error.message ?: "Failed to load the channels")
                    }
                }
        }
    }

    /** Filters the channels as the user types. */
    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            loadChannels()
        }
    }

    /** Adds a playlist. */
    fun addPlaylist(source: String) {
        val trimmed = source.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            runCatching { repository.addTvPlaylist(trimmed) }
                .onSuccess { playlists ->
                    _state.update {
                        it.copy(playlists = playlists, newPlaylist = "", showManager = false)
                    }
                    loadChannels()
                }
                .onFailure { error ->
                    _state.update { it.copy(error = error.message ?: "Could not add the playlist") }
                }
        }
    }

    /** Removes a playlist. */
    fun removePlaylist(source: String) {
        viewModelScope.launch {
            runCatching { repository.removeTvPlaylist(source) }
                .onSuccess { playlists ->
                    _state.update { it.copy(playlists = playlists) }
                    loadChannels()
                }
                .onFailure { error ->
                    _state.update { it.copy(error = error.message ?: "Could not remove the playlist") }
                }
        }
    }

    /** The playable source of a channel. */
    fun resolve(channel: TvChannel, onReady: (com.moviebox.app.core.PlaybackSource) -> Unit) {
        viewModelScope.launch {
            runCatching { repository.resolveTvChannel(channel.streamUrl) }
                .onSuccess(onReady)
                .onFailure { error ->
                    _state.update { it.copy(error = error.message ?: "Could not play the channel") }
                }
        }
    }

    fun showManager(show: Boolean) {
        _state.update { it.copy(showManager = show) }
    }

    fun onNewPlaylistChange(value: String) {
        _state.update { it.copy(newPlaylist = value) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
