package com.moviebox.app.core

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import java.io.File
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The text behind "copy diagnostic info".
 *
 * The app has no telemetry, so a bug report is the only way a problem reaches
 * us. This assembles what such a report needs — the version, the device and the
 * last lines of the engine log — into the clipboard. It is only ever put there
 * on the user's request, and the app never reads it back or sends it anywhere.
 */
object Diagnostics {
    /** How many trailing log lines the report carries. */
    private const val MAX_LOG_LINES = 200

    /** Log lines longer than this are truncated, so one huge line cannot bloat the report. */
    private const val MAX_LINE_LENGTH = 500

    /** The most of the tail that is read from disk at all. */
    private const val MAX_LOG_BYTES = 256 * 1024

    private const val CLIP_LABEL = "MovieBox diagnostics"

    /** Puts the report on the clipboard. Returns false when there is no clipboard. */
    fun copy(context: Context): Boolean {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return false
        clipboard.setPrimaryClip(ClipData.newPlainText(CLIP_LABEL, report(context)))
        return true
    }

    /** The report itself, as plain text. */
    fun report(context: Context): String = buildString {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        appendLine("MovieBox ${packageInfo.versionName} (${versionCode(context)})")
        appendLine("Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("ABIs: ${Build.SUPPORTED_ABIS.joinToString()}")
        appendLine("Locale: ${context.resources.configuration.locales[0]?.toLanguageTag() ?: "unknown"}")
        appendLine("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")

        val log = engineLog(context)
        if (log == null) {
            appendLine()
            appendLine("Engine log: not found")
            return@buildString
        }
        appendLine("Engine log: ${log.absolutePath} (${log.length()} bytes)")
        appendLine()
        append(tail(log))
    }

    private fun versionCode(context: Context): Long {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
    }

    /**
     * The engine is handed the app's files directory as both its config and data
     * directory, and writes its rotating log into `logs/` under it. The
     * app-name-suffixed layout is checked as well: which of the two applies
     * depends on how the directory override is resolved, and a bug report that
     * silently loses the log is worse than one extra `exists` check.
     */
    private fun engineLog(context: Context): File? {
        val candidates = listOf(
            File(context.filesDir, "logs"),
            File(context.filesDir, "moviebox-tui/logs"),
            context.filesDir,
        )
        for (directory in candidates) {
            newestLog(directory)?.let { return it }
        }
        return null
    }

    private fun newestLog(directory: File): File? {
        val files = directory.listFiles()?.filter { it.isFile } ?: return null
        files.firstOrNull { it.name.endsWith("rCURRENT.log") }?.let { return it }
        return files.filter { it.name.endsWith(".log") }.maxByOrNull { it.lastModified() }
    }

    private fun tail(file: File): String {
        if (!file.canRead()) return "Engine log: not readable"
        val text = runCatching {
            RandomAccessFile(file, "r").use { handle ->
                val length = handle.length()
                val start = (length - MAX_LOG_BYTES).coerceAtLeast(0)
                handle.seek(start)
                val bytes = ByteArray((length - start).toInt())
                handle.readFully(bytes)
                String(bytes, Charsets.UTF_8)
            }
        }.getOrNull() ?: return "Engine log: could not be read"
        return text.lines()
            .takeLast(MAX_LOG_LINES)
            .map { if (it.length > MAX_LINE_LENGTH) it.take(MAX_LINE_LENGTH) + "…" else it }
            .joinToString("\n")
    }
}
