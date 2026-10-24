package com.meeting.copilot.caption

import com.meeting.copilot.text.TextNorm

/**
 * Turns what a meeting app shows on screen into a clean stream of sentences.
 *
 * Captions on screen behave like a moving window: the last line grows word by word,
 * the recognizer revises recent words, and old lines scroll away. Every scan we compare
 * the visible words with the previous scan; words that scrolled out of view are final.
 * When the captions stop changing for a moment, everything visible is final too.
 *
 * Which on-screen element holds the captions isn't known in advance (it differs between
 * Zoom, Meet and their versions), so a region becomes the caption source when its id looks
 * like one, or when it keeps changing with real sentences in it.
 */
class CaptionAssembler(
    private val listener: Listener,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    interface Listener {
        /** Text not yet final, for the live line. */
        fun onLive(text: String, speaker: String?)
        fun onUtterance(text: String, speaker: String?, startedAt: Long)
        fun onSourceLocked(key: String)
    }

    /** All text one screen element type shows in a single scan, top to bottom. */
    data class Region(val key: String, val text: String, val speaker: String?, val explicit: Boolean)

    private class Stats {
        var lastText = ""
        val changes = ArrayDeque<Long>()
    }

    companion object {
        const val QUALIFY_WINDOW_MS = 20_000L
        const val QUALIFY_CHANGES = 3
        const val IDLE_COMMIT_MS = 2_500L
        const val SWITCH_IDLE_MS = 20_000L
        const val MAX_WORDS = 220
        const val MAX_UTTERANCE_WORDS = 45
        private val SENTENCE_END = Regex("[.?!…]$")
    }

    private val stats = HashMap<String, Stats>()
    var activeKey: String? = null
        private set
    var lastActiveChange = 0L
        private set

    private var tail: List<String> = emptyList()
    private var committedInTail = 0
    private val pending = mutableListOf<String>()
    private var pendingStartedAt = 0L
    private var speaker: String? = null

    fun observe(regions: List<Region>) {
        val now = clock()
        for (region in regions) {
            val s = stats.getOrPut(region.key) { Stats() }
            if (s.lastText == region.text) continue
            s.lastText = region.text
            s.changes.addLast(now)
            while (s.changes.isNotEmpty() && now - s.changes.first() > QUALIFY_WINDOW_MS) s.changes.removeFirst()

            if (region.key == activeKey) {
                lastActiveChange = now
                onActiveText(region.text, region.speaker, now)
            } else if (canSwitchTo(region, s, now)) {
                switchTo(region, now)
            }
        }
        if (stats.size > 500) stats.keys.retainAll(setOfNotNull(activeKey))
    }

    /** Call about twice a second: finalizes text once the captions pause. */
    fun tick() {
        val now = clock()
        if (activeKey == null || now - lastActiveChange < IDLE_COMMIT_MS) return
        if (committedInTail < tail.size || pending.isNotEmpty()) {
            commitWords(tail.subList(committedInTail, tail.size), now)
            committedInTail = tail.size
            flushPending()
            emitLive()
        }
    }

    /** Finalizes everything (meeting ended) and forgets the source. */
    fun reset() {
        flushAll(clock())
        stats.clear()
        activeKey = null
        tail = emptyList()
        committedInTail = 0
        speaker = null
        listener.onLive("", null)
    }

    private fun canSwitchTo(region: Region, s: Stats, now: Long): Boolean {
        val qualifies = region.explicit ||
            (s.changes.size >= QUALIFY_CHANGES && TextNorm.words(region.text).size >= 3)
        if (!qualifies) return false
        return activeKey == null || now - lastActiveChange > SWITCH_IDLE_MS
    }

    private fun switchTo(region: Region, now: Long) {
        flushAll(now)
        activeKey = region.key
        tail = emptyList()
        committedInTail = 0
        lastActiveChange = now
        listener.onSourceLocked(region.key)
        onActiveText(region.text, region.speaker, now)
    }

    private fun onActiveText(text: String, newSpeaker: String?, now: Long) {
        val words = TextNorm.words(text).takeLast(MAX_WORDS)
        val speakerChanged = newSpeaker != null && speaker != null && newSpeaker != speaker

        if (words.isEmpty()) {
            commitWords(tail.subList(committedInTail, tail.size), now)
            tail = emptyList()
            committedInTail = 0
            if (speakerChanged) flushPending()
            if (newSpeaker != null) speaker = newSpeaker
            emitLive()
            return
        }

        val offset = align(tail, words)
        if (offset < 0) {
            // Completely new text: whatever was visible before is final, and a new caption block starts.
            commitWords(tail.subList(committedInTail, tail.size), now)
            flushPending()
            committedInTail = 0
        } else if (offset > 0) {
            // The first `offset` words scrolled out of view.
            if (offset > committedInTail) commitWords(tail.subList(committedInTail, offset), now)
            committedInTail = maxOf(0, committedInTail - offset)
        }
        // Words finalized above were said by the previous speaker; switch only now.
        if (speakerChanged) flushPending()
        if (newSpeaker != null) speaker = newSpeaker
        tail = words
        committedInTail = minOf(committedInTail, tail.size)
        emitLive()
    }

    private fun commitWords(words: List<String>, now: Long) {
        for (word in words.toList()) {
            if (pending.isEmpty()) pendingStartedAt = now
            pending.add(word)
            if (SENTENCE_END.containsMatchIn(word) || pending.size >= MAX_UTTERANCE_WORDS) flushPending()
        }
    }

    private fun flushPending() {
        if (pending.isEmpty()) return
        listener.onUtterance(pending.joinToString(" "), speaker, pendingStartedAt)
        pending.clear()
    }

    private fun flushAll(now: Long) {
        commitWords(tail.subList(committedInTail, tail.size), now)
        committedInTail = tail.size
        flushPending()
    }

    private fun emitLive() {
        val live = pending + tail.subList(committedInTail, tail.size)
        listener.onLive(live.joinToString(" "), speaker)
    }
}

/**
 * Where the new visible words start inside the previously visible words: 0 = same start
 * (text grew or was revised), n = the first n old words scrolled away, -1 = unrelated text.
 * Tolerates the recognizer revising some words and the last word still growing.
 */
internal fun align(previous: List<String>, current: List<String>): Int {
    if (previous.isEmpty()) return 0
    if (current.isEmpty()) return -1
    val old = previous.map(TextNorm::key)
    val new = current.map(TextNorm::key)

    var bestOffset = -1
    var bestMatches = 0
    for (offset in old.indices) {
        val overlap = minOf(old.size - offset, new.size)
        fun same(i: Int): Boolean {
            val a = old[offset + i]
            val b = new[i]
            if (a == b) return true
            // The last visible old word may still have been growing: "ziu" -> "ziua".
            return offset + i == old.size - 1 && a.isNotEmpty() && b.startsWith(a)
        }
        var lead = 0
        while (lead < overlap && same(lead)) lead++
        if (lead < minOf(2, overlap)) continue
        var matches = lead
        for (i in lead until overlap) if (same(i)) matches++
        if (matches * 10 < overlap * 6) continue
        if (matches > bestMatches) {
            bestMatches = matches
            bestOffset = offset
        }
    }
    return bestOffset
}
