package com.meeting.copilot.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Modifier
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

private val dateFormat = SimpleDateFormat("EEE d MMM yyyy, HH:mm", Locale("ro", "RO"))
private val timeFormat = SimpleDateFormat("HH:mm", Locale.US)

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
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Text("Istoric ședințe", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(vertical = 12.dp))
        }
        val list = meetings
        if (list != null && list.isEmpty()) {
            item { Text("Nicio ședință încă.", color = Palette.Muted) }
        }
        items(list.orEmpty(), key = { it.id }) { meeting ->
            Section(modifier = Modifier.clickable { onOpen(meeting.id) }) {
                Text(meeting.title ?: "Ședință ${meeting.app}", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${dateFormat.format(Date(meeting.startedAt))} · ${duration(meeting)} · ${meeting.lineCount} replici",
                    style = MaterialTheme.typography.bodySmall, color = Palette.Muted,
                )
                val tasks = Prompts.summaryFromJson(meeting.summary)?.myTasks?.size ?: 0
                if (tasks > 0) Text("$tasks ${if (tasks == 1) "sarcină" else "sarcini"} pentru tine", color = Palette.Amber, style = MaterialTheme.typography.bodySmall)
            }
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

    LazyColumn(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            TextButton(onClick = onBack) { Text("← Înapoi") }
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text("${dateFormat.format(Date(m.startedAt))} · ${duration(m)}", color = Palette.Muted, style = MaterialTheme.typography.bodySmall)
            Row(modifier = Modifier.padding(vertical = 8.dp)) {
                Button(onClick = {
                    Setup.share(context, Prompts.shareText(title, dateFormat.format(Date(m.startedAt)), summary, lines.map { it.toPrompt() }), title)
                }) { Text("Trimite") }
                Spacer(Modifier.padding(4.dp))
                OutlinedButton(enabled = !busy && app.llm.isConfigured() && lines.isNotEmpty(), onClick = {
                    busy = true
                    message = null
                    scope.launch {
                        message = app.engine.summarize(meetingId)
                        busy = false
                        reload++
                    }
                }) { Text(if (busy) "Se face…" else if (summary == null) "Fă rezumatul" else "Refă rezumatul") }
            }
            message?.let { Text(it, color = Palette.Red, style = MaterialTheme.typography.bodySmall) }
        }

        if (summary != null) {
            item {
                if (summary.myTasks.isNotEmpty()) Section(color = Palette.CardWarn) {
                    Label("Sarcinile mele", Palette.Amber)
                    summary.myTasks.forEach { t ->
                        Text("• ${t.task}", style = MaterialTheme.typography.bodyLarge)
                        val meta = listOfNotNull(
                            t.from.takeIf { it.isNotBlank() }?.let { "cerut de $it" },
                            t.deadline.takeIf { it.isNotBlank() }?.let { "termen: $it" },
                        ).joinToString(" · ")
                        if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                    }
                }
                if (summary.myPromises.isNotEmpty()) Section {
                    Label("Ce am promis", Palette.Amber)
                    summary.myPromises.forEach { Text("• $it", style = MaterialTheme.typography.bodyLarge) }
                }
                if (summary.points.isNotEmpty()) Section {
                    Label("Rezumat", Palette.Blue)
                    summary.points.forEach { Text("• $it", style = MaterialTheme.typography.bodyLarge) }
                }
                if (summary.decisions.isNotEmpty()) Section {
                    Label("Decizii", Palette.Blue)
                    summary.decisions.forEach { Text("• $it", style = MaterialTheme.typography.bodyLarge) }
                }
            }
        } else {
            item {
                Text(
                    if (app.llm.isConfigured()) "Nu există încă un rezumat." else "Adaugă cheia AI în Setări pentru rezumat.",
                    color = Palette.Muted, modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }

        item {
            TextButton(onClick = { showTranscript = !showTranscript }) {
                Text(if (showTranscript) "Ascunde transcrierea" else "Arată transcrierea (${lines.size} replici)")
            }
        }
        if (showTranscript) {
            items(lines, key = { it.id }) { line ->
                Column(Modifier.padding(vertical = 3.dp)) {
                    Text(
                        "${timeFormat.format(Date(line.ts))}  ${line.speaker ?: ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (line.flags and Db.FLAG_ADDRESSED != 0) Palette.Red else Palette.Muted,
                    )
                    Text(line.text, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item {
            TextButton(onClick = { confirmDelete = true }, modifier = Modifier.padding(vertical = 16.dp)) {
                Text("Șterge ședința", color = Palette.Red)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Ștergi ședința?") },
            text = { Text("Transcrierea și rezumatul se șterg definitiv de pe telefon.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        withContext(Dispatchers.IO) { app.db.deleteMeeting(meetingId) }
                        onBack()
                    }
                }) { Text("Șterge", color = Palette.Red) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Renunță") } },
        )
    }
}

private fun LineRow.toPrompt() = PromptLine(ts, speaker, text)

private fun duration(m: MeetingRow): String {
    val end = m.endedAt ?: return "în curs"
    val minutes = (end - m.startedAt) / 60_000
    return if (minutes < 60) "$minutes min" else "${minutes / 60} h ${minutes % 60} min"
}
