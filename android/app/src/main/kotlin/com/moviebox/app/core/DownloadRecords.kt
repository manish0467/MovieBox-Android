package com.moviebox.app.core

import java.io.File

/**
 * Pure record handling for downloads.
 *
 * Keeping the merge and reconciliation rules here makes them testable on the
 * JVM without a MediaStore or an Android runtime.
 */
internal object DownloadRecords {

    /**
     * Merges the records saved on disk with the jobs the native manager still
     * knows about.
     *
     * A saved job that no longer has a native counterpart was interrupted by a
     * process death. It is shown as paused, and its partial file is kept so the
     * next resume can continue from it.
     */
    fun merge(saved: List<DownloadJob>, active: List<DownloadJob>): List<DownloadJob> {
        val merged = LinkedHashMap<String, DownloadJob>()
        for (job in saved) merged[job.id] = job
        for (job in active) merged[job.id] = job

        val activeIds = active.mapTo(mutableSetOf()) { it.id }
        return merged.values.map { job ->
            if (job.id !in activeIds && (job.isRunning || job.isQueued)) {
                job.copy(state = "paused")
            } else {
                job
            }
        }.sortedByDescending { it.id }
    }

    /** Whether a file name is a finished media file that should be published. */
    fun isCompletedMediaName(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".mkv.mp4") ||
            lower.endsWith(".mp4") ||
            lower.endsWith(".mkv") ||
            lower.endsWith(".webm") ||
            lower.endsWith(".ts") ||
            lower.endsWith(".avi") ||
            lower.endsWith(".mov")
    }

    /**
     * Picks the container extension from a signed URL. MovieBox release titles
     * usually have no suffix, so without this an MKV stream would be stored as
     * `.mp4` even though the bytes are Matroska.
     */
    fun extensionFor(url: String): String {
        val path = url.substringBefore('?').substringBefore('#')
        return when (path.substringAfterLast('.', "").lowercase()) {
            "mkv" -> "mkv"
            "webm" -> "webm"
            "ts" -> "ts"
            "avi" -> "avi"
            "mov" -> "mov"
            else -> "mp4"
        }
    }

    /**
     * Maps a legacy `name.mkv.mp4.part` sidecar to the `name.mkv.part` path the
     * current engine expects, so a download paused before the container fix can
     * still be resumed instead of starting from zero.
     *
     * The old bridge appended `.mp4` to every title, so the same migration is
     * needed for `.avi`, `.mov`, `.webm` and `.ts`. A plain `name.mp4.part`
     * already has the current name and is left alone.
     */
    fun legacyPartialTarget(file: File): File? {
        val lower = file.name.lowercase()
        val suffix = when {
            lower.endsWith(".part.json") -> ".part.json"
            lower.endsWith(".part") -> ".part"
            else -> return null
        }
        val base = file.name.dropLast(suffix.length)
        if (!base.lowercase().endsWith(".mp4")) return null
        val candidate = base.dropLast(4)
        val candidateLower = candidate.lowercase()
        val isKnownContainer = listOf(".mkv", ".webm", ".ts", ".avi", ".mov", ".mp4")
            .any { candidateLower.endsWith(it) }
        if (!isKnownContainer) return null
        return File(file.parentFile, candidate + suffix)
    }

    /**
     * Marks the saved record that owns [file] as completed, or creates a
     * restored record when the download was started before records existed.
     *
     * [bytes] must be read before publishing, because publishing moves or
     * deletes the source file.
     */
    fun markCompleted(
        jobs: MutableList<DownloadJob>,
        file: File,
        cleanName: String,
        bytes: Long,
    ): Boolean {
        val index = jobs.indexOfFirst { matches(it, file, cleanName) }
        if (index >= 0) {
            val existing = jobs[index]
            val completed = existing.copy(
                state = "completed",
                error = null,
                progress = existing.progress.copy(
                    downloaded = bytes,
                    total = bytes,
                    bytesPerSecond = 0.0,
                ),
            )
            if (completed == existing) return false
            jobs[index] = completed
            return true
        }

        jobs.add(restored(file, cleanName, bytes))
        return true
    }

    private fun matches(job: DownloadJob, file: File, cleanName: String): Boolean {
        val destination = File(job.destination).name.lowercase()
        val fileName = file.name.lowercase()
        val clean = cleanName.lowercase()
        return destination == fileName ||
            destination == clean ||
            destination.removeSuffix(".mp4") == clean
    }

    private fun restored(file: File, cleanName: String, bytes: Long): DownloadJob {
        return DownloadJob(
            id = "restored-${file.absolutePath.hashCode().toUInt()}",
            title = cleanName.substringBeforeLast('.'),
            destination = file.absolutePath,
            state = "completed",
            progress = DownloadProgress(downloaded = bytes, total = bytes),
        )
    }
}
