package com.moviebox.app.ui

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.moviebox.app.R
import com.moviebox.app.core.CatalogItem
import com.moviebox.app.core.DownloadJob
import com.moviebox.app.core.DownloadRecords
import com.moviebox.app.core.DownloadRequest
import com.moviebox.app.core.FavoriteItem
import com.moviebox.app.core.DownloadStorage
import com.moviebox.app.core.MovieBoxRepository
import com.moviebox.app.core.ProviderInfo
import com.moviebox.app.core.UpdateChecker
import com.moviebox.app.core.WatchHistoryItem
import com.moviebox.app.ui.theme.themeKeyFromName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class MainTab(val title: String) {
    HOME("Home"),
    FAVORITES("Favorites"),
    HISTORY("History"),
    DOWNLOADS("Downloads"),
}

data class HomeDeckTab(
    val id: String,
    /** The label comes from the resources, so it is localisable. */
    @StringRes val titleRes: Int,
)

val DEFAULT_HOME_DECK_TABS = listOf(
    HomeDeckTab("0", R.string.deck_featured),
    HomeDeckTab("1", R.string.deck_movies),
    HomeDeckTab("2", R.string.deck_series),
)

/** What the update check found. */
enum class UpdateStatus {
    IDLE,
    CHECKING,
    UP_TO_DATE,
    DOWNLOADING,
    READY,
    CHECK_FAILED,
    DOWNLOAD_FAILED,
    INSTALL_BLOCKED,
    INSTALL_FAILED,
}

/** The state of the update check. */
data class UpdateState(
    val status: UpdateStatus = UpdateStatus.IDLE,
    val versionCode: Int = 0,
)

/**
 * The providers the app does not offer.
 *
 * The two BDIX mirrors are geo-blocked for some regions, so they are hidden
 * here. The engine keeps them, and the terminal client is not affected.
 */
private val HIDDEN_PROVIDERS = setOf("bdix_circleftp", "bdix_dhakaflix")

data class HomeUiState(
    val selectedTab: MainTab = MainTab.HOME,
    val providers: List<ProviderInfo> = emptyList(),
    val selectedProvider: String = "moviebox",
    val query: String = "",
    val results: List<CatalogItem> = emptyList(),
    val homepageItems: List<CatalogItem> = emptyList(),
    val selectedHomeDeckTab: String = "0",
    val continueWatching: List<WatchHistoryItem> = emptyList(),
    val favorites: List<FavoriteItem> = emptyList(),
    val history: List<WatchHistoryItem> = emptyList(),
    val downloads: List<DownloadJob> = emptyList(),
    val loading: Boolean = false,
    val deckLoading: Boolean = false,
    val favoritesLoading: Boolean = false,
    val historyLoading: Boolean = false,
    val downloadsLoading: Boolean = false,
    val error: String? = null,
) {
    val activeProviderInfo: ProviderInfo?
        get() = providers.firstOrNull { it.id == selectedProvider }

    val supportsHomepage: Boolean
        get() = activeProviderInfo?.supportsHomepage == true
}

class MovieBoxViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = MovieBoxRepository().also { it.initialize(app) }

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    /** The theme key the app uses, loaded from the engine. */
    var themeKey: String by mutableStateOf("system")
        private set

    private var searchJob: Job? = null
    private var deckJob: Job? = null

    init {
        loadProviders()
        loadHomeDeck("0")
        loadLibrary()
        loadThemeMode()
        // Publish files left behind by a previous process before the user
        // opens the Downloads tab, and restore their records.
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                DownloadStorage.reconcile(getApplication<Application>())
            }
        }
    }

    /** Reads the stored colour scheme. */
    private fun loadThemeMode() {
        viewModelScope.launch {
            runCatching { repository.getThemeMode() }
                .onSuccess { mode -> themeKey = themeKeyFromName(mode) }
        }
    }

    private val _updateState = MutableStateFlow(UpdateState())
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private var downloadedUpdate: java.io.File? = null

    /**
     * Looks for a newer release and downloads it.
     *
     * The download is only kept when it carries the certificate of the
     * installed app.
     */
    fun checkForUpdates() {
        val status = _updateState.value.status
        if (status == UpdateStatus.CHECKING || status == UpdateStatus.DOWNLOADING) return
        val context = getApplication<Application>()
        viewModelScope.launch {
            _updateState.update { it.copy(status = UpdateStatus.CHECKING) }
            val installed = UpdateChecker.installedVersionCode(context)
            val release = UpdateChecker.latestRelease()
            when {
                release == null ->
                    _updateState.update { it.copy(status = UpdateStatus.CHECK_FAILED) }

                release.versionCode <= installed ->
                    _updateState.update { it.copy(status = UpdateStatus.UP_TO_DATE) }

                else -> {
                    _updateState.update {
                        it.copy(
                            status = UpdateStatus.DOWNLOADING,
                            versionCode = release.versionCode,
                        )
                    }
                    val apk = UpdateChecker.download(context, release)
                    if (apk == null) {
                        _updateState.update { it.copy(status = UpdateStatus.DOWNLOAD_FAILED) }
                    } else {
                        downloadedUpdate = apk
                        _updateState.update { it.copy(status = UpdateStatus.READY) }
                    }
                }
            }
        }
    }

    /** Hands the downloaded update to the installer. */
    fun installUpdate() {
        val context = getApplication<Application>()
        val apk = downloadedUpdate ?: return
        if (!UpdateChecker.canInstallPackages(context)) {
            UpdateChecker.requestInstallPermission(context)
            _updateState.update { it.copy(status = UpdateStatus.INSTALL_BLOCKED) }
            return
        }
        _updateState.update {
            it.copy(
                status = if (UpdateChecker.install(context, apk)) {
                    UpdateStatus.READY
                } else {
                    UpdateStatus.INSTALL_FAILED
                },
            )
        }
    }

    /** Stores a new theme key and applies it immediately. */
    fun selectTheme(key: String) {
        themeKey = key
        viewModelScope.launch {
            runCatching { repository.setThemeMode(key) }
        }
    }

    private fun loadProviders() {
        viewModelScope.launch {
            runCatching { repository.providers() }
                .onSuccess { providers ->
                    val visible = providers.filter { it.id !in HIDDEN_PROVIDERS }
                    _state.update { state ->
                        state.copy(
                            providers = visible,
                            // The stored provider may be one of the hidden ones.
                            selectedProvider = if (state.selectedProvider in HIDDEN_PROVIDERS) {
                                visible.firstOrNull()?.id ?: state.selectedProvider
                            } else {
                                state.selectedProvider
                            },
                        )
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(error = error.message) }
                }
        }
    }

    fun selectMainTab(tab: MainTab) {
        _state.update { it.copy(selectedTab = tab) }
        when (tab) {
            MainTab.HOME -> {
                loadLibrary()
                if (_state.value.query.isBlank() && _state.value.homepageItems.isEmpty()) {
                    loadHomeDeck(_state.value.selectedHomeDeckTab)
                }
            }
            MainTab.FAVORITES -> loadFavorites()
            MainTab.HISTORY -> loadHistory()
            MainTab.DOWNLOADS -> loadDownloads()
        }
    }

    fun selectHomeDeckTab(tabId: String) {
        if (_state.value.selectedHomeDeckTab == tabId && _state.value.homepageItems.isNotEmpty()) return
        _state.update { it.copy(selectedHomeDeckTab = tabId) }
        loadHomeDeck(tabId)
    }

    fun loadHomeDeck(tabId: String = _state.value.selectedHomeDeckTab) {
        deckJob?.cancel()
        deckJob = viewModelScope.launch {
            _state.update { it.copy(deckLoading = true) }
            runCatching { repository.homepage(tabId, 1) }
                .onSuccess { items ->
                    _state.update { it.copy(homepageItems = items, deckLoading = false) }
                }
                .onFailure { error ->
                    _state.update { it.copy(deckLoading = false) }
                }
        }
    }

    fun loadLibrary() {
        loadContinueWatching()
        loadFavorites()
    }

    fun loadContinueWatching() {
        viewModelScope.launch {
            runCatching { repository.history() }
                .onSuccess { items ->
                    val inProgress = items.filter { it.isInProgress }
                    _state.update { it.copy(continueWatching = inProgress, history = items) }
                }
        }
    }

    fun loadFavorites() {
        viewModelScope.launch {
            _state.update { it.copy(favoritesLoading = true) }
            runCatching { repository.favorites() }
                .onSuccess { items ->
                    _state.update { it.copy(favorites = items, favoritesLoading = false) }
                }
                .onFailure {
                    _state.update { it.copy(favoritesLoading = false) }
                }
        }
    }

    fun loadHistory() {
        viewModelScope.launch {
            _state.update { it.copy(historyLoading = true) }
            runCatching { repository.history() }
                .onSuccess { items ->
                    _state.update {
                        it.copy(
                            history = items,
                            continueWatching = items.filter { item -> item.isInProgress },
                            historyLoading = false,
                        )
                    }
                }
                .onFailure {
                    _state.update { it.copy(historyLoading = false) }
                }
        }
    }

    fun toggleFavorite(item: FavoriteItem) {
        viewModelScope.launch {
            runCatching { repository.toggleFavorite(item) }
                .onSuccess {
                    loadFavorites()
                }
        }
    }

    /* ------------------------------------------------------------------ *
     * Downloads engine
     * ------------------------------------------------------------------ */

    fun loadDownloads() {
        val context = getApplication<Application>()
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                DownloadStorage.reconcile(context)
            }
            val activeJobs = runCatching { repository.listDownloads() }.getOrDefault(emptyList())
            val savedJobs = withContext(Dispatchers.IO) {
                DownloadStorage.getSavedJobs(context)
            }

            val finalJobs = DownloadRecords.merge(savedJobs, activeJobs)

            withContext(Dispatchers.IO) {
                DownloadStorage.saveJobs(context, finalJobs)
            }

            _state.update { it.copy(downloads = finalJobs) }
            // A finished download moves into the public Movies folder,
            // so it shows up in a gallery and in a file manager.
            finalJobs.filter { it.isCompleted }.forEach { job ->
                publishDownload(job)
            }
        }
    }

    /**
     * Moves a finished download into the public Movies collection.
     *
     * The file stays in the app's folder when the copy does not fit, and the
     * reason is shown to the user.
     */
    private fun publishDownload(job: DownloadJob) {
        val context = getApplication<Application>()
        val file = java.io.File(job.destination)
        val targetFile = DownloadStorage.resolvePlaybackFile(file)
        if (!targetFile.exists()) return
        viewModelScope.launch {
            // The file is copied into the media store, which is a lot of
            // work for a large film and must not run on the main thread.
            withContext(Dispatchers.IO) {
                DownloadStorage.publish(context, targetFile)
            }.let { result ->
                if (result is DownloadStorage.Result.Failed) {
                    _state.update { it.copy(error = result.reason) }
                }
            }
        }
    }

    fun pauseDownload(jobId: String) {
        viewModelScope.launch {
            runCatching { repository.cancelDownload(jobId) }
                .onSuccess { loadDownloads() }
        }
    }

    fun resumeDownload(job: DownloadJob) {
        viewModelScope.launch {
            val destinationBase = DownloadStorage.workingDirectory(getApplication()).absolutePath
            // Re-resolve source to guarantee a fresh, non-expired signed CDN URL
            if (job.subjectId.isNotBlank() && job.provider.isNotBlank()) {
                runCatching {
                    val releases = repository.episodeStreams(job.provider, job.subjectId, job.season, job.episode)
                    val matching = releases.firstOrNull { it.filename == job.title } ?: releases.firstOrNull()
                    if (matching != null) {
                        val source = repository.resolvePlayback(job.provider, matching)
                        repository.startDownload(
                            DownloadRequest(
                                url = source.url,
                                headers = source.headers,
                                title = job.title,
                                provider = job.provider,
                                subjectId = job.subjectId,
                                season = job.season,
                                episode = job.episode,
                                destination = destinationBase,
                            ),
                        )
                        // The native id is generated for this process. Drop the
                        // saved placeholder now that the new job owns the file.
                        withContext(Dispatchers.IO) {
                            DownloadStorage.removeJob(getApplication(), job.id)
                        }
                    }
                }.onSuccess {
                    loadDownloads()
                }
            }
        }
    }

    fun removeDownload(jobId: String) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            runCatching { repository.removeDownload(jobId) }
            withContext(Dispatchers.IO) {
                DownloadStorage.removeJob(context, jobId)
            }
            loadDownloads()
        }
    }

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
        if (query.isBlank()) {
            _state.update { it.copy(results = emptyList(), error = null) }
            if (_state.value.homepageItems.isEmpty() && _state.value.supportsHomepage) {
                loadHomeDeck(_state.value.selectedHomeDeckTab)
            }
        } else {
            search()
        }
    }

    fun selectProvider(providerId: String) {
        if (_state.value.selectedProvider == providerId) return
        _state.update { it.copy(selectedProvider = providerId) }
        val current = _state.value
        if (current.query.isNotBlank()) {
            search()
        } else if (current.supportsHomepage) {
            loadHomeDeck(current.selectedHomeDeckTab)
        }
    }

    fun search() {
        val current = _state.value
        val query = current.query.trim()
        if (query.isEmpty()) {
            _state.update { it.copy(results = emptyList(), error = null) }
            return
        }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(350) // debounce keystrokes
            _state.update { it.copy(loading = true, error = null) }
            runCatching { repository.search(current.selectedProvider, query, 1) }
                .onSuccess { items ->
                    _state.update { it.copy(results = items, loading = false) }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            loading = false,
                            error = error.message ?: "Search failed",
                        )
                    }
                }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
