package com.moviebox.app.player

import android.app.PictureInPictureParams
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.moviebox.app.core.MovieBoxRepository
import com.moviebox.app.core.SubtitleOption
import com.moviebox.app.player.features.AudioTracksFeature
import com.moviebox.app.player.features.LockModeFeature
import com.moviebox.app.player.features.PlayerFeatures
import com.moviebox.app.player.features.SleepTimerFeature
import com.moviebox.app.player.features.SpeedBoostFeature
import com.moviebox.app.player.features.SubtitleTimingFeature
import com.moviebox.app.ui.player.GestureIconType
import com.moviebox.app.ui.player.GestureHudState
import com.moviebox.app.ui.player.GestureType
import com.moviebox.app.ui.player.PlayerChapter
import com.moviebox.app.ui.player.PlayerControlsOverlay
import com.moviebox.app.ui.player.PlayerDialogType
import com.moviebox.app.ui.player.PlayerSeekDragMapper
import com.moviebox.app.ui.player.PlayerViewModel
import com.moviebox.app.ui.theme.MovieBoxTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

private val playerJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}

@UnstableApi
class PlayerActivity : ComponentActivity() {

    private var player: ExoPlayer? = null
    private var playerViewRef: PlayerView? = null
    private var mediaManager: PlayerMediaManager? = null

    /** Seams for the player features; inert until each feature lands. */
    private lateinit var features: PlayerFeatures

    /** The media notification needs this permission on Android 13 and newer.
     *  Playback works without it, so the answer is ignored. */
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val repository = MovieBoxRepository()
    private val viewModel: PlayerViewModel by viewModels()

    private var provider: String = ""
    private var subjectId: String = ""
    private var title: String = ""
    private var coverUrl: String? = null
    private var season: Int = 0
    private var episode: Int = 0
    private var year: String? = null
    private var headersMap: Map<String, String> = emptyMap()
    private var currentMediaUrl: String = ""

    // Gesture tracking state
    private var startBrightness: Float = 0.5f
    private var startVolume: Int = 0
    private var maxVolume: Int = 1
    private var seekDragMapper: PlayerSeekDragMapper? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository.initialize(this)

        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        currentMediaUrl = url
        headersMap = intent.getStringArrayListExtra(EXTRA_HEADERS)
            ?.mapNotNull { entry ->
                val parts = entry.split('=', limit = 2)
                if (parts.size == 2 && parts[0].isNotBlank()) parts[0] to parts[1] else null
            }
            ?.toMap()
            .orEmpty()

        title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        provider = intent.getStringExtra(EXTRA_PROVIDER).orEmpty()
        subjectId = intent.getStringExtra(EXTRA_SUBJECT_ID).orEmpty()
        coverUrl = intent.getStringExtra(EXTRA_COVER_URL)
        season = intent.getIntExtra(EXTRA_SEASON, 0)
        episode = intent.getIntExtra(EXTRA_EPISODE, 0)
        year = intent.getStringExtra(EXTRA_YEAR)
        val startPositionSec = intent.getLongExtra(EXTRA_START_POSITION, 0L)
        val initialSubUrl = intent.getStringExtra(EXTRA_SUBTITLE_URL)
        val initialSubName = intent.getStringExtra(EXTRA_SUBTITLE_NAME)
        val subtitlesJson = intent.getStringExtra(EXTRA_SUBTITLES_JSON).orEmpty()

        if (url.isEmpty()) {
            finish()
            return
        }

        viewModel.setMetadata(title, season, episode, coverUrl)

        if (subtitlesJson.isNotBlank()) {
            runCatching {
                val list = playerJson.decodeFromString(
                    ListSerializer(SubtitleOption.serializer()),
                    subtitlesJson,
                )
                viewModel.setSubtitles(list)
            }
        }

        val httpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(headersMap)
        val dataSourceFactory = DefaultDataSource.Factory(this, httpFactory)

        val exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
        player = exoPlayer

