package com.meeting.captionprobe

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * Reads every piece of text visible on screen (all windows, including caption overlays
 * like Android Live Caption) and logs text that is new or changed. The goal is to find
 * out whether Zoom / Google Meet captions are readable, from which view, and how fast.
 */
class CaptionProbeService : AccessibilityService() {

    companion object {
        private const val SCAN_DELAY_MS = 250L
        private const val MAX_DEPTH = 80
        private const val MAX_LOGGED_CHARS = 600

        // Our own UI, status bar, launchers and keyboards only add noise.
        private val IGNORED_PACKAGES = setOf(
            "com.meeting.captionprobe",
            "com.android.systemui",
            "com.miui.home",
            "com.mi.android.globallauncher",
            "com.google.android.apps.nexuslauncher",
        )

        // Pure timers / counters such as "00:12:34" change every second.
        private val NOISE = Regex("^[\\d:.,\\s%]+$")

        val MEETING_PACKAGES = mapOf(
            "us.zoom.videomeetings" to "Zoom",
            "com.google.android.apps.tachyon" to "Meet",
            "com.google.android.apps.meetings" to "Meet (old)",
            "com.microsoft.teams" to "Teams",
            "com.google.android.as" to "Live Caption",
        )
    }

    private val handler = Handler(Looper.getMainLooper())
    private val lastText = HashMap<String, String>()
    private var scanPending = false
    private var overlay: View? = null
    private var overlayText: TextView? = null
    private var recButton: Button? = null
    private var dumpCount = 0

    override fun onServiceConnected() {
        Probe.service = this
        Probe.log(this, "SERVICE CONNECTED · Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}")
        if (Probe.overlayVisible) showOverlay()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!Probe.recording || scanPending) return
        scanPending = true
        handler.postDelayed({
            scanPending = false
            scan()
        }, SCAN_DELAY_MS)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        hideOverlay()
        if (Probe.service === this) Probe.service = null
        super.onDestroy()
    }

    fun onRecordingChanged() {
        lastText.clear()
        recButton?.text = if (Probe.recording) "STOP" else "REC"
        overlayText?.text = if (Probe.recording) "Recording… waiting for text" else "Caption Probe: not recording"
        if (Probe.recording) scan()
    }

    private fun scan() {
        val allWindows = try {
            windows
        } catch (e: Exception) {
            Probe.log(this, "ERROR reading windows: ${e.message}")
            return
        }
        for (window in allWindows) {
            if (window.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) continue
            val root = window.root ?: continue
            val pkg = root.packageName?.toString() ?: continue
            if (pkg in IGNORED_PACKAGES) continue
            walk(root, pkg, "0", 0)
        }
        if (lastText.size > 5000) lastText.clear()
    }

    private fun walk(node: AccessibilityNodeInfo, pkg: String, path: String, depth: Int) {
        if (depth > MAX_DEPTH) return
        val text = node.text?.toString()?.trim().orEmpty()
        if (text.length >= 2 && !NOISE.matches(text)) {
            val viewId = node.viewIdResourceName ?: "-"
            val key = "$pkg|$viewId|$path"
            val previous = lastText[key]
            if (previous != text) {
                lastText[key] = text
                val marker = if (previous == null) "+" else "~"
                val app = MEETING_PACKAGES[pkg] ?: pkg
                val cls = node.className?.toString()?.substringAfterLast('.') ?: "?"
                Probe.log(this, "$marker [$app] id=$viewId cls=$cls :: ${text.takeLast(MAX_LOGGED_CHARS)}")
                overlayText?.text = "$app: ${text.takeLast(160)}"
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            walk(child, pkg, "$path.$i", depth + 1)
        }
    }

    /** Writes the full view tree of every window, so we can find the exact caption view later. */
    fun dump() {
        dumpCount++
        val out = StringBuilder("=== DUMP #$dumpCount ===\n")
        val allWindows = try { windows } catch (e: Exception) { emptyList() }
        for (window in allWindows) {
            out.append("WINDOW type=${window.type} title=${window.title} layer=${window.layer} active=${window.isActive}\n")
            val root = window.root
            if (root == null) {
                out.append("  (no content)\n")
                continue
            }
            dumpNode(root, 1, out)
        }
        out.append("=== END DUMP #$dumpCount ===")
        Probe.log(this, out.toString(), toFeed = false)
        Probe.log(this, "DUMP #$dumpCount saved (${allWindows.size} windows, ${out.length} chars)")
        Toast.makeText(this, "Dump #$dumpCount saved", Toast.LENGTH_SHORT).show()
    }

    private fun dumpNode(node: AccessibilityNodeInfo, depth: Int, out: StringBuilder) {
        if (depth > MAX_DEPTH) return
        val bounds = Rect().also { node.getBoundsInScreen(it) }
        out.append("  ".repeat(depth))
            .append(node.className?.toString()?.substringAfterLast('.') ?: "?")
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

    fun showOverlay() {
        if (overlay != null) return
        val density = resources.displayMetrics.density
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(0xDD000000.toInt())
            setPadding((10 * density).toInt(), (4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt())
        }
        val text = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 13f
            maxLines = 3
            text = if (Probe.recording) "Recording… waiting for text" else "Caption Probe: not recording"
        }
        row.addView(text, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val rec = Button(this).apply {
            this.text = if (Probe.recording) "STOP" else "REC"
            setOnClickListener { Probe.setRecording(this@CaptionProbeService, !Probe.recording) }
        }
        row.addView(rec)
        val dumpButton = Button(this).apply {
            this.text = "DUMP"
            setOnClickListener { dump() }
        }
        row.addView(dumpButton)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP
            y = (40 * density).toInt()
        }
        try {
            (getSystemService(WINDOW_SERVICE) as WindowManager).addView(row, params)
            overlay = row
            overlayText = text
            recButton = rec
        } catch (e: Exception) {
            Probe.log(this, "ERROR showing overlay: ${e.message}")
        }
    }

    fun hideOverlay() {
        val view = overlay ?: return
        try {
            (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(view)
        } catch (_: Exception) {
        }
        overlay = null
        overlayText = null
        recButton = null
    }
}
