package com.meeting.copilot.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Diagnostic log (off by default) plus the crash log. Exported to Downloads so it can be
 * sent for analysis: it shows exactly what text the meeting apps put on screen.
 */
object DiagLog {
    private const val MAX_BYTES = 8L * 1024 * 1024
    private val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private lateinit var file: File
    private lateinit var crashFile: File

    fun init(context: Context) {
        file = File(context.filesDir, "diagnostic.txt")
        crashFile = File(context.filesDir, "crash.txt")
    }

    @Synchronized
    fun log(line: String) {
        if (!::file.isInitialized) return
        try {
            if (file.length() > MAX_BYTES) file.delete()
            file.appendText("${time.format(Date())} $line\n")
        } catch (_: Exception) {
        }
    }

    fun recordCrash(thread: Thread, error: Throwable) {
        try {
            crashFile.appendText("=== ${Date()} on ${thread.name} ===\n${error.stackTraceToString()}\n")
        } catch (_: Exception) {
        }
    }

    fun hasCrashes(): Boolean = ::crashFile.isInitialized && crashFile.exists() && crashFile.length() > 0

    @Synchronized
    fun clear() {
        file.delete()
        crashFile.delete()
    }

    /** Writes diagnostic + crash logs into one file in Downloads; returns its uri. */
    @Synchronized
    fun export(context: Context): Uri? {
        val name = "asistent-diagnostic-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".txt"
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        resolver.openOutputStream(uri)?.bufferedWriter()?.use { out ->
            out.write("=== CRASH LOG ===\n")
            if (crashFile.exists()) out.write(crashFile.readText()) else out.write("(none)\n")
            out.write("\n=== DIAGNOSTIC LOG ===\n")
            if (file.exists()) out.write(file.readText()) else out.write("(empty: turn on Diagnostic in Settings)\n")
        }
        return uri
    }
}
