package com.meeting.copilot

import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import android.util.DisplayMetrics
import android.util.Log
import android.view.*
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

class FloatingBubbleService : Service(), TextToSpeech.OnInitListener {

    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var cardView: View? = null
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var screenWidth = 1080

    private var backendUrl = "http://10.0.2.2:8000"
    private var targetName = "User"
    private var activeSessionId = "default-session"

    // Tactical responses cache
    private var currentAgree = "Sunt complet de acord cu propunerea și putem merge mai departe."
    private var currentStall = "Vă rog să mă scuzați, a durat o secundă să găsesc butonul de unmute. Sunt aliniată cu ideea, trimit confirmarea detaliată pe mail imediat după ședință."
    private var currentCaution = "Avem nevoie de cifrele finale de la financiar înainte să fixăm o decizie fermă; revin cu confirmarea după ce verific datele."
    private var currentSelectedText = currentAgree
    private var currentLiveQuestion = "Așteptare conversație din ședință..."

    private var webSocket: WebSocket? = null
    private val okHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    companion object {
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "MeetingCopilotOverlayChannel"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            backendUrl = it.getStringExtra("SERVER_URL") ?: backendUrl
            targetName = it.getStringExtra("TARGET_NAME") ?: targetName
        }
        initWebSocketConnection()
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        tts = TextToSpeech(this, this)
        measureScreen()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Settings.canDrawOverlays(this)) {
                initBubbleOverlay()
            } else {
                Log.w("FloatingBubbleService", "Overlay permission not granted; skipping initBubbleOverlay")
            }
        } else {
            initBubbleOverlay()
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("ro", "RO"))
            isTtsReady = (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED)
        }
    }

    private fun measureScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            screenWidth = metrics.bounds.width()
        } else {
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getMetrics(dm)
            screenWidth = dm.widthPixels
        }
    }

    private fun startAsForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Meeting Copilot Overlay",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Bula Copilot este activă peste Zoom")
            .setContentText("Atinge bula pentru răspunsul AI instant în ședință.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun initBubbleOverlay() {
        runCatching {
            val layoutInflater = LayoutInflater.from(this)
            bubbleView = layoutInflater.inflate(R.layout.floating_bubble_layout, null)

            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 20
                y = 300
            }

            bubbleView?.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        if (bubbleView?.isAttachedToWindow == true) {
                            runCatching { windowManager.updateViewLayout(bubbleView, params) }
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        val deltaX = Math.abs(event.rawX - initialTouchX)
                        val deltaY = Math.abs(event.rawY - initialTouchY)
                        if (deltaX < 15 && deltaY < 15) {
                            // Click detected: Toggle answer card
                            toggleAnswerCard()
                        } else {
                            // Drag released: Smooth snap to closest edge (left or right)
                            snapBubbleToEdge(params)
                        }
                        true
                    }
                    else -> false
                }
            }

            windowManager.addView(bubbleView, params)
        }.onFailure { e ->
            Log.e("FloatingBubbleService", "Error adding floating bubble to WindowManager", e)
        }
    }

    private fun snapBubbleToEdge(params: WindowManager.LayoutParams) {
        val targetX = if (params.x < screenWidth / 2) 20 else screenWidth - 160
        val animator = ValueAnimator.ofInt(params.x, targetX)
        animator.duration = 200
        animator.addUpdateListener { animation ->
            params.x = animation.animatedValue as Int
            if (bubbleView?.isAttachedToWindow == true) {
                runCatching { windowManager.updateViewLayout(bubbleView, params) }
            }
        }
        animator.start()
    }

    private fun toggleAnswerCard() {
        if (cardView != null) {
            removeAnswerCard()
        } else {
            showAnswerCard()
        }
    }

    private fun showAnswerCard() {
        if (cardView != null) return

        runCatching {
            val layoutInflater = LayoutInflater.from(this)
            cardView = layoutInflater.inflate(R.layout.floating_answer_card, null)

            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val cardParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
            }

            // Setup Card UI Elements
            val btnClose = cardView?.findViewById<TextView>(R.id.btnCloseCard)
            val btnAgree = cardView?.findViewById<Button>(R.id.btnTacticalAgree)
            val btnStall = cardView?.findViewById<Button>(R.id.btnTacticalStall)
            val btnCaution = cardView?.findViewById<Button>(R.id.btnTacticalCaution)
            val btnCopy = cardView?.findViewById<Button>(R.id.btnCopyResponse)
            val btnSpeak = cardView?.findViewById<Button>(R.id.btnSpeakResponse)
            val btnDone = cardView?.findViewById<Button>(R.id.btnDone)
            val txtSpoken = cardView?.findViewById<TextView>(R.id.txtSpokenResponse)
            val txtCaptions = cardView?.findViewById<TextView>(R.id.txtLiveCaptions)

            txtCaptions?.text = "\"$currentLiveQuestion\""
            txtSpoken?.text = "\"$currentSelectedText\""

            btnAgree?.setOnClickListener {
                currentSelectedText = currentAgree
                txtSpoken?.text = "\"$currentAgree\""
                highlightTacticalButton(btnAgree, btnStall, btnCaution)
            }

            btnStall?.setOnClickListener {
                currentSelectedText = currentStall
                txtSpoken?.text = "\"$currentStall\""
                highlightTacticalButton(btnStall, btnAgree, btnCaution)
            }

            btnCaution?.setOnClickListener {
                currentSelectedText = currentCaution
                txtSpoken?.text = "\"$currentCaution\""
                highlightTacticalButton(btnCaution, btnAgree, btnStall)
            }

            btnCopy?.setOnClickListener {
                runCatching {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Raspuns Copilot", currentSelectedText)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(this, "Copiat în memorie!", Toast.LENGTH_SHORT).show()
                }
            }

            btnSpeak?.setOnClickListener {
                runCatching {
                    if (isTtsReady) {
                        tts?.speak(currentSelectedText, TextToSpeech.QUEUE_FLUSH, null, "CopilotTTS")
                    }
                }
            }

            btnClose?.setOnClickListener { removeAnswerCard() }
            btnDone?.setOnClickListener { removeAnswerCard() }

            // Reset bubble alert visual state once opened
            resetBubbleToIdle()

            windowManager.addView(cardView, cardParams)
        }.onFailure { e ->
            Log.e("FloatingBubbleService", "Error showing answer card", e)
            cardView = null
        }
    }

    private fun highlightTacticalButton(selected: Button?, other1: Button?, other2: Button?) {
        selected?.alpha = 1.0f
        other1?.alpha = 0.5f
        other2?.alpha = 0.5f
    }

    private fun refreshCardUI() {
        cardView?.let { card ->
            val txtSpoken = card.findViewById<TextView>(R.id.txtSpokenResponse)
            val txtCaptions = card.findViewById<TextView>(R.id.txtLiveCaptions)
            txtCaptions?.text = "\"$currentLiveQuestion\""
            txtSpoken?.text = "\"$currentSelectedText\""
        }
    }

    private fun removeAnswerCard() {
        cardView?.let { view ->
            runCatching {
                if (view.isAttachedToWindow) {
                    windowManager.removeView(view)
                }
            }
            cardView = null
        }
    }

    private fun initWebSocketConnection() {
        val wsUrl = backendUrl.replace("http://", "ws://").replace("https://", "wss://") + "/ws/$activeSessionId"
        val request = Request.Builder().url(wsUrl).build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching {
                    val obj = JSONObject(text)
                    when (obj.optString("type")) {
                        "TARGET_ALERT" -> {
                            val alertText = obj.optString("text")
                            val urgency = obj.optInt("urgency", 7)
                            currentLiveQuestion = alertText
                            triggerBubbleAlert(alertText, urgency)
                        }
                        "PRECOMPUTED_ANSWERS", "QUICK_ANSWER" -> {
                            val tactical = obj.optJSONObject("tactical_responses")
                            if (tactical != null) {
                                currentAgree = tactical.optJSONObject("agree")?.optString("spoken_response") ?: currentAgree
                                currentStall = tactical.optJSONObject("stall")?.optString("spoken_response") ?: currentStall
                                currentCaution = tactical.optJSONObject("caution")?.optString("spoken_response") ?: currentCaution
                                currentSelectedText = currentAgree
                            }
                            currentLiveQuestion = obj.optString("question_detected", currentLiveQuestion)

                            // Show the answer instantly on screen!
                            scope.launch(Dispatchers.Main) {
                                if (cardView == null) {
                                    showAnswerCard()
                                } else {
                                    refreshCardUI()
                                }
                            }
                        }
                    }
                }.onFailure { e ->
                    Log.w("FloatingBubbleService", "Error parsing WebSocket message", e)
                }
            }
        })
    }

    private fun triggerBubbleAlert(questionText: String, urgency: Int) {
        scope.launch(Dispatchers.Main) {
            runCatching {
                val bgView = bubbleView?.findViewById<View>(R.id.bubbleBackground)
                bgView?.setBackgroundResource(R.drawable.circle_bubble_alert)
                vibrateAlert()

                // Say to user question (voice whisper in ear / speaker)
                if (urgency >= 7 && questionText.isNotBlank() && isTtsReady) {
                    tts?.speak("Întrebare: $questionText", TextToSpeech.QUEUE_FLUSH, null, "QuestionTTS")
                }

                // Show instantly the answer if card not already visible
                if (cardView == null && urgency >= 7) {
                    showAnswerCard()
                } else {
                    refreshCardUI()
                }
            }
        }
    }

    private fun resetBubbleToIdle() {
        runCatching {
            val bgView = bubbleView?.findViewById<View>(R.id.bubbleBackground)
            bgView?.setBackgroundResource(R.drawable.circle_bubble)
        }
    }

    private fun vibrateAlert() {
        runCatching {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            // Urgent 3-pulse vibration pattern: pulse 250ms, pause 150ms, pulse 250ms, pause 150ms, attention 450ms
            val pattern = longArrayOf(0, 250, 150, 250, 150, 450)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, -1)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { scope.cancel() }
        runCatching { webSocket?.close(1000, "Service stopped") }
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        bubbleView?.let { view ->
            runCatching {
                if (view.isAttachedToWindow) {
                    windowManager.removeView(view)
                }
            }
            bubbleView = null
        }
        removeAnswerCard()
    }
}
