package com.moviebox.app.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeKeyTest {
    @Test
    fun normalisesKnownKeys() {
        assertEquals("catppuccin", themeKeyFromName(" Catppuccin "))
        assertEquals("oled", themeKeyFromName("OLED"))
        assertEquals("tokyo-night", themeKeyFromName("tokyo-night"))
    }

    @Test
    fun unknownOrBlankFallsBackToSystem() {
        assertEquals("system", themeKeyFromName("does-not-exist"))
        assertEquals("system", themeKeyFromName(""))
        assertEquals("system", themeKeyFromName("   "))
    }

    @Test
    fun everyCatalogKeyResolvesToItself() {
        ThemeCatalog.forEach { entry ->
            assertEquals(entry.key, themeKeyFromName(entry.key))
        }
    }
}
