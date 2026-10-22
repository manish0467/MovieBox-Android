package com.moviebox.app.player.features

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Seam for the control lock.
 *
 * Inert: [available] is false, so no lock button is drawn, nothing is ever
 * locked, and the back button keeps its current meaning.
 */
class LockModeFeature(
    /** Hides the controls when true, restores them when false. */
    val onLockChanged: (Boolean) -> Unit,
) {
    /** Whether the lock exists in this build. Its button hides while false. */
    val available: Boolean = false

    private val locked = MutableStateFlow(false)

    /** True while the controls are locked. */
    val isLocked: StateFlow<Boolean> = locked.asStateFlow()

    fun toggle() = Unit

    fun unlock() = Unit

    /**
     * Returns true when the lock handled the back press, which keeps the player
     * open. Returns false to let the activity finish as it does today.
     */
    fun onBackPressed(): Boolean = false

    /** Called when picture-in-picture is entered or left. */
    fun onPipChanged(isInPipMode: Boolean) = Unit
}
