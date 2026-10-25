package com.meeting.copilot.util

import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.AudioManager
import android.os.Build
import android.util.Base64
import com.meeting.copilot.App
import com.meeting.copilot.caption.CaptionService
import com.meeting.copilot.engine.AlertLevel
import com.meeting.copilot.engine.Alerts
import com.meeting.copilot.data.Prefs
import com.meeting.copilot.ui.xiaomiStepsDone
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream

/**
 * "Verifică tot": checks, one by one, everything the app needs during a meeting, so a problem
 * shows up before the meeting instead of in the middle of it. Each result says in Romanian
 * what is wrong and offers the screen that fixes it.
 */
object SelfCheck {

    enum class Result { OK, WARN, FAIL }

    /** The screen that fixes a problem; the home screen maps it to a button. */
    enum class Fix(val label: String) {
        TURN_ON("Pornește"),
        ACCESSIBILITY("Deschide"),
        NOTIFICATIONS("Permite"),
        QUESTION_SOUND("Deschide"),
        BATTERY("Permite"),
        XIAOMI("Deschide"),
        SETTINGS("Setări"),
    }

    data class Check(val title: String, val result: Result, val detail: String, val fix: Fix? = null)

    /** Runs every check in order; [onProgress] gets the results so far after each one. */
    suspend fun run(context: Context, app: App, onProgress: (List<Check>) -> Unit): List<Check> {
        val results = mutableListOf<Check>()
        suspend fun add(check: suspend () -> Check?) {
            val result = try {
                check()
            } catch (e: Exception) {
                Check("Eroare la verificare", Result.WARN, e.message ?: e.javaClass.simpleName)
            } ?: return
            results.add(result)
            onProgress(results.toList())
        }

        add { activeCheck(app.prefs) }
        add { serviceCheck() }
        add { notificationsCheck(context) }
        add { questionSoundCheck(context, app.alerts) }
        add { batteryCheck(context) }
        add { if (Setup.isXiaomi) xiaomiCheck(app.prefs, context) else null }
        add { nameCheck(app.prefs) }
        add { aiCheck(app) }
        add { screenCheck() }
        add { updateCheck() }
        return results
    }

    private fun activeCheck(prefs: Prefs) = if (prefs.active) {
        Check("Asistentul e pornit", Result.OK, "Ascultă automat ședințele.")
    } else {
        Check("Asistentul e oprit", Result.FAIL, "Pornește-l înainte de ședință.", Fix.TURN_ON)
    }

    private fun serviceCheck() = if (CaptionService.connected.value) {
        Check("Citirea subtitrărilor și insula", Result.OK, "Serviciul de accesibilitate merge.")
    } else {
        Check(
            "Citirea subtitrărilor e oprită", Result.FAIL,
            "Fără ea aplicația nu aude nimic. Accesibilitate → Aplicații descărcate → Asistent Ședințe. " +
                "Butonul e gri? ${Setup.restrictedSettingsPath}.",
            Fix.ACCESSIBILITY,
        )
    }

    private fun notificationsCheck(context: Context): Check {
        val manager = context.getSystemService(NotificationManager::class.java)
        return if (Setup.notificationsAllowed(context) && manager.areNotificationsEnabled()) {
            Check("Notificări", Result.OK, "Permise.")
        } else {
            Check("Notificările sunt blocate", Result.FAIL, "Nu vei afla când ești întrebată.", Fix.NOTIFICATIONS)
        }
    }

    /** Sends a real test alert: she should hear the beep and feel the vibration now. */
    private fun questionSoundCheck(context: Context, alerts: Alerts): Check {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = manager.getNotificationChannel(Alerts.CHANNEL_DIRECT)
        if (channel != null && (channel.importance < NotificationManager.IMPORTANCE_HIGH || channel.sound == null)) {
            return Check(
                "Alerta de întrebare e fără sunet", Result.FAIL,
                "Pornește sunetul și „Notificări plutitoare” pentru „Te-a întrebat cineva”.", Fix.QUESTION_SOUND,
            )
        }
        val posted = alerts.fire(AlertLevel.DIRECT, "Test: te-a întrebat cineva!", "Așa te anunță aplicația când ești întrebată.")
        if (!posted) return Check("Alerta de întrebare nu a pornit", Result.FAIL, "Verifică notificările aplicației.", Fix.QUESTION_SOUND)
        if (manager.currentInterruptionFilter > NotificationManager.INTERRUPTION_FILTER_ALL) {
            return Check(
                "„Nu deranja” e pornit", Result.WARN,
                "Alerta poate veni fără sunet. Oprește „Nu deranja” înainte de ședință.",
            )
        }
        val ringer = context.getSystemService(AudioManager::class.java)?.ringerMode
        if (ringer != null && ringer != AudioManager.RINGER_MODE_NORMAL) {
            return Check("Telefonul e pe silențios", Result.WARN, "Vei simți doar vibrația, fără bip. Am trimis o alertă de test.")
        }
        val floating = if (Setup.isXiaomi) " Dacă nu a apărut sus pe ecran, pornește „Notificări plutitoare”." else ""
        return Check("Alerta de întrebare", Result.OK, "Am trimis o alertă de test: ar trebui să auzi bipul și să simți vibrația.$floating", Fix.QUESTION_SOUND)
    }

