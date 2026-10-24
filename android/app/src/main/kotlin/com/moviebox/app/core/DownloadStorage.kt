package com.moviebox.app.core

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Where a finished download lives.
 *
 * The download itself runs in the app's external folder, because the engine
 * writes a plain file. When it is done, the file is moved into the public
 * Movies collection, so it shows up in a gallery and in a file manager and
 * survives the removal of the app. The original is only deleted once the copy
 * has succeeded.
 */
object DownloadStorage {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private const val DOWNLOADS_RECORD_FILE = "downloads.json"

    /** The folder the engine writes into. */
    fun workingDirectory(context: Context): File {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: File(context.filesDir, "Movies")
        return File(base, "MovieBox")
    }

    /** The public folder the finished file is moved into. */
    private const val PUBLIC_FOLDER = "Movies/MovieBox"

    /** How much free space a copy needs beyond the size of the file. */
    private const val SPACE_MARGIN_BYTES = 32L * 1024 * 1024

    /** Resolves the proper MIME type for a given media file. */
    fun mimeTypeFor(file: File): String = when (file.extension.lowercase()) {
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "ts" -> "video/mp2t"
        "avi" -> "video/x-msvideo"
        "mov" -> "video/quicktime"
        else -> "video/mp4"
    }

    /** Resolves the on-disk file, handling legacy .mkv.mp4 renames if necessary. */
    fun resolvePlaybackFile(file: File): File {
        if (file.exists()) return file
        if (file.name.endsWith(".mkv.mp4", ignoreCase = true)) {
            val clean = File(file.parentFile, file.name.removeSuffix(".mp4"))
            if (clean.exists()) return clean
        }
        return file
    }

    /** The result of publishing a finished download. */
    sealed class Result {
        /** The file was moved into the public collection. */
        data class Published(val uri: Uri) : Result()

        /** The file stayed where it is and was only indexed. */
        data class Indexed(val file: File) : Result()

        /** The copy did not happen; the original is untouched. */
        data class Failed(val reason: String) : Result()
    }

    /**
     * Moves a finished download into the public Movies collection.
     *
     * On Android 9 and older the file stays in the app's external folder and
     * is only registered with the media scanner, because the public folder
     * would need a storage permission there.
     */
    fun publish(context: Context, file: File): Result {
        if (!file.exists()) {
            return Result.Failed("The downloaded file is missing")
        }

        // Clean up legacy .mkv.mp4 double extension before publishing
        val targetFile = if (file.name.endsWith(".mkv.mp4", ignoreCase = true)) {
            val fixed = File(file.parentFile, file.name.removeSuffix(".mp4"))
            if (!fixed.exists() && file.renameTo(fixed)) {
                fixed
            } else {
                file
            }
        } else {
            file
        }
        val mimeType = mimeTypeFor(targetFile)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            android.media.MediaScannerConnection.scanFile(
                context,
                arrayOf(targetFile.absolutePath),
                arrayOf(mimeType),
                null,
            )
            return Result.Indexed(targetFile)
        }

        // The file may have been published already, for example when the
        // download finished while the app was closed. Then only the leftover
        // has to go.
        val existing = findPublished(context, targetFile.name, targetFile.length())
        if (existing != null) {
            targetFile.delete()
            return Result.Published(existing)
        }

