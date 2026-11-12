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
        /** Times the text grew (words added, or old words scrolled away) rather than being replaced. */
        val growths = ArrayDeque<Long>()
    }

    companion object {
        const val QUALIFY_WINDOW_MS = 20_000L
        const val QUALIFY_GROWTHS = 2
        const val IDLE_COMMIT_MS = 2_500L
        /** A locked source that stopped growing this long gives way to one that grows. */
        const val SWITCH_NO_GROWTH_MS = 12_000L
        const val MAX_WORDS = 220
        const val MAX_UTTERANCE_WORDS = 45
        private val SENTENCE_END = Regex("[.?!…]$")

        /** Text of meeting-app menus and settings, never captions (Meet's options sheet, etc.). */
        private val MENU_PHRASES = listOf(
            "show captions in this call", "studio sound", "filters out background noise", "reactions move on the screen",
            "watch only presentations", "your own reactions", "caption language", "captions in this call",
            "afiseaza subtitrarile", "sunet de studio", "limba subtitrarilor", "subtitrari in acest apel",
        )

        fun looksLikeMenu(text: String): Boolean {
            val normalized = TextNorm.norm(text)
            return MENU_PHRASES.any { normalized.contains(it) }
        }

        /** Captions progress: the new text continues the old one with more words, or old lines scrolled away. */
        fun isGrowth(old: String, new: String): Boolean {
            val before = TextNorm.words(old).takeLast(MAX_WORDS)
            val after = TextNorm.words(new).takeLast(MAX_WORDS)
            if (before.isEmpty() || after.isEmpty()) return false
            val offset = align(before, after)
            return offset > 0 || (offset == 0 && after.size > before.size)
        }
    }

    private val stats = HashMap<String, Stats>()
    var activeKey: String? = null
        private set
    var lastActiveChange = 0L
        private set
    private var lastActiveGrowth = 0L

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
            val grew = isGrowth(s.lastText, region.text)
            s.lastText = region.text
            if (grew) s.growths.addLast(now)
            while (s.growths.isNotEmpty() && now - s.growths.first() > QUALIFY_WINDOW_MS) s.growths.removeFirst()

            if (region.key == activeKey) {
                lastActiveChange = now
                if (grew) lastActiveGrowth = now
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

    /** Finalizes what is on screen now without waiting for a pause ("Ce să răspund?" pressed). */
    fun commitNow() {
        if (activeKey == null) return
        flushAll(clock())
        emitLive()
    }

    /** Finalizes everything (meeting ended) and forgets the source. */
    fun reset() {
        flushAll(clock())
        stats.clear()
        activeKey = null
        lastActiveGrowth = 0L
        tail = emptyList()
        committedInTail = 0
        speaker = null
        listener.onLive("", null)
    }

    private fun canSwitchTo(region: Region, s: Stats, now: Long): Boolean {
        if (looksLikeMenu(region.text)) return false
        val qualifies = region.explicit || s.growths.size >= QUALIFY_GROWTHS
        if (!qualifies) return false
        return activeKey == null || now - lastActiveGrowth > SWITCH_NO_GROWTH_MS
    }

    private fun switchTo(region: Region, now: Long) {
        flushAll(now)
        activeKey = region.key
        tail = emptyList()
        committedInTail = 0
        lastActiveChange = now
        lastActiveGrowth = now
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
        val stillVisible: List<String>
        if (offset < 0) {
            // Completely new text: whatever was visible before is final, and a new caption block starts.
            commitWords(tail.subList(committedInTail, tail.size), now)
            flushPending()
            committedInTail = 0
            stillVisible = emptyList()
        } else {
            if (offset > 0) {
                // The first `offset` words scrolled out of view.
                if (offset > committedInTail) commitWords(tail.subList(committedInTail, offset), now)
                committedInTail = maxOf(0, committedInTail - offset)
            }
            stillVisible = tail.subList(offset, tail.size)
        }
        // Words finalized above were said by the previous speaker; switch only now.
        if (speakerChanged) flushPending()
        if (newSpeaker != null) speaker = newSpeaker
        committedInTail = stillCommitted(stillVisible, words)
        tail = words
        emitLive()
    }

    /**
     * How many of the new words are already final. Final words stay final while the caption
     * continues, even if the recognizer later revises one of them: saving a late fix would mean
     * saving the rest of the sentence a second time, and a tiny loss beats a duplicate. Only when
     * most final words are gone is it a new sentence (after fixed text such as a title or a name,
     * the new sentence replaces the old one), and then only the unchanged leading words stay final.
     */
    private fun stillCommitted(stillVisible: List<String>, words: List<String>): Int {
        if (committedInTail == 0) return 0
        val walk = walk(stillVisible.map(TextNorm::key), words.map(TextNorm::key), 0, committedInTail)
        if (walk.matches * 10 >= committedInTail * 6) return walk.newEnd
        return minOf(committedInTail, sameLeading(stillVisible, words))
    }

    private fun sameLeading(before: List<String>, after: List<String>): Int {
        var i = 0
        while (i < before.size && i < after.size && TextNorm.key(before[i]) == TextNorm.key(after[i])) i++
        return i
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
    if (bestOffset >= 0) return bestOffset

    // No exact match: the recognizer may have revised the first words ("bun a ziua" -> "Bună ziua",
    // "zua" -> "ziua"). Compare again word by word with tolerance, and take the overlap where most
    // words still match, so the revised sentence replaces the old one instead of being saved twice.
    for (offset in old.indices) {
        val overlap = minOf(old.size - offset, new.size)
        val matches = walk(old, new, offset, old.size).matches
        if (matches < minOf(2, overlap) || matches * 10 < overlap * 6) continue
        if (matches > bestMatches) {
            bestMatches = matches
            bestOffset = offset
        }
    }
    return bestOffset
}

/** Result of [walk]: how many old words matched, and where in the new words the walk stopped. */
internal class Walk(val matches: Int, val newEnd: Int)

/**
 * Walks old keys `old[from until to]` alongside new keys from the start, tolerating revised,
 * merged, split, dropped and inserted words. Counts the old words that found a match.
 */
internal fun walk(old: List<String>, new: List<String>, from: Int, to: Int): Walk {
    var i = from
    var j = 0
    var matches = 0
    while (i < to && j < new.size) {
        val a = old[i]
        val b = new[j]
        when {
            // Two words merged: "bun a" -> "buna".
            i + 1 < to && a + old[i + 1] == b -> { matches += 2; i += 2; j++ }
            // One word split: "deacord" -> "de acord".
            j + 1 < new.size && a == b + new[j + 1] -> { matches++; i++; j += 2 }
            similarWord(a, b, growing = i == old.size - 1) -> { matches++; i++; j++ }
            // An old word was dropped, or a new one inserted.
            i + 1 < to && similarWord(old[i + 1], b, growing = false) -> i++
            j + 1 < new.size && similarWord(a, new[j + 1], growing = false) -> j++
            // A word replaced by an unrelated one.
            else -> { i++; j++ }
        }
    }
    // Old words left over when the new text ended early correspond to nothing new.
    return Walk(matches, j)
}

/** Same word, give or take a recognizer revision: "zua" ~ "ziua", "bun" ~ "buna", "sedinta" ~ "sedintei". */
private fun similarWord(a: String, b: String, growing: Boolean): Boolean {
    if (a == b) return true
    if (a.isEmpty() || b.isEmpty()) return false
    if (growing && b.startsWith(a)) return true
    val shorter = if (a.length <= b.length) a else b
    val longer = if (a.length <= b.length) b else a
    if (shorter.length >= 3 && longer.startsWith(shorter)) return true
    // Short words differ by one letter at most, longer ones by two ("este" and "asta" are different words).
    val allowed = when {
        longer.length >= 6 -> 2
        longer.length >= 4 -> 1
        else -> 0
    }
    return allowed > 0 && TextNorm.levenshtein(a, b) <= allowed
}