    private fun batteryCheck(context: Context) = if (Setup.batteryUnrestricted(context)) {
        Check("Baterie fără restricții", Result.OK, "Telefonul nu oprește aplicația.")
    } else {
        Check("Bateria poate opri aplicația", Result.FAIL, "Alege „Fără restricții”, altfel telefonul o poate opri în ședință.", Fix.BATTERY)
    }

    private fun xiaomiCheck(prefs: Prefs, context: Context) = if (xiaomiStepsDone(prefs, context) >= 4) {
        Check("Pașii pentru Xiaomi", Result.OK, "Pornire automată, baterie, notificări plutitoare, blocată în aplicații recente.")
    } else {
        Check("Pașii pentru Xiaomi nu sunt gata", Result.WARN, "Fă pașii Xiaomi din Configurare (Pornire automată etc.), ca HyperOS să nu oprească aplicația.", Fix.XIAOMI)
    }

    private fun nameCheck(prefs: Prefs) = if (prefs.userName.isNotBlank()) {
        Check("Numele tău: ${prefs.userName.trim()}", Result.OK, "Știu când ești strigată.")
    } else {
        Check("Lipsește numele tău", Result.FAIL, "Fără el nu știu când ești strigată.", Fix.SETTINGS)
    }

    /**
     * A real question with a small picture: proves the key, the internet and the speed, and
     * that the model can read the slide on screen (it must read the number in the picture).
     */
    private suspend fun aiCheck(app: App): Check {
        if (!app.llm.isConfigured()) return Check("Lipsește cheia AI", Result.FAIL, "Fără ea nu primești răspunsuri și rezumate.", Fix.SETTINGS)
        val model = if (app.prefs.provider == Prefs.PROVIDER_OPENROUTER) app.prefs.openRouterModel else app.prefs.geminiModel
        val started = System.currentTimeMillis()
        val json = try {
            app.llm.json(
                "Răspunzi doar cu JSON.",
                "Ce număr scrie în imagine? Răspunde exact în forma {\"number\":\"...\"}; dacă nu vezi nicio imagine, {\"number\":\"\"}.",
                maxTokens = 1_000,
                timeoutMs = 25_000,
                imageJpeg = testImage(),
            )
        } catch (e: Exception) {
            return Check("AI-ul nu răspunde", Result.FAIL, e.message ?: "Eroare AI", Fix.SETTINGS)
        }
        val seconds = (System.currentTimeMillis() - started) / 1000.0
        val time = String.format(java.util.Locale.US, "%.1f", seconds).replace('.', ',')
        val seesImages = json.optString("number").contains(TEST_NUMBER)
        return when {
            !seesImages -> Check(
                "AI-ul nu vede ecranul", Result.WARN,
                "Răspunde în $time s, dar modelul $model nu citește imagini: nu poate răspunde la întrebări despre slide. " +
                    "Folosește OpenRouter cu anthropic/claude-haiku-5.5.",
                Fix.SETTINGS,
            )
            seconds > SLOW_SECONDS -> Check("AI-ul răspunde încet", Result.WARN, "A durat $time s ($model). Verifică internetul.")
            else -> Check("AI-ul răspunde", Result.OK, "În $time s și vede imaginile ($model).")
        }
    }

    private suspend fun screenCheck(): Check {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return Check("Captura ecranului", Result.WARN, "Android prea vechi: AI-ul nu va vedea prezentarea.")
        }
        val service = CaptionService.instance
            ?: return Check("Captura ecranului", Result.FAIL, "Pornește întâi citirea subtitrărilor.", Fix.ACCESSIBILITY)
        val shot = withTimeoutOrNull(3_000) { service.captureScreen(meetingOnly = false) }
        return if (shot != null) {
            Check("Captura ecranului", Result.OK, "AI-ul poate vedea prezentarea din ședință.")
        } else {
            Check(
                "Captura ecranului nu merge", Result.WARN,
                "AI-ul nu va vedea prezentarea. Oprește și repornește citirea subtitrărilor din Accesibilitate.",
                Fix.ACCESSIBILITY,
            )
        }
    }

    private suspend fun updateCheck(): Check {
        val update = runCatching { UpdateChecker.check(force = true) }.getOrElse {
            return Check("Actualizări", Result.WARN, "Nu am putut verifica (internet?).")
        }
        return if (update == null) {
            Check("Ultima versiune", Result.OK, "Aplicația e la zi.")
        } else {
            Check("Există o versiune nouă: ${update.name}", Result.WARN, "Apasă „Actualizează acum” mai sus, în afara unui apel.")
        }
    }

    private const val TEST_NUMBER = "47"
    private const val SLOW_SECONDS = 8.0

    /** A small picture with a number on it, like a slide. */
    private fun testImage(): String {
        val bitmap = Bitmap.createBitmap(480, 200, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 110f
            isFakeBoldText = true
        }
        canvas.drawText("Nr. $TEST_NUMBER", 60f, 140f, paint)
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }
}