        val available = freeSpace()
        if (available != null && available < targetFile.length() + SPACE_MARGIN_BYTES) {
            return Result.Failed(
                "There is not enough free space to move the file " +
                    "(${available / 1024 / 1024} MB free, " +
                    "${targetFile.length() / 1024 / 1024} MB needed)"
            )
        }

        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, targetFile.name)
            put(MediaStore.Video.Media.MIME_TYPE, mimeType)
            put(MediaStore.Video.Media.RELATIVE_PATH, PUBLIC_FOLDER)
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }

        val uri = try {
            resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
        } catch (error: Exception) {
            null
        } ?: return Result.Failed("The media store refused the file")

        try {
            resolver.openOutputStream(uri)?.use { output ->
                targetFile.inputStream().use { input -> input.copyTo(output) }
            } ?: throw IllegalStateException("the media store did not open the file")
        } catch (error: Exception) {
            // The copy failed: the original stays, the empty entry goes.
            runCatching { resolver.delete(uri, null, null) }
            return Result.Failed(
                "The file could not be moved: ${error.message ?: "unknown error"}"
            )
        }

        values.clear()
        values.put(MediaStore.Video.Media.IS_PENDING, 0)
        runCatching { resolver.update(uri, values, null, null) }

        // The copy is in place, only now the original is removed.
        targetFile.delete()
        return Result.Published(uri)
    }

    /**
     * Finds a published download again, after the app was restarted.
     *
     * The engine still reports the path of the file it wrote, but the file
     * itself has moved into the media store by then.
     */
    fun publishedUri(context: Context, file: File): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        // The file is still there, so it was not moved.
        if (file.exists()) return null
        val uri = findPublished(context, file.name, null)
        if (uri != null) return uri
        if (file.name.endsWith(".mkv.mp4", ignoreCase = true)) {
            return findPublished(context, file.name.removeSuffix(".mp4"), null)
        }
        return null
    }

    /**
     * Looks the file up in the public collection.
     *
     * The size is only compared when it is known, so a file of the same name
     * but a different size is not mistaken for it.
     */
    private fun findPublished(context: Context, name: String, size: Long?): Uri? {
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.SIZE)
        val selection = "${MediaStore.Video.Media.DISPLAY_NAME} = ? AND " +
            "(${MediaStore.Video.Media.RELATIVE_PATH} = ? OR ${MediaStore.Video.Media.RELATIVE_PATH} = ?)"
        val arguments = arrayOf(name, "$PUBLIC_FOLDER/", PUBLIC_FOLDER)
        return try {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                arguments,
                null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val fileSize = cursor.getLong(1)
                    if (size != null && fileSize > 0 && fileSize != size) continue
                    return ContentUris.withAppendedId(collection, id)
                }
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /** The free space of the volume that holds the public collection. */
    private fun freeSpace(): Long? = try {
        val path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        StatFs(path.absolutePath).availableBytes
    } catch (_: Exception) {
        null
    }

    /** Reads saved download jobs from persistent storage. */
    fun getSavedJobs(context: Context): List<DownloadJob> {
        val file = File(context.filesDir, DOWNLOADS_RECORD_FILE)
        if (!file.exists()) return emptyList()
        return try {
            val content = file.readText()
            json.decodeFromString<List<DownloadJob>>(content)
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Saves download jobs atomically to persistent storage. */
    fun saveJobs(context: Context, jobs: List<DownloadJob>) {
        val file = File(context.filesDir, DOWNLOADS_RECORD_FILE)
        val tmp = File(context.filesDir, "$DOWNLOADS_RECORD_FILE.tmp")
        try {
            val content = json.encodeToString(jobs)
            tmp.writeText(content)
            tmp.renameTo(file)
        } catch (_: Exception) {
            // Leave previous file untouched on error
        }
    }

    /** Removes a job record from persistent storage. */
    fun removeJob(context: Context, id: String) {
        val current = getSavedJobs(context)
        val updated = current.filter { it.id != id }
        saveJobs(context, updated)
    }

    /**
     * Scans the working directory for finished media files that have not yet been
     * published into the public collection, publishes them, and records them.
     */
    fun reconcile(context: Context) {
        val dir = workingDirectory(context)
        if (!dir.exists() || !dir.isDirectory) return

        val saved = getSavedJobs(context).toMutableList()
        val foundFiles = mutableListOf<File>()
        scanCompletedFiles(dir, foundFiles)

        var changed = false
        for (file in foundFiles) {
            val length = file.length()
            val cleanName = if (file.name.endsWith(".mkv.mp4", ignoreCase = true)) {
                file.name.removeSuffix(".mp4")
            } else {
                file.name
            }
            val result = publish(context, file)
            if (result is Result.Published || result is Result.Indexed) {
                val existsInSaved = saved.any { job ->
                    job.destination.endsWith(file.name) || job.destination.endsWith(cleanName)
                }
                if (!existsInSaved) {
                    val restoredJob = DownloadJob(
                        id = "restored-${cleanName.hashCode().toUInt()}",
                        title = cleanName.substringBeforeLast('.'),
                        destination = file.absolutePath,
                        state = "completed",
                        progress = DownloadProgress(
                            downloaded = length,
                            total = length,
                            bytesPerSecond = 0.0,
                            attempt = 1,
                            workers = 1,
                        ),
                    )
                    saved.add(restoredJob)
                    changed = true
                }
            }
        }
        if (changed) {
            saveJobs(context, saved)
        }
    }

    private fun scanCompletedFiles(dir: File, out: MutableList<File>) {
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                scanCompletedFiles(file, out)
            } else if (file.isFile) {
                val name = file.name
                if (name.endsWith(".part") || name.endsWith(".part.json")) continue
                val isVideo = name.endsWith(".mkv.mp4", ignoreCase = true) ||
                    name.endsWith(".mp4", ignoreCase = true) ||
                    name.endsWith(".mkv", ignoreCase = true) ||
                    name.endsWith(".webm", ignoreCase = true) ||
                    name.endsWith(".ts", ignoreCase = true)
                if (isVideo) {
                    val part = File(file.parentFile, "$name.part")
                    val meta = File(file.parentFile, "$name.part.json")
                    if (!part.exists() && !meta.exists()) {
                        out.add(file)
                    }
                }
            }
        }
    }
}
