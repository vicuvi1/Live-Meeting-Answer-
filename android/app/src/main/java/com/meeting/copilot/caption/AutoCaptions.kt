package com.meeting.copilot.caption

import android.view.accessibility.AccessibilityNodeInfo
import com.meeting.copilot.text.TextNorm
import com.meeting.copilot.util.DiagLog

/**
 * Turns captions on by itself, so she never has to find the CC button: when a meeting is on
 * screen and no captions arrive, it presses the "turn on captions" button (or opens the
 * "more options" menu and picks "Captions"), and picks Romanian if a language list appears.
 *
 * It only presses controls whose label clearly means "turn on", so it never switches
 * captions off. Every step goes to the diagnostic log, to adapt to new Zoom/Meet versions.
 */
class AutoCaptions {

    companion object {
        private const val START_DELAY_MS = 4_000L
        private const val RETRY_MS = 3_500L
        private const val MAX_ATTEMPTS = 6
        private const val MENU_WINDOW_MS = 4_000L
        private const val LANGUAGE_WINDOW_MS = 15_000L
        private const val MAX_NODES = 2_500

        private val TURN_ON = listOf(
            "turn on captions", "show captions", "enable captions", "turn on closed captions", "captions off",
            "turn on live captions", "start captions",
            "activeaza subtitrarile", "activati subtitrarile", "porneste subtitrarile", "porniti subtitrarile",
            "afiseaza subtitrarile", "afisati subtitrarile", "activeaza subtitrari", "afisare subtitrari",
            "subtitrarile sunt dezactivate",
        )
        private val TURN_OFF = listOf("turn off", "hide", "disable", "dezactiveaza", "dezactivati", "ascunde", "opreste", "opriti", "inchide")
        private val MENU_ITEM = setOf(
            "captions", "closed captions", "show captions", "live captions", "subtitrari", "subtitrari cc",
            "afiseaza subtitrarile", "afisati subtitrarile", "subtitrari live",
        )
        private val MORE = setOf("more options", "more", "mai multe optiuni", "mai multe", "alte optiuni", "optiuni suplimentare")
        private val ROMANIAN = setOf("romana", "romanian", "romana romania", "romanian romania", "romana (romania)", "romanian (romania)")
        /** The row showing the captions' language, e.g. Meet's "Show captions in this call  English (United Kingdom)". */
        private val LANGUAGE_ROW = listOf(
            "captions in this call", "caption language", "spoken language", "meeting language",
            "subtitrari in acest apel", "limba subtitrarilor", "limba vorbita", "limba sedintei",
        )
        private const val MAX_LANGUAGE_ATTEMPTS = 8

        private fun isRomanianOption(label: String) =
            label in ROMANIAN || label.startsWith("romanian") || label.startsWith("romana")

        /** Controls only present during a call (not on a meeting app's home screen or Gmail's inbox). */
        private val CALL_MARKERS = listOf(
            "leave call", "end call", "hang up", "parasiti apelul", "paraseste apelul", "inchide apelul",
            "incheie apelul", "terminati apelul", "iesi din apel", "parasiti intalnirea", "leave meeting",
            "microphone", "microfon", "turn on captions", "turn off captions", "subtitrari", "camera off", "turn off camera",
        )
        private val CALL_EXACT = setOf("leave", "end", "parasiti", "iesi", "mute", "unmute")

        fun label(node: AccessibilityNodeInfo): String =
            TextNorm.norm((node.contentDescription ?: node.text ?: "").toString()).trim().trimEnd('.', '…')

        fun hasCallControls(root: AccessibilityNodeInfo): Boolean =
            find(listOf(root)) { node -> label(node).let { l -> l in CALL_EXACT || CALL_MARKERS.any { l.contains(it) } } } != null

        /** Breadth-first search over visible nodes, bounded so it stays cheap. */
        fun find(roots: List<AccessibilityNodeInfo>, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
            val queue = ArrayDeque(roots)
            var budget = MAX_NODES
            while (queue.isNotEmpty() && budget-- > 0) {
                val node = queue.removeFirst()
                if (!node.isVisibleToUser) continue
                if (predicate(node)) return node
                for (i in 0 until node.childCount) node.getChild(i)?.let { queue.addLast(it) }
            }
            return null
        }
    }

    private var visibleSince = 0L
    private var lastAttemptAt = 0L
    private var attempts = 0
    private var captionsSeen = false
    private var menuOpenedAt = 0L
    private var captionsClickedAt = 0L
    private var languagePicked = false
    private var fixLanguageUntil = 0L
    private var languageAttempts = 0
    private var languageLastAt = 0L
    private var closeMenuAt = 0L

    /** The captions came in another language: switch them to Romanian over the next minute. */
    fun requestRomanian(now: Long) {
        if (now < fixLanguageUntil) return
        fixLanguageUntil = now + 60_000
        languageAttempts = 0
        languagePicked = false
        log("captions are not Romanian: switching the language")
    }

