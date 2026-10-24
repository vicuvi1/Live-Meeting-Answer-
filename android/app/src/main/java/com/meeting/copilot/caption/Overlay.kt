package com.meeting.copilot.caption

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.meeting.copilot.data.Prefs
import com.meeting.copilot.engine.AlertLevel
import com.meeting.copilot.engine.MeetingEngine
import com.meeting.copilot.engine.Status
import com.meeting.copilot.engine.UiState
import kotlin.math.abs

/**
 * Floating bubble over Zoom / Meet. Its colour says what's happening (green: listening,
 * amber: mentioned or a topic came up, red: she was asked something). Tapping it opens a
 * panel with "what did I miss" and the suggested answer; it opens by itself on a question.
 */
@SuppressLint("ClickableViewAccessibility")
class Overlay(
    private val context: Context,
    private val engine: MeetingEngine,
    private val prefs: Prefs,
    private val onDump: () -> Unit,
) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private val density = context.resources.displayMetrics.density
    private fun dp(value: Int) = (value * density).toInt()
    private val screenWidth get() = context.resources.displayMetrics.widthPixels
    private val screenHeight get() = context.resources.displayMetrics.heightPixels

    private var bubble: TextView? = null
    private val bubbleSize = dp(58)
    private val bubbleParams = WindowManager.LayoutParams(
        bubbleSize, bubbleSize,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = screenWidth - bubbleSize - dp(6)
        y = (screenHeight * 0.38).toInt()
    }

    private var panel: View? = null
    private var panelContent: LinearLayout? = null
    private var liveView: TextView? = null
    private var lastPanelState: UiState? = null
    private var keepScreenOn = false

    fun render(state: UiState) {
        val show = prefs.showBubble && (state.meetingVisible || state.status == Status.LISTENING)
        if (!show) {
            closePanel()
            removeBubble()
            return
        }
        val view = bubble ?: createBubble() ?: return
        styleBubble(view, state)
        setKeepScreenOn(state.status == Status.LISTENING)
        if (panel != null) renderPanel(state)
    }

    fun openPanel() {
        if (bubble == null || panel != null) return
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(14))
        }
        val scroll = object : ScrollView(context) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val max = MeasureSpec.makeMeasureSpec((screenHeight * 0.62).toInt(), MeasureSpec.AT_MOST)
                super.onMeasure(widthMeasureSpec, max)
            }
        }.apply {
            background = GradientDrawable().apply {
                setColor(Color.argb(245, 10, 12, 20))
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), Color.rgb(51, 65, 85))
            }
            addView(content)
        }
        val params = WindowManager.LayoutParams(
            screenWidth - dp(16), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(44)
        }
        try {
            wm.addView(scroll, params)
            panel = scroll
            panelContent = content
            lastPanelState = null
            renderPanel(engine.state.value)
        } catch (_: Exception) {
            panel = null
            panelContent = null
        }
    }

    fun closePanel() {
        val view = panel ?: return
        try {
            wm.removeView(view)
        } catch (_: Exception) {
        }
        panel = null
        panelContent = null
        liveView = null
        lastPanelState = null
    }

    fun destroy() {
        closePanel()
        removeBubble()
    }

    // ---------------------------------------------------------------- bubble

    private fun createBubble(): TextView? {
        val view = TextView(context).apply {
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            elevation = dp(6).toFloat()
        }
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var dragging = false
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = bubbleParams.x
                    startY = bubbleParams.y
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!dragging && (abs(dx) > dp(10) || abs(dy) > dp(10))) dragging = true
                    if (dragging) {
                        bubbleParams.x = (startX + dx).toInt()
                        bubbleParams.y = (startY + dy).toInt().coerceIn(0, screenHeight - bubbleSize)
                        update(view, bubbleParams)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (dragging) snapToEdge(view) else if (panel == null) openPanel() else closePanel()
                    true
                }
                else -> false
            }
        }
        return try {
            wm.addView(view, bubbleParams)
            bubble = view
            view
        } catch (_: Exception) {
            null
        }
    }

    private fun styleBubble(view: TextView, state: UiState) {
        val now = System.currentTimeMillis()
        val age = now - state.alertAt
        val (color, label) = when {
            state.alert == AlertLevel.DIRECT && age < 120_000 -> Color.rgb(220, 38, 38) to "!"
            (state.alert == AlertLevel.MENTION || state.alert == AlertLevel.TOPIC) && age < 60_000 -> Color.rgb(245, 158, 11) to "•"
            state.health != null -> Color.rgb(180, 83, 9) to "?"
            state.status == Status.LISTENING -> Color.rgb(22, 163, 74) to ""
            else -> Color.rgb(71, 85, 105) to "CC"
        }
        view.text = label
        view.textSize = if (label == "CC") 15f else 24f
        view.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setStroke(dp(2), Color.argb(200, 255, 255, 255))
        }
    }

    private fun snapToEdge(view: View) {
        val targetX = if (bubbleParams.x + bubbleSize / 2 < screenWidth / 2) dp(6) else screenWidth - bubbleSize - dp(6)
        ValueAnimator.ofInt(bubbleParams.x, targetX).apply {
            duration = 180
            addUpdateListener {
                bubbleParams.x = it.animatedValue as Int
                update(view, bubbleParams)
            }
            start()
        }
    }

    private fun setKeepScreenOn(on: Boolean) {
        if (on == keepScreenOn) return
        keepScreenOn = on
        val view = bubble ?: return
        bubbleParams.flags = if (on) {
            bubbleParams.flags or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        } else {
            bubbleParams.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON.inv()
        }
        update(view, bubbleParams)
    }

    private fun removeBubble() {
        val view = bubble ?: return
        try {
            wm.removeView(view)
        } catch (_: Exception) {
        }
        bubble = null
        keepScreenOn = false
        bubbleParams.flags = bubbleParams.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON.inv()
    }

    private fun update(view: View, params: WindowManager.LayoutParams) {
        try {
            if (view.isAttachedToWindow) wm.updateViewLayout(view, params)
        } catch (_: Exception) {
        }
    }

    // ---------------------------------------------------------------- panel

    private fun renderPanel(state: UiState) {
        val content = panelContent ?: return
        val previous = lastPanelState
        lastPanelState = state
        // The live line changes several times a second: update just that view when nothing else changed.
        if (previous != null && previous.copy(live = "", liveSpeaker = null) == state.copy(live = "", liveSpeaker = null)) {
            liveView?.text = liveText(state)
            return
        }
        content.removeAllViews()
        val now = System.currentTimeMillis()

        // Header
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(text(statusText(state, now), 14f, Color.rgb(148, 163, 184)), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(smallButton("✕") { closePanel() })
        content.addView(header)

        state.health?.let { content.addView(text("⚠ $it", 15f, Color.rgb(251, 191, 36)).withTop(6)) }

        // Answer
        val answer = state.answer
        if (answer != null && (now - answer.createdAt < 5 * 60_000 || state.answerLoading)) {
            content.addView(label("ÎNTREBAREA", Color.rgb(248, 113, 113)).withTop(12))
            content.addView(text(answer.question, 18f, Color.WHITE))
            answer.context.forEach { content.addView(text("· $it", 14f, Color.rgb(148, 163, 184))) }
            content.addView(label("SPUNE", Color.rgb(74, 222, 128)).withTop(10))
            when {
                answer.answer.isNotBlank() -> {
                    content.addView(text(answer.answer, 23f, Color.WHITE, bold = true))
                    if (!answer.confident) content.addView(text("Răspuns prudent: nu am date sigure despre asta.", 13f, Color.rgb(148, 163, 184)))
                }
                state.answerLoading -> content.addView(text("Pregătesc răspunsul…", 18f, Color.rgb(148, 163, 184)))
            }
            state.answerError?.let { content.addView(text(it, 14f, Color.rgb(248, 113, 113))) }
            content.addView(smallButton("Am răspuns") { engine.dismissAnswer() }.withTop(6))
        } else {
            state.answerError?.let { content.addView(text(it, 14f, Color.rgb(248, 113, 113)).withTop(8)) }
        }

        // Catch-up
        val catchUp = state.catchUp
        if (catchUp != null && catchUp.topic.isNotBlank()) {
            content.addView(label("ACUM SE DISCUTĂ", Color.rgb(96, 165, 250)).withTop(14))
            content.addView(text(catchUp.topic, 19f, Color.WHITE, bold = true))
            catchUp.points.forEach { content.addView(text("• $it", 16f, Color.rgb(226, 232, 240))) }
            if (catchUp.forMe.isNotBlank()) {
                content.addView(label("DE LA TINE SE AȘTEAPTĂ", Color.rgb(251, 191, 36)).withTop(8))
                content.addView(text(catchUp.forMe, 18f, Color.rgb(253, 230, 138), bold = true))
            }
            content.addView(text(updatedText(catchUp.updatedAt, now, state.catchUpLoading), 12f, Color.rgb(100, 116, 139)).withTop(4))
        } else if (state.lines.isNotEmpty()) {
            content.addView(label("ULTIMELE REPLICI", Color.rgb(96, 165, 250)).withTop(14))
            state.lines.takeLast(4).forEach { line ->
                content.addView(text((line.speaker?.let { "$it: " } ?: "") + line.text, 15f, Color.rgb(226, 232, 240)))
            }
            if (state.catchUpLoading) content.addView(text("Pregătesc rezumatul…", 13f, Color.rgb(148, 163, 184)))
        }
        state.catchUpError?.let { content.addView(text(it, 13f, Color.rgb(248, 113, 113))) }

        val live = text(liveText(state), 14f, Color.rgb(148, 163, 184)).apply { setTypeface(typeface, Typeface.ITALIC) }
        liveView = live
        content.addView(live.withTop(10))

        val buttons = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(bigButton("Ce am pierdut?") { engine.requestCatchUp() }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        buttons.addView(bigButton("Ce să răspund?") { engine.requestAnswer() }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        content.addView(buttons.withTop(10))
        if (prefs.diagnostics) content.addView(smallButton("Captură diagnostic") { onDump() })
    }

    private fun statusText(state: UiState, now: Long): String = when {
        state.status == Status.LISTENING && !state.meetingVisible ->
            "${state.meetingApp ?: "Ședința"} nu e pe ecran: nu pot citi subtitrările"
        state.status == Status.LISTENING -> {
            val minutes = state.startedAt?.let { (now - it) / 60_000 } ?: 0
            "● Ascult · ${state.meetingApp ?: ""} · $minutes min"
        }
        state.status == Status.WAITING_CAPTIONS -> "Aștept subtitrările (apasă CC)"
        else -> "Nicio ședință"
    }

    private fun liveText(state: UiState): String =
        if (state.live.isBlank()) "" else "… " + (state.liveSpeaker?.let { "$it: " } ?: "") + state.live.takeLast(200)

    private fun updatedText(updatedAt: Long, now: Long, loading: Boolean): String {
        if (loading) return "se actualizează…"
        val seconds = (now - updatedAt) / 1000
        return if (seconds < 60) "actualizat acum $seconds s" else "actualizat acum ${seconds / 60} min"
    }

    private fun label(value: String, color: Int) = text(value, 12f, color, bold = true).apply { letterSpacing = 0.08f }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(context).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        setLineSpacing(0f, 1.1f)
    }

    private fun bigButton(value: String, onClick: () -> Unit) = Button(context).apply {
        text = value
        isAllCaps = false
        textSize = 16f
        setOnClickListener { onClick() }
    }

    private fun smallButton(value: String, onClick: () -> Unit) = Button(context).apply {
        text = value
        isAllCaps = false
        textSize = 13f
        minHeight = 0
        minimumHeight = 0
        setOnClickListener { onClick() }
    }

    private fun <T : View> T.withTop(topDp: Int): T = apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(topDp)
        }
    }
}
