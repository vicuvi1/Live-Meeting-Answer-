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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.meeting.copilot.App
import com.meeting.copilot.BuildConfig
import com.meeting.copilot.PowerTileService
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

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenTitle("Setări", "Totul rămâne pe telefon")

        SettingsGroup("Despre tine", Icons.Filled.Person, Tone.Success) {
            PrefField("Numele tău", prefs.userName, hint = "cum te strigă colegii") { prefs.userName = it }
            PrefField("Alte forme ale numelui", prefs.aliases, hint = "ex: Oli, doamna Popescu (cu virgulă)") { prefs.aliases = it }
            PrefField(
                "Notițe pentru răspunsuri", prefs.profileNotes,
                hint = "Rolul tău, proiectele și stadiul lor, cifre importante, ce nu ai voie să promiți.", lines = 4,
            ) { prefs.profileNotes = it }
            PrefField(
                "Nume și termeni din ședințe", prefs.vocabulary,
                hint = "ex: Radu Ionescu, proiectul Delta, SAP (corectează subtitrările)", lines = 2,
            ) { prefs.vocabulary = it }
            PrefField("Subiecte urmărite", prefs.topics, hint = "ex: buget, raport (vibrație scurtă)") { prefs.topics = it }
        }

        SettingsGroup("Inteligență artificială", Icons.Filled.Star, Tone.Info) {
            Text(
                "Pentru rezumat, corectarea subtitrărilor și răspunsuri.",
                style = MaterialTheme.typography.bodySmall, color = Palette.Muted, modifier = Modifier.padding(bottom = 10.dp),
            )
            var provider by remember { mutableStateOf(prefs.provider) }
            Segmented(
                listOf(Prefs.PROVIDER_GEMINI to "Google Gemini", Prefs.PROVIDER_OPENROUTER to "OpenRouter"),
                provider,
            ) {
                provider = it
                prefs.provider = it
            }
            Spacer(Modifier.height(6.dp))
            if (provider == Prefs.PROVIDER_GEMINI) {
                PrefField("Cheie Gemini", prefs.geminiKey, secret = true) { prefs.geminiKey = it }
                PrefField("Model (se actualizează singur)", prefs.geminiModel, hint = "gemini-3.8-flash") { prefs.geminiModel = it }
                TextButton(onClick = { Setup.openUrl(context, "https://aistudio.google.com/apikey") }) {
                    Text("Obține o cheie gratuită pe aistudio.google.com", color = Palette.Blue)
                }
            } else {
                PrefField("Cheie OpenRouter", prefs.openRouterKey, secret = true) { prefs.openRouterKey = it }
                PrefField("Model", prefs.openRouterModel, hint = "anthropic/claude-haiku-5.5") { prefs.openRouterModel = it }
                TextButton(onClick = { Setup.openUrl(context, "https://openrouter.ai/keys") }) {
                    Text("Obține o cheie pe openrouter.ai", color = Palette.Blue)
                }
            }
            var testResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
            var testing by remember { mutableStateOf(false) }
            AppButton(
                if (testing) "Testez…" else "Testează cheia",
                {
                    testing = true
                    testResult = null
                    scope.launch {
                        val started = System.currentTimeMillis()
                        testResult = try {
                            app.llm.json("Răspunde doar cu JSON.", "Returnează exact {\"ok\":true}", maxTokens = 50, timeoutMs = 20_000)
                            val model = if (prefs.provider == Prefs.PROVIDER_GEMINI) prefs.geminiModel else prefs.openRouterModel
                            true to "Funcționează: $model, ${System.currentTimeMillis() - started} ms"
                        } catch (e: Exception) {
                            false to (e.message ?: "Eroare")
                        }
                        testing = false
                    }
                },
                style = ButtonStyle.Tonal, tone = Tone.Info, enabled = !testing, modifier = Modifier.fillMaxWidth(),
            )
            testResult?.let { (ok, text) ->
                Text(
                    (if (ok) "✓ " else "✕ ") + text,
                    style = MaterialTheme.typography.bodySmall, color = if (ok) Palette.Green else Palette.Red,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Text(
                "Doar fragmentele necesare din ședință sunt trimise la serviciul AI ales.",
                style = MaterialTheme.typography.bodySmall, color = Palette.Faint, modifier = Modifier.padding(top = 10.dp),
            )
        }

        SettingsGroup("Pornire și oprire", Icons.Filled.Star, Tone.Success) {
            Text(
                "Pornește asistentul înainte de ședință; oprit, nu ascultă nimic și nu consumă baterie.",
                style = MaterialTheme.typography.bodySmall, color = Palette.Muted, modifier = Modifier.padding(bottom = 6.dp),
            )
            PrefSwitch(
                "Se oprește singur după ședință",
                "Ca să nu rămână pornit din greșeală",
                prefs.autoOff,
            ) { prefs.autoOff = it }
            var tileResult by remember { mutableStateOf<String?>(null) }
            AppButton(
                "Pune butonul Pornit/Oprit în panoul de sus",
                { PowerTileService.requestAdd(context) { tileResult = it } },
                style = ButtonStyle.Tonal, tone = Tone.Success, modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
            tileResult?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, modifier = Modifier.padding(top = 8.dp)) }
        }

        SettingsGroup("Subtitrări", Icons.Filled.PlayArrow, Tone.Info) {
            PrefSwitch(
                "Pornește subtitrările automat",
                "Apasă singură butonul CC în Zoom / Meet și alege limba română",
                prefs.autoCaptions,
            ) { prefs.autoCaptions = it }
        }

        SettingsGroup("Alerte", Icons.Filled.Notifications, Tone.Warning) {
            PrefSwitch("Vibrații", "Diferite pentru întrebare, mențiune, subiect", prefs.vibrate) { prefs.vibrate = it }
            PrefSwitch("Citește întrebarea în cască", "Doar când sunt conectate căști", prefs.whisperQuestion) { prefs.whisperQuestion = it }
            PrefSwitch("Caută pe web când e nevoie", "Pentru fapte actuale sau exacte; răspunsul verificat vine după câteva secunde", prefs.webSearch) { prefs.webSearch = it }
            PrefSwitch("Insula de sus (peste Zoom / Meet)", "Arată timpul, alertele și răspunsul în partea de sus", prefs.showBubble) { prefs.showBubble = it }
            Text(
                "Ceas sau brățară Xiaomi: în Mi Fitness → Notificări aplicații, pornește Asistent Ședințe.",
                style = MaterialTheme.typography.bodySmall, color = Palette.Faint, modifier = Modifier.padding(top = 6.dp),
            )
        }

        SettingsGroup(if (Setup.isXiaomi) "Telefon Xiaomi" else "Funcționare în fundal", Icons.Filled.Lock, Tone.Neutral) {
            val batteryOk = remember(resumes) { Setup.batteryUnrestricted(context) }
            Text(
                "Ca sistemul să nu oprească aplicația în timpul ședinței:",
                style = MaterialTheme.typography.bodySmall, color = Palette.Muted, modifier = Modifier.padding(bottom = 4.dp),
            )
            if (Setup.isXiaomi) {
                XiaomiSteps(prefs, resumes, firstNumber = 1)
            } else {
                StepRow(1, batteryOk, "Baterie fără restricții", "Ca să nu fie oprită în fundal", "Deschide") {
                    Setup.requestBatteryUnrestricted(context)
                }
                StepRow(2, false, "Blochează în Recente", "Ține apăsat pe aplicație în Recente → lacăt", null) {}
            }
        }

        SettingsGroup("Test fără ședință", Icons.Filled.PlayArrow, Tone.Success) {
            Text(
                "Scrie o replică așa cum ar apărea în subtitrări: primești alerta, răspunsul și rezumatul ca într-o ședință.",
                style = MaterialTheme.typography.bodySmall, color = Palette.Muted, modifier = Modifier.padding(bottom = 6.dp),
            )
            var speaker by remember { mutableStateOf("Radu") }
            val example = "${prefs.userName.trim().split(' ').firstOrNull()?.ifBlank { null } ?: "Maria"}, poți să ne spui când e gata raportul?"
            var line by remember { mutableStateOf(example) }
            AppTextField(speaker, { speaker = it }, "Cine vorbește")
            AppTextField(line, { line = it }, "Replica", lines = 2)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
                AppButton("Trimite", { app.engine.testUtterance(line, speaker) }, icon = Icons.Filled.Send, modifier = Modifier.weight(1f))
                AppButton("Încheie testul", { app.engine.endMeeting() }, style = ButtonStyle.Ghost, modifier = Modifier.weight(1f))
            }
        }

        SettingsGroup("Diagnostic", Icons.Filled.Build, Tone.Neutral) {
            PrefSwitch("Jurnal diagnostic", "Salvează textul citit de pe ecran, pentru depanare", prefs.diagnostics) { prefs.diagnostics = it }
            if (prefs.diagnostics) {
                PrefSwitch("Ascultă toate aplicațiile", "Pentru a găsi alte subtitrări", prefs.diagnosticsAllApps) { prefs.diagnosticsAllApps = it }
            }
            var info by remember { mutableStateOf<String?>(null) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
                AppButton(
                    "Exportă jurnalul",
                    {
                        scope.launch {
                            val uri = withContext(Dispatchers.IO) { runCatching { DiagLog.export(context) }.getOrNull() }
                            if (uri == null) info = "Exportul a eșuat." else Setup.shareFile(context, uri)
                        }
                    },
                    style = ButtonStyle.Tonal, tone = Tone.Info, modifier = Modifier.weight(1f),
                )
                AppButton("Șterge", {
                    DiagLog.clear()
                    info = "Jurnal șters."
                }, style = ButtonStyle.Ghost, modifier = Modifier.weight(1f))
            }
            if (DiagLog.hasCrashes()) {
                Text("Există erori înregistrate: exportă jurnalul și trimite-l.", color = Palette.Amber, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            }
            info?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, modifier = Modifier.padding(top = 8.dp)) }
        }

        SettingsGroup("Versiune ${BuildConfig.VERSION_NAME}", Icons.Filled.Info, Tone.Neutral) {
            var status by remember { mutableStateOf<String?>(null) }
            var update by remember { mutableStateOf<UpdateChecker.Update?>(null) }
            AppButton(
                "Caută actualizări",
                {
                    status = "Caut…"
                    scope.launch {
                        status = try {
                            update = UpdateChecker.check(force = true)
                            if (update == null) "Ai ultima versiune." else "Versiune nouă: ${update?.name}"
                        } catch (e: Exception) {
                            "Nu am putut verifica: ${e.message}"
                        }
                    }
                },
                style = ButtonStyle.Ghost, modifier = Modifier.fillMaxWidth(),
            )
            status?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, modifier = Modifier.padding(top = 8.dp)) }
            update?.let { u ->
                Spacer(Modifier.height(10.dp))
                UpdateCard(u, resumes)
            }
        }
        Spacer(Modifier.height(24.dp))
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
    // Keyed on the stored value so a change made by the app (model auto-switch) shows up.
    var value by remember(initial) { mutableStateOf(initial) }
    var reveal by remember { mutableStateOf(false) }
    AppTextField(
        value = value,
        onValueChange = {
            value = it
            onChange(it)
        },
        label = label,
        placeholder = hint,
        lines = lines,
        visualTransformation = if (secret && !reveal) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = if (secret) KeyboardOptions(keyboardType = KeyboardType.Password) else KeyboardOptions.Default,
        trailing = if (secret) {
            { TextButton(onClick = { reveal = !reveal }) { Text(if (reveal) "Ascunde" else "Arată", color = Palette.Blue) } }
        } else null,
    )
}

@Composable
private fun PrefSwitch(label: String, subtitle: String?, initial: Boolean, onChange: (Boolean) -> Unit) {
    var value by remember(initial) { mutableStateOf(initial) }
    SwitchRow(label, subtitle, value) {
        value = it
        onChange(it)
    }
}
