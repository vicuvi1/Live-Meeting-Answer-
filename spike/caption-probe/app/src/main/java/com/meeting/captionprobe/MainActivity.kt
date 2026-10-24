package com.meeting.captionprobe

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Plain test screen: setup steps, recording controls and the live feed of captured text. */
class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var recordButton: Button
    private lateinit var overlayButton: Button
    private lateinit var feed: TextView
    private val onLogChanged: () -> Unit = { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
        }

        column.addView(TextView(this).apply {
            text = "Caption Probe (test)"
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
        })
        column.addView(TextView(this).apply {
            text = "Checks whether Zoom / Google Meet captions can be read on this phone. " +
                "Everything stays on the phone until you export the log."
            textSize = 14f
            setPadding(0, pad / 2, 0, pad)
        })

        status = TextView(this).apply { textSize = 15f }
        column.addView(status)

        column.addView(button("1. App info → ⋮ → Allow restricted settings") {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        })
        column.addView(button("2. Accessibility → Caption Probe → ON") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        recordButton = button("") { Probe.setRecording(this, !Probe.recording) }
        column.addView(recordButton)
        overlayButton = button("") {
            Probe.overlayVisible = !Probe.overlayVisible
            if (Probe.overlayVisible) Probe.service?.showOverlay() else Probe.service?.hideOverlay()
            refresh()
        }
        column.addView(overlayButton)
        column.addView(button("Dump screen now") {
            val service = Probe.service
            if (service == null) toast("Turn on the accessibility service first") else service.dump()
        })
        column.addView(button("Export log to Downloads + share") { export() })
        column.addView(button("Clear log") { Probe.clear(this) })

        feed = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 11f
            setTextColor(Color.rgb(170, 255, 170))
            setBackgroundColor(Color.BLACK)
            setPadding(pad / 2, pad / 2, pad / 2, pad / 2)
        }
        column.addView(feed, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = pad })

        setContentView(ScrollView(this).apply { addView(column) })
    }

    override fun onResume() {
        super.onResume()
        Probe.addListener(onLogChanged)
        refresh()
    }

    override fun onPause() {
        Probe.removeListener(onLogChanged)
        super.onPause()
    }

    private fun refresh() {
        val connected = Probe.service != null
        status.text = buildString {
            append(if (connected) "✅ Accessibility service ON" else "❌ Accessibility service OFF (do steps 1 and 2)")
            append('\n')
            append(if (Probe.recording) "🔴 Recording" else "⚪ Not recording")
        }
        recordButton.text = if (Probe.recording) "Stop recording" else "Start recording"
        overlayButton.text = if (Probe.overlayVisible) "Hide floating strip" else "Show floating strip"
        val lines = Probe.feedLines()
        feed.text = if (lines.isEmpty()) "(nothing captured yet)" else lines.asReversed().joinToString("\n")
    }

    private fun export() {
        val uri = try {
            Probe.exportToDownloads(this)
        } catch (e: Exception) {
            toast("Export failed: ${e.message}")
            return
        }
        if (uri == null) {
            toast("Log is empty")
            return
        }
        toast("Saved to Downloads")
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(share, "Send the log"))
    }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { onClick() }
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
