package com.meeting.copilot.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.meeting.copilot.ai.PromptLine
import com.meeting.copilot.ai.Prompts
import com.meeting.copilot.data.Db
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A plain-text copy of every finished meeting in Downloads/Asistent Sedinte, so the whole
 * conversation survives even if the app's data is cleared or the app is uninstalled.
 * Android 10+ needs no permission to write there. Call from a background thread.
 */
object TranscriptBackup {
    private const val FOLDER = "Asistent Sedinte"
    private val fileDate = SimpleDateFormat("yyyy-MM-dd HH-mm", Locale.US)
    private val titleDate = SimpleDateFormat("EEEE, d MMMM yyyy · HH:mm", Locale("ro", "RO"))

    /** Writes (or rewrites) the meeting's file; returns false when it couldn't. */
    fun save(context: Context, db: Db, meetingId: Long): Boolean {
        val meeting = db.meeting(meetingId) ?: return false
        val lines = db.lines(meetingId).map { PromptLine(it.ts, it.speaker, it.text) }
        if (lines.isEmpty()) return false
        val summary = Prompts.summaryFromJson(meeting.summary)
        val title = summary?.title ?: meeting.title ?: "Ședință ${meeting.app}"
        val text = Prompts.shareText(title, titleDate.format(Date(meeting.startedAt)), summary, lines)
        val name = "${fileDate.format(Date(meeting.startedAt))} ${meeting.app} #$meetingId.txt"
        return try {
            val resolver = context.contentResolver
            val existing = find(context, name)
            val uri = existing ?: resolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER")
                },
            ) ?: return false
            resolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) } ?: return false
            true
        } catch (e: Exception) {
            DiagLog.log("BACKUP ERROR ${e.message}")
            false
        }
    }

    private fun find(context: Context, name: String): Uri? = try {
        context.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?",
            arrayOf(name, "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER/"),
            null,
        )?.use { c ->
            if (c.moveToFirst()) Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, c.getLong(0).toString()) else null
        }
    } catch (_: Exception) {
        null
    }
}
