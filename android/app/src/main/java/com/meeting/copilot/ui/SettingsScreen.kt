package com.meeting.copilot.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.meeting.copilot.App
import com.meeting.copilot.BuildConfig
import com.meeting.copilot.data.Prefs
import com.meeting.copilot.util.DiagLog
import com.meeting.copilot.util.Setup
import com.meeting.copilot.util.UpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(app: App, resumes: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = app.prefs
    prefs.version.collectAsState().value // recompose when a setting changes

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text("Setări", style = MaterialTheme.typography.headlineSmall)

        // ---- About her
        Section {
            Label("Despre tine")
            PrefField("Numele tău (cum te strigă colegii)", prefs.userName) { prefs.userName = it }
            PrefField("Alte forme ale numelui (virgulă între ele)", prefs.aliases, hint = "ex: Oli, doamna Popescu") { prefs.aliases = it }
            PrefField(
                "Notițe pentru răspunsuri",
                prefs.profileNotes,
                hint = "Rolul tău, proiectele și stadiul lor, cifre importante, ce nu ai voie să promiți.",
                lines = 5,
            ) { prefs.profileNotes = it }
            PrefField(
                "Nume și termeni din ședințe (pentru corectarea subtitrărilor)",
                prefs.vocabulary,
                hint = "ex: Radu Ionescu, proiectul Delta, SAP, ANAF",
                lines = 3,
            ) { prefs.vocabulary = it }
            PrefField(
                "Subiecte urmărite (vibrație scurtă când apar)",
                prefs.topics,
                hint = "ex: buget, raport, achiziții",
            ) { prefs.topics = it }
        }

        // ---- AI
        Section {
            Label("AI (rezumat, corectare, răspunsuri)")
            var provider by remember { mutableStateOf(prefs.provider) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = provider == Prefs.PROVIDER_GEMINI, onClick = {
                    provider = Prefs.PROVIDER_GEMINI
                    prefs.provider = provider
                })
                Text("Google Gemini")
                Spacer(Modifier.padding(8.dp))
                RadioButton(selected = provider == Prefs.PROVIDER_OPENROUTER, onClick = {
                    provider = Prefs.PROVIDER_OPENROUTER
                    prefs.provider = provider
                })
                Text("OpenRouter")
            }
            if (provider == Prefs.PROVIDER_GEMINI) {
                PrefField("Cheie Gemini", prefs.geminiKey, secret = true) { prefs.geminiKey = it }
                PrefField("Model", prefs.geminiModel, hint = "gemini-2.5-flash") { prefs.geminiModel = it }
                TextButton(onClick = { Setup.openUrl(context, "https://aistudio.google.com/apikey") }) {
                    Text("Obține o cheie gratuită (aistudio.google.com)")
                }
            } else {
                PrefField("Cheie OpenRouter", prefs.openRouterKey, secret = true) { prefs.openRouterKey = it }
                PrefField("Model", prefs.openRouterModel, hint = "google/gemini-2.5-flash") { prefs.openRouterModel = it }
                TextButton(onClick = { Setup.openUrl(context, "https://openrouter.ai/keys") }) { Text("Obține o cheie (openrouter.ai)") }
            }
            var testResult by remember { mutableStateOf<String?>(null) }
            var testing by remember { mutableStateOf(false) }
            OutlinedButton(enabled = !testing, onClick = {
                testing = true
                testResult = null
                scope.launch {
                    val started = System.currentTimeMillis()
                    testResult = try {
                        app.llm.json("Răspunde doar cu JSON.", "Returnează exact {\"ok\":true}", maxTokens = 50, timeoutMs = 20_000)
                        "✅ Funcționează (${System.currentTimeMillis() - started} ms)"
                    } catch (e: Exception) {
                        "❌ ${e.message}"
                    }
                    testing = false
                }
            }) { Text(if (testing) "Testez…" else "Testează cheia") }
            testResult?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Text(
                "Pentru rezumat și răspunsuri, fragmente din ședință sunt trimise la serviciul AI ales. Restul rămâne pe telefon.",
                style = MaterialTheme.typography.bodySmall, color = Palette.Muted, modifier = Modifier.padding(top = 6.dp),
            )
        }

        // ---- Alerts
        Section {
            Label("Alerte")
            PrefSwitch("Vibrații", prefs.vibrate) { prefs.vibrate = it }
            PrefSwitch("Citește întrebarea în cască (doar cu căști conectate)", prefs.whisperQuestion) { prefs.whisperQuestion = it }
            PrefSwitch("Bula peste Zoom/Meet", prefs.showBubble) { prefs.showBubble = it }
            Text(
                "Pentru ceas/brățară Xiaomi: în Mi Fitness → Notificări aplicații, pornește Asistent Ședințe.",
                style = MaterialTheme.typography.bodySmall, color = Palette.Muted,
            )
        }

        // ---- Xiaomi / HyperOS
        Section {
            Label(if (Setup.isXiaomi) "Telefon Xiaomi (HyperOS)" else "Funcționare în fundal")
            val batteryOk = remember(resumes) { Setup.batteryUnrestricted(context) }
            Text(
                "Ca HyperOS să nu oprească aplicația în timpul ședinței:",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (Setup.isXiaomi) {
                TextButton(onClick = { Setup.openAutostart(context) }) { Text("1. Pornire automată → Pornit") }
            }
            TextButton(onClick = { Setup.requestBatteryUnrestricted(context) }) {
                Text((if (batteryOk) "✅ " else "") + "2. Baterie → Fără restricții")
            }
            Text(
                "3. În Recente, ține apăsat pe Asistent Ședințe și apasă lacătul.",
                style = MaterialTheme.typography.bodySmall, color = Palette.Muted,
            )
        }

        // ---- Test without a meeting
        Section {
            Label("Test fără ședință")
            Text(
                "Scrie o replică așa cum ar apărea în subtitrări. Merge ca într-o ședință reală: alertă, răspuns, rezumat.",
                style = MaterialTheme.typography.bodySmall, color = Palette.Muted,
            )
            var speaker by remember { mutableStateOf("Radu") }
            val example = "${prefs.userName.ifBlank { "Maria" }}, poți să ne spui când e gata raportul?"
            var line by remember { mutableStateOf(example) }
            OutlinedTextField(speaker, { speaker = it }, label = { Text("Cine vorbește") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(line, { line = it }, label = { Text("Replica") }, modifier = Modifier.fillMaxWidth())
            Row(modifier = Modifier.padding(top = 6.dp)) {
                Button(onClick = { app.engine.testUtterance(line, speaker) }) { Text("Trimite replica") }
                Spacer(Modifier.padding(4.dp))
                OutlinedButton(onClick = { app.engine.endMeeting() }) { Text("Încheie testul") }
            }
        }

        // ---- Diagnostics
        Section {
            Label("Diagnostic")
            PrefSwitch("Jurnal diagnostic (textul citit de pe ecran)", prefs.diagnostics) { prefs.diagnostics = it }
            if (prefs.diagnostics) {
                PrefSwitch("Ascultă toate aplicațiile (pentru alte subtitrări)", prefs.diagnosticsAllApps) { prefs.diagnosticsAllApps = it }
            }
            var info by remember { mutableStateOf<String?>(null) }
            Row {
                OutlinedButton(onClick = {
                    scope.launch {
                        val uri = withContext(Dispatchers.IO) { runCatching { DiagLog.export(context) }.getOrNull() }
                        if (uri == null) info = "Exportul a eșuat." else Setup.shareFile(context, uri)
                    }
                }) { Text("Exportă jurnalul") }
                Spacer(Modifier.padding(4.dp))
                TextButton(onClick = {
                    DiagLog.clear()
                    info = "Jurnal șters."
                }) { Text("Șterge") }
            }
            if (DiagLog.hasCrashes()) Text("Există erori înregistrate: exportă jurnalul și trimite-l.", color = Palette.Amber, style = MaterialTheme.typography.bodySmall)
            info?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.Muted) }
        }

        // ---- Updates
        Section {
            Label("Versiune")
            Text("Asistent Ședințe ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
            var status by remember { mutableStateOf<String?>(null) }
            var update by remember { mutableStateOf<UpdateChecker.Update?>(null) }
            OutlinedButton(onClick = {
                status = "Caut…"
                scope.launch {
                    status = try {
                        update = UpdateChecker.check()
                        if (update == null) "Ai ultima versiune." else "Versiune nouă: ${update?.name}"
                    } catch (e: Exception) {
                        "Nu am putut verifica: ${e.message}"
                    }
                }
            }) { Text("Caută actualizări") }
            status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            update?.let { u -> Button(onClick = { Setup.openUrl(context, u.url) }) { Text("Descarcă actualizarea") } }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun PrefField(
    label: String,
    initial: String,
    hint: String? = null,
    lines: Int = 1,
    secret: Boolean = false,
    onChange: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    var reveal by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = {
            value = it
            onChange(it)
        },
        label = { Text(label) },
        placeholder = hint?.let { { Text(it, color = Palette.Muted) } },
        singleLine = lines == 1,
        minLines = lines,
        visualTransformation = if (secret && !reveal) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = if (secret) KeyboardOptions(keyboardType = KeyboardType.Password) else KeyboardOptions.Default,
        trailingIcon = if (secret) {
            { TextButton(onClick = { reveal = !reveal }) { Text(if (reveal) "Ascunde" else "Arată") } }
        } else null,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}

@Composable
private fun PrefSwitch(label: String, initial: Boolean, onChange: (Boolean) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = {
            value = it
            onChange(it)
        })
    }
}
