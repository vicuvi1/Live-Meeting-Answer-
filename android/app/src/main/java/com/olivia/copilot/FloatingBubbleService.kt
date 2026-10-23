package com.olivia.copilot

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.*
import android.widget.Button
import android.widget.TextView
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONObject

class FloatingBubbleService : Service() {

    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var cardView: View? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    private var activeSessionId = "default-session"
    private var backendUrl = "http://10.0.2.2:8000" // Default emulator/local IP

    companion object {
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "OliviaCopilotOverlayChannel"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        initBubbleOverlay()
    }

    private fun startAsForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Olivia Copilot Overlay",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Olivia Copilot este activ")
            .setContentText("Atinge bula plutitoare pentru a primi răspunsul AI în ședință.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun initBubbleOverlay() {
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
            x = 100
            y = 200
        }

        bubbleView?.setOnTouchListener { view, event ->
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
                    windowManager.updateViewLayout(bubbleView, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val deltaX = Math.abs(event.rawX - initialTouchX)
                    val deltaY = Math.abs(event.rawY - initialTouchY)
                    if (deltaX < 10 && deltaY < 10) {
                        // Considered a Click
                        showAnswerCard()
                    }
                    true
                }
                else -> false
            }
        }

        windowManager.addView(bubbleView, params)
    }

    private fun showAnswerCard() {
        if (cardView != null) return // Already shown

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
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        val btnClose = cardView?.findViewById<Button>(R.id.btnCloseCard)
        val btnQuickAnswer = cardView?.findViewById<Button>(R.id.btnQuickAnswer)
        val responseBox = cardView?.findViewById<View>(R.id.responseBox)
        val txtSpoken = cardView?.findViewById<TextView>(R.id.txtSpokenResponse)

        btnClose?.setOnClickListener {
            removeAnswerCard()
        }

        btnQuickAnswer?.setOnClickListener {
            btnQuickAnswer.text = "Se generează..."
            btnQuickAnswer.isEnabled = false

            scope.launch(Dispatchers.IO) {
                val answer = fetchQuickAnswer()
                withContext(Dispatchers.Main) {
                    btnQuickAnswer.text = "💡 Ce să răspund acum?"
                    btnQuickAnswer.isEnabled = true
                    responseBox?.visibility = View.VISIBLE
                    txtSpoken?.text = "\"$answer\""
                }
            }
        }

        windowManager.addView(cardView, cardParams)
    }

    private fun removeAnswerCard() {
        if (cardView != null) {
            windowManager.removeView(cardView)
            cardView = null
        }
    }

    private fun fetchQuickAnswer(): String {
        return try {
            val client = OkHttpClient()
            val json = JSONObject().apply {
                put("session_id", activeSessionId)
                put("target_name", "Olivia")
            }
            val body = RequestBody.create(
                MediaType.parse("application/json; charset=utf-8"),
                json.toString()
            )
            val request = Request.Builder()
                .url("$backendUrl/api/copilot/quick-answer")
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            val bodyStr = response.body()?.string() ?: ""
            val obj = JSONObject(bodyStr)
            obj.optString("spoken_response", "Sunt de acord cu planul stabilit și putem continua.")
        } catch (e: Exception) {
            "Sunt de acord cu decizia colegilor; voi reveni cu confirmarea scrisă pe mail imediat după ședință."
        }
    }

    private fun vibrateAlert() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(400)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        if (bubbleView != null) {
            windowManager.removeView(bubbleView)
            bubbleView = null
        }
        removeAnswerCard()
    }
}