        // Feature seams: each player feature of the 0.10.0 release plugs in
        // here. Every one of them is inert until the feature itself lands, so
        // the player behaves exactly as it did before.
        features = PlayerFeatures(
            audio = AudioTracksFeature(player = { player }),
            sleep = SleepTimerFeature(
                scope = lifecycleScope,
                onPause = { player?.pause() },
            ),
            subtitles = SubtitleTimingFeature(),
            boost = SpeedBoostFeature(
                currentSpeed = { viewModel.uiState.value.playbackSpeed },
                onSpeedChanged = { speed ->
                    player?.playbackParameters = PlaybackParameters(speed)
                },
            ),
            lock = LockModeFeature(onLockChanged = { locked -> applyLock(locked) }),
        )

        // The top bar and the system back button go through the lock feature,
        // which lets a locked player stay open.
        onBackPressedDispatcher.addCallback(this) {
            if (!features.lock.onBackPressed()) {
                saveProgress()
                finish()
            }
        }

        // Apply session speed
        exoPlayer.playbackParameters = PlaybackParameters(PlayerSessionPreferences.playbackSpeed)
        viewModel.setPlaybackSpeed(PlayerSessionPreferences.playbackSpeed)

        val initialMediaItem = buildMediaItem(url, title, null, null)
        exoPlayer.setMediaItem(initialMediaItem)

        if (startPositionSec > 0) {
            exoPlayer.seekTo(startPositionSec * 1000L)
        }

        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        updateScreenAwake()
        setSystemBarsVisible(false)

        mediaManager = PlayerMediaManager(this, exoPlayer, title, season, episode)
        mediaManager?.updateNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (!initialSubUrl.isNullOrBlank()) {
            val name = initialSubName ?: "Subtitles"
            viewModel.setSelectedSubtitle(name)
            attachSubtitleFromUrl(initialSubUrl, name)
        }

