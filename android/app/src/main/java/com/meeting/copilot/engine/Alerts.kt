package com.meeting.copilot.engine

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.meeting.copilot.R
import com.meeting.copilot.data.Prefs
import com.meeting.copilot.ui.MainActivity
import java.util.Locale

enum class AlertLevel { NONE, TOPIC, MENTION, QUESTION, DIRECT, HEALTH }

/**
 * Gets her attention without her looking at the phone: distinct vibration per level and a
 * notification (which a Mi Band / Xiaomi watch mirrors to the wrist). Optionally reads the
 * question into the earbud, only when headphones are connected.
 */
class Alerts(private val context: Context, private val prefs: Prefs) : TextToSpeech.OnInitListener {

    companion object {
        /** v2: the first version had no sound; Android doesn't let an existing channel gain one. */
        const val CHANNEL_DIRECT = "direct_question_v2"
        private const val OLD_CHANNEL_DIRECT = "direct_question"
        private const val CHANNEL_SOFT = "mentions_topics"
        private const val CHANNEL_INFO = "meeting_info"
        private const val ID_ALERT = 10
        private const val ID_INFO = 11

        private val PATTERNS = mapOf(
            AlertLevel.DIRECT to longArrayOf(0, 450, 150, 450, 150, 700),
            AlertLevel.MENTION to longArrayOf(0, 250, 150, 250),
            AlertLevel.QUESTION to longArrayOf(0, 450, 150, 450, 150, 700),
            AlertLevel.TOPIC to longArrayOf(0, 120),
            AlertLevel.HEALTH to longArrayOf(0, 100, 120, 100, 120, 100),
        )
    }

    private var tts: TextToSpeech? = null
    private var ttsReady = false

    init {
        createChannels()
    }

    /** Returns whether the notification was posted (false when notifications are blocked). */
    fun fire(level: AlertLevel, title: String, text: String): Boolean {
        if (level == AlertLevel.NONE) return false
        val posted = notify(level, title, text)
        // The app always vibrates itself as well: during a call, on silent, or with HyperOS's
        // notification vibration off, the notification alone may arrive without any.
        if (prefs.vibrate) vibrate(level)
        if (isQuestion(level) && prefs.whisperQuestion) speakIfHeadset(text)
        return posted
    }

    private fun isQuestion(level: AlertLevel) = level == AlertLevel.DIRECT || level == AlertLevel.QUESTION

    fun info(title: String, text: String) {
        post(ID_INFO, CHANNEL_INFO, title, text, NotificationCompat.PRIORITY_DEFAULT)
    }

    private fun vibrate(level: AlertLevel) {
        val pattern = PATTERNS[level] ?: return
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            val effect = VibrationEffect.createWaveform(pattern, -1)
            // Marked so Android doesn't drop it: a question vibrates like an alarm (also on silent and
            // during the call, which is exactly when she needs it); the rest like a notification.
            val question = isQuestion(level)
            if (Build.VERSION.SDK_INT >= 33) {
                val usage = if (question) VibrationAttributes.USAGE_ALARM else VibrationAttributes.USAGE_NOTIFICATION
                vibrator.vibrate(effect, VibrationAttributes.createForUsage(usage))
            } else {
                val usage = if (question) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION_EVENT
                @Suppress("DEPRECATION")
                vibrator.vibrate(effect, AudioAttributes.Builder().setUsage(usage).build())
            }
        } catch (_: Exception) {
        }
    }

    /** True when the notification could be shown. */
    private fun notify(level: AlertLevel, title: String, text: String): Boolean {
        // Any question, to her or to everyone, uses the channel with sound.
        val channel = if (isQuestion(level)) CHANNEL_DIRECT else CHANNEL_SOFT
        val priority = if (isQuestion(level)) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT
        return post(ID_ALERT, channel, title, text, priority)
    }

    private fun post(id: Int, channel: String, title: String, text: String, priority: Int): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled() || manager.getNotificationChannel(channel)?.importance == NotificationManager.IMPORTANCE_NONE) {
            return false
        }
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(priority)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setSilent(channel != CHANNEL_DIRECT)
            .build()
        return try {
            NotificationManagerCompat.from(context).notify(id, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    private fun createChannels() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.deleteNotificationChannel(OLD_CHANNEL_DIRECT)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_DIRECT, "Întrebări pentru tine", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Când cineva te întreabă ceva în ședință: sunet scurt și vibrație (după modul sonerie/silențios)"
                enableVibration(true)
                vibrationPattern = PATTERNS[AlertLevel.DIRECT]
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_SOFT, "Mențiuni și subiecte", NotificationManager.IMPORTANCE_DEFAULT).apply {
                enableVibration(false)
                setSound(null, null)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_INFO, "Rezumate și stare", NotificationManager.IMPORTANCE_DEFAULT).apply {
                enableVibration(false)
                setSound(null, null)
            }
        )
    }

    // ---- Earbud voice ----

    private fun speakIfHeadset(text: String) {
        if (!headsetConnected()) return
        val engine = tts
        if (engine == null) {
            tts = TextToSpeech(context, this)
            pendingSpeech = text
            return
        }
        if (ttsReady) engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "question")
    }

    private var pendingSpeech: String? = null

    override fun onInit(status: Int) {
        val engine = tts ?: return
        ttsReady = status == TextToSpeech.SUCCESS &&
            engine.setLanguage(Locale("ro", "RO")) >= TextToSpeech.LANG_AVAILABLE
        if (ttsReady) pendingSpeech?.let { engine.speak(it, TextToSpeech.QUEUE_FLUSH, null, "question") }
        pendingSpeech = null
    }

    private fun headsetConnected(): Boolean {
        val audio = context.getSystemService(AudioManager::class.java) ?: return false
        val headsetTypes = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
        )
        return audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type in headsetTypes }
    }
}
