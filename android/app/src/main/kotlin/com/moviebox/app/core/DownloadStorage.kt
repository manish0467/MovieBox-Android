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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            android.media.MediaScannerConnection.scanFile(
                context,
                arrayOf(file.absolutePath),
                arrayOf("video/mp4"),
                null,
            )
            return Result.Indexed(file)
        }

        // The file may have been published already, for example when the
        // download finished while the app was closed. Then only the leftover
        // has to go.
        val existing = findPublished(context, file.name, file.length())
        if (existing != null) {
            file.delete()
            return Result.Published(existing)
        }

        val available = freeSpace()
        if (available != null && available < file.length() + SPACE_MARGIN_BYTES) {
            return Result.Failed(
                "There is not enough free space to move the file " +
                    "(${available / 1024 / 1024} MB free, " +
                    "${file.length() / 1024 / 1024} MB needed)"
            )
        }

        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
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
                file.inputStream().use { input -> input.copyTo(output) }
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
        file.delete()
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
        return findPublished(context, file.name, null)
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
            "${MediaStore.Video.Media.RELATIVE_PATH} = ?"
        val arguments = arrayOf(name, "$PUBLIC_FOLDER/")
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
                    if (size != null && cursor.getLong(1) != size) continue
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
}
