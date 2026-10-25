package com.moviebox.app.player.features

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The control lock: while locked, the gesture surface is disabled, the
 * controls stay hidden and the shield swallows every touch meant for them.
 * The first back press (or the shield's unlock button) unlocks instead of
 * leaving the player.
 */
class LockModeFeature(
    /** Hides the controls when true, restores them when false. */
    val onLockChanged: (Boolean) -> Unit,
) {
    /** Whether the lock exists in this build. Its button hides while false. */
    val available: Boolean = true

    private val locked = MutableStateFlow(false)

    /** True while the controls are locked. */
    val isLocked: StateFlow<Boolean> = locked.asStateFlow()

    /** Toggles between locked and unlocked. */
    fun toggle() {
        setLocked(!locked.value)
    }

    /** Unlocks the controls. */
    fun unlock() {
        setLocked(false)
    }

    /**
     * Returns true when the lock handled the back press, which keeps the player
     * open (the press unlocked it). Returns false to let the activity finish
     * as it does today.
     */
    fun onBackPressed(): Boolean {
        if (!locked.value) return false
        unlock()
        return true
    }

    /** A lock makes no sense over the system's picture-in-picture controls,
     * so entering PiP unlocks. */
    fun onPipChanged(isInPipMode: Boolean) {
        if (isInPipMode) unlock()
    }

    private fun setLocked(value: Boolean) {
        if (locked.value == value) return
        locked.value = value
        onLockChanged(value)
    }
}
