package com.moviebox.app.player.features

/**
 * Container for the player feature seams.
 *
 * The 0.10.0 release adds five player features that are built independently.
 * Each one gets a seam (a small class) so the player can be wired for it once,
 * while the feature itself only touches its own files.
 *
 * Every seam ships inert in this refactor: `available` is false and every call
 * is a no-op, so the player behaves exactly as it did before.
 */
class PlayerFeatures(
    val audio: AudioTracksFeature,
    val sleep: SleepTimerFeature,
    val subtitles: SubtitleTimingFeature,
    val boost: SpeedBoostFeature,
    val lock: LockModeFeature,
)
