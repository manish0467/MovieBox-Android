package com.moviebox.app.player

import android.os.Bundle
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.metadata.id3.ChapterFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import com.moviebox.app.ui.player.PlayerChapter

/**
 * Utility to extract chapter information from Media3 metadata streams and MediaMetadata extras.
 */
@UnstableApi
object PlayerChapters {

    fun extractFromMetadata(metadata: Metadata): List<PlayerChapter> {
        val chapters = mutableListOf<PlayerChapter>()
        for (i in 0 until metadata.length()) {
            val entry = metadata.get(i)
            if (entry is ChapterFrame) {
                var title = "Chapter ${chapters.size + 1}"
                for (j in 0 until entry.subFrameCount) {
                    val sub = entry.getSubFrame(j)
                    if (sub is TextInformationFrame && sub.values.isNotEmpty()) {
                        title = sub.values[0]
                        break
                    }
                }
                chapters.add(
                    PlayerChapter(
                        id = entry.chapterId,
                        title = title,
                        startTimeMs = entry.startTimeMs.toLong(),
                        endTimeMs = entry.endTimeMs.toLong(),
                    ),
                )
            }
        }
        return chapters
    }

    fun extractFromMediaMetadata(mediaMetadata: MediaMetadata): List<PlayerChapter> {
        val extras: Bundle = mediaMetadata.extras ?: return emptyList()
        val chapters = mutableListOf<PlayerChapter>()

        // Check common extras keys for chapter metadata
        val keys = listOf("chapterMetadata", "chapters", "media3.chapterMetadata")
        for (key in keys) {
            val list = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                extras.getParcelableArrayList(key, Bundle::class.java)
            } else {
                @Suppress("DEPRECATION")
                extras.getParcelableArrayList<Bundle>(key)
            }
            if (list != null && list.isNotEmpty()) {
                list.forEachIndexed { index, b ->
                    val id = b.getString("id", "ch_$index")
                    val title = b.getString("title", "Chapter ${index + 1}")
                    val startMs = b.getLong("start_time_ms", 0L)
                    val endMs = b.getLong("end_time_ms", 0L)
                    chapters.add(PlayerChapter(id, title, startMs, endMs))
                }
                break
            }
        }
        return chapters
    }
}
