package com.meeting.copilot.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Palette {
    val Green = Color(0xFF22C55E)
    val Red = Color(0xFFF87171)
    val Amber = Color(0xFFFBBF24)
    val Blue = Color(0xFF60A5FA)
    val Muted = Color(0xFF94A3B8)
    val Card = Color(0xFF0B1220)
    val CardAlert = Color(0xFF3B0A0A)
    val CardWarn = Color(0xFF2B1D05)
}

// AMOLED black background: pixels stay off and text has maximum contrast.
private val colors = darkColorScheme(
    primary = Palette.Green,
    onPrimary = Color.Black,
    secondary = Palette.Blue,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color.White,
    surfaceVariant = Palette.Card,
    onSurfaceVariant = Color(0xFFE2E8F0),
    error = Palette.Red,
)

private val typography = Typography(
    headlineSmall = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 18.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontSize = 14.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
)

@Composable
fun CopilotTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}

@Composable
fun Section(
    modifier: Modifier = Modifier,
    color: Color = Palette.Card,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = color),
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun Label(text: String, color: Color = Palette.Muted) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = color)
    Spacer(Modifier.height(4.dp))
}
