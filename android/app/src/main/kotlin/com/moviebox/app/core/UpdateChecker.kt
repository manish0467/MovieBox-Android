package com.moviebox.app.core

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** A published release of the app. */
@Serializable
data class AppRelease(
    /** The GitHub API names it `tag_name`. */
    @SerialName("tag_name") val tagName: String = "",
    val assets: List<ReleaseAsset> = emptyList(),
) {
    /** The version code the release carries, taken from the tag. */
    val versionCode: Int
        get() = tagName.trim().substringAfterLast('-', "")
            .takeWhile { it.isDigit() }
            .toIntOrNull() ?: 0

    /**
     * The signed APK of the release.
     *
     * A release also carries `app-debug.apk`, which is signed with the debug key
     * and would be refused by the certificate check below, so the name matters:
     * taking whichever asset happens to be listed first downloaded the debug
     * build and then reported "could not be downloaded or verified".
     */
    val apk: ReleaseAsset?
        get() = assets.firstOrNull { it.name == RELEASE_APK }
            ?: assets.firstOrNull { it.name.endsWith(".apk") && !it.name.contains("debug") }
}

/** The asset name the updater must pick; the debug APK is signed with another key. */
private const val RELEASE_APK = "app-release.apk"

/** One asset of a release. */
@Serializable
data class ReleaseAsset(
    val name: String = "",
    /** The GitHub API names it `browser_download_url`. */
    @SerialName("browser_download_url") val browserDownloadUrl: String = "",
    val size: Long = 0,
)

/**
 * Checks the GitHub releases for a newer build and installs it.
 *
 * The app is distributed by sideloading, so without this the user has to
 * download the APK by hand for every fix. The downloaded file is only handed
 * to the installer when its signing certificate matches the installed one, so
 * a tampered download cannot replace the app.
 */
object UpdateChecker {

    private const val RELEASES_URL =
        "https://api.github.com/repos/manish0467/MovieBox-Android/releases/latest"

    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 20_000

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /** The newest published release, or null when it cannot be read. */
    suspend fun latestRelease(): AppRelease? = withContext(Dispatchers.IO) {
        val connection = (URL(RELEASES_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Accept", "application/vnd.github+json")
            // The GitHub API rejects a request without one.
            setRequestProperty("User-Agent", "MovieBox-Android")
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            json.decodeFromString(AppRelease.serializer(), body)
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    /** The version code of the installed app. */
    fun installedVersionCode(context: Context): Int = try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        @Suppress("DEPRECATION")
        info.versionCode
    } catch (_: Exception) {
        0
    }

    /**
     * Downloads the release into the cache directory.
     *
     * The file is only returned when it was signed by the same certificate as
     * the installed app.
     */
    suspend fun download(
        context: Context,
        release: AppRelease,
    ): File? = withContext(Dispatchers.IO) {
        val asset = release.apk ?: return@withContext null
        val target = File(context.cacheDir, "update-${release.versionCode}.apk")
        val connection = (URL(asset.browserDownloadUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("User-Agent", "MovieBox-Android")
            instanceFollowRedirects = true
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
            connection.inputStream.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            if (!isSignedByTheInstalledCertificate(context, target)) {
                target.delete()
                return@withContext null
            }
            target
        } catch (_: Exception) {
            target.delete()
            null
        } finally {
            connection.disconnect()
        }
    }

    /** Whether the APK was signed by the certificate of the installed app. */
    fun isSignedByTheInstalledCertificate(context: Context, apk: File): Boolean {
        val installed = signingCertificates(context, context.packageName) ?: return false
        val archive = signingCertificates(context, apk.absolutePath) ?: return false
        return installed.isNotEmpty() &&
            installed.size == archive.size &&
            installed.zip(archive).all { (a, b) -> a.toByteArray().contentEquals(b.toByteArray()) }
    }

    private fun signingCertificates(context: Context, source: String): List<Signature>? {
        return try {
            val flags = PackageManager.GET_SIGNING_CERTIFICATES
            val info = if (source == context.packageName) {
                context.packageManager.getPackageInfo(source, flags)
            } else {
                context.packageManager.getPackageArchiveInfo(source, flags)
            }
            info?.signingInfo?.apkContentsSigners?.toList()
        } catch (_: Exception) {
            null
        }
    }

    /** Hands the downloaded APK to the installer. */
    fun install(context: Context, apk: File): Boolean {
        val uri = try {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        } catch (_: Exception) {
            return false
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            // The context is the application, not an activity.
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Whether the app may install packages. */
    fun canInstallPackages(context: Context): Boolean =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }

    /** Asks the user for the permission to install packages. */
    fun requestInstallPermission(context: Context) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return
        val intent = Intent(
            "android.settings.MANAGE_UNKNOWN_APP_SOURCES",
            Uri.parse("package:${context.packageName}"),
        ).apply {
            // The context is the application, not an activity.
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            // The setting is not reachable on this device; the install will
            // fail with a clear message instead.
        }
    }
}
