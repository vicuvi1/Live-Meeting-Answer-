package com.meeting.captionprobe

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Shared state between the accessibility service and the screen.
 * Everything runs on the main thread (accessibility events and UI both arrive there).
 */
object Probe {
    var recording = false
    var overlayVisible = true
    var service: CaptionProbeService? = null

    private const val FEED_SIZE = 150
    private val feed = ArrayDeque<String>()
    private val listeners = mutableListOf<() -> Unit>()
    private var writer: BufferedWriter? = null
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun logFile(context: Context) = File(context.filesDir, "probe-log.txt")

    /** Appends a timestamped line to the log file; [toFeed] also shows it on the screen. */
    fun log(context: Context, line: String, toFeed: Boolean = true) {
        val stamped = "${timeFormat.format(Date())} $line"
        try {
            val w = writer ?: BufferedWriter(FileWriter(logFile(context), true)).also { writer = it }
            w.write(stamped)
            w.newLine()
            w.flush()
        } catch (e: Exception) {
            feed.addLast("LOG WRITE FAILED: ${e.message}")
        }
        if (toFeed) {
            feed.addLast(stamped)
            while (feed.size > FEED_SIZE) feed.removeFirst()
        }
        notifyListeners()
    }

    fun feedLines(): List<String> = feed.toList()

    fun setRecording(context: Context, on: Boolean) {
        recording = on
        log(context, if (on) "=== RECORDING STARTED ===" else "=== RECORDING STOPPED ===")
        service?.onRecordingChanged()
    }

    fun clear(context: Context) {
        writer?.close()
        writer = null
        logFile(context).delete()
        feed.clear()
        notifyListeners()
    }

    /** Copies the log into Downloads so it can be sent from WhatsApp/Telegram/Files. */
    fun exportToDownloads(context: Context): Uri? {
        writer?.flush()
        val source = logFile(context)
        if (!source.exists()) return null
        val name = "caption-probe-" +
            SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".txt"
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        resolver.openOutputStream(uri)?.use { out ->
            source.inputStream().use { it.copyTo(out) }
        }
        return uri
    }

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    private fun notifyListeners() {
        listeners.toList().forEach { it() }
    }
}
