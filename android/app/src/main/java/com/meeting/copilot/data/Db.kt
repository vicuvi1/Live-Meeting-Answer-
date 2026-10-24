package com.meeting.copilot.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class MeetingRow(
    val id: Long,
    val startedAt: Long,
    val endedAt: Long?,
    val app: String,
    val title: String?,
    val summary: String?,
    val lineCount: Int,
)

data class LineRow(
    val id: Long,
    val ts: Long,
    val speaker: String?,
    val raw: String,
    val cleaned: String?,
    val flags: Int,
) {
    val text: String get() = cleaned ?: raw
}

data class EventRow(val id: Long, val ts: Long, val type: String, val payload: String)

/** Meetings, every caption line and the AI answers, kept on the phone. Call from a background thread. */
class Db(context: Context) : SQLiteOpenHelper(context, "copilot.db", null, 1) {

    companion object {
        const val FLAG_ADDRESSED = 1
        const val FLAG_MENTION = 2
        const val FLAG_TOPIC = 4
    }

    override fun onConfigure(db: SQLiteDatabase) {
        db.enableWriteAheadLogging()
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE meetings(id INTEGER PRIMARY KEY AUTOINCREMENT, started_at INTEGER NOT NULL, " +
                "ended_at INTEGER, app TEXT NOT NULL, title TEXT, summary TEXT)"
        )
        db.execSQL(
            "CREATE TABLE lines(id INTEGER PRIMARY KEY AUTOINCREMENT, meeting_id INTEGER NOT NULL, " +
                "ts INTEGER NOT NULL, speaker TEXT, raw TEXT NOT NULL, cleaned TEXT, flags INTEGER NOT NULL DEFAULT 0)"
        )
        db.execSQL("CREATE INDEX idx_lines_meeting ON lines(meeting_id, ts)")
        db.execSQL(
            "CREATE TABLE events(id INTEGER PRIMARY KEY AUTOINCREMENT, meeting_id INTEGER NOT NULL, " +
                "ts INTEGER NOT NULL, type TEXT NOT NULL, payload TEXT NOT NULL)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    fun newMeeting(startedAt: Long, app: String): Long =
        writableDatabase.insert("meetings", null, ContentValues().apply {
            put("started_at", startedAt)
            put("app", app)
        })

    fun endMeeting(id: Long, endedAt: Long) {
        writableDatabase.update("meetings", ContentValues().apply { put("ended_at", endedAt) }, "id=?", arrayOf("$id"))
    }

    fun setSummary(id: Long, title: String?, summaryJson: String) {
        writableDatabase.update("meetings", ContentValues().apply {
            put("title", title)
            put("summary", summaryJson)
        }, "id=?", arrayOf("$id"))
    }

    /** Meetings left open by a crash or a killed process get closed at their last line. */
    fun closeDanglingMeetings() {
        writableDatabase.execSQL(
            "UPDATE meetings SET ended_at = COALESCE((SELECT MAX(ts) FROM lines WHERE meeting_id = meetings.id), started_at) " +
                "WHERE ended_at IS NULL"
        )
    }

    fun insertLine(meetingId: Long, ts: Long, speaker: String?, raw: String, flags: Int): Long =
        writableDatabase.insert("lines", null, ContentValues().apply {
            put("meeting_id", meetingId)
            put("ts", ts)
            put("speaker", speaker)
            put("raw", raw)
            put("flags", flags)
        })

    fun updateCleaned(lineId: Long, cleaned: String) {
        writableDatabase.update("lines", ContentValues().apply { put("cleaned", cleaned) }, "id=?", arrayOf("$lineId"))
    }

    fun insertEvent(meetingId: Long, ts: Long, type: String, payload: String) {
        writableDatabase.insert("events", null, ContentValues().apply {
            put("meeting_id", meetingId)
            put("ts", ts)
            put("type", type)
            put("payload", payload)
        })
    }

    fun meetings(): List<MeetingRow> = readableDatabase.rawQuery(
        "SELECT m.id, m.started_at, m.ended_at, m.app, m.title, m.summary, " +
            "(SELECT COUNT(*) FROM lines l WHERE l.meeting_id = m.id) FROM meetings m ORDER BY m.started_at DESC",
        null,
    ).use { c ->
        buildList {
            while (c.moveToNext()) add(
                MeetingRow(
                    id = c.getLong(0),
                    startedAt = c.getLong(1),
                    endedAt = if (c.isNull(2)) null else c.getLong(2),
                    app = c.getString(3),
                    title = c.getString(4),
                    summary = c.getString(5),
                    lineCount = c.getInt(6),
                )
            )
        }
    }

    fun meeting(id: Long): MeetingRow? = meetings().firstOrNull { it.id == id }

    fun lines(meetingId: Long): List<LineRow> = readableDatabase.rawQuery(
        "SELECT id, ts, speaker, raw, cleaned, flags FROM lines WHERE meeting_id=? ORDER BY ts, id",
        arrayOf("$meetingId"),
    ).use { c ->
        buildList {
            while (c.moveToNext()) add(
                LineRow(c.getLong(0), c.getLong(1), c.getString(2), c.getString(3), c.getString(4), c.getInt(5))
            )
        }
    }

    fun events(meetingId: Long): List<EventRow> = readableDatabase.rawQuery(
        "SELECT id, ts, type, payload FROM events WHERE meeting_id=? ORDER BY ts",
        arrayOf("$meetingId"),
    ).use { c ->
        buildList { while (c.moveToNext()) add(EventRow(c.getLong(0), c.getLong(1), c.getString(2), c.getString(3))) }
    }

    fun deleteMeeting(id: Long) {
        writableDatabase.transaction {
            delete("lines", "meeting_id=?", arrayOf("$id"))
            delete("events", "meeting_id=?", arrayOf("$id"))
            delete("meetings", "id=?", arrayOf("$id"))
        }
    }

    private inline fun SQLiteDatabase.transaction(block: SQLiteDatabase.() -> Unit) {
        beginTransaction()
        try {
            block()
            setTransactionSuccessful()
        } finally {
            endTransaction()
        }
    }
}
