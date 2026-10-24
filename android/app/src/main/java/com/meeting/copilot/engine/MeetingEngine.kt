package com.meeting.copilot.engine

import com.meeting.copilot.ai.Answer
import com.meeting.copilot.ai.CatchUp
import com.meeting.copilot.ai.LlmClient
import com.meeting.copilot.ai.Profile
import com.meeting.copilot.ai.PromptLine
import com.meeting.copilot.ai.Prompts
import com.meeting.copilot.caption.CaptionAssembler
import com.meeting.copilot.data.Db
import com.meeting.copilot.data.Prefs
import com.meeting.copilot.text.Detector
import com.meeting.copilot.text.TextNorm
import com.meeting.copilot.util.DiagLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

/** One finalized caption line of the current meeting. */
data class Line(
    val localId: Int,
    val ts: Long,
    val speaker: String?,
    val raw: String,
    val cleaned: String? = null,
    val digested: Boolean = false,
    val flags: Int = 0,
) {
    val text: String get() = cleaned ?: raw
    fun toPrompt() = PromptLine(ts, speaker, text)
}

enum class Status { IDLE, WAITING_CAPTIONS, LISTENING }

data class UiState(
    val status: Status = Status.IDLE,
    val meetingVisible: Boolean = false,
    val meetingApp: String? = null,
    val startedAt: Long? = null,
    val lines: List<Line> = emptyList(),
    val live: String = "",
    val liveSpeaker: String? = null,
    val catchUp: CatchUp? = null,
    val catchUpLoading: Boolean = false,
    val catchUpError: String? = null,
    val answer: Answer? = null,
    val answerLoading: Boolean = false,
    val answerError: String? = null,
    val alert: AlertLevel = AlertLevel.NONE,
    val alertText: String? = null,
    val alertAt: Long = 0,
    val health: String? = null,
    val summaryRunning: Boolean = false,
)

/**
 * The meeting brain. Receives caption text, keeps the transcript, decides when she is
 * addressed, and keeps the "what did I miss" digest and answers ready. Runs on the main thread;
 * AI calls and database writes happen in the background.
 */
