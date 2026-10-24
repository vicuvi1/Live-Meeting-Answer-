package com.meeting.copilot.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meeting.copilot.App
import com.meeting.copilot.caption.CaptionService
import com.meeting.copilot.caption.Overlay
import com.meeting.copilot.engine.AlertLevel
import com.meeting.copilot.engine.Status
import com.meeting.copilot.engine.UiState
import com.meeting.copilot.util.Setup
import com.meeting.copilot.util.UpdateChecker
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(app: App, resumes: Int, onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val state by app.engine.state.collectAsState()
    val serviceOn by CaptionService.connected.collectAsState()
    val prefsVersion by app.prefs.version.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(5_000)
        }
    }
    var update by remember { mutableStateOf<UpdateChecker.Update?>(null) }
    LaunchedEffect(Unit) { update = runCatching { UpdateChecker.check() }.getOrNull() }

    val notificationsOk = remember(resumes) { Setup.notificationsAllowed(context) }
    val hasName = remember(prefsVersion) { app.prefs.userName.isNotBlank() }
    val hasKey = remember(prefsVersion) { app.prefs.hasAiKey() }
    val firstName = remember(prefsVersion) { app.prefs.userName.trim().split(' ').firstOrNull().orEmpty() }

    val showAnswer = state.answer != null && (now - state.answer!!.createdAt < 5 * 60_000 || state.answerLoading)

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Header(firstName, state)

        if (!serviceOn || !notificationsOk || !hasName || !hasKey) {
            SetupCard(serviceOn, notificationsOk, hasName, hasKey, onOpenSettings)
        }

        update?.let { u ->
            AppCard(tone = Tone.Info) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Star, null, tint = Palette.Blue, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Versiune nouă: ${u.name}", style = MaterialTheme.typography.titleSmall, color = Palette.Text, modifier = Modifier.weight(1f))
                }
                Text(
                    "Se descarcă în browser. Apoi atinge fișierul și alege Actualizează: datele rămân.",
                    style = MaterialTheme.typography.bodySmall, color = Palette.Muted, modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
                )
                AppButton("Descarcă actualizarea", { Setup.openUrl(context, u.url) }, tone = Tone.Info, modifier = Modifier.fillMaxWidth())
            }
        }

        val bubbleHidden by Overlay.hidden.collectAsState()
        if (bubbleHidden && (state.meetingVisible || state.status == Status.LISTENING)) {
            AppCard(padding = 16.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Bula e ascunsă", style = MaterialTheme.typography.titleSmall, color = Palette.Text, modifier = Modifier.weight(1f))
                    AppButton("Arat-o", { Overlay.hidden.value = false }, style = ButtonStyle.Tonal, height = 40.dp)
                }
            }
        }

        AnimatedVisibility(showAnswer, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            AnswerHero(state, onDismiss = { app.engine.dismissAnswer() })
        }
        if (!showAnswer) StatusHero(state, now, onTest = onOpenSettings)
        if (!showAnswer && state.answerError != null) {
            Text(state.answerError!!, color = Palette.Red, style = MaterialTheme.typography.bodySmall)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionTile("Ce am pierdut?", "Rezumatul de acum", Icons.Filled.Refresh, Tone.Success, filled = true) {
                app.engine.requestCatchUp()
            }
            ActionTile("Ce să răspund?", "La ultima întrebare", Icons.Filled.Face, Tone.Info, filled = false) {
                app.engine.requestAnswer()
            }
        }

        CatchUpCard(state, now)
        TranscriptCard(state)

        if (state.status == Status.LISTENING) {
            AppButton(
                "Încheie ședința și fă rezumatul", { app.engine.endMeeting() },
                style = ButtonStyle.Ghost, modifier = Modifier.fillMaxWidth(),
            )
        }
        if (state.summaryRunning) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                StatusDot(Palette.Violet, pulsing = true, size = 8.dp)
                Text("Pregătesc rezumatul ședinței…", color = Palette.Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun Header(firstName: String, state: UiState) {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                if (firstName.isNotEmpty()) "Bună, $firstName" else "Asistent Ședințe",
                style = MaterialTheme.typography.headlineSmall, color = Palette.Text,
            )
            Text("Asistentul tău de ședință", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        }
        when (state.status) {
            Status.LISTENING -> Pill("Ascult", Tone.Success)
            Status.WAITING_CAPTIONS -> Pill("Aștept CC", Tone.Warning)
            Status.IDLE -> Pill("Pregătit", Tone.Neutral)
        }
    }
}

