package com.moviebox.app.core

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadRecordsTest {

    private fun job(
        id: String,
        state: String,
        destination: String,
        title: String = "Dune",
    ): DownloadJob = DownloadJob(
        id = id,
        title = title,
        destination = destination,
        state = state,
    )

    @Test
    fun mergeKeepsCompletedRecordsAndPausesOrphanedRunningOnes() {
        val saved = listOf(
            job("old-completed", "completed", "/downloads/Dune.mkv"),
            job("old-running", "running", "/downloads/Other.mkv"),
            job("old-queued", "queued", "/downloads/Queued.mkv"),
        )

        val merged = DownloadRecords.merge(saved, emptyList())

        assertEquals(
            listOf("completed", "paused", "paused"),
            merged.associateBy { it.id }.let {
                listOf(
                    it.getValue("old-completed").state,
                    it.getValue("old-running").state,
                    it.getValue("old-queued").state,
                )
            },
        )
    }

    @Test
    fun mergeLetsTheNativeJobOverrideTheSavedRecord() {
        val saved = listOf(job("same-id", "paused", "/downloads/Dune.mkv"))
        val active = listOf(job("same-id", "running", "/downloads/Dune.mkv"))

        val merged = DownloadRecords.merge(saved, active)

        assertEquals(1, merged.size)
        assertEquals("running", merged.single().state)
    }

    @Test
    fun markCompletedUpdatesAnExistingRecord() {
        val jobs = mutableListOf(
            job("old", "running", "/downloads/Dune.mkv.mp4"),
        )
        val file = File("/downloads/Dune.mkv")

        val changed = DownloadRecords.markCompleted(jobs, file, "Dune.mkv", 1234L)

        assertTrue(changed)
        assertEquals("completed", jobs.single().state)
        assertEquals(1234L, jobs.single().progress.downloaded)
        assertEquals(1234L, jobs.single().progress.total)
        assertNull(jobs.single().error)
    }

    @Test
    fun markCompletedAddsARestoredRecordWhenNoneExists() {
        val jobs = mutableListOf<DownloadJob>()
        val file = File("New.mkv")

        val changed = DownloadRecords.markCompleted(jobs, file, "New.mkv", 99L)

        assertTrue(changed)
        assertEquals(1, jobs.size)
        val restored = jobs.single()
        assertTrue(restored.id.startsWith("restored-"))
        assertEquals("New", restored.title)
        assertEquals("completed", restored.state)
        assertEquals(99L, restored.progress.downloaded)
    }

    @Test
    fun completedMediaNamesIncludeTheContainersTheEngineCanProduce() {
        for (name in listOf(
            "Dune.mkv.mp4",
            "Dune.mp4",
            "Dune.mkv",
            "Dune.webm",
            "Dune.ts",
            "Dune.avi",
            "Dune.mov",
        )) {
            assertTrue(name, DownloadRecords.isCompletedMediaName(name))
        }

        assertFalse(DownloadRecords.isCompletedMediaName("Dune.mkv.part"))
        assertFalse(DownloadRecords.isCompletedMediaName("Dune.mkv.part.json"))
        assertFalse(DownloadRecords.isCompletedMediaName("Dune.srt"))
    }

    @Test
    fun legacyPartialsMoveToTheCurrentContainerName() {
        val partial = DownloadRecords.legacyPartialTarget(File("/tmp/Dune.mkv.mp4.part"))
        assertEquals(File("/tmp/Dune.mkv.part"), partial)

        val metadata = DownloadRecords.legacyPartialTarget(File("/tmp/Dune.mkv.mp4.part.json"))
        assertEquals(File("/tmp/Dune.mkv.part.json"), metadata)

        assertEquals(
            File("/tmp/Dune.avi.part"),
            DownloadRecords.legacyPartialTarget(File("/tmp/Dune.avi.mp4.part")),
        )
        assertEquals(
            File("/tmp/Dune.mov.part"),
            DownloadRecords.legacyPartialTarget(File("/tmp/Dune.mov.mp4.part")),
        )

        assertNull(DownloadRecords.legacyPartialTarget(File("/tmp/Dune.mkv.part")))
        assertNull(DownloadRecords.legacyPartialTarget(File("/tmp/Dune.mp4.part")))
    }
}
