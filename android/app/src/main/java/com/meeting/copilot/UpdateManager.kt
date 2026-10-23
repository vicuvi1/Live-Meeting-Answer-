package com.meeting.copilot

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val latestVersion: String,
    val apkUrl: String,
    val releaseNotes: String,
    val htmlUrl: String,
    val hasUpdate: Boolean
)

object UpdateManager {

    private const val TAG = "UpdateManager"
    private const val GITHUB_REPO = "vicuvi1/Live-Meeting-Answer-"
    private const val GITHUB_API_URL = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Checks GitHub for the latest release and determines if an update is available.
     */
    suspend fun checkForUpdate(currentVersion: String): Result<UpdateInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(GITHUB_API_URL)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "MeetingCopilot-Android")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                throw IllegalStateException("GitHub a returnat codul ${response.code}")
            }

            val bodyStr = response.body?.string() ?: throw IllegalStateException("Răspuns gol de la GitHub")
            val json = JSONObject(bodyStr)

            val tagName = json.optString("tag_name", "").removePrefix("v").trim()
            val releaseNotes = json.optString("body", "Actualizare cu îmbunătățiri de stabilitate și performanță.")
            val htmlUrl = json.optString("html_url", "https://github.com/$GITHUB_REPO/releases")

            // Look for APK asset in the release
            var apkUrl = ""
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val assetName = asset.optString("name", "")
                    if (assetName.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.optString("browser_download_url", "")
                        break
                    }
                }
            }

            // Fallback APK URL if not in assets list
            if (apkUrl.isEmpty()) {
                apkUrl = "https://github.com/$GITHUB_REPO/releases/latest/download/app-release.apk"
            }

            val cleanCurrent = currentVersion.removePrefix("v").trim()
            val isNewer = isVersionGreater(tagName, cleanCurrent)

            UpdateInfo(
                latestVersion = "v$tagName",
                apkUrl = apkUrl,
                releaseNotes = releaseNotes,
                htmlUrl = htmlUrl,
                hasUpdate = isNewer
            )
        }
    }

    /**
     * Downloads the APK file and triggers the native Android package installer.
     */
    suspend fun downloadAndInstallApk(
        activity: Activity,
        apkUrl: String,
        onProgress: (Int) -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            // Check Android 8+ Unknown Sources permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!activity.packageManager.canRequestPackageInstalls()) {
                    withContext(Dispatchers.Main) {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:${activity.packageName}")
                        )
                        activity.startActivity(intent)
                        onError("Te rugăm să permiți 'Instalarea aplicațiilor necunoscute' pentru Meeting Copilot, apoi apasă din nou pe Actualizare.")
                    }
                    return@withContext
                }
            }

            val request = Request.Builder()
                .url(apkUrl)
                .header("User-Agent", "MeetingCopilot-Android")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                throw IllegalStateException("Descărcarea a eșuat (cod ${response.code})")
            }

            val responseBody = response.body ?: throw IllegalStateException("Fișierul APK este indisponibil")
            val totalBytes = responseBody.contentLength()

            val updatesDir = File(activity.cacheDir, "updates")
            if (!updatesDir.exists()) {
                updatesDir.mkdirs()
            }
            val apkFile = File(updatesDir, "meeting-copilot-update.apk")
            if (apkFile.exists()) {
                apkFile.delete()
            }

            responseBody.byteStream().use { inputStream ->
                FileOutputStream(apkFile).use { outputStream ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    var downloadedBytes = 0L

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        if (totalBytes > 0) {
                            val progress = ((downloadedBytes * 100) / totalBytes).toInt()
                            withContext(Dispatchers.Main) {
                                onProgress(progress)
                            }
                        }
                    }
                    outputStream.flush()
                }
            }

            // Trigger Android Package Installer
            withContext(Dispatchers.Main) {
                val apkUri = FileProvider.getUriForFile(
                    activity,
                    "${activity.packageName}.fileprovider",
                    apkFile
                )

                val installIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(apkUri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                activity.startActivity(installIntent)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to download and install update", e)
            withContext(Dispatchers.Main) {
                onError(e.localizedMessage ?: "Eroare la descărcarea actualizării")
            }
        }
    }

    /**
     * Compares two semantic version strings (e.g., "1.0.1" > "1.0.0").
     */
    private fun isVersionGreater(remote: String, local: String): Boolean {
        if (remote.isEmpty() || local.isEmpty()) return false
        val remoteParts = remote.split(".").mapNotNull { it.toIntOrNull() }
        val localParts = local.split(".").mapNotNull { it.toIntOrNull() }

        val length = maxOf(remoteParts.size, localParts.size)
        for (i in 0 until length) {
            val r = remoteParts.getOrElse(i) { 0 }
            val l = localParts.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        return false
    }
}