@Composable
private fun SetupCard(serviceOn: Boolean, notificationsOk: Boolean, hasName: Boolean, hasKey: Boolean, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val done = listOf(serviceOn, notificationsOk, hasName, hasKey).count { it }
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Configurare", style = MaterialTheme.typography.titleMedium, color = Palette.Text, modifier = Modifier.weight(1f))
            Text("$done din 4", style = MaterialTheme.typography.labelMedium, color = Palette.Green)
        }
        Spacer(Modifier.height(10.dp))
        ProgressBar(done / 4f)
        Spacer(Modifier.height(6.dp))
        StepRow(1, serviceOn, "Citirea subtitrărilor", "Accesibilitate → Aplicații descărcate → Asistent Ședințe", "Pornește") {
            Setup.openAccessibility(context)
        }
        if (!serviceOn) {
            Text(
                "Butonul e gri? Info aplicație → ⋮ sus-dreapta → Permite setări restricționate.",
                style = MaterialTheme.typography.bodySmall, color = Palette.Faint,
                modifier = Modifier.padding(start = 48.dp, bottom = 4.dp),
            )
            AppButton("Deschide Info aplicație", { Setup.openAppInfo(context) }, style = ButtonStyle.Ghost, height = 40.dp, modifier = Modifier.padding(start = 48.dp))
        }
        StepRow(2, notificationsOk, "Notificări", "Ca să vibreze și pe ceas", "Permite") {
            (context as? MainActivity)?.requestNotifications() ?: Setup.openAppInfo(context)
        }
        StepRow(3, hasName, "Numele tău", "Ca să știu când ești strigată", "Adaugă", onOpenSettings)
        StepRow(4, hasKey, "Cheia AI", "Pentru rezumat și răspunsuri", "Adaugă", onOpenSettings)
    }
}

