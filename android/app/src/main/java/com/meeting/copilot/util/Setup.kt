package com.meeting.copilot.util

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.meeting.copilot.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Shortcuts to the Android / HyperOS settings the app needs. */
object Setup {
    val isXiaomi: Boolean get() = Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true) ||
        Build.BRAND.equals("Redmi", ignoreCase = true) || Build.BRAND.equals("POCO", ignoreCase = true)

    fun notificationsAllowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun batteryUnrestricted(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

    fun openAccessibility(context: Context) = start(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    fun openAppInfo(context: Context) =
        start(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))

    @SuppressLint("BatteryLife")
    fun requestBatteryUnrestricted(context: Context) {
        val asked = start(
            context,
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")),
        )
        if (!asked) openAppInfo(context)
    }

    /** HyperOS "Autostart" list; falls back to app info on other phones. */
    fun openAutostart(context: Context) {
        val intent = Intent().setComponent(
            ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
        )
        if (!start(context, intent)) openAppInfo(context)
    }

    fun openUrl(context: Context, url: String) = start(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    fun share(context: Context, text: String, subject: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        start(context, Intent.createChooser(send, "Trimite"))
    }

    fun shareFile(context: Context, uri: Uri) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        start(context, Intent.createChooser(send, "Trimite jurnalul"))
    }

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}

/**
 * Looks for a newer build on GitHub Releases (each CI build is tagged build-<number>),
 * downloads it inside the app and hands it to the Android installer: one tap for her.
 */
object UpdateChecker {
    private const val LATEST = "https://api.github.com/repos/vicuvi1/Live-Meeting-Answer-/releases/latest"

    data class Update(val versionCode: Int, val name: String, val url: String, val size: Long)

    /** Downloads the APK into the app's cache, reporting progress 0–100. */
    suspend fun download(context: Context, update: Update, onProgress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "asistent-${update.versionCode}.apk")
        val connection = (URL(update.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true // GitHub redirects to its file storage
        }
        try {
            if (connection.responseCode !in 200..299) throw IOException("HTTP ${connection.responseCode}")
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: update.size
            connection.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    var lastPercent = -1
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        val percent = if (total > 0) (done * 100 / total).toInt() else 0
                        if (percent != lastPercent) {
                            lastPercent = percent
                            onProgress(percent)
                        }
                    }
                }
            }
            if (update.size > 0 && file.length() != update.size) throw IOException("descărcare incompletă")
            file
        } finally {
            connection.disconnect()
        }
    }

    /** Android asks once per app before it may install updates ("Install unknown apps"). */
    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(context: Context) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: ActivityNotFoundException) {
        }
    }

    /** Opens Android's "Update this app?" screen for the downloaded file. */
    fun install(context: Context, file: File): Boolean = try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    } catch (_: Exception) {
        false
    }

    private const val CACHE_MS = 30 * 60_000L
    private var cached: Pair<Long, Update?>? = null

    /**
     * Null when up to date; throws on network errors. Cached for 30 minutes because GitHub
     * allows only 60 anonymous checks an hour; [force] (the Settings button) skips the cache.
     */
    suspend fun check(force: Boolean = false): Update? {
        val hit = cached
        if (!force && hit != null && System.currentTimeMillis() - hit.first < CACHE_MS) return hit.second
        return fetch().also { cached = System.currentTimeMillis() to it }
    }

    private suspend fun fetch(): Update? = withContext(Dispatchers.IO) {
        val connection = (URL(LATEST).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        try {
            if (connection.responseCode == 404) return@withContext null
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val code = json.optString("tag_name").removePrefix("build-").toIntOrNull() ?: return@withContext null
            if (code <= BuildConfig.VERSION_CODE) return@withContext null
            val assets = json.optJSONArray("assets") ?: return@withContext null
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                if (asset.optString("name").endsWith(".apk")) {
                    return@withContext Update(code, json.optString("name"), asset.optString("browser_download_url"), asset.optLong("size"))
                }
            }
            null
        } finally {
            connection.disconnect()
        }
    }
}
