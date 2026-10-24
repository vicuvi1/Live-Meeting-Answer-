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
        private val ROMANIAN = setOf("romana", "romanian", "romana romania", "romanian romania", "romana (romania)")

        /** Controls only present in an active call: lets Gmail count as a meeting only during a Meet call. */
        private val CALL_MARKERS = listOf(
            "leave call", "end call", "hang up", "parasiti apelul", "paraseste apelul", "inchide apelul",
            "incheie apelul", "terminati apelul", "iesi din apel", "parasiti intalnirea", "leave meeting",
        )

        fun label(node: AccessibilityNodeInfo): String =
            TextNorm.norm((node.contentDescription ?: node.text ?: "").toString()).trim().trimEnd('.', '…')

        fun hasCallControls(root: AccessibilityNodeInfo): Boolean =
            find(listOf(root)) { node -> label(node).let { l -> CALL_MARKERS.any { l.contains(it) } } } != null

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

        // A language list right after we turned captions on: choose Romanian.
        if (!languagePicked && now - captionsClickedAt < LANGUAGE_WINDOW_MS) {
            find(roots) { label(it) in ROMANIAN }?.let { node ->
                if (click(node)) {
                    languagePicked = true
                    return log("selected Romanian (${label(node)})")
                }
            }
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
