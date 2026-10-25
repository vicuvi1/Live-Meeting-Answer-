package com.meeting.copilot.caption

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast
import com.meeting.copilot.App
import com.meeting.copilot.engine.Status
import com.meeting.copilot.util.DiagLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume

/**
 * Reads the text Zoom / Meet / Live Caption draw on screen, groups it by screen element and
 * hands it to the caption assembler. Also hosts the floating bubble, because an accessibility
 * service can draw over other apps without extra permissions and Android keeps it running.
 */
class CaptionService : AccessibilityService() {

    companion object {
        const val LIVE_CAPTION = "com.google.android.as"
        /** Gmail hosts Meet calls on many phones; it counts only while its screen shows call controls. */
        const val GMAIL = "com.google.android.gm"
        val MEETING_PACKAGES = setOf(
            "us.zoom.videomeetings",
            "com.google.android.apps.tachyon",
            "com.google.android.apps.meetings",
            "com.microsoft.teams",
            GMAIL,
            LIVE_CAPTION,
        )
        private val IGNORED_PACKAGES = setOf("com.android.systemui", "com.miui.home", "com.mi.android.globallauncher")
        private val CAPTION_ID_HINTS = listOf("caption", "subtitle", "transcri", "closedcc", "cc_text", "livecc")
        private val HAS_LETTER = Regex("\\p{L}")
        /** "Victor Barbuta: text" – up to four capitalized words, a colon, then the caption. */
        private val SPEAKER_PREFIX = Regex("^(\\p{Lu}[\\p{L}.'-]*(?: \\p{Lu}[\\p{L}.'-]*){0,3}): (.+)$", RegexOption.DOT_MATCHES_ALL)
        private val ONLY_NUMBERS = Regex("^[\\d:.,\\s%]+$")
        private const val SCAN_DELAY_MS = 200L
        private const val EVENT_TYPES = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
            AccessibilityEvent.TYPE_WINDOWS_CHANGED
        private const val POLL_MS = 1_500L
        private const val STATIC_LINE_MS = 6_000L
        private const val MAX_NODES = 4_000
        private const val MAX_DEPTH = 60
        /** Enough to read slide text, small enough to upload in a moment. */
        private const val SCREENSHOT_MAX_SIDE = 1280f

        private val _connected = MutableStateFlow(false)
        val connected: StateFlow<Boolean> = _connected

        @Volatile
        var instance: CaptionService? = null
            private set
    }

    private class RegionBuilder(val key: String, val explicit: Boolean) {
        val texts = mutableListOf<String>()
        var speaker: String? = null

        /** Meet writes captions as "Victor Barbuta: Bună ziua"; the name is kept apart from the words. */
        fun add(text: String) {
            val match = SPEAKER_PREFIX.matchEntire(text)
            if (match != null) {
                speaker = match.groupValues[1]
                texts.add(match.groupValues[2])
            } else {
                texts.add(text)
            }
        }

        /**
         * Lines unchanged for a while (titles, labels, finished sentences) are left out, so the
         * region reads as the captions still being written; the last line always stays.
         */
        fun build(firstSeen: MutableMap<String, Long>, now: Long): CaptionAssembler.Region {
            firstSeen.keys.retainAll(texts.toSet())
            texts.forEach { firstSeen.putIfAbsent(it, now) }
            val kept = texts.filterIndexed { i, line -> i == texts.lastIndex || now - (firstSeen[line] ?: now) < STATIC_LINE_MS }
            return CaptionAssembler.Region(key, kept.joinToString("\n"), speaker, explicit)
        }
    }

    private val app get() = App.instance
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var scanPending = false
    private var overlay: Overlay? = null
    private var allAppsMode: Boolean? = null
    private var activeMode: Boolean? = null
    private val autoCaptions = AutoCaptions()
    private val diagLastText = HashMap<String, String>()
    private var meetingWindowId = -1

    private val poll = object : Runnable {
        override fun run() {
            val state = app.engine.state.value
            if (app.prefs.active && (state.meetingVisible || state.status == Status.LISTENING)) safeScan()
            handler.postDelayed(this, POLL_MS)
        }
    }

    override fun onServiceConnected() {
        instance = this
        _connected.value = true
        applyServiceConfig()
        val overlay = Overlay(this, app.engine, app.prefs, onDump = { dump() })
        this.overlay = overlay
        scope.launch { app.engine.state.collect { overlay.render(it) } }
        scope.launch { app.engine.openPanel.collect { overlay.openPanel() } }
        scope.launch { app.engine.fixLanguage.collect { autoCaptions.requestRomanian(System.currentTimeMillis()) } }
        scope.launch { Overlay.hidden.collect { overlay.render(app.engine.state.value) } }
        scope.launch { Overlay.appVisible.collect { overlay.render(app.engine.state.value) } }
        scope.launch {
            app.prefs.version.collect {
                applyServiceConfig()
                overlay.render(app.engine.state.value)
            }
        }
        handler.postDelayed(poll, POLL_MS)
        app.engine.screenshot = { captureScreen(meetingOnly = true) }
        if (app.prefs.diagnostics) DiagLog.log("SERVICE CONNECTED")
    }

