package com.meeting.copilot.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Design tokens. Near-black background (AMOLED friendly), soft surfaces, one accent per meaning. */
object Palette {
    val Background = Color(0xFF05070C)
    val Surface = Color(0xFF0F1420)
    val SurfaceHigh = Color(0xFF161D2D)
    val Border = Color(0xFF222B3D)
    val Text = Color(0xFFF4F6FB)
    val TextSoft = Color(0xFFC9D1E0)
    val Muted = Color(0xFF8A94A8)
    val Faint = Color(0xFF5B6478)

    val Green = Color(0xFF34D399)
    val GreenDeep = Color(0xFF0E3B2C)
    val Amber = Color(0xFFFBBF24)
    val AmberDeep = Color(0xFF3A2A06)
    val Red = Color(0xFFF87171)
    val RedDeep = Color(0xFF3F1010)
    val Blue = Color(0xFF7CA7FF)
    val BlueDeep = Color(0xFF14223F)
    val Violet = Color(0xFFB39DFF)

    /** Distinct, calm colours for speaker names in the transcript. */
    val Speakers = listOf(
        Color(0xFF7CA7FF), Color(0xFFF0A6CA), Color(0xFF8BE3C9),
        Color(0xFFFFC98B), Color(0xFFB39DFF), Color(0xFF9ED8F5),
    )

    fun speaker(name: String?): Color =
        if (name.isNullOrBlank()) Muted else Speakers[(name.hashCode() and 0x7fffffff) % Speakers.size]
}

/** Meaning of a card or badge: drives its accent, tint and border. */
enum class Tone(val accent: Color, val tint: Color) {
    Neutral(Palette.Muted, Palette.Surface),
    Success(Palette.Green, Palette.GreenDeep),
    Warning(Palette.Amber, Palette.AmberDeep),
    Danger(Palette.Red, Palette.RedDeep),
    Info(Palette.Blue, Palette.BlueDeep),
}

private val colors = darkColorScheme(
    primary = Palette.Green,
    onPrimary = Color(0xFF04140D),
    primaryContainer = Palette.GreenDeep,
    onPrimaryContainer = Palette.Green,
    secondary = Palette.Blue,
    background = Palette.Background,
    onBackground = Palette.Text,
    surface = Palette.Background,
    onSurface = Palette.Text,
    surfaceVariant = Palette.Surface,
    onSurfaceVariant = Palette.TextSoft,
    outline = Palette.Border,
    outlineVariant = Palette.Border,
    error = Palette.Red,
)

private val typography = Typography(
    displaySmall = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 18.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodySmall = TextStyle(fontSize = 14.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp),
)

@Composable
fun CopilotTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}
