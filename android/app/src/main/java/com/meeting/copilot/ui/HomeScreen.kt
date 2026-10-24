package com.meeting.copilot.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meeting.copilot.App
import com.meeting.copilot.caption.CaptionService
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
    val needsName = remember(prefsVersion) { app.prefs.userName.isBlank() }
    val needsKey = remember(prefsVersion) { !app.prefs.hasAiKey() }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text("Asistent Ședințe", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))

        if (!serviceOn || !notificationsOk || needsName || needsKey) {
            SetupCard(serviceOn, notificationsOk, needsName, needsKey, onOpenSettings)
        }

        update?.let { u ->
            Section(color = Palette.CardWarn) {
                Text("Versiune nouă disponibilă (${u.name})", style = MaterialTheme.typography.titleMedium)
                Text("Se descarcă din browser; apoi atinge fișierul descărcat și alege Actualizează.", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                Button(onClick = { Setup.openUrl(context, u.url) }, modifier = Modifier.padding(top = 8.dp)) { Text("Descarcă actualizarea") }
            }
        }

        StatusCard(state, now)

        if (state.alert == AlertLevel.DIRECT && now - state.alertAt < 120_000) {
            Section(color = Palette.CardAlert) {
                Text("Te-a întrebat cineva!", style = MaterialTheme.typography.titleLarge, color = Palette.Red)
                state.alertText?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
            }
        }

        AnswerCard(state, now, onDismiss = { app.engine.dismissAnswer() })

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 6.dp)) {
            Button(
                onClick = { app.engine.requestCatchUp() },
                modifier = Modifier.weight(1f).height(64.dp),
            ) { Text("Ce am pierdut?", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
            OutlinedButton(
                onClick = { app.engine.requestAnswer() },
                modifier = Modifier.weight(1f).height(64.dp),
            ) { Text("Ce să răspund?", fontSize = 18.sp) }
        }

        CatchUpCard(state, now)
        TranscriptCard(state)

        if (state.status == Status.LISTENING) {
            TextButton(onClick = { app.engine.endMeeting() }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Încheie ședința și fă rezumatul", color = Palette.Muted)
            }
        }
        if (state.summaryRunning) {
            Text("Pregătesc rezumatul ședinței anterioare…", color = Palette.Muted, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SetupCard(serviceOn: Boolean, notificationsOk: Boolean, needsName: Boolean, needsKey: Boolean, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    Section(color = Palette.CardWarn) {
        Text("Pregătire (o singură dată)", style = MaterialTheme.typography.titleMedium, color = Palette.Amber)
        Spacer(Modifier.height(6.dp))
        SetupRow(serviceOn, "Citirea subtitrărilor", "Pornește") { Setup.openAccessibility(context) }
        if (!serviceOn) {
            Text(
                "Accesibilitate → Aplicații descărcate → Asistent Ședințe → Pornit. Dacă butonul e blocat: " +
                    "Info aplicație → ⋮ (sus, dreapta) → Permite setări restricționate, apoi încearcă din nou.",
                style = MaterialTheme.typography.bodySmall, color = Palette.Muted,
            )
            TextButton(onClick = { Setup.openAppInfo(context) }) { Text("Deschide Info aplicație") }
        }
        SetupRow(notificationsOk, "Notificări (și pe ceas/brățară)", "Permite") {
            (context as? MainActivity)?.requestNotifications() ?: Setup.openAppInfo(context)
        }
        SetupRow(!needsName, "Numele tău", "Completează", onOpenSettings)
        SetupRow(!needsKey, "Cheia AI (pentru rezumat și răspunsuri)", "Adaugă", onOpenSettings)
    }
}

@Composable
private fun SetupRow(done: Boolean, title: String, action: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(if (done) "✅" else "⬜", fontSize = 18.sp)
        Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(start = 8.dp))
        if (!done) TextButton(onClick = onClick) { Text(action) }
    }
}

@Composable
private fun StatusCard(state: UiState, now: Long) {
    val (color, title, detail) = when (state.status) {
        Status.LISTENING -> {
            val minutes = state.startedAt?.let { (now - it) / 60_000 } ?: 0
            val where = if (state.meetingVisible) "" else " · ${state.meetingApp ?: "Ședința"} nu e pe ecran, nu pot citi subtitrările"
            Triple(Palette.Green, "Ascult · ${state.meetingApp ?: ""} · $minutes min", where.removePrefix(" · "))
        }
        Status.WAITING_CAPTIONS -> Triple(Palette.Amber, "Ședință detectată", "Apasă CC (subtitrări) în Zoom/Meet și alege limba română.")
        Status.IDLE -> Triple(
            Palette.Muted, "Nicio ședință",
            "Intră în ședință în Zoom sau Meet pe acest telefon și pornește subtitrările (CC). Bula apare peste ședință.",
        )
    }
    Section {
        Text("● $title", style = MaterialTheme.typography.titleMedium, color = color)
        if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
        state.health?.let { Text("⚠ $it", style = MaterialTheme.typography.bodyMedium, color = Palette.Amber, modifier = Modifier.padding(top = 6.dp)) }
    }
}

@Composable
private fun AnswerCard(state: UiState, now: Long, onDismiss: () -> Unit) {
    val answer = state.answer
    if (answer == null || (now - answer.createdAt > 5 * 60_000 && !state.answerLoading)) {
        state.answerError?.let { Text(it, color = Palette.Red, style = MaterialTheme.typography.bodySmall) }
        return
    }
    Section(color = Color(0xFF0A1A10)) {
        Label("Întrebarea", Palette.Red)
        Text(answer.question, style = MaterialTheme.typography.titleMedium)
        answer.context.forEach { Text("· $it", style = MaterialTheme.typography.bodySmall, color = Palette.Muted) }
        Spacer(Modifier.height(10.dp))
        Label("Spune", Palette.Green)
        when {
            answer.answer.isNotBlank() -> {
                Text(answer.answer, fontSize = 24.sp, lineHeight = 31.sp, fontWeight = FontWeight.Bold)
                if (!answer.confident) Text("Răspuns prudent: nu am date sigure despre asta.", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
            }
            state.answerLoading -> Text("Pregătesc răspunsul…", style = MaterialTheme.typography.bodyLarge, color = Palette.Muted)
        }
        state.answerError?.let { Text(it, color = Palette.Red, style = MaterialTheme.typography.bodySmall) }
        TextButton(onClick = onDismiss) { Text("Am răspuns") }
    }
}

@Composable
private fun CatchUpCard(state: UiState, now: Long) {
    val catchUp = state.catchUp
    if (catchUp != null && catchUp.topic.isNotBlank()) {
        Section {
            Label("Acum se discută", Palette.Blue)
            Text(catchUp.topic, style = MaterialTheme.typography.titleLarge)
            catchUp.points.forEach { Text("• $it", style = MaterialTheme.typography.bodyLarge) }
            if (catchUp.forMe.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Label("De la tine se așteaptă", Palette.Amber)
                Text(catchUp.forMe, style = MaterialTheme.typography.titleMedium, color = Color(0xFFFDE68A))
            }
            val seconds = (now - catchUp.updatedAt) / 1000
            Text(
                if (state.catchUpLoading) "se actualizează…" else if (seconds < 60) "actualizat acum $seconds s" else "actualizat acum ${seconds / 60} min",
                style = MaterialTheme.typography.bodySmall, color = Palette.Muted, modifier = Modifier.padding(top = 4.dp),
            )
            state.catchUpError?.let { Text(it, color = Palette.Red, style = MaterialTheme.typography.bodySmall) }
        }
    } else if (state.catchUpLoading || state.catchUpError != null) {
        Section {
            if (state.catchUpLoading) Text("Pregătesc rezumatul…", color = Palette.Muted)
            state.catchUpError?.let { Text(it, color = Palette.Red, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun TranscriptCard(state: UiState) {
    if (state.lines.isEmpty() && state.live.isBlank()) return
    Section {
        Label("Ultimele replici")
        state.lines.takeLast(8).forEach { line ->
            Text(
                (line.speaker?.let { "$it: " } ?: "") + line.text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (line.flags != 0) Palette.Amber else Color.White,
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
        if (state.live.isNotBlank()) {
            Text(
                "… " + (state.liveSpeaker?.let { "$it: " } ?: "") + state.live.takeLast(240),
                style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, fontStyle = FontStyle.Italic,
            )
        }
    }
}
