package com.meeting.copilot.caption

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
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
 * amber: mentioned or a topic came up, red and pulsing: she was asked something). Tapping it
 * opens a panel with the suggested answer and "what did I miss"; it opens by itself on a question.
 */
@SuppressLint("ClickableViewAccessibility")
class Overlay(
    private val context: Context,
    private val engine: MeetingEngine,
    private val prefs: Prefs,
    private val onDump: () -> Unit,
) {
    private object C {
        val panel = Color.argb(248, 12, 16, 26)
        val card = Color.rgb(21, 27, 41)
        val border = Color.rgb(34, 43, 61)
        val text = Color.rgb(244, 246, 251)
        val soft = Color.rgb(201, 209, 224)
        val muted = Color.rgb(138, 148, 168)
        val faint = Color.rgb(91, 100, 120)
        val green = Color.rgb(52, 211, 153)
        val greenDeep = Color.rgb(14, 59, 44)
        val amber = Color.rgb(251, 191, 36)
        val amberDeep = Color.rgb(58, 42, 6)
        val red = Color.rgb(248, 113, 113)
        val redStrong = Color.rgb(239, 68, 68)
        val redDeep = Color.rgb(63, 16, 16)
        val blue = Color.rgb(124, 167, 255)
        val blueDeep = Color.rgb(20, 34, 63)
        val idle = Color.rgb(71, 85, 105)
        val onAccent = Color.rgb(6, 17, 12)
    }

    private val wm = context.getSystemService(WindowManager::class.java)
    private val density = context.resources.displayMetrics.density
    private fun dp(value: Int) = (value * density).toInt()
    private val screenWidth get() = context.resources.displayMetrics.widthPixels
    private val screenHeight get() = context.resources.displayMetrics.heightPixels

    private var bubble: TextView? = null
    private var pulse: ObjectAnimator? = null
    private val bubbleSize = dp(60)
    private val bubbleParams = WindowManager.LayoutParams(
        bubbleSize, bubbleSize,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = screenWidth - bubbleSize - dp(8)
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
            setPadding(dp(18), dp(16), dp(18), dp(18))
        }
        val scroll = object : ScrollView(context) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val max = MeasureSpec.makeMeasureSpec((screenHeight * 0.64).toInt(), MeasureSpec.AT_MOST)
                super.onMeasure(widthMeasureSpec, max)
            }
        }.apply {
            background = rounded(C.panel, 28, C.border)
            elevation = dp(12).toFloat()
            clipToOutline = true
            isVerticalScrollBarEnabled = false
            addView(content)
        }
        val params = WindowManager.LayoutParams(
            screenWidth - dp(20), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(44)
            windowAnimations = android.R.style.Animation_Toast
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
            typeface = Typeface.DEFAULT_BOLD
            elevation = dp(8).toFloat()
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
                    view.animate().scaleX(0.92f).scaleY(0.92f).setDuration(90).start()
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
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                    if (event.action == MotionEvent.ACTION_UP) {
                        if (dragging) snapToEdge(view) else if (panel == null) openPanel() else closePanel()
                    }
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
        val age = System.currentTimeMillis() - state.alertAt
        val asked = state.alert == AlertLevel.DIRECT && age < 120_000
        val (color, label) = when {
            asked -> C.redStrong to "!"
            (state.alert == AlertLevel.MENTION || state.alert == AlertLevel.TOPIC) && age < 60_000 -> C.amber to "•"
            state.health != null -> C.amber to "?"
            state.status == Status.LISTENING -> C.green to ""
            else -> C.idle to "CC"
        }
        view.text = label
        view.textSize = if (label == "CC") 14f else 26f
        view.setTextColor(if (color == C.green || color == C.amber) C.onAccent else Color.WHITE)
        // Outer soft halo + solid core with a thin light ring.
        val halo = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.argb(70, Color.red(color), Color.green(color), Color.blue(color)))
        }
        val core = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setStroke(dp(2), Color.argb(150, 255, 255, 255))
        }
        view.background = LayerDrawable(arrayOf(halo, core)).apply { setLayerInset(1, dp(5), dp(5), dp(5), dp(5)) }
        setPulsing(view, asked)
    }

    private fun setPulsing(view: View, on: Boolean) {
        if (on && pulse == null) {
            pulse = ObjectAnimator.ofPropertyValuesHolder(
                view,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.14f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.14f),
            ).apply {
                duration = 650
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                start()
            }
        } else if (!on && pulse != null) {
            pulse?.cancel()
            pulse = null
            view.scaleX = 1f
            view.scaleY = 1f
        }
    }

    private fun snapToEdge(view: View) {
        val targetX = if (bubbleParams.x + bubbleSize / 2 < screenWidth / 2) dp(8) else screenWidth - bubbleSize - dp(8)
        ValueAnimator.ofInt(bubbleParams.x, targetX).apply {
            duration = 200
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
        pulse?.cancel()
        pulse = null
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
            liveView?.let { setLive(it, state) }
            return
        }
        content.removeAllViews()
        val now = System.currentTimeMillis()

        // Header: status pill + close
        val header = row()
        val (statusColor, statusText) = statusOf(state, now)
        header.addView(statusPill(statusText, statusColor), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(closeButton())
        content.addView(header)

        state.health?.let { content.addView(note("⚠  $it", C.amber).top(10)) }

        // Answer
        val answer = state.answer
        if (answer != null && (now - answer.createdAt < 5 * 60_000 || state.answerLoading)) {
            val card = card(C.redDeep, C.red)
            card.addView(eyebrow("ÎNTREBARE PENTRU TINE", C.red))
            card.addView(text(answer.question, 19f, C.text, bold = true).top(6))
            answer.context.forEach { card.addView(text("·  $it", 14f, C.muted).top(4)) }

            val say = card(C.greenDeep, C.green)
            say.addView(eyebrow("SPUNE", C.green))
            when {
                answer.answer.isNotBlank() -> {
                    say.addView(text(answer.answer, 23f, C.text, bold = true).top(6))
                    if (!answer.confident) say.addView(text("Răspuns prudent: nu am date sigure despre asta.", 13f, C.muted).top(8))
                }
                state.answerLoading -> say.addView(text("Pregătesc răspunsul…", 18f, C.soft).top(6))
                state.answerError != null -> say.addView(text(state.answerError, 15f, C.red).top(6))
            }
            card.addView(say.top(14))
            card.addView(button("Am răspuns", C.red, filled = false) { engine.dismissAnswer() }.top(12))
            content.addView(card.top(14))
        } else {
            state.answerError?.let { content.addView(note(it, C.red).top(10)) }
        }

        // Catch-up
        val catchUp = state.catchUp
        if (catchUp != null && catchUp.topic.isNotBlank()) {
            val card = card(C.card, C.border)
            val head = row()
            head.addView(eyebrow("ACUM SE DISCUTĂ", C.blue), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            head.addView(text(updatedText(catchUp.updatedAt, now, state.catchUpLoading), 12f, C.faint))
            card.addView(head)
            card.addView(text(catchUp.topic, 19f, C.text, bold = true).top(6))
            catchUp.points.forEach { card.addView(text("•  $it", 16f, C.soft).top(5)) }
            if (catchUp.forMe.isNotBlank()) {
                val forMe = card(C.amberDeep, C.amber)
                forMe.addView(eyebrow("DE LA TINE SE AȘTEAPTĂ", C.amber))
                forMe.addView(text(catchUp.forMe, 17f, Color.rgb(253, 231, 168), bold = true).top(4))
                card.addView(forMe.top(12))
            }
            content.addView(card.top(12))
        } else if (state.lines.isNotEmpty()) {
            val card = card(C.card, C.border)
            card.addView(eyebrow("ULTIMELE REPLICI", C.blue))
            state.lines.takeLast(4).forEach { line ->
                card.addView(text(line.speaker ?: "Participant", 12f, C.blue, bold = true).top(8))
                card.addView(text(line.text, 15f, C.soft))
            }
            if (state.catchUpLoading) card.addView(text("Pregătesc rezumatul…", 13f, C.muted).top(8))
            content.addView(card.top(12))
        }
        state.catchUpError?.let { content.addView(note(it, C.red).top(8)) }

        val live = text("", 14f, C.muted).apply { setTypeface(typeface, Typeface.ITALIC) }
        setLive(live, state)
        liveView = live
        content.addView(live.top(10))

        val buttons = row()
        buttons.addView(button("Ce am pierdut?", C.green, filled = true) { engine.requestCatchUp() }, weighted().apply { marginEnd = dp(6) })
        buttons.addView(button("Ce să răspund?", C.blue, filled = false) { engine.requestAnswer() }, weighted().apply { marginStart = dp(6) })
        content.addView(buttons.top(12))
        if (prefs.diagnostics) content.addView(button("Captură diagnostic", C.muted, filled = false) { onDump() }.top(8))
    }

    private fun statusOf(state: UiState, now: Long): Pair<Int, String> = when {
        state.status == Status.LISTENING && !state.meetingVisible -> C.amber to "${state.meetingApp ?: "Ședința"} nu e pe ecran"
        state.status == Status.LISTENING -> {
            val minutes = state.startedAt?.let { (now - it) / 60_000 } ?: 0
            C.green to "Ascult · ${state.meetingApp ?: ""} · $minutes min"
        }
        state.status == Status.WAITING_CAPTIONS -> C.amber to "Pornește subtitrările (CC)"
        else -> C.idle to "Nicio ședință"
    }

    private fun setLive(view: TextView, state: UiState) {
        val text = if (state.live.isBlank()) "" else "… " + (state.liveSpeaker?.let { "$it: " } ?: "") + state.live.takeLast(200)
        view.text = text
        view.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun updatedText(updatedAt: Long, now: Long, loading: Boolean): String {
        if (loading) return "se actualizează…"
        val seconds = (now - updatedAt) / 1000
        return if (seconds < 60) "acum $seconds s" else "acum ${seconds / 60} min"
    }

    // ---------------------------------------------------------------- view helpers

    private fun rounded(color: Int, radiusDp: Int, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
        if (stroke != null) setStroke(dp(1), stroke)
    }

    private fun row() = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun weighted() = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

    private fun card(background: Int, accent: Int) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(16))
        val border = if (accent == C.border) C.border else Color.argb(90, Color.red(accent), Color.green(accent), Color.blue(accent))
        this.background = rounded(background, 20, border)
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(context).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        setLineSpacing(dp(2).toFloat(), 1f)
    }

    private fun eyebrow(value: String, color: Int) = text(value, 11.5f, color, bold = true).apply { letterSpacing = 0.1f }

    private fun note(value: String, color: Int) = text(value, 14f, color)

    private fun statusPill(value: String, color: Int): View {
        val pill = row().apply {
            setPadding(dp(12), dp(7), dp(14), dp(7))
            background = rounded(Color.argb(40, Color.red(color), Color.green(color), Color.blue(color)), 50)
        }
        val dot = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
            }
        }
        pill.addView(dot, LinearLayout.LayoutParams(dp(8), dp(8)).apply { marginEnd = dp(8) })
        pill.addView(text(value, 13f, color, bold = true))
        return LinearLayout(context).apply { addView(pill) } // keep the pill content-sized inside the weighted slot
    }

    private fun closeButton() = TextView(context).apply {
        text = "✕"
        textSize = 15f
        gravity = Gravity.CENTER
        setTextColor(C.soft)
        background = RippleDrawable(ColorStateList.valueOf(Color.argb(60, 255, 255, 255)), GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(C.card)
            setStroke(dp(1), C.border)
        }, null)
        layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
        setOnClickListener { closePanel() }
    }

    private fun button(value: String, accent: Int, filled: Boolean, onClick: () -> Unit) = TextView(context).apply {
        text = value
        textSize = 15.5f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        minHeight = dp(50)
        setPadding(dp(12), dp(12), dp(12), dp(12))
        val base = if (filled) {
            rounded(accent, 16)
        } else {
            rounded(
                Color.argb(36, Color.red(accent), Color.green(accent), Color.blue(accent)), 16,
                Color.argb(90, Color.red(accent), Color.green(accent), Color.blue(accent)),
            )
        }
        setTextColor(if (filled) C.onAccent else accent)
        background = RippleDrawable(ColorStateList.valueOf(Color.argb(70, 255, 255, 255)), base, null)
        isClickable = true
        setOnClickListener { onClick() }
    }

    private fun <T : View> T.top(topDp: Int): T = apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(topDp)
        }
    }
}
