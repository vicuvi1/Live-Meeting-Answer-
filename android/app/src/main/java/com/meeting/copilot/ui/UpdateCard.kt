package com.meeting.copilot.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.meeting.copilot.util.UpdateChecker
import kotlinx.coroutines.launch
import java.io.File

/**
 * One-tap update: downloads the new version inside the app, then opens Android's
 * "Update this app?" screen. The first time, Android asks to allow installs from this app;
 * when she comes back from that screen the install continues by itself.
 */
@Composable
fun UpdateCard(update: UpdateChecker.Update, resumes: Int) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var progress by remember { mutableStateOf<Int?>(null) }
    var file by remember { mutableStateOf<File?>(null) }
    var needsPermission by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun installNow(apk: File) {
        if (!UpdateChecker.canInstall(context)) {
            needsPermission = true
            UpdateChecker.openInstallPermission(context)
        } else if (!UpdateChecker.install(context, apk)) {
            error = "Nu am putut deschide instalarea."
        }
    }

    // Back from "Install unknown apps": continue without another tap.
    LaunchedEffect(resumes) {
        val apk = file
        if (needsPermission && apk != null && UpdateChecker.canInstall(context)) {
            needsPermission = false
            UpdateChecker.install(context, apk)
        }
    }

    val downloading = progress != null && file == null
    AppCard(tone = Tone.Info) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Star, null, tint = Palette.Blue, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text("Versiune nouă: ${update.name}", style = MaterialTheme.typography.titleSmall, color = Palette.Text, modifier = Modifier.weight(1f))
        }
        Text(
            when {
                downloading -> "Se descarcă… ${progress}%"
                needsPermission -> "Pornește „Permite din această sursă”, apoi revino: instalarea continuă singură."
                file != null -> "Descărcată. Apasă Actualizează în fereastra Android."
                else -> "Un singur apăsat: se descarcă și se instalează. Setările și istoricul rămân."
            },
            style = MaterialTheme.typography.bodySmall, color = Palette.Muted,
            modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
        )
        if (downloading) {
            ProgressBar((progress ?: 0) / 100f, Palette.Blue)
            Spacer(Modifier.height(12.dp))
        }
        error?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.Red, modifier = Modifier.padding(bottom = 10.dp))
        }
        AppButton(
            text = when {
                downloading -> "Se descarcă…"
                file != null -> "Instalează"
                error != null -> "Încearcă din nou"
                else -> "Actualizează acum"
            },
            onClick = {
                error = null
                val ready = file
                if (ready != null) {
                    installNow(ready)
                    return@AppButton
                }
                progress = 0
                scope.launch {
                    try {
                        val apk = UpdateChecker.download(context, update) { progress = it }
                        file = apk
                        installNow(apk)
                    } catch (e: Exception) {
                        progress = null
                        error = "Descărcarea a eșuat: ${e.message}. Verifică internetul."
                    }
                }
            },
            tone = Tone.Info,
            enabled = !downloading,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
