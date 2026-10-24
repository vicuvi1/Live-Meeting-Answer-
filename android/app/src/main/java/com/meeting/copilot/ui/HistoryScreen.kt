package com.meeting.copilot.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.meeting.copilot.App
import com.meeting.copilot.ai.PromptLine
import com.meeting.copilot.ai.Prompts
import com.meeting.copilot.data.Db
import com.meeting.copilot.data.LineRow
import com.meeting.copilot.data.MeetingRow
import com.meeting.copilot.util.Setup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ro = Locale("ro", "RO")
private val dateFormat = SimpleDateFormat("EEEE, d MMMM yyyy · HH:mm", ro)
private val dayFormat = SimpleDateFormat("d", ro)
private val monthFormat = SimpleDateFormat("MMM", ro)
private val clockFormat = SimpleDateFormat("HH:mm", Locale.US)

@Composable
fun HistoryScreen(app: App, modifier: Modifier = Modifier) {
    var openId by rememberSaveable { mutableStateOf<Long?>(null) }
    val id = openId
    if (id != null) {
        BackHandler { openId = null }
        MeetingDetail(app, id, onBack = { openId = null }, modifier = modifier)
    } else {
        MeetingList(app, onOpen = { openId = it }, modifier = modifier)
    }
}

@Composable
private fun MeetingList(app: App, onOpen: (Long) -> Unit, modifier: Modifier) {
    val state by app.engine.state.collectAsState()
    var meetings by remember { mutableStateOf<List<MeetingRow>?>(null) }
    LaunchedEffect(state.status, state.summaryRunning) {
        meetings = withContext(Dispatchers.IO) { app.db.meetings() }
    }
    LazyColumn(modifier.fillMaxSize().padding(ScreenPadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ScreenTitle("Istoric", "Fiecare ședință, cu rezumat și sarcini") }
        val list = meetings
        if (list != null && list.isEmpty()) {
            item {
                AppCard {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        RingsIllustration(Palette.Blue, Modifier.padding(bottom = 10.dp))
                        Text("Nicio ședință încă", style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                        Text("Ședințele apar aici automat.", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
                    }
                }
            }
        }
        items(list.orEmpty(), key = { it.id }) { meeting -> MeetingRowCard(meeting) { onOpen(meeting.id) } }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun MeetingRowCard(meeting: MeetingRow, onClick: () -> Unit) {
    val summary = Prompts.summaryFromJson(meeting.summary)
    val tasks = summary?.myTasks?.size ?: 0
    AppCard(onClick = onClick, padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(Palette.SurfaceHigh),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(dayFormat.format(Date(meeting.startedAt)), style = MaterialTheme.typography.titleLarge, color = Palette.Text)
                Text(monthFormat.format(Date(meeting.startedAt)).uppercase(), style = MaterialTheme.typography.labelSmall, color = Palette.Green)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(summary?.title ?: meeting.title ?: "Ședință ${meeting.app}", style = MaterialTheme.typography.titleSmall, color = Palette.Text, maxLines = 2)
                Text(
                    "${clockFormat.format(Date(meeting.startedAt))} · ${duration(meeting)} · ${meeting.app}",
                    style = MaterialTheme.typography.bodySmall, color = Palette.Muted,
                )
                if (tasks > 0) {
                    Spacer(Modifier.height(6.dp))
                    Pill("$tasks ${if (tasks == 1) "sarcină" else "sarcini"} pentru tine", Tone.Warning)
                }
            }
            Icon(Icons.Filled.KeyboardArrowRight, null, tint = Palette.Faint)
        }
    }
}

@Composable
private fun MeetingDetail(app: App, meetingId: Long, onBack: () -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var reload by remember { mutableIntStateOf(0) }
    var meeting by remember { mutableStateOf<MeetingRow?>(null) }
    var lines by remember { mutableStateOf<List<LineRow>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showTranscript by remember { mutableStateOf(false) }

    LaunchedEffect(meetingId, reload) {
        withContext(Dispatchers.IO) {
            meeting = app.db.meeting(meetingId)
            lines = app.db.lines(meetingId)
        }
    }
    val m = meeting ?: return
    val summary = Prompts.summaryFromJson(m.summary)
    val title = summary?.title ?: m.title ?: "Ședință ${m.app}"

    LazyColumn(modifier.fillMaxSize().padding(ScreenPadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(
                Modifier.padding(top = 10.dp).clip(CircleShape).clickable(onClick = onBack).padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.ArrowBack, null, tint = Palette.TextSoft, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Istoric", style = MaterialTheme.typography.labelLarge, color = Palette.TextSoft)
            }
            Spacer(Modifier.height(6.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall, color = Palette.Text)
            Text(
                "${dateFormat.format(Date(m.startedAt)).replaceFirstChar { it.uppercase() }} · ${duration(m)}",
                style = MaterialTheme.typography.bodySmall, color = Palette.Muted, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppButton(
                    "Trimite",
                    { Setup.share(context, Prompts.shareText(title, dateFormat.format(Date(m.startedAt)), summary, lines.map { it.toPrompt() }), title) },
                    icon = Icons.Filled.Share, modifier = Modifier.weight(1f),
                )
                AppButton(
                    if (busy) "Se face…" else if (summary == null) "Fă rezumatul" else "Refă rezumatul",
                    {
                        busy = true
                        message = null
                        scope.launch {
                            message = app.engine.summarize(meetingId)
                            busy = false
                            reload++
                        }
                    },
                    style = ButtonStyle.Tonal, tone = Tone.Info, icon = Icons.Filled.Refresh,
                    enabled = !busy && app.llm.isConfigured() && lines.isNotEmpty(), modifier = Modifier.weight(1f),
                )
            }
            message?.let { Text(it, color = Palette.Red, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
        }

        if (summary != null) {
            if (summary.myTasks.isNotEmpty()) item {
                AppCard(tone = Tone.Warning) {
                    Eyebrow("Sarcinile mele", Palette.Amber)
                    Spacer(Modifier.height(8.dp))
                    summary.myTasks.forEach { t ->
                        Row(Modifier.padding(vertical = 6.dp)) {
                            Icon(Icons.Filled.CheckCircle, null, tint = Palette.Amber, modifier = Modifier.size(20.dp).padding(top = 2.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(t.task, style = MaterialTheme.typography.bodyLarge, color = Palette.Text)
                                val meta = listOfNotNull(
                                    t.from.takeIf { it.isNotBlank() }?.let { "cerut de $it" },
                                    t.deadline.takeIf { it.isNotBlank() }?.let { "termen: $it" },
                                ).joinToString(" · ")
                                if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodySmall, color = Palette.Amber.copy(alpha = 0.8f))
                            }
                        }
                    }
                }
            }
            if (summary.myPromises.isNotEmpty()) item {
                AppCard {
                    Eyebrow("Ce am promis", Palette.Violet)
                    Spacer(Modifier.height(8.dp))
                    summary.myPromises.forEach { Bullet(it, Palette.Violet) }
                }
            }
            if (summary.points.isNotEmpty()) item {
                AppCard {
                    Eyebrow("Rezumat", Palette.Blue)
                    Spacer(Modifier.height(8.dp))
                    summary.points.forEach { Bullet(it) }
                }
            }
            if (summary.decisions.isNotEmpty()) item {
                AppCard {
                    Eyebrow("Decizii", Palette.Green)
                    Spacer(Modifier.height(8.dp))
                    summary.decisions.forEach { Bullet(it, Palette.Green) }
                }
            }
        } else item {
            AppCard {
                Text(
                    if (app.llm.isConfigured()) "Nu există încă un rezumat. Apasă „Fă rezumatul”." else "Adaugă cheia AI în Setări ca să primești rezumatul.",
                    style = MaterialTheme.typography.bodyMedium, color = Palette.Muted,
                )
            }
        }

        item {
            AppButton(
                if (showTranscript) "Ascunde transcrierea" else "Arată transcrierea · ${lines.size} replici",
                { showTranscript = !showTranscript }, style = ButtonStyle.Ghost, modifier = Modifier.fillMaxWidth(),
            )
        }
        if (showTranscript) {
            items(lines, key = { it.id }) { line ->
                val addressed = line.flags and Db.FLAG_ADDRESSED != 0
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) {
                    Box(
                        Modifier.width(3.dp).height(38.dp).clip(CircleShape)
                            .background(if (addressed) Palette.Red else Palette.speaker(line.speaker).copy(alpha = 0.5f))
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Row {
                            Text(line.speaker ?: "Participant", style = MaterialTheme.typography.labelMedium, color = Palette.speaker(line.speaker))
                            Spacer(Modifier.width(8.dp))
                            Text(clockFormat.format(Date(line.ts)), style = MaterialTheme.typography.labelMedium, color = Palette.Faint)
                        }
                        Text(line.text, style = MaterialTheme.typography.bodyMedium, color = if (addressed) Color(0xFFFFC9C9) else Palette.TextSoft)
                    }
                }
            }
        }
        item {
            AppButton(
                "Șterge ședința", { confirmDelete = true }, style = ButtonStyle.Ghost, icon = Icons.Filled.Delete,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = Palette.SurfaceHigh,
            title = { Text("Ștergi ședința?", color = Palette.Text) },
            text = { Text("Transcrierea și rezumatul se șterg definitiv de pe telefon.", color = Palette.TextSoft) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        withContext(Dispatchers.IO) { app.db.deleteMeeting(meetingId) }
                        onBack()
                    }
                }) { Text("Șterge", color = Palette.Red) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Renunță", color = Palette.TextSoft) } },
        )
    }
}

private fun LineRow.toPrompt() = PromptLine(ts, speaker, text)

private fun duration(m: MeetingRow): String {
    val end = m.endedAt ?: return "în curs"
    val minutes = (end - m.startedAt) / 60_000
    return if (minutes < 60) "$minutes min" else "${minutes / 60} h ${minutes % 60} min"
}