@Composable
private fun StatusHero(state: UiState, now: Long, onTest: () -> Unit) {
    when (state.status) {
        Status.LISTENING -> {
            val alertRecent = now - state.alertAt < 60_000 && (state.alert == AlertLevel.MENTION || state.alert == AlertLevel.TOPIC)
            val tone = when {
                !state.meetingVisible || state.health != null -> Tone.Warning
                alertRecent -> Tone.Warning
                else -> Tone.Success
            }
            AppCard(tone = tone) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(tone.accent, pulsing = state.meetingVisible)
                    Spacer(Modifier.width(6.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (state.meetingVisible) "Ascult ședința" else "${state.meetingApp ?: "Ședința"} nu e pe ecran",
                            style = MaterialTheme.typography.titleLarge, color = Palette.Text,
                        )
                        val minutes = state.startedAt?.let { (now - it) / 60_000 } ?: 0
                        Text(
                            "${state.meetingApp ?: "Ședință"} · $minutes min · ${state.lines.size} replici",
                            style = MaterialTheme.typography.bodySmall, color = Palette.Muted,
                        )
                    }
                }
                if (!state.meetingVisible) {
                    Text(
                        "Deschide ședința pe ecran ca să pot citi subtitrările.",
                        style = MaterialTheme.typography.bodyMedium, color = Palette.Amber, modifier = Modifier.padding(top = 10.dp),
                    )
                }
                if (alertRecent && state.alertText != null) {
                    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Filled.Notifications, null, tint = Palette.Amber, modifier = Modifier.size(18.dp).padding(top = 2.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            (if (state.alert == AlertLevel.MENTION) "Ai fost menționată: " else "Subiect urmărit: ") + state.alertText,
                            style = MaterialTheme.typography.bodyMedium, color = Palette.TextSoft,
                        )
                    }
                }
                HealthLine(state.health)
                if (state.live.isNotBlank()) {
                    Spacer(Modifier.height(14.dp))
                    Box(Modifier.fillMaxWidth().clip(InnerShape).background(Color.Black.copy(alpha = 0.3f)).padding(14.dp)) {
                        Column {
                            state.liveSpeaker?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = Palette.speaker(it)) }
                            Text(
                                state.live.takeLast(180),
                                style = MaterialTheme.typography.bodyMedium, color = Palette.TextSoft, fontStyle = FontStyle.Italic,
                            )
                        }
                    }
                }
            }
        }
        Status.WAITING_CAPTIONS -> AppCard(tone = Tone.Warning) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(Palette.Amber, pulsing = true)
                Spacer(Modifier.width(6.dp))
                Text("Ședință detectată", style = MaterialTheme.typography.titleLarge, color = Palette.Text)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Pornesc singură subtitrările în română. Dacă nu reușesc în câteva secunde, apasă CC (subtitrări) și alege română. Bula devine verde când ascult.",
                style = MaterialTheme.typography.bodyMedium, color = Palette.TextSoft,
            )
            HealthLine(state.health)
        }
        Status.IDLE -> AppCard {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                RingsIllustration(Palette.Green, Modifier.padding(top = 4.dp, bottom = 10.dp))
                Text("Gata pentru ședință", style = MaterialTheme.typography.titleLarge, color = Palette.Text)
                Text(
                    "Pornesc singură când văd subtitrări în Zoom sau Meet.",
                    style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                )
            }
            HowToStep(1, "Intră în ședință în Zoom sau Meet, pe acest telefon")
            HowToStep(2, "Pornesc singură subtitrările în română")
            HowToStep(3, "Bula verde de pe margine înseamnă că ascult")
            Spacer(Modifier.height(10.dp))
            AppButton("Încearcă un test fără ședință", onTest, style = ButtonStyle.Ghost, icon = Icons.Filled.PlayArrow, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun HowToStep(number: Int, text: String) {
    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(26.dp).clip(CircleShape).background(Palette.SurfaceHigh), contentAlignment = Alignment.Center) {
            Text("$number", style = MaterialTheme.typography.labelMedium, color = Palette.Green)
        }
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Palette.TextSoft)
    }
}

@Composable
private fun HealthLine(health: String?) {
    if (health == null) return
    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.Top) {
        Icon(Icons.Filled.Warning, null, tint = Palette.Amber, modifier = Modifier.size(18.dp).padding(top = 2.dp))
        Spacer(Modifier.width(8.dp))
        Text(health, style = MaterialTheme.typography.bodyMedium, color = Palette.Amber)
    }
}

