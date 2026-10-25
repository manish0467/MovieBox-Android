package com.moviebox.app.player.features

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockModeFeatureTest {
    @Test
    fun toggleLocksAndUnlocksAndNotifiesOncePerChange() {
        val changes = mutableListOf<Boolean>()
        val feature = LockModeFeature(onLockChanged = changes::add)

        assertTrue(feature.available)
        assertFalse(feature.isLocked.value)

        feature.toggle()
        assertTrue(feature.isLocked.value)
        feature.toggle()
        assertFalse(feature.isLocked.value)

        // A redundant unlock must not fire another change.
        feature.unlock()
        assertFalse(feature.isLocked.value)

        assertEquals(listOf(true, false), changes)
    }

    @Test
    fun backPressUnlocksInsteadOfLeavingWhileLocked() {
        val feature = LockModeFeature(onLockChanged = { })

        // Unlocked: the back press falls through and the activity finishes.
        assertFalse(feature.onBackPressed())

        feature.toggle()
        // Locked: the press is consumed and only unlocks the controls.
        assertTrue(feature.onBackPressed())
        assertFalse(feature.isLocked.value)
    }

    @Test
    fun enteringPipUnlocksTheControls() {
        val changes = mutableListOf<Boolean>()
        val feature = LockModeFeature(onLockChanged = changes::add)

        feature.toggle()
        assertTrue(feature.isLocked.value)

        feature.onPipChanged(true)
        assertFalse(feature.isLocked.value)
        assertEquals(listOf(true, false), changes)

        // Leaving PiP must not re-lock.
        feature.onPipChanged(false)
        assertFalse(feature.isLocked.value)
        assertEquals(listOf(true, false), changes)
    }
}
