package com.meeting.copilot.caption

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.meeting.copilot.data.Prefs
import com.meeting.copilot.engine.AlertLevel
import com.meeting.copilot.engine.MeetingEngine
import com.meeting.copilot.engine.Status
import com.meeting.copilot.engine.UiState
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A Dynamic-Island-style overlay wrapped around the front camera.
 *
 * Compact: a black pill with sound bars on the left (they move while people talk) and the
 * meeting timer on the right, the camera hole sitting between them. When she is mentioned it
 * stretches into a wider card for a few seconds; when she is asked something, or taps it, it
 * grows down into the full panel with the suggested answer and "what did I miss".
 */
class Overlay(
    private val context: Context,
    private val engine: MeetingEngine,
    private val prefs: Prefs,
    private val onDump: () -> Unit,
) {
    private object C {
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
        val idle = Color.rgb(100, 116, 139)
        val onAccent = Color.rgb(6, 17, 12)
        val islandEdge = Color.argb(28, 255, 255, 255)
    }

    private enum class Mode { COMPACT, ALERT, EXPANDED }

    companion object {
        /** True after she long-pressed the island; cleared when the meeting ends or from the app. */
        val hidden = kotlinx.coroutines.flow.MutableStateFlow(false)
        /** The app itself is open: it shows everything, so the island steps aside. */
        val appVisible = kotlinx.coroutines.flow.MutableStateFlow(false)

        /** How long a mention or topic keeps the island stretched before it shrinks back. */
        private const val ALERT_PEEK_MS = 6_000L
    }

    private val wm = context.getSystemService(WindowManager::class.java)
    private val density = context.resources.displayMetrics.density
    private fun dp(value: Int) = (value * density).toInt()
    private val screenWidth get() = context.resources.displayMetrics.widthPixels
    private val screenHeight get() = context.resources.displayMetrics.heightPixels
    private val handler = Handler(Looper.getMainLooper())

    // Geometry, measured from the camera cut-out when the island is created.
    private var geometryWidth = 0
    private var camCenterX = 0
    private var camGap = 0
    private var compactW = 0
    private var compactH = 0
    private var islandTop = 0
    private var contentTop = 0

    private var island: FrameLayout? = null
    private var islandBg: GradientDrawable? = null
    private var compactLayer: View? = null
    private var waves: WaveView? = null
    private var compactDot: View? = null
    private var compactLabel: TextView? = null
    private var alertLayer: View? = null
    private var alertDot: View? = null
    private var alertTitle: TextView? = null
    private var alertBody: TextView? = null
    private var expandedLayer: View? = null
    private var panelContent: LinearLayout? = null
    private var liveView: TextView? = null
    private var lastPanelState: UiState? = null

    private var mode = Mode.COMPACT
    private var userExpanded = false
    private var morph: ValueAnimator? = null
    private var pulse: ObjectAnimator? = null
    private var keepScreenOn = false
    private var lastState: UiState? = null

    private val params = WindowManager.LayoutParams(
        0, 0,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= 30) {
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else {
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    /** Keeps the timer running and lets a mention peek shrink back even when nothing else changes. */
    private val ticker = object : Runnable {
        override fun run() {
            lastState?.let { refresh(it) }
            handler.postDelayed(this, 1_000)
        }
    }

    fun render(state: UiState) {
        lastState = state
        val inMeeting = state.meetingVisible || state.status == Status.LISTENING
        if (!inMeeting && hidden.value) hidden.value = false // meeting over: next one shows the island again
        val show = prefs.active && prefs.showBubble && inMeeting && !hidden.value && !appVisible.value
        if (!show) {
            removeIsland()
            return
        }
        if (island != null && geometryWidth != screenWidth) removeIsland() // rotated: the camera moved
        if (island == null && !createIsland()) return
        refresh(state)
    }

    /** Grow into the full panel (tap, or she was asked something). */
    fun openPanel() {
        val root = island ?: return
        userExpanded = true
        renderPanel(lastState ?: engine.state.value)
        root.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        morphTo(Mode.EXPANDED)
    }

    /** Shrink back into the pill. */
    fun closePanel() {
        userExpanded = false
        if (island == null) return
        morphTo(targetMode(lastState ?: engine.state.value, System.currentTimeMillis()))
    }

    fun destroy() {
        removeIsland()
    }

    private fun refresh(state: UiState) {
        if (island == null) return
        val now = System.currentTimeMillis()
        updateCompact(state, now)
        updateAlert(state)
        if (userExpanded) renderPanel(state)
        val target = targetMode(state, now)
        if (target != mode) morphTo(target)
        setKeepScreenOn(state.status == Status.LISTENING)
    }

    private fun targetMode(state: UiState, now: Long): Mode {
        if (userExpanded) return Mode.EXPANDED
        val peek = state.alert == AlertLevel.MENTION || state.alert == AlertLevel.TOPIC || state.alert == AlertLevel.QUESTION
        return if (peek && now - state.alertAt < ALERT_PEEK_MS) Mode.ALERT else Mode.COMPACT
    }

    // ---------------------------------------------------------------- geometry

    private fun computeGeometry() {
        geometryWidth = screenWidth
        // A slim pill, like the real Dynamic Island. Phones report the camera cut-out as a box that
        // often reaches the top of the screen, so only its position is used, not its height.
        compactH = dp(36)
        val camera = cameraRect()
        if (camera != null) {
            camCenterX = camera.centerX()
            // The round hole sits at the bottom of that box.
            val holeCenterY = camera.bottom - minOf(camera.width(), camera.height()) / 2
            islandTop = (holeCenterY - compactH / 2).coerceAtLeast(dp(2))
            camGap = minOf(camera.width(), dp(40)) + dp(12)
        } else {
            camCenterX = screenWidth / 2
            islandTop = dp(6)
            camGap = dp(12)
        }
        contentTop = compactH + dp(6)
        compactW = dp(28) + 2 * dp(58) + camGap
    }

    /** The front-camera hole at the top of the screen, if the phone has one in the middle. */
    private fun cameraRect(): Rect? {
        val cutout = try {
            if (Build.VERSION.SDK_INT >= 30) {
                wm.currentWindowMetrics.windowInsets.displayCutout
            } else {
                @Suppress("DEPRECATION")
                wm.defaultDisplay.cutout
            }
        } catch (_: Exception) {
            null
        } ?: return null
        val rect = cutout.boundingRectTop
        // No hole, or a wide notch: centre at the top instead.
        if (rect.isEmpty || rect.width() > screenWidth / 3) return null
        return Rect(rect)
    }

    private fun widthFor(mode: Mode) = when (mode) {
        Mode.COMPACT -> compactW
        Mode.ALERT -> screenWidth - dp(20)
        Mode.EXPANDED -> screenWidth - dp(14)
    }

    private fun clampX(width: Int) = (camCenterX - width / 2).coerceIn(dp(4), maxOf(dp(4), screenWidth - width - dp(4)))

    // ---------------------------------------------------------------- building

    private fun createIsland(): Boolean {
        computeGeometry()
        val background = GradientDrawable().apply {
            setColor(Color.BLACK)
            cornerRadius = compactH / 2f
            setStroke(dp(1), C.islandEdge)
        }
        val root = FrameLayout(context).apply {
            this.background = background
            clipToOutline = true
            elevation = dp(8).toFloat()
        }

        // Compact: sound bars | camera | timer
        val wave = WaveView(context, C.green)
        val dot = View(context).apply { this.background = oval(C.idle) }
        val label = text("", 14.5f, Color.WHITE, bold = true).apply {
            // Fixed size like the real island: a large system font would push it over the status icons.
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14.5f)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        }
        val left = FrameLayout(context).apply {
            addView(wave, FrameLayout.LayoutParams(dp(26), dp(16), Gravity.CENTER_VERTICAL or Gravity.START))
            addView(dot, FrameLayout.LayoutParams(dp(10), dp(10), Gravity.CENTER_VERTICAL or Gravity.START).apply { marginStart = dp(6) })
        }
        val compact = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), 0, dp(14), 0)
            addView(left, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
            addView(View(context), LinearLayout.LayoutParams(camGap, 1))
            addView(label, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            setOnClickListener { openPanel() }
            setOnLongClickListener {
                hideIsland()
                true
            }
        }

        // Alert: who mentioned her and what was said
        val aDot = View(context).apply { this.background = oval(C.amber) }
        val aTitle = text("", 16f, Color.WHITE, bold = true).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        val aBody = text("", 15f, C.soft).apply {
            maxLines = 3
            ellipsize = TextUtils.TruncateAt.END
        }
        val alert = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), contentTop, dp(20), dp(16))
            addView(row().apply {
                addView(aDot, LinearLayout.LayoutParams(dp(10), dp(10)).apply { marginEnd = dp(10) })
                addView(aTitle)
            })
            addView(aBody.top(6))
            addView(text("Atinge pentru detalii", 12f, C.faint).top(8))
            visibility = View.GONE
            setOnClickListener { openPanel() }
            setOnLongClickListener {
                hideIsland()
                true
            }
        }

        // Expanded: the full panel
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), dp(16))
        }
        val scroll = object : ScrollView(context) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val max = MeasureSpec.makeMeasureSpec((screenHeight * 0.66).toInt(), MeasureSpec.AT_MOST)
                super.onMeasure(widthMeasureSpec, max)
            }
        }.apply {
            isVerticalScrollBarEnabled = false
            addView(content)
        }
        val expanded = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, contentTop, 0, 0)
            addView(scroll)
            visibility = View.GONE
        }

        root.addView(compact, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        root.addView(alert, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        root.addView(expanded, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        // A tap anywhere else shrinks the open island back, like on iPhone.
        root.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_OUTSIDE && mode == Mode.EXPANDED) {
                closePanel()
                true
            } else {
                false
            }
        }

        params.width = compactW
        params.height = compactH
        params.x = clampX(compactW)
        params.y = islandTop
        return try {
            wm.addView(root, params)
            island = root
            islandBg = background
            compactLayer = compact
            waves = wave
            compactDot = dot
            compactLabel = label
            alertLayer = alert
            alertDot = aDot
            alertTitle = aTitle
            alertBody = aBody
            expandedLayer = expanded
            panelContent = content
            mode = Mode.COMPACT
            handler.removeCallbacks(ticker)
            handler.postDelayed(ticker, 1_000)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun hideIsland() {
        hidden.value = true
        removeIsland()
        Toast.makeText(context, "Ascuns până la finalul ședinței. Îl poți reafișa din aplicație.", Toast.LENGTH_LONG).show()
    }

    private fun removeIsland() {
        val root = island ?: return
        handler.removeCallbacks(ticker)
        island = null
        morph?.cancel()
        morph = null
        pulse?.cancel()
        pulse = null
        try {
            wm.removeView(root)
        } catch (_: Exception) {
        }
        islandBg = null
        compactLayer = null
        waves = null
        compactDot = null
        compactLabel = null
        alertLayer = null
        alertDot = null
        alertTitle = null
        alertBody = null
        expandedLayer = null
        panelContent = null
        liveView = null
        lastPanelState = null
        mode = Mode.COMPACT
        userExpanded = false
        keepScreenOn = false
        params.flags = params.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON.inv()
    }

    // ---------------------------------------------------------------- content

    private fun updateCompact(state: UiState, now: Long) {
        val age = now - state.alertAt
        val asked = state.alert == AlertLevel.DIRECT && age < 120_000
        val talking = state.live.isNotBlank() || (state.lines.lastOrNull()?.let { now - it.ts < 4_000 } ?: false)
        val (dotColor, label) = when {
            asked -> C.redStrong to "Întrebare"
            state.alert == AlertLevel.MENTION && age < 60_000 -> C.amber to "Menționată"
            state.alert == AlertLevel.QUESTION && age < 60_000 -> C.blue to "Întrebare"
            state.status == Status.LISTENING && !state.meetingVisible -> C.amber to "Ascuns"
            state.status == Status.LISTENING && state.health != null -> C.amber to "Fără CC"
            state.status == Status.LISTENING -> null to elapsed(state.startedAt, now)
            else -> C.idle to "CC…"
        }
        waves?.apply {
            visibility = if (dotColor == null) View.VISIBLE else View.GONE
            active = talking
        }
        compactDot?.apply {
            visibility = if (dotColor == null) View.GONE else View.VISIBLE
            if (dotColor != null) (background as? GradientDrawable)?.setColor(dotColor)
            setPulsing(this, asked)
        }
        compactLabel?.apply {
            text = label
            setTextColor(if (asked) C.red else Color.WHITE)
            // Both sides keep the same width so the pill stays centred on the camera.
            val side = (paint.measureText(label).roundToInt() + dp(4)).coerceIn(dp(58), dp(84))
            fitCompactWidth(dp(28) + 2 * side + camGap)
        }
        islandBg?.setStroke(dp(1), if (asked) Color.argb(160, 239, 68, 68) else C.islandEdge)
    }

    /** Widens or narrows the compact pill smoothly when its label needs more or less room. */
    private fun fitCompactWidth(width: Int) {
        if (width == compactW) return
        compactW = width
        if (mode != Mode.COMPACT || morph != null) return
        val start = params.width
        morph = ValueAnimator.ofInt(start, width).apply {
            duration = 260
            interpolator = DecelerateInterpolator(1.5f)
            addUpdateListener { setSize(it.animatedValue as Int, compactH) }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (morph === animation) morph = null
                }
            })
            start()
        }
    }

    private fun updateAlert(state: UiState) {
        val line = state.alertText ?: return
        val speaker = line.substringBefore(": ", "").takeIf { it.isNotBlank() && it.length < 40 }
        val (color, title) = when (state.alert) {
            AlertLevel.DIRECT -> C.redStrong to "${speaker ?: "Cineva"} te întreabă"
            AlertLevel.MENTION -> C.amber to "${speaker ?: "Cineva"} te-a menționat"
            AlertLevel.QUESTION -> C.blue to "${speaker ?: "Cineva"} întreabă"
            AlertLevel.TOPIC -> C.blue to "Subiect urmărit"
            else -> C.amber to "Atenție"
        }
        (alertDot?.background as? GradientDrawable)?.setColor(color)
        alertTitle?.text = title
        alertBody?.text = if (speaker != null) line.substringAfter(": ") else line
    }

    private fun elapsed(startedAt: Long?, now: Long): String {
        val seconds = ((now - (startedAt ?: now)) / 1000).coerceAtLeast(0)
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    /** The red dot beats while a question waits for her. */
    private fun setPulsing(view: View, on: Boolean) {
        if (on && pulse == null) {
            pulse = ObjectAnimator.ofPropertyValuesHolder(
                view,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.5f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.5f),
                PropertyValuesHolder.ofFloat(View.ALPHA, 1f, 0.55f),
            ).apply {
                duration = 600
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                start()
            }
        } else if (!on && pulse != null) {
            pulse?.cancel()
            pulse = null
            view.scaleX = 1f
            view.scaleY = 1f
            view.alpha = 1f
        }
    }

    // ---------------------------------------------------------------- morphing

    /** Animates the island's size and corners from its current shape to [target]'s, with a soft spring. */
    private fun morphTo(target: Mode) {
        val root = island ?: return
        if (target == mode) return
        mode = target
        morph?.cancel()
        val layer = when (target) {
            Mode.COMPACT -> compactLayer
            Mode.ALERT -> alertLayer
            Mode.EXPANDED -> expandedLayer
        } ?: return
        listOf(compactLayer, alertLayer, expandedLayer).forEach { if (it != null && it !== layer) it.visibility = View.GONE }
        layer.visibility = View.VISIBLE
        layer.alpha = 0f

        val startW = params.width
        val startH = if (root.height > 0) root.height else compactH
        val startR = islandBg?.cornerRadius ?: (compactH / 2f)
        val endW = widthFor(target)
        val endH = if (target == Mode.COMPACT) compactH else measure(layer, endW)
        val endR = when (target) {
            Mode.COMPACT -> compactH / 2f
            Mode.ALERT -> dp(30).toFloat()
            Mode.EXPANDED -> dp(34).toFloat()
        }
        if (target == Mode.COMPACT) userExpanded = false

        morph = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = if (target == Mode.COMPACT) 320L else 460L
            interpolator = if (target == Mode.COMPACT) DecelerateInterpolator(1.8f) else OvershootInterpolator(0.75f)
            addUpdateListener {
                val f = it.animatedValue as Float
                setSize(lerp(startW, endW, f), lerp(startH, endH, f))
                islandBg?.cornerRadius = startR + (endR - startR) * f.coerceIn(0f, 1f)
                layer.alpha = ((f - 0.3f) / 0.7f).coerceIn(0f, 1f)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (morph !== animation) return
                    morph = null
                    layer.alpha = 1f
                    // Open shapes follow their content (answer arriving, catch-up updating).
                    if (target != Mode.COMPACT && island != null) {
                        params.height = WindowManager.LayoutParams.WRAP_CONTENT
                        update(root)
                    }
                }
            })
            start()
        }
    }

    private fun measure(layer: View, width: Int): Int {
        layer.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec((screenHeight * 0.85).toInt(), View.MeasureSpec.AT_MOST),
        )
        return layer.measuredHeight.coerceAtLeast(compactH)
    }

    private fun lerp(from: Int, to: Int, f: Float) = (from + (to - from) * f).roundToInt().coerceAtLeast(dp(24))

    private fun setSize(width: Int, height: Int) {
        val root = island ?: return
        params.width = width
        params.height = height
        params.x = clampX(width)
        update(root)
    }

    private fun setKeepScreenOn(on: Boolean) {
        if (on == keepScreenOn) return
        keepScreenOn = on
        val root = island ?: return
        params.flags = if (on) {
            params.flags or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        } else {
            params.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON.inv()
        }
        update(root)
    }

    private fun update(view: View) {
        try {
            if (view.isAttachedToWindow) wm.updateViewLayout(view, params)
        } catch (_: Exception) {
        }
    }

    // ---------------------------------------------------------------- panel content

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

        // Answer: kept small so the shared screen / presentation stays visible below it.
        val answer = state.answer
        if (answer != null && (now - answer.createdAt < 5 * 60_000 || state.answerLoading)) {
            val card = card(C.greenDeep, C.green)
            card.addView(text(answer.question, 13f, C.muted).apply {
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            when {
                answer.answer.isNotBlank() -> {
                    card.addView(text(answer.answer, 18f, C.text, bold = true).top(4))
                    if (!answer.confident) card.addView(text("Răspuns prudent", 12f, C.muted).top(4))
                }
                state.answerLoading -> card.addView(text("Pregătesc răspunsul…", 16f, C.soft).top(4))
                state.answerError != null -> card.addView(text(state.answerError, 14f, C.red).top(4))
            }
            content.addView(card.top(8))
            val buttons = row()
            buttons.addView(button("Am răspuns", C.green, filled = false) { engine.dismissAnswer() }, weighted().apply { marginEnd = dp(6) })
            buttons.addView(button("Ultima întrebare", C.blue, filled = false) { engine.requestAnswer() }, weighted().apply { marginStart = dp(6) })
            content.addView(buttons.top(8))
            return
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
        state.status == Status.LISTENING -> C.green to "Ascult · ${state.meetingApp ?: ""} · ${elapsed(state.startedAt, now)}"
        state.status == Status.WAITING_CAPTIONS -> C.amber to "Pornesc subtitrările…"
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

    /** Sound bars that dance while someone is talking and settle when the meeting is quiet. */
    private class WaveView(context: Context, color: Int) : View(context) {
        var active = true
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        private val speeds = intArrayOf(1, 2, 1, 3)
        private val offsets = floatArrayOf(0f, 1.7f, 3.1f, 4.4f)
        private var phase = 0f
        private var level = 1f
        private val animator = ValueAnimator.ofFloat(0f, (2 * Math.PI).toFloat()).apply {
            duration = 1_400
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                phase = it.animatedValue as Float
                level += ((if (active) 1f else 0.18f) - level) * 0.08f
                invalidate()
            }
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            animator.start()
        }

        override fun onDetachedFromWindow() {
            animator.cancel()
            super.onDetachedFromWindow()
        }

        override fun onDraw(canvas: Canvas) {
            val bars = speeds.size
            val gap = width * 0.14f
            val barWidth = (width - gap * (bars - 1)) / bars
            for (i in 0 until bars) {
                val wave = (sin(phase * speeds[i] + offsets[i]) + 1f) / 2f
                val h = height * (0.22f + 0.78f * wave * level)
                val left = i * (barWidth + gap)
                val top = (height - h) / 2f
                canvas.drawRoundRect(left, top, left + barWidth, top + h, barWidth / 2, barWidth / 2, paint)
            }
        }
    }

    private fun oval(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
    }

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
        pill.addView(View(context).apply { background = oval(color) }, LinearLayout.LayoutParams(dp(8), dp(8)).apply { marginEnd = dp(8) })
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
