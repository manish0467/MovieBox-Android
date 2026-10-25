package com.moviebox.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class RestoredResolutionFilterTest {
    private val available = listOf("All", "1080p", "480p")

    @Test
    fun theRememberedPickWinsWhenTheTitleOffersIt() {
        assertEquals("1080p", restoredResolutionFilter("1080p", available))
    }

    @Test
    fun allStaysAll() {
        assertEquals("All", restoredResolutionFilter("All", available))
    }

    @Test
    fun aRememberedPickTheTitleDoesNotOfferFallsBackToAll() {
        assertEquals("All", restoredResolutionFilter("720p", available))
        assertEquals("All", restoredResolutionFilter("4K", emptyList()))
    }
}
