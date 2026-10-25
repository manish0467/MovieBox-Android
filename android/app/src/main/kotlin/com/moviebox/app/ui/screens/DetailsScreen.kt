package com.moviebox.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.moviebox.app.R
import coil.compose.AsyncImage
import com.moviebox.app.core.Release
import com.moviebox.app.core.SubtitleOption
import com.moviebox.app.ui.DetailsUiState
import com.moviebox.app.ui.components.pressScale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(
    state: DetailsUiState,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onResolutionSelected: (String) -> Unit,
    onSubtitleSelected: (SubtitleOption?) -> Unit,
    onSeasonSelected: (Int) -> Unit,
    onEpisodeSelected: (Int) -> Unit,
    onPlay: (Release, SubtitleOption?) -> Unit,
    onDownload: (Release) -> Unit,
    onAutoPlayConsumed: () -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it) }
    }

    // Auto-play (requested by the history tab's resume button): start the
    // first available source as soon as this title's releases arrive.
    LaunchedEffect(state.autoPlay, state.releases) {
        if (state.autoPlay) {
            state.filteredReleases.firstOrNull()?.let { release ->
                onPlay(release, state.selectedSubtitle)
            }
            onAutoPlayConsumed()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.details?.title ?: stringResource(R.string.details),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = onToggleFavorite) {
                        if (state.isFavorite) {
                            Icon(
                                Icons.Filled.Favorite,
                                contentDescription = stringResource(R.string.remove_favorite),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            Icon(
                                Icons.Outlined.FavoriteBorder,
                                contentDescription = stringResource(R.string.add_favorite),
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val details = state.details
        if (details == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                if (state.loading) CircularProgressIndicator() else Text(stringResource(R.string.nothing_to_show))
            }
            return@Scaffold
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (maxWidth >= 840.dp) {
                Row(modifier = Modifier.fillMaxSize()) {
                    DetailsInformationPane(details, Modifier.weight(0.4f))
                    DetailsContentList(
                        state = state,
                        details = details,
                        includeInformation = false,
                        modifier = Modifier.weight(0.6f),
                        onResolutionSelected = onResolutionSelected,
                        onSubtitleSelected = onSubtitleSelected,
                        onSeasonSelected = onSeasonSelected,
                        onEpisodeSelected = onEpisodeSelected,
                        onPlay = onPlay,
                        onDownload = onDownload,
                    )
                }
            } else {
                DetailsContentList(
                    state = state,
                    details = details,
                    includeInformation = true,
                    modifier = Modifier.fillMaxSize(),
                    onResolutionSelected = onResolutionSelected,
                    onSubtitleSelected = onSubtitleSelected,
                    onSeasonSelected = onSeasonSelected,
                    onEpisodeSelected = onEpisodeSelected,
                    onPlay = onPlay,
                    onDownload = onDownload,
                )
            }
        }

    }
}

@Composable
private fun DetailsContentList(
    state: DetailsUiState,
    details: com.moviebox.app.core.MediaDetails,
    includeInformation: Boolean,
    modifier: Modifier,
    onResolutionSelected: (String) -> Unit,
    onSubtitleSelected: (SubtitleOption?) -> Unit,
    onSeasonSelected: (Int) -> Unit,
    onEpisodeSelected: (Int) -> Unit,
    onPlay: (Release, SubtitleOption?) -> Unit,
    onDownload: (Release) -> Unit,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
    ) {
        if (includeInformation) {
            item { DetailsInfoHeader(details) }
            if (details.description?.isNotBlank() == true) item { DetailsDescription(details) }
            if (details.genres.isNotEmpty()) item { DetailsGenres(details) }
        }

            if (details.seasons.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.seasons),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
                item {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(details.seasons) { season ->
                            AssistChip(
                                onClick = { onSeasonSelected(season.number) },
                                label = { Text(stringResource(R.string.season_chip, season.number)) },
                                leadingIcon = if (state.selectedSeason == season.number) {
                                    {
                                        Icon(
                                            Icons.Filled.PlayArrow,
                                            contentDescription = null,
                                            modifier = Modifier.width(16.dp),
                                        )
                                    }
                                } else {
                                    null
                                },
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                val episodes = details.seasons
                    .firstOrNull { it.number == state.selectedSeason }
                    ?.episodes
                    .orEmpty()
                if (episodes.isNotEmpty()) {
                    item {
                        Text(
                                text = stringResource(R.string.episodes),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    itemsIndexed(episodes) { index, episode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .pressScale { onEpisodeSelected(index + 1) }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "E${episode.number}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.width(40.dp),
                            )
                            Text(
                                text = episode.title ?: "Episode ${episode.number}",
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            if (state.subtitles.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.subtitles),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item {
                            FilterChip(
                                selected = state.selectedSubtitle == null,
                                onClick = { onSubtitleSelected(null) },
                                label = { Text(stringResource(R.string.none)) },
                            )
                        }
                        items(state.subtitles) { sub ->
                            FilterChip(
                                selected = state.selectedSubtitle == sub,
                                onClick = { onSubtitleSelected(sub) },
                                label = { Text(sub.name) },
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.sources),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            if (state.availableResolutions.size > 1) {
                item {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.availableResolutions) { res ->
                            FilterChip(
                                selected = state.selectedResolution == res,
                                onClick = { onResolutionSelected(res) },
                                label = { Text(if (res == "All") stringResource(R.string.all) else res) },
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            if (state.loading && state.releases.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                }
            }

            val displayReleases = state.filteredReleases
            if (displayReleases.isEmpty() && !state.loading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (state.releases.isNotEmpty()) stringResource(R.string.no_sources_matching, state.selectedResolution) else stringResource(R.string.no_sources_available),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                items(displayReleases, key = { it.filename + (it.quality ?: "") + it.sourceLabel }) { release ->
                    ReleaseRow(
                        release = release,
                        onPlay = { onPlay(release, state.selectedSubtitle) },
                        onDownload = { onDownload(release) },
                    )
                }
            }
        }
}

@Composable
private fun DetailsInformationPane(
    details: com.moviebox.app.core.MediaDetails,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    ) {
        DetailsInfoHeader(details)
        if (details.description?.isNotBlank() == true) DetailsDescription(details)
        if (details.genres.isNotEmpty()) DetailsGenres(details)
    }
}

@Composable
private fun DetailsInfoHeader(details: com.moviebox.app.core.MediaDetails) {
    Row(modifier = Modifier.padding(16.dp)) {
        details.posterUrl?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = details.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.width(120.dp).aspectRatio(2f / 3f).clip(MaterialTheme.shapes.medium),
            )
            Spacer(modifier = Modifier.width(16.dp))
        }
        Column {
            Text(details.title, style = MaterialTheme.typography.titleMedium)
            details.year?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            details.imdbRating?.let { Text("IMDb $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary) }
            details.duration?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            details.director?.let { Text(stringResource(R.string.director, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun DetailsDescription(details: com.moviebox.app.core.MediaDetails) {
    Text(details.description.orEmpty(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp))
    Spacer(modifier = Modifier.height(12.dp))
}

@Composable
private fun DetailsGenres(details: com.moviebox.app.core.MediaDetails) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        details.genres.take(4).forEach { genre -> AssistChip(onClick = {}, label = { Text(genre) }) }
    }
    Spacer(modifier = Modifier.height(12.dp))
}

@Composable
private fun ReleaseRow(
    release: Release,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
) {
    // A narrow phone cannot hold the file name and two labelled buttons side
    // by side, so the buttons turn into icons there.
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val compact = maxWidth < 380.dp
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = release.filename,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(release.sourceLabel)
                        release.quality?.let { append(" · ").append(it) }
                        release.codec?.let { append(" · ").append(it) }
                        release.language?.let { append(" · ").append(it) }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            if (compact) {
                IconButton(onClick = onDownload) {
                    Icon(
                        Icons.Filled.Download,
                        contentDescription = stringResource(R.string.download),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(onClick = onPlay) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = stringResource(R.string.play),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            } else {
                OutlinedButton(onClick = onDownload) {
                    Icon(Icons.Filled.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.download), fontWeight = FontWeight.Medium)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onPlay) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.play), fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