@Composable
private fun AnswerHero(state: UiState, onDismiss: () -> Unit) {
    val answer = state.answer ?: return
    AppCard(tone = Tone.Danger) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(Palette.Red, pulsing = true, size = 10.dp)
            Spacer(Modifier.width(4.dp))
            Eyebrow("Întrebare pentru tine", Palette.Red)
        }
        Spacer(Modifier.height(8.dp))
        Text(answer.question, style = MaterialTheme.typography.titleLarge, color = Palette.Text)
        if (answer.context.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            answer.context.forEach { Bullet(it, Palette.Faint, MaterialTheme.typography.bodySmall) }
        }
        Spacer(Modifier.height(16.dp))
        Column(
            Modifier.fillMaxWidth().clip(InnerShape).background(Palette.GreenDeep)
                .border(1.dp, Palette.Green.copy(alpha = 0.35f), InnerShape).padding(16.dp),
        ) {
            Eyebrow("Spune", Palette.Green)
            Spacer(Modifier.height(6.dp))
            when {
                answer.answer.isNotBlank() -> {
                    Text(answer.answer, fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
                    if (!answer.confident) {
                        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Info, null, tint = Palette.Muted, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Răspuns prudent: nu am date sigure despre asta.", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                        }
                    }
                }
                state.answerLoading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(Palette.Green, pulsing = true, size = 8.dp)
                    Text("Pregătesc răspunsul…", style = MaterialTheme.typography.bodyLarge, color = Palette.TextSoft)
                }
                state.answerError != null -> Text(state.answerError!!, style = MaterialTheme.typography.bodyMedium, color = Palette.Red)
            }
        }
        if (answer.answer.isNotBlank() && state.answerError != null) {
            Text(state.answerError!!, style = MaterialTheme.typography.bodySmall, color = Palette.Red, modifier = Modifier.padding(top = 8.dp))
        }
        Spacer(Modifier.height(14.dp))
        AppButton("Am răspuns", onDismiss, style = ButtonStyle.Tonal, tone = Tone.Danger, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun CatchUpCard(state: UiState, now: Long) {
    val catchUp = state.catchUp
    if (catchUp != null && catchUp.topic.isNotBlank()) {
        AppCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Eyebrow("Acum se discută", Palette.Blue, Modifier.weight(1f))
                val seconds = (now - catchUp.updatedAt) / 1000
                Text(
                    if (state.catchUpLoading) "se actualizează…" else if (seconds < 60) "acum $seconds s" else "acum ${seconds / 60} min",
                    style = MaterialTheme.typography.labelMedium, color = Palette.Faint,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(catchUp.topic, style = MaterialTheme.typography.titleLarge, color = Palette.Text)
            Spacer(Modifier.height(8.dp))
            catchUp.points.forEach { Bullet(it) }
            if (catchUp.forMe.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Column(
                    Modifier.fillMaxWidth().clip(InnerShape).background(Palette.AmberDeep)
                        .border(1.dp, Palette.Amber.copy(alpha = 0.35f), InnerShape).padding(14.dp),
                ) {
                    Eyebrow("De la tine se așteaptă", Palette.Amber)
                    Spacer(Modifier.height(4.dp))
                    Text(catchUp.forMe, style = MaterialTheme.typography.titleMedium, color = Color(0xFFFDE7A8))
                }
            }
            state.catchUpError?.let { Text(it, color = Palette.Red, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
        }
    } else if (state.catchUpLoading || state.catchUpError != null) {
        AppCard {
            if (state.catchUpLoading) Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(Palette.Blue, pulsing = true, size = 8.dp)
                Text("Pregătesc rezumatul…", color = Palette.TextSoft, style = MaterialTheme.typography.bodyMedium)
            }
            state.catchUpError?.let { Text(it, color = Palette.Red, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun TranscriptCard(state: UiState) {
    if (state.lines.isEmpty()) return
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow("Ultimele replici", modifier = Modifier.weight(1f))
            Text("${state.lines.size}", style = MaterialTheme.typography.labelMedium, color = Palette.Faint)
        }
        Spacer(Modifier.height(10.dp))
        state.lines.takeLast(8).forEach { line ->
            val flagged = line.flags != 0
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Box(
                    Modifier.width(3.dp).height(40.dp).clip(CircleShape)
                        .background(if (flagged) Palette.Amber else Palette.speaker(line.speaker).copy(alpha = 0.5f))
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(line.speaker ?: "Participant", style = MaterialTheme.typography.labelMedium, color = Palette.speaker(line.speaker))
                    Text(line.text, style = MaterialTheme.typography.bodyMedium, color = if (flagged) Color(0xFFFDE7A8) else Palette.TextSoft)
                }
            }
        }
        if (!state.catchUpLoading && state.catchUp == null && state.catchUpError == null && state.lines.size > 2) {
            Text(
                "Apasă „Ce am pierdut?” pentru rezumat.",
                style = MaterialTheme.typography.bodySmall, color = Palette.Faint, modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