        // Listener for player events
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                updateScreenAwake()
                if (playbackState == Player.STATE_ENDED) {
                    features.sleep.onPlaybackEnded()
                }
                updateProgressState()
                mediaManager?.updateNotification()
            }

            override fun onTracksChanged(tracks: Tracks) {
                features.audio.onTracks(tracks)
            }

            override fun onCues(cueGroup: CueGroup) {
                features.subtitles.onCues(cueGroup)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateProgressState()
                mediaManager?.updateNotification()
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                updateScreenAwake()
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                mediaManager?.updateNotification()
            }

            override fun onMetadata(metadata: androidx.media3.common.Metadata) {
                val extracted = PlayerChapters.extractFromMetadata(metadata)
                if (extracted.isNotEmpty()) {
                    viewModel.setChapters(extracted)
                }
            }

            override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
                val extracted = PlayerChapters.extractFromMediaMetadata(mediaMetadata)
                if (extracted.isNotEmpty()) {
                    viewModel.setChapters(extracted)
                }
            }
        })

        // Keep Compose's transport readout in step with the player without coupling it to disk I/O.
        lifecycleScope.launch {
            while (isActive) {
                delay(250)
                if (player?.playWhenReady == true) updateProgressState()
            }
        }

        // Periodic watch progress recording back to the bridge
        lifecycleScope.launch {
            while (isActive) {
                delay(4000)
                if (player?.isPlaying == true) {
                    saveProgress()
                }
            }
        }

        setContent {
            MovieBoxTheme {
                val uiState by viewModel.uiState.collectAsState()

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { context ->
                            PlayerView(context).apply {
                                player = exoPlayer
                                useController = false
                                playerViewRef = this
                                applySubtitleStyle()
                                // Once the subtitle customizer draws cues itself,
                                // Media3's own subtitle view gets out of the way.
                                if (features.subtitles.rendersCues) {
                                    subtitleView?.visibility = View.GONE
                                }
                            }
                        },
                    )

                    PlayerControlsOverlay(
                        uiState = uiState,
                        features = features,
                        onBack = {
                            saveProgress()
                            finish()
                        },
                        onTogglePip = {
                            enterPipMode()
                        },
                        onOpenDialog = { dialog ->
                            viewModel.setDialog(dialog)
                        },
                        onDismissDialog = {
                            viewModel.dismissDialog()
                        },
                        onSelectResolution = { label, height ->
                            viewModel.setResolution(label)
                            setResolutionLimit(height)
                        },
                        onSelectSubtitle = { sub ->
                            if (sub == null) {
                                viewModel.setSelectedSubtitle("None")
                                disableSubtitles()
                            } else {
                                viewModel.setSelectedSubtitle(sub.name)
                                attachSubtitleFromUrl(sub.url, sub.name)
                            }
                        },
                        onSelectSpeed = { speed ->
                            viewModel.setPlaybackSpeed(speed)
                            exoPlayer.playbackParameters = PlaybackParameters(speed)
                        },
                        onSeekToChapter = { chapter ->
                            exoPlayer.seekTo(chapter.startTimeMs)
                        },
                        onUpdateSubtitleStyle = { sizeSp, textColor, backgroundColor ->
                            PlayerSessionPreferences.subtitleTextSizeSp = sizeSp
                            PlayerSessionPreferences.subtitleTextColor = textColor
                            PlayerSessionPreferences.subtitleBackgroundColor = backgroundColor
                            applySubtitleStyle()
                        },
                        onDragStart = { gesture, startX, startY, surfaceWidthPx ->
                            handleGestureStart(gesture, surfaceWidthPx)
                        },
                        onDragStep = { gesture, deltaX, deltaY, totalDx, totalDy ->
                            handleGestureStep(gesture, deltaX, deltaY, totalDx, totalDy)
                        },
                        onDragEnd = { gesture ->
                            handleGestureEnd(gesture)
                        },
                        onTapSurface = {
                            val visible = !viewModel.uiState.value.controlsVisible
                            viewModel.setControlsVisible(visible)
                            setSystemBarsVisible(visible)
                        },
                        onPlayPause = {
                            if (exoPlayer.isPlaying || exoPlayer.playWhenReady) exoPlayer.pause() else exoPlayer.play()
                        },
                        onSeekPreview = viewModel::setSeekScrubPosition,
                        onSeekCommit = { target -> exoPlayer.seekTo(target) },
                        onSkipBack = { exoPlayer.seekTo((exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L)) },
                        onSkipForward = {
                            val duration = exoPlayer.duration
                            val target = exoPlayer.currentPosition + 10_000L
                            exoPlayer.seekTo(if (duration > 0L) target.coerceAtMost(duration) else target)
                        },
                        onHideControls = {
                            viewModel.setControlsVisible(false)
                            setSystemBarsVisible(false)
                        },
                    )
                }
            }
        }
    }

    private fun handleGestureStart(gesture: GestureType, surfaceWidthPx: Float) {
        when (gesture) {
            GestureType.BRIGHTNESS -> {
                val current = window.attributes.screenBrightness
                startBrightness = if (current < 0f) 0.5f else current
            }
            GestureType.VOLUME -> {
                val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                startVolume = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                maxVolume = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            }
            GestureType.SEEK -> {
                val currentPlayer = player
                if (currentPlayer != null && currentPlayer.duration > 0L) {
                    seekDragMapper = PlayerSeekDragMapper(
                        startPositionMs = currentPlayer.currentPosition,
                        durationMs = currentPlayer.duration,
                        surfaceWidthPx = surfaceWidthPx,
                    )
                }
            }
        }
    }

    private fun handleGestureStep(gesture: GestureType, deltaX: Float, deltaY: Float, totalDx: Float, totalDy: Float) {
        val dm = resources.displayMetrics
        val screenHeight = dm.heightPixels.toFloat().coerceAtLeast(1f)

        when (gesture) {
            GestureType.BRIGHTNESS -> {
                // Drag up (negative dy) increases brightness
                val delta = -totalDy / (screenHeight * 0.75f)
                val targetBrightness = (startBrightness + delta).coerceIn(0.01f, 1.0f)

                val lp = window.attributes
                lp.screenBrightness = targetBrightness
                window.attributes = lp

                val percent = (targetBrightness * 100).roundToInt()
                val icon = when {
                    percent > 66 -> GestureIconType.BRIGHTNESS_HIGH
                    percent > 33 -> GestureIconType.BRIGHTNESS_MED
                    else -> GestureIconType.BRIGHTNESS_LOW
                }

                viewModel.showGestureHud(
                    GestureHudState(
                        type = GestureType.BRIGHTNESS,
                        valuePercent = percent,
                        text = "Brightness $percent%",
                        iconType = icon,
                    ),
                )
            }

            GestureType.VOLUME -> {
                val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val delta = -totalDy / (screenHeight * 0.75f)
                val targetVolume = (startVolume + (delta * maxVolume)).roundToInt().coerceIn(0, maxVolume)

                am.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0)
                val percent = (targetVolume * 100 / maxVolume)
                val icon = when {
                    targetVolume == 0 -> GestureIconType.VOLUME_MUTE
                    percent > 66 -> GestureIconType.VOLUME_HIGH
                    percent > 33 -> GestureIconType.VOLUME_MED
                    else -> GestureIconType.VOLUME_LOW
                }

                viewModel.showGestureHud(
                    GestureHudState(
                        type = GestureType.VOLUME,
                        valuePercent = percent,
                        text = "Volume $percent%",
                        iconType = icon,
                    ),
                )
            }

            GestureType.SEEK -> {
                val mapper = seekDragMapper ?: return
                val targetMs = mapper.addDragDelta(deltaX)
                viewModel.setSeekScrubPosition(targetMs)

                val totalDeltaMs = targetMs - mapper.startPositionMs
                val absDeltaSec = abs(totalDeltaMs) / 1000
                val deltaSign = if (totalDeltaMs >= 0) "+" else "-"
                val deltaStr = String.format("%s%02d:%02d", deltaSign, absDeltaSec / 60, absDeltaSec % 60)

                val targetSec = targetMs / 1000
                val targetStr = String.format("%02d:%02d", targetSec / 60, targetSec % 60)
                val icon = if (totalDeltaMs >= 0) GestureIconType.SEEK_FORWARD else GestureIconType.SEEK_REWIND
                val duration = player?.duration ?: 0L
                val percent = if (duration > 0) ((targetMs * 100) / duration).toInt() else 0

                viewModel.showGestureHud(
                    GestureHudState(
                        type = GestureType.SEEK,
                        valuePercent = percent,
                        text = targetStr,
                        iconType = icon,
                        deltaText = deltaStr,
                    ),
                )
            }
        }
    }

    private fun handleGestureEnd(gesture: GestureType) {
        if (gesture == GestureType.SEEK) {
            seekDragMapper?.targetPositionMs?.let { player?.seekTo(it) }
            seekDragMapper = null
            viewModel.setSeekScrubPosition(null)
        }
        viewModel.hideGestureHudDelayed(800L)
    }

    /**
     * Shows or hides the system bars.
     *
     * The bars sit on top of the video, so they are hidden while the video
     * plays and come back together with the controls. A swipe from the edge
     * still reveals them for a moment.
     */
    private fun setSystemBarsVisible(visible: Boolean) {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (visible) {
            controller.show(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    /**
     * Holds the screen awake while the video plays.
     *
     * Without it the device dims and locks the screen in the middle of a
     * film, even though the playback continues in the background.
     */
    private fun setScreenAwake(awake: Boolean) {
        if (awake) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun updateScreenAwake() {
        val currentPlayer = player ?: return
        setScreenAwake(
            shouldKeepPlayerScreenAwake(
                playWhenReady = currentPlayer.playWhenReady,
                playbackState = currentPlayer.playbackState,
            ),
        )
    }

    private fun updateProgressState() {
        val p = player ?: return
        viewModel.updatePlaybackProgress(
            positionMs = p.currentPosition,
            durationMs = p.duration,
            isPlaying = p.isPlaying,
        )
    }

    private fun buildPipParams(): PictureInPictureParams? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        val builder = PictureInPictureParams.Builder()

        val p = player
        if (p != null) {
            val videoSize = p.videoSize
            if (videoSize.width > 0 && videoSize.height > 0) {
                val ratio = videoSize.width.toFloat() / videoSize.height.toFloat()
                // Rational aspect ratio must be between 0.418410 and 2.390000
                if (ratio in 0.42f..2.38f) {
                    builder.setAspectRatio(android.util.Rational(videoSize.width, videoSize.height))
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(true)
        }

        return builder.build()
    }

    private fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = buildPipParams() ?: PictureInPictureParams.Builder().build()
            enterPictureInPictureMode(params)
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (player?.isPlaying == true) {
            enterPipMode()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        updateScreenAwake()
        viewModel.setInPipMode(isInPictureInPictureMode)
        features.lock.onPipChanged(isInPictureInPictureMode)
        if (!isInPictureInPictureMode && !features.lock.isLocked.value) {
            viewModel.setControlsVisible(true)
        }
    }

    /** Hides or restores the controls when the lock feature asks for it. */
    private fun applyLock(locked: Boolean) {
        val playerView = playerViewRef ?: return
        if (locked) {
            playerView.hideController()
            viewModel.setControlsVisible(false)
            setSystemBarsVisible(false)
        } else {
            playerView.showController()
            viewModel.setControlsVisible(true)
            setSystemBarsVisible(true)
        }
    }

    private fun buildMediaItem(
        url: String,
        mediaTitle: String,
        subtitleFile: File?,
        subtitleLabel: String?,
    ): MediaItem {
        val builder = MediaItem.Builder()
            .setUri(url)
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(mediaTitle)
                    .build(),
            )
            .apply {
                mimeTypeFor(url)?.let { setMimeType(it) }
            }

        if (subtitleFile != null && subtitleFile.exists() && subtitleFile.length() > 0) {
            val subMime = if (subtitleFile.name.endsWith(".vtt", ignoreCase = true) ||
                subtitleFile.name.contains(".vtt", ignoreCase = true)
            ) {
                MimeTypes.TEXT_VTT
            } else {
                MimeTypes.APPLICATION_SUBRIP
            }
            val subConfig = MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(subtitleFile))
                .setMimeType(subMime)
                .setLabel(subtitleLabel ?: "Default")
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
            builder.setSubtitleConfigurations(listOf(subConfig))
        }

        return builder.build()
    }

    private fun attachSubtitleFromUrl(subUrl: String, label: String) {
        lifecycleScope.launch {
            val file = repository.downloadSubtitleTrack(
                context = this@PlayerActivity,
                subtitleUrl = subUrl,
                label = label,
                headers = headersMap,
            )
            if (file != null && player != null) {
                val exo = player ?: return@launch
                val currentPos = exo.currentPosition
                val isPlaying = exo.playWhenReady
                val updatedItem = buildMediaItem(currentMediaUrl, title, file, label)
                exo.setMediaItem(updatedItem, currentPos)
                exo.playWhenReady = isPlaying
                exo.trackSelectionParameters = exo.trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .build()
            }
        }
    }

    private fun applySubtitleStyle() {
        val subtitleView = playerViewRef?.subtitleView ?: return
        val style = androidx.media3.ui.CaptionStyleCompat(
            PlayerSessionPreferences.subtitleTextColor,
            PlayerSessionPreferences.subtitleBackgroundColor,
            android.graphics.Color.TRANSPARENT,
            PlayerSessionPreferences.subtitleEdgeType,
            PlayerSessionPreferences.subtitleEdgeColor,
            null,
        )
        subtitleView.setStyle(style)
        subtitleView.setFixedTextSize(
            android.util.TypedValue.COMPLEX_UNIT_SP,
            PlayerSessionPreferences.subtitleTextSizeSp,
        )
    }

    private fun disableSubtitles() {
        val exo = player ?: return
        exo.trackSelectionParameters = exo.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
    }

    private fun setResolutionLimit(height: Int?) {
        val exo = player ?: return
        val builder = exo.trackSelectionParameters.buildUpon()
        if (height == null || height <= 0) {
            builder.clearVideoSizeConstraints()
        } else {
            builder.setMaxVideoSize(Int.MAX_VALUE, height)
        }
        exo.trackSelectionParameters = builder.build()
    }

    private fun saveProgress() {
        val p = player ?: return
        val durationMs = p.duration
        val positionMs = p.currentPosition
        if (durationMs > 0 && positionMs > 0 && subjectId.isNotBlank()) {
            val progressSec = positionMs / 1000
            val durationSec = durationMs / 1000
            val completed = progressSec >= (durationSec * 0.90)
            lifecycleScope.launch(Dispatchers.IO) {
                repository.recordWatchProgress(
                    context = applicationContext,
                    provider = provider,
                    subjectId = subjectId,
                    title = title,
                    coverUrl = coverUrl,
                    season = season,
                    episode = episode,
                    progressSeconds = progressSec,
                    durationSeconds = durationSec,
                    completed = completed,
                    releaseYear = year,
                )
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) return
        // A system dialog (the notification permission, the volume panel)
        // brings the bars back, so they are hidden again with the controls.
        // The call is posted, because the system restores the bars after the
        // focus callback.
        window.decorView.post {
            setSystemBarsVisible(viewModel.uiState.value.controlsVisible)
        }
    }

    override fun onPause() {
        super.onPause()
        saveProgress()
        // The rest of the app is not fullscreen.
        setSystemBarsVisible(true)
    }

    override fun onResume() {
        super.onResume()
        updateScreenAwake()
    }

    override fun onStop() {
        super.onStop()
        saveProgress()
        setScreenAwake(false)
    }

    override fun onDestroy() {
        saveProgress()
        setScreenAwake(false)
        mediaManager?.release()
        mediaManager = null
        player?.release()
        player = null
        super.onDestroy()
    }

    private fun mimeTypeFor(url: String): String? = when {
        url.contains(".mpd") -> MimeTypes.APPLICATION_MPD
        url.contains(".m3u8") -> MimeTypes.APPLICATION_M3U8
        else -> null
    }

    companion object {
        const val EXTRA_URL = "com.moviebox.app.extra.URL"
        const val EXTRA_HEADERS = "com.moviebox.app.extra.HEADERS"
        const val EXTRA_TITLE = "com.moviebox.app.extra.TITLE"
        const val EXTRA_PROVIDER = "com.moviebox.app.extra.PROVIDER"
        const val EXTRA_SUBJECT_ID = "com.moviebox.app.extra.SUBJECT_ID"
        const val EXTRA_COVER_URL = "com.moviebox.app.extra.COVER_URL"
        const val EXTRA_SEASON = "com.moviebox.app.extra.SEASON"
        const val EXTRA_EPISODE = "com.moviebox.app.extra.EPISODE"
        const val EXTRA_YEAR = "com.moviebox.app.extra.YEAR"
        const val EXTRA_START_POSITION = "com.moviebox.app.extra.START_POSITION"
        const val EXTRA_SUBTITLE_URL = "com.moviebox.app.extra.SUBTITLE_URL"
        const val EXTRA_SUBTITLE_NAME = "com.moviebox.app.extra.SUBTITLE_NAME"
        const val EXTRA_SUBTITLES_JSON = "com.moviebox.app.extra.SUBTITLES_JSON"
    }
}