class MeetingEngine(
    private val prefs: Prefs,
    private val db: Db,
    private val llm: LlmClient,
    private val alerts: Alerts,
) : CaptionAssembler.Listener {

    companion object {
        const val DIGEST_INTERVAL_MS = 90_000L
        const val DIGEST_BATCH = 60
        const val CATCH_UP_STALE_MS = 45_000L
        const val MENTION_WINDOW_MS = 15_000L
        const val DIRECT_COOLDOWN_MS = 20_000L
        const val TOPIC_COOLDOWN_MS = 180_000L
        const val NO_CAPTIONS_WARNING_MS = 60_000L
        const val CAPTIONS_HINT_MS = 45_000L
        const val END_NOT_VISIBLE_MS = 10 * 60_000L
        const val END_SILENT_MS = 15 * 60_000L
        const val ANSWER_CONTEXT_MS = 6 * 60_000L
        const val DIGEST_CONTEXT_MS = 8 * 60_000L
        private val SELF_SPEAKERS = setOf("tu", "you", "eu", "dvs", "dumneavoastra", "me")

        val APP_NAMES = mapOf(
            "us.zoom.videomeetings" to "Zoom",
            "com.google.android.apps.tachyon" to "Meet",
            "com.google.android.apps.meetings" to "Meet",
            "com.microsoft.teams" to "Teams",
            "com.google.android.as" to "Live Caption",
        )
    }

    private val main = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val dbThread = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    /** Emits when the floating panel should open by itself (she was asked something). */
    private val _openPanel = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val openPanel: SharedFlow<Unit> = _openPanel

    val assembler = CaptionAssembler(this)

    private var detector = Detector(emptyList(), emptyList())
    private var detectorVersion = -1

    private val lines = mutableListOf<Line>()
    private var nextLocalId = 0
    private val dbIds = HashMap<Int, Long>() // localId -> row id, filled on the main thread

    private var sessionActive = false
    private var startedAt = 0L
    private var meetingApp: String? = null
    private var currentMeetingId: Long? = null // touched only on dbThread
    private var lastMeetingVisibleAt = 0L
    private var meetingVisibleSince = 0L
    private var lastCaptionAt = 0L
    private var noCaptionsWarned = false
    private var captionsHintShown = false
    private var lastDigestAt = 0L
    private var digestJob: Job? = null
    private var answerJob: Job? = null
    private var pendingMentionAt = 0L
    private var lastDirectAt = 0L
    private val lastTopicAlert = HashMap<String, Long>()
    /** After she ends a meeting by hand, captions still on screen must not start a new one. */
    private var suppressUntilHidden = false

    init {
        main.launch(dbThread) { runCatching { db.closeDanglingMeetings() } }
        main.launch {
            while (isActive) {
                delay(500)
                try {
                    tick()
                } catch (e: Exception) {
                    DiagLog.log("ENGINE TICK ERROR ${e.stackTraceToString()}")
                }
            }
        }
    }

    // ---------------------------------------------------------------- input from the service

    fun onScan(meetingVisible: Boolean, app: String?) {
        val now = now()
        if (meetingVisible) {
            if (meetingVisibleSince == 0L) meetingVisibleSince = now
            lastMeetingVisibleAt = now
            if (app != null && app != "com.google.android.as") meetingApp = APP_NAMES[app] ?: app
        } else {
            meetingVisibleSince = 0L
            suppressUntilHidden = false
        }
        refreshStatus(now)
    }

    override fun onSourceLocked(key: String) {
        if (suppressUntilHidden) return
        if (prefs.diagnostics) DiagLog.log("CAPTION SOURCE LOCKED $key")
        val pkg = key.substringBefore('|')
        if (meetingApp == null) meetingApp = APP_NAMES[pkg] ?: pkg
        ensureSession()
    }

    override fun onLive(text: String, speaker: String?) {
        if (suppressUntilHidden) return
        if (text.isNotBlank()) {
            lastCaptionAt = now()
            ensureSession()
        }
        _state.update { it.copy(live = text, liveSpeaker = speaker) }
    }

    override fun onUtterance(text: String, speaker: String?, startedAt: Long) {
        if (suppressUntilHidden) return
        ensureSession()
        lastCaptionAt = now()
        addLine(text, speaker, startedAt)
    }

    /** Settings → test: behaves exactly like a caption line from a real meeting. */
    fun testUtterance(text: String, speaker: String) {
        if (text.isBlank()) return
        if (!sessionActive) meetingApp = "Test"
        suppressUntilHidden = false
        lastMeetingVisibleAt = now()
        onUtterance(text.trim(), speaker.trim().ifEmpty { null }, now())
    }

    // ---------------------------------------------------------------- actions from the UI

    /** "Ce am pierdut?" */
    fun requestCatchUp() {
        val now = now()
        val current = _state.value
        if (lines.isEmpty()) {
            _state.update { it.copy(catchUpError = "Încă nu am nicio replică din ședință.") }
            return
        }
        if (!llm.isConfigured()) {
            _state.update { it.copy(catchUpError = "Adaugă cheia AI în Setări ca să primești rezumatul. Mai jos sunt ultimele replici.") }
            return
        }
        if (digestJob?.isActive == true) return
        val hasNew = lines.any { !it.digested }
        val stale = current.catchUp == null || now - current.catchUp.updatedAt > CATCH_UP_STALE_MS
        if (hasNew || current.catchUp == null || stale) runDigest()
    }

    /** "Ce să răspund?" pressed by hand, or a detected question. */
    fun requestAnswer(trigger: Line? = null) {
        val target = trigger ?: lines.lastOrNull { !isSelf(it.speaker) }
        if (target == null) {
            _state.update { it.copy(answerError = "Încă nu am nicio replică din ședință.", answer = null) }
            return
        }
        val now = now()
        val index = lines.indexOfFirst { it.localId == target.localId }
        val before = if (index > 0) lines.subList(maxOf(0, index - 2), index).map { formatShort(it) } else emptyList()
        // Show the question and what was said just before immediately; the AI fills in the answer.
        _state.update {
            it.copy(
                answer = Answer(target.text, target.text, before, "", confident = false, createdAt = now),
                answerLoading = llm.isConfigured(),
                answerError = if (llm.isConfigured()) null else "Adaugă cheia AI în Setări ca să primești un răspuns sugerat.",
            )
        }
        if (!llm.isConfigured()) return

        val recent = lines.filter { it.ts >= target.ts - ANSWER_CONTEXT_MS && it.localId != target.localId }
            .takeLast(120).map { it.toPrompt() }
        val catchUp = _state.value.catchUp
        answerJob?.cancel()
        answerJob = main.launch {
            try {
                val json = llm.json(
                    Prompts.answerSystem(prefs.displayName()),
                    Prompts.answerUser(profile(), catchUp, recent, target.toPrompt()),
                    maxTokens = 700,
                    timeoutMs = 20_000,
                )
                val answer = Prompts.parseAnswer(json, target.text, now())
                _state.update { it.copy(answer = answer, answerLoading = false, answerError = null) }
                recordEvent("answer", JSONObject()
                    .put("trigger", answer.trigger).put("question", answer.question)
                    .put("context", JSONArray(answer.context)).put("answer", answer.answer)
                    .put("confident", answer.confident).toString())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(answerLoading = false, answerError = e.message ?: "Eroare AI") }
            }
        }
    }

    fun dismissAnswer() {
        _state.update { it.copy(answer = null, answerError = null, answerLoading = false, alert = AlertLevel.NONE) }
    }

    fun endMeeting(byUser: Boolean = true) {
        if (!sessionActive) return
        assembler.reset() // finalizes the last words while the session is still open
        sessionActive = false
        suppressUntilHidden = byUser && _state.value.meetingVisible
        digestJob?.cancel()
        answerJob?.cancel()
        val endedAt = now()
        val snapshot = lines.toList()
        lines.clear()
        dbIds.clear()
        _state.value = UiState(meetingVisible = _state.value.meetingVisible, summaryRunning = llm.isConfigured() && snapshot.size >= 3)
        refreshStatus(endedAt)

        main.launch {
            val meetingId = withContext(dbThread) {
                val id = currentMeetingId
                if (id != null) runCatching { db.endMeeting(id, endedAt) }
                currentMeetingId = null
                id
            } ?: return@launch
            if (snapshot.size >= 3 && llm.isConfigured()) {
                summarize(meetingId, snapshot.map { it.toPrompt() })
            }
            _state.update { it.copy(summaryRunning = false) }
        }
    }

    /** Summary + "what I promised" for a finished meeting (also "regenerate" in History). */
    suspend fun summarize(meetingId: Long, transcript: List<PromptLine>? = null): String? {
        return try {
            val lines = transcript ?: withContext(dbThread) { db.lines(meetingId).map { PromptLine(it.ts, it.speaker, it.text) } }
            if (lines.isEmpty()) return "Ședința nu are replici."
            val json = llm.json(
                Prompts.summarySystem(prefs.displayName()),
                Prompts.summaryUser(profile(), lines),
                maxTokens = 3000,
                timeoutMs = 60_000,
            )
            val summary = Prompts.parseSummary(json)
            withContext(dbThread) { db.setSummary(meetingId, summary.title, Prompts.summaryToJson(summary)) }
            val tasks = summary.myTasks.size
            alerts.info(
                "Rezumat gata: ${summary.title}",
                if (tasks > 0) "Ai $tasks ${if (tasks == 1) "sarcină" else "sarcini"} din ședință. Deschide Istoric." else "Deschide Istoric ca să-l vezi.",
            )
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            alerts.info("Rezumatul nu s-a putut face", e.message ?: "Eroare AI")
            e.message ?: "Eroare AI"
        }
    }

    // ---------------------------------------------------------------- internals

    private fun ensureSession() {
        if (sessionActive) return
        val now = now()
        sessionActive = true
        startedAt = now
        lastCaptionAt = now
        lastDigestAt = now
        noCaptionsWarned = false
        pendingMentionAt = 0L
        lastTopicAlert.clear()
        lines.clear()
        dbIds.clear()
        val app = meetingApp ?: "Ședință"
        _state.update {
            UiState(meetingVisible = it.meetingVisible, status = Status.LISTENING, meetingApp = app, startedAt = now)
        }
        main.launch(dbThread) {
            currentMeetingId = runCatching { db.newMeeting(now, app) }.getOrNull()
        }
        if (prefs.diagnostics) DiagLog.log("SESSION START app=$app")
    }

    private fun addLine(text: String, speaker: String?, ts: Long) {
        refreshDetector()
        val now = now()
        val self = isSelf(speaker)
        val result = detector.analyze(text)
        var flags = 0
        var direct = false

        if (!self) {
            when {
                result.mentionsName && result.isQuestion -> direct = true
                result.mentionsName -> {
                    flags = flags or Db.FLAG_MENTION
                    pendingMentionAt = now
                    alert(AlertLevel.MENTION, "Ai fost menționată", lineLabel(speaker, text))
                }
                result.isQuestion && now - pendingMentionAt < MENTION_WINDOW_MS -> direct = true
                result.topic != null && now - (lastTopicAlert[result.topic] ?: 0L) > TOPIC_COOLDOWN_MS -> {
                    flags = flags or Db.FLAG_TOPIC
                    lastTopicAlert[result.topic] = now
                    alert(AlertLevel.TOPIC, "Se discută: ${result.topic}", lineLabel(speaker, text))
                }
            }
        }
        if (direct) flags = flags or Db.FLAG_ADDRESSED

        val line = Line(nextLocalId++, ts, speaker, text, flags = flags)
        lines.add(line)
        _state.update { it.copy(lines = lines.takeLast(40)) }
        if (prefs.diagnostics) DiagLog.log("LINE flags=$flags speaker=$speaker :: $text")

        main.launch(dbThread) {
            val meetingId = currentMeetingId ?: return@launch
            val rowId = runCatching { db.insertLine(meetingId, ts, speaker, text, flags) }.getOrNull() ?: return@launch
            withContext(Dispatchers.Main) { dbIds[line.localId] = rowId }
        }

        if (direct && now - lastDirectAt > DIRECT_COOLDOWN_MS) {
            lastDirectAt = now
            pendingMentionAt = 0L
            alert(AlertLevel.DIRECT, "Te-a întrebat cineva!", lineLabel(speaker, text))
            _openPanel.tryEmit(Unit)
            requestAnswer(line)
        } else if (direct) {
            requestAnswer(line) // follow-up question right after: refresh the answer quietly
        }
    }

    private fun runDigest() {
        val now = now()
        val fresh = lines.filter { !it.digested }.take(DIGEST_BATCH)
        val freshIds = fresh.map { it.localId }.toSet()
        val context = lines.filter { it.digested && it.ts >= now - DIGEST_CONTEXT_MS && it.localId !in freshIds }
            .takeLast(80).map { it.toPrompt() }
        val previous = _state.value.catchUp
        lastDigestAt = now
        _state.update { it.copy(catchUpLoading = true, catchUpError = null) }
        digestJob = main.launch {
            try {
                val json = llm.json(
                    Prompts.digestSystem(prefs.displayName()),
                    Prompts.digestUser(profile(), previous, context, fresh.map { it.toPrompt() }),
                    maxTokens = 3500,
                    timeoutMs = 30_000,
                )
                val (cleaned, catchUp) = Prompts.parseDigest(json, fresh.size, now())
                val updates = mutableListOf<Pair<Long, String>>()
                fresh.forEachIndexed { i, original ->
                    val index = lines.indexOfFirst { it.localId == original.localId }
                    if (index < 0) return@forEachIndexed
                    val text = cleaned[i]
                    lines[index] = lines[index].copy(cleaned = text ?: lines[index].cleaned, digested = true)
                    val rowId = dbIds[original.localId]
                    if (text != null && rowId != null) updates.add(rowId to text)
                }
                _state.update {
                    it.copy(lines = lines.takeLast(40), catchUp = catchUp, catchUpLoading = false, catchUpError = null)
                }
                if (updates.isNotEmpty()) main.launch(dbThread) {
                    updates.forEach { (rowId, text) -> runCatching { db.updateCleaned(rowId, text) } }
                }
            } catch (e: CancellationException) {
                _state.update { it.copy(catchUpLoading = false) }
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(catchUpLoading = false, catchUpError = e.message ?: "Eroare AI") }
            }
        }
    }

    private fun tick() {
        assembler.tick()
        val now = now()
        refreshStatus(now)
        val visible = now - lastMeetingVisibleAt < 5_000
        if (!visible) captionsHintShown = false

        if (sessionActive) {
            if (llm.isConfigured() && digestJob?.isActive != true) {
                val pending = lines.count { !it.digested }
                val sinceLast = now - lastDigestAt
                if (pending > 0 && (sinceLast > DIGEST_INTERVAL_MS || (pending >= DIGEST_BATCH && sinceLast > 10_000))) runDigest()
            }
            val silentFor = now - lastCaptionAt
            if (visible && silentFor > NO_CAPTIONS_WARNING_MS && !noCaptionsWarned) {
                noCaptionsWarned = true
                val message = "Nu am primit subtitrări de un minut. Verifică dacă subtitrările (CC) sunt pornite."
                _state.update { it.copy(health = message) }
                alerts.fire(AlertLevel.HEALTH, "Nu mai primesc subtitrări", message)
            } else if (silentFor < NO_CAPTIONS_WARNING_MS && noCaptionsWarned) {
                noCaptionsWarned = false
                _state.update { it.copy(health = null) }
            }
            if (now - lastMeetingVisibleAt > END_NOT_VISIBLE_MS || silentFor > END_SILENT_MS) endMeeting(byUser = false)
        } else if (meetingVisibleSince > 0 && now - meetingVisibleSince > CAPTIONS_HINT_MS && !captionsHintShown) {
            captionsHintShown = true
            val message = "Ședință deschisă, dar nu văd subtitrări. Apasă CC (subtitrări) în Zoom/Meet și alege limba română."
            _state.update { it.copy(health = message) }
            alerts.fire(AlertLevel.HEALTH, "Pornește subtitrările", message)
        }
    }

    private fun refreshStatus(now: Long) {
        val visible = now - lastMeetingVisibleAt < 5_000
        val status = when {
            sessionActive -> Status.LISTENING
            visible -> Status.WAITING_CAPTIONS
            else -> Status.IDLE
        }
        val current = _state.value
        if (current.status != status || current.meetingVisible != visible ||
            (status == Status.IDLE && current.health != null && !visible)
        ) {
            _state.update {
                it.copy(
                    status = status,
                    meetingVisible = visible,
                    health = if (status == Status.IDLE) null else it.health,
                )
            }
        }
    }

    private fun alert(level: AlertLevel, title: String, text: String) {
        _state.update { it.copy(alert = level, alertText = text, alertAt = now()) }
        alerts.fire(level, title, text)
    }

    private fun isSelf(speaker: String?): Boolean {
        if (speaker.isNullOrBlank()) return false
        val key = TextNorm.key(speaker)
        if (key in SELF_SPEAKERS) return true
        refreshDetector()
        return detector.hasNames && detector.analyze(speaker).mentionsName
    }

    private fun refreshDetector() {
        val version = prefs.version.value
        if (version == detectorVersion) return
        detectorVersion = version
        detector = Detector(prefs.names(), prefs.topicList())
    }

    private fun recordEvent(type: String, payload: String) {
        val ts = now()
        main.launch(dbThread) {
            val meetingId = currentMeetingId ?: return@launch
            runCatching { db.insertEvent(meetingId, ts, type, payload) }
        }
    }

    private fun profile() = Profile(prefs.displayName(), prefs.profileNotes, prefs.vocabulary)

    private fun lineLabel(speaker: String?, text: String) = (speaker?.let { "$it: " } ?: "") + text

    private fun formatShort(line: Line) = lineLabel(line.speaker, line.text)

    private fun now() = System.currentTimeMillis()
}
