package com.moviebox.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The parts of the updater that decide what to download and whether it is newer.
 *
 * The regression this guards: a release publishes `app-release.apk` *and*
 * `app-debug.apk`, and the latter is signed with the debug key. Selecting it by
 * "first asset ending in .apk" downloaded a build the certificate check then had
 * to refuse, so the updater reported "could not be downloaded or verified" for
 * every release.
 */
class UpdateCheckerTest {
    private fun asset(name: String) = ReleaseAsset(
        name = name,
        browserDownloadUrl = "https://example.invalid/$name",
        size = 1,
    )

    @Test
    fun `the release apk is picked over the debug apk`() {
        val release = AppRelease(
            tagName = "android-preview-9",
            assets = listOf(asset("app-debug.apk"), asset("app-release.apk")),
        )
        assertEquals("app-release.apk", release.apk?.name)
    }

    @Test
    fun `a debug apk on its own is not an update`() {
        val release = AppRelease(tagName = "android-preview-9", assets = listOf(asset("app-debug.apk")))
        assertNull(release.apk)
    }

    @Test
    fun `an asset with another name is still usable`() {
        val release = AppRelease(tagName = "android-preview-1", assets = listOf(asset("moviebox-preview.apk")))
        assertEquals("moviebox-preview.apk", release.apk?.name)
    }

    @Test
    fun `the version code comes from the tag`() {
        assertEquals(9, AppRelease(tagName = "android-preview-9", assets = emptyList()).versionCode)
        assertEquals(0, AppRelease(tagName = "v0.9.0", assets = emptyList()).versionCode)
    }
}