    /**
     * True once, shortly after it changed a setting through a menu, if that menu still covers the
     * call. The caller then presses Back, which closes the menu, not the call.
     */
    fun shouldCloseMenu(roots: List<AccessibilityNodeInfo>, now: Long): Boolean {
        if (closeMenuAt == 0L || now < closeMenuAt) return false
        closeMenuAt = 0L
        return find(roots) { node -> label(node).let { CaptionAssembler.looksLikeMenu(it) || isRomanianOption(it) } } != null
    }

    /**
     * Called on every scan. [captionsFlowing] = caption text arrived recently.
     * Returns a short description of what it pressed, or null.
     */
    fun onScan(roots: List<AccessibilityNodeInfo>, meetingVisible: Boolean, captionsFlowing: Boolean, enabled: Boolean, now: Long): String? {
        if (!meetingVisible) {
            visibleSince = 0L
            attempts = 0
            captionsSeen = false
            languagePicked = false
            return null
        }
        if (visibleSince == 0L) visibleSince = now
        if (!enabled || roots.isEmpty()) return null

        // Captions in the wrong language: open the caption settings and pick Romanian.
        if (now < fixLanguageUntil && languageAttempts < MAX_LANGUAGE_ATTEMPTS && now - languageLastAt > RETRY_MS) {
            fixLanguage(roots, now, mayOpenMenus = true)?.let { return it }
        }
        // Right after we turned captions on: if a language choice is on screen, choose Romanian.
        if (!languagePicked && now - captionsClickedAt < LANGUAGE_WINDOW_MS && now - languageLastAt > RETRY_MS) {
            fixLanguage(roots, now, mayOpenMenus = false)?.let { return it }
        }

        if (captionsFlowing) captionsSeen = true
        if (captionsSeen || attempts >= MAX_ATTEMPTS) return null
        if (now - visibleSince < START_DELAY_MS || now - lastAttemptAt < RETRY_MS) return null

        // 1. A button that clearly turns captions on.
        val direct = find(roots) { node ->
            val l = label(node)
            l.isNotEmpty() && TURN_ON.any { l.contains(it) } && TURN_OFF.none { l.contains(it) }
        } ?: find(roots) { node ->
            // A captions toggle that reports itself as unchecked.
            node.isCheckable && !node.isChecked && label(node).let { it.contains("caption") || it.contains("subtitr") }
        }
        if (direct != null) return attempt(direct, now, "pressed '${label(direct)}'")

        // 2. The "Captions" entry of a menu we just opened.
        if (now - menuOpenedAt < MENU_WINDOW_MS) {
            find(roots) { label(it) in MENU_ITEM }?.let { return attempt(it, now, "chose menu item '${label(it)}'") }
        }

        // 3. Open the "more options" menu; the next scan looks for "Captions" inside it.
        find(roots) { node -> label(node) in MORE && (node.isClickable || node.parent?.isClickable == true) }?.let { more ->
            if (click(more)) {
                menuOpenedAt = now
                lastAttemptAt = now
                attempts++
                return log("opened menu '${label(more)}'")
            }
        }
        lastAttemptAt = now
        attempts++
        if (attempts == MAX_ATTEMPTS) log("could not find a captions button")
        return null
    }

    /**
     * One step towards Romanian captions: pick Romanian if offered, else open the language row,
     * else (when allowed) open the captions settings or the "more options" menu.
     */
    private fun fixLanguage(roots: List<AccessibilityNodeInfo>, now: Long, mayOpenMenus: Boolean): String? {
        find(roots) { isRomanianOption(label(it)) }?.let { node ->
            if (click(node)) {
                languageLastAt = now
                languagePicked = true
                fixLanguageUntil = 0L
                closeMenuAt = now + 1_500
                return log("selected Romanian (${label(node)})")
            }
        }
        find(roots) { node -> label(node).let { l -> LANGUAGE_ROW.any { l.contains(it) } && !l.contains("roman") } }?.let { row ->
            if (click(row)) {
                languageLastAt = now
                languageAttempts++
                return log("opened caption language '${label(row).take(60)}'")
            }
        }
        if (!mayOpenMenus) return null
        languageLastAt = now
        languageAttempts++
        find(roots) { label(it) in MENU_ITEM }?.let { item ->
            if (click(item)) return log("opened captions settings '${label(item)}'")
        }
        find(roots) { node -> label(node) in MORE && (node.isClickable || node.parent?.isClickable == true) }?.let { more ->
            if (click(more)) return log("opened menu for the language '${label(more)}'")
        }
        if (languageAttempts >= MAX_LANGUAGE_ATTEMPTS) log("could not switch captions to Romanian")
        return null
    }

    private fun attempt(node: AccessibilityNodeInfo, now: Long, what: String): String? {
        lastAttemptAt = now
        attempts++
        if (!click(node)) return null
        captionsClickedAt = now
        return log(what)
    }

    /** Clicks the node or its nearest clickable ancestor. */
    private fun click(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth < 5) {
            if (current.isClickable && current.isEnabled) return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            current = current.parent
            depth++
        }
        return false
    }

    private fun log(message: String): String {
        DiagLog.log("AUTO-CAPTIONS $message")
        return message
    }
}
