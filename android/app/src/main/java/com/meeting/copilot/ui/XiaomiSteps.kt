package com.meeting.copilot.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.meeting.copilot.data.Prefs
import com.meeting.copilot.util.Setup

/**
 * HyperOS settings that keep the app running through a meeting and its alerts visible.
 * Only the battery one can be checked by the app; the others she marks done after opening.
 */
@Composable
fun XiaomiSteps(prefs: Prefs, resumes: Int, firstNumber: Int) {
    val context = LocalContext.current
    prefs.version.collectAsState().value // recompose when a step is marked done
    val batteryOk = remember(resumes) { Setup.batteryUnrestricted(context) }

    ManualStep(
        firstNumber, prefs.xiaomiAutostartDone, "Pornire automată",
        "Pornește comutatorul pentru Asistent Ședințe",
        open = { Setup.openAutostart(context) },
    ) { prefs.xiaomiAutostartDone = true }
    StepRow(firstNumber + 1, batteryOk, "Baterie fără restricții", "Ca HyperOS să nu o oprească în ședință", "Deschide") {
        Setup.requestBatteryUnrestricted(context)
    }
    ManualStep(
        firstNumber + 2, prefs.xiaomiFloatingDone, "Notificări plutitoare",
        "La „Întrebări pentru tine”: pornește Notificări plutitoare și Ecran de blocare",
        open = { Setup.openQuestionAlertSettings(context) },
    ) { prefs.xiaomiFloatingDone = true }
    ManualStep(
        firstNumber + 3, prefs.xiaomiRecentsDone, "Blochează în Recente",
        "Deschide Recente, ține apăsat pe Asistent Ședințe și apasă lacătul",
        open = null,
    ) { prefs.xiaomiRecentsDone = true }
}

/** How many of the four HyperOS steps are done. */
fun xiaomiStepsDone(prefs: Prefs, context: Context): Int =
    listOf(prefs.xiaomiAutostartDone, Setup.batteryUnrestricted(context), prefs.xiaomiFloatingDone, prefs.xiaomiRecentsDone)
        .count { it }

/** A step the app can't verify: "Deschide" opens the right screen, then "Gata ✓" marks it done. */
@Composable
private fun ManualStep(number: Int, done: Boolean, title: String, hint: String, open: (() -> Unit)?, markDone: () -> Unit) {
    var opened by remember { mutableStateOf(open == null) }
    StepRow(number, done, title, hint, if (opened) "Gata ✓" else "Deschide") {
        if (opened) {
            markDone()
        } else {
            open?.invoke()
            opened = true
        }
    }
}
