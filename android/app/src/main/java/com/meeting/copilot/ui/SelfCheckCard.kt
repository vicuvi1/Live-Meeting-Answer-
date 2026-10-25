package com.meeting.copilot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.meeting.copilot.App
import com.meeting.copilot.util.SelfCheck
import com.meeting.copilot.util.SelfCheck.Fix
import com.meeting.copilot.util.SelfCheck.Result
import com.meeting.copilot.util.Setup
import kotlinx.coroutines.launch

/** "Verifică tot": one tap before a meeting, a green or red line for everything the app needs. */
@Composable
fun SelfCheckCard(app: App, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var checks by remember { mutableStateOf<List<SelfCheck.Check>?>(null) }
    var running by remember { mutableStateOf(false) }

    fun fix(fix: Fix) {
        when (fix) {
            Fix.TURN_ON -> app.engine.setActive(true)
            Fix.ACCESSIBILITY -> Setup.openAccessibility(context)
            Fix.NOTIFICATIONS -> (context as? MainActivity)?.requestNotifications() ?: Setup.openAppInfo(context)
            Fix.QUESTION_SOUND -> Setup.openQuestionAlertSettings(context)
            Fix.BATTERY -> Setup.requestBatteryUnrestricted(context)
            Fix.XIAOMI -> Setup.openAutostart(context)
            Fix.SETTINGS -> onOpenSettings()
        }
    }

    val results = checks
    val failures = results?.count { it.result == Result.FAIL } ?: 0
    val warnings = results?.count { it.result == Result.WARN } ?: 0
    val tone = when {
        results == null || running -> Tone.Neutral
        failures > 0 -> Tone.Danger
        warnings > 0 -> Tone.Warning
        else -> Tone.Success
    }
    AppCard(tone = tone) {
        Text(
            when {
                running -> "Verific…"
                results == null -> "Verifică tot"
                failures > 0 -> if (failures == 1) "O problemă de rezolvat" else "$failures probleme de rezolvat"
                warnings > 0 -> "Merge, cu ${if (warnings == 1) "o atenționare" else "$warnings atenționări"}"
                else -> "Totul e gata pentru ședință"
            },
            style = MaterialTheme.typography.titleMedium, color = Palette.Text,
        )
        Text(
            if (results == null) "Apasă înainte de ședință: verific tot ce trebuie să meargă, cu un test real de alertă și de AI."
            else "Rezolvă ce e roșu, apoi verifică din nou.",
            style = MaterialTheme.typography.bodySmall, color = Palette.Muted,
            modifier = Modifier.padding(top = 4.dp),
        )
        results?.forEach { check -> CheckRow(check, onFix = { fix(it) }) }
        Spacer(Modifier.height(12.dp))
        AppButton(
            text = when {
                running -> "Verific…"
                results == null -> "Verifică tot"
                else -> "Verifică din nou"
            },
            onClick = {
                running = true
                checks = emptyList()
                scope.launch {
                    try {
                        SelfCheck.run(context, app) { checks = it }
                    } finally {
                        running = false
                    }
                }
            },
            enabled = !running,
            style = if (results == null) ButtonStyle.Filled else ButtonStyle.Tonal,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CheckRow(check: SelfCheck.Check, onFix: (Fix) -> Unit) {
    val (color, icon) = when (check.result) {
        Result.OK -> Palette.Green to Icons.Filled.Check
        Result.WARN -> Palette.Amber to Icons.Filled.Warning
        Result.FAIL -> Palette.Red to Icons.Filled.Close
    }
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(26.dp).clip(CircleShape).background(color.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(check.title, style = MaterialTheme.typography.titleSmall, color = Palette.Text)
            Text(check.detail, style = MaterialTheme.typography.bodySmall, color = if (check.result == Result.OK) Palette.Muted else Palette.TextSoft)
        }
        val fix = check.fix
        if (fix != null && check.result != Result.OK) {
            Spacer(Modifier.width(8.dp))
            AppButton(fix.label, { onFix(fix) }, style = ButtonStyle.Tonal, height = 36.dp)
        }
    }
}