    /**
     * What the meeting shows (the shared presentation), as a small JPEG in base64 for the AI.
     * On Android 14+ only the meeting window is captured, so the island never covers it.
     */
    suspend fun captureScreen(meetingOnly: Boolean): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val shot = suspendCancellableCoroutine<ScreenshotResult?> { cont ->
            val callback = object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) = cont.resume(result)
                override fun onFailure(errorCode: Int) = cont.resume(null)
            }
            try {
                if (meetingOnly && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && meetingWindowId >= 0) {
                    takeScreenshotOfWindow(meetingWindowId, mainExecutor, callback)
                } else {
                    takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, callback)
                }
            } catch (e: Exception) {
                cont.resume(null)
            }
        } ?: return null
        return withContext(Dispatchers.Default) {
            try {
                val buffer = shot.hardwareBuffer
                val full = try {
                    Bitmap.wrapHardwareBuffer(buffer, shot.colorSpace)?.copy(Bitmap.Config.ARGB_8888, false)
                } finally {
                    buffer.close()
                } ?: return@withContext null
                val scale = minOf(1f, SCREENSHOT_MAX_SIDE / maxOf(full.width, full.height).toFloat())
                val small = Bitmap.createScaledBitmap(full, (full.width * scale).toInt(), (full.height * scale).toInt(), true)
                val out = ByteArrayOutputStream()
                small.compress(Bitmap.CompressFormat.JPEG, 70, out)
                Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            } catch (e: Exception) {
                DiagLog.log("SCREENSHOT ERROR ${e.message}")
                null
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!app.prefs.active || scanPending) return
        scanPending = true
        handler.postDelayed({
            scanPending = false
            safeScan()
        }, SCAN_DELAY_MS)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        app.engine.screenshot = null
        overlay?.destroy()
        overlay = null
        scope.cancel()
        if (instance === this) instance = null
        _connected.value = false
        super.onDestroy()
    }

    /**
     * Switched off: Android stops sending screen events altogether, so the app does nothing and
     * costs no battery. In "all apps" diagnostic mode it listens everywhere, to discover other
     * caption overlays.
     */
    private fun applyServiceConfig() {
        val active = app.prefs.active
        val wantAll = active && app.prefs.diagnostics && app.prefs.diagnosticsAllApps
        if (allAppsMode == wantAll && activeMode == active) return
        val info = serviceInfo ?: return
        info.packageNames = if (wantAll) null else MEETING_PACKAGES.toTypedArray()
        info.eventTypes = if (active) EVENT_TYPES else 0
        serviceInfo = info
        allAppsMode = wantAll
        activeMode = active
    }

    private fun safeScan() {
        try {
            scan()
        } catch (e: Exception) {
            DiagLog.log("SCAN ERROR ${e.stackTraceToString()}")
        }
    }

    private fun scan() {
        val allWindows = try {
            windows
        } catch (e: Exception) {
            return
        }
        val diagnostics = app.prefs.diagnostics
        val allApps = diagnostics && app.prefs.diagnosticsAllApps
        val builders = LinkedHashMap<String, RegionBuilder>()
        val budget = intArrayOf(MAX_NODES)
        var meetingPackage: String? = null
        val meetingRoots = mutableListOf<AccessibilityNodeInfo>()
        val now = System.currentTimeMillis()
        val assembler = app.engine.assembler

        for (window in allWindows) {
            if (window.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD ||
                window.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY
            ) continue
            val root = window.root ?: continue
            val pkg = root.packageName?.toString() ?: continue
            if (pkg == packageName || pkg in IGNORED_PACKAGES) continue
            val isMeetingApp = pkg in MEETING_PACKAGES
            if (!isMeetingApp && !allApps) continue
            val inCall = isMeetingApp && (pkg == LIVE_CAPTION || inCall(pkg, root, now))
            if (inCall && pkg != LIVE_CAPTION) {
                if (meetingPackage == null) {
                    meetingPackage = pkg
                    meetingWindowId = window.id
                }
                meetingRoots.add(root)
            }
            // Gmail's inbox is never read; Meet/Zoom screens are, so captions keep a call alive.
            if (inCall || (isMeetingApp && pkg != GMAIL) || allApps) collect(root, pkg, builders, 0, budget)
        }

        val regions = builders.values.map { it.build(lineFirstSeen.getOrPut(it.key) { HashMap() }, now) }
        if (lineFirstSeen.size > 300) lineFirstSeen.keys.retainAll(builders.keys)
        if (diagnostics) logRegions(regions)
        app.engine.onScan(meetingPackage != null, meetingPackage)
        assembler.observe(regions.filter {
            val pkg = it.key.substringBefore('|')
            pkg in MEETING_PACKAGES && (pkg != GMAIL || meetingPackage == GMAIL)
        })

        val flowing = assembler.activeKey != null && now - assembler.lastActiveChange < 30_000
        autoCaptions.onScan(meetingRoots, meetingPackage != null, flowing, app.prefs.autoCaptions, now)
            ?.let { app.engine.onAutoCaptions(it) }
        if (autoCaptions.shouldCloseMenu(meetingRoots, now)) performGlobalAction(GLOBAL_ACTION_BACK)
    }

    /** Per screen element: when each of its lines first appeared (to drop fixed labels). */
    private val lineFirstSeen = HashMap<String, MutableMap<String, Long>>()
    private val callControlsSeenAt = HashMap<String, Long>()
    private val callControlsCheckedAt = HashMap<String, Long>()

    /**
     * Whether this meeting app is in a call (not on its home screen): its call controls were seen
     * in the last 15 seconds, or its captions are still flowing (Zoom hides its toolbar).
     */
    private fun inCall(pkg: String, root: AccessibilityNodeInfo, now: Long): Boolean {
        if (now - (callControlsCheckedAt[pkg] ?: 0L) > 1_500) {
            callControlsCheckedAt[pkg] = now
            if (AutoCaptions.hasCallControls(root)) callControlsSeenAt[pkg] = now
        }
        val assembler = app.engine.assembler
        val captionsHere = assembler.activeKey?.substringBefore('|') == pkg && now - assembler.lastActiveChange < 15_000
        return now - (callControlsSeenAt[pkg] ?: 0L) < 15_000 || captionsHere
    }

    private fun collect(
        node: AccessibilityNodeInfo,
        pkg: String,
        builders: MutableMap<String, RegionBuilder>,
        depth: Int,
        budget: IntArray,
    ) {
        if (depth > MAX_DEPTH || budget[0]-- <= 0) return
        if (!node.isVisibleToUser) return
        val text = node.text?.toString()?.trim()
        if (!text.isNullOrEmpty() && HAS_LETTER.containsMatchIn(text) && !ONLY_NUMBERS.matches(text)) {
            val viewId = node.viewIdResourceName
            val cls = node.className?.toString()?.substringAfterLast('.') ?: "View"
            val key = "$pkg|" + (viewId ?: "$cls@$depth")
            val explicit = pkg == LIVE_CAPTION ||
                (viewId != null && CAPTION_ID_HINTS.any { viewId.lowercase().contains(it) })
            val builder = builders.getOrPut(key) { RegionBuilder(key, explicit) }
            builder.add(text)
            if (builder.speaker == null && (explicit || key == app.engine.assembler.activeKey)) {
                builder.speaker = findSpeaker(node, text)
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collect(child, pkg, builders, depth + 1, budget)
        }
    }

    /** Meeting apps usually show the speaker's name as a short label next to the caption text. */
    private fun findSpeaker(node: AccessibilityNodeInfo, captionText: String): String? {
        val parent = node.parent ?: return null
        for (i in 0 until parent.childCount) {
            val sibling = parent.getChild(i) ?: continue
            if (sibling == node) continue
            val label = sibling.text?.toString()?.trim() ?: continue
            if (label.length !in 2..40 || label == captionText) continue
            if (label.split(' ').size > 4 || label.any { it.isDigit() }) continue
            if (!label.first().isUpperCase() || label.last() in ".?!,") continue
            return label
        }
        return null
    }

    private fun logRegions(regions: List<CaptionAssembler.Region>) {
        val active = app.engine.assembler.activeKey
        for (region in regions) {
            if (diagLastText[region.key] == region.text) continue
            diagLastText[region.key] = region.text
            val mark = if (region.key == active) "*" else if (region.explicit) "!" else " "
            DiagLog.log("$mark ${region.key} speaker=${region.speaker} :: ${region.text.takeLast(400).replace('\n', '⏎')}")
        }
        if (diagLastText.size > 3000) diagLastText.clear()
    }

    /** Full view tree of every window, to find the exact caption element of a new app version. */
    fun dump() {
        val out = StringBuilder("=== DUMP ===\n")
        val allWindows = try { windows } catch (e: Exception) { emptyList() }
        for (window in allWindows) {
            out.append("WINDOW type=${window.type} title=${window.title} layer=${window.layer}\n")
            val root = window.root ?: continue
            dumpNode(root, 1, out)
        }
        out.append("=== END DUMP ===")
        DiagLog.log(out.toString())
        Toast.makeText(this, "Captură salvată în jurnalul de diagnostic", Toast.LENGTH_SHORT).show()
    }

    private fun dumpNode(node: AccessibilityNodeInfo, depth: Int, out: StringBuilder) {
        if (depth > MAX_DEPTH) return
        val bounds = Rect().also { node.getBoundsInScreen(it) }
        out.append("  ".repeat(depth)).append(node.className?.toString()?.substringAfterLast('.') ?: "?")
            .append(" pkg=").append(node.packageName)
        node.viewIdResourceName?.let { out.append(" id=").append(it) }
        node.text?.let { out.append(" text=\"").append(it).append('"') }
        node.contentDescription?.let { out.append(" desc=\"").append(it).append('"') }
        out.append(" bounds=").append(bounds.toShortString())
        if (!node.isVisibleToUser) out.append(" (hidden)")
        out.append('\n')
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            dumpNode(child, depth + 1, out)
        }
    }
}
