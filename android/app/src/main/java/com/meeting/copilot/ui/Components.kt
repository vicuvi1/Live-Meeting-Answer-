package com.meeting.copilot.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val CardShape = RoundedCornerShape(24.dp)
val InnerShape = RoundedCornerShape(16.dp)

/** Rounded surface with a soft border; [tone] tints it for warnings, questions, etc. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    tone: Tone = Tone.Neutral,
    onClick: (() -> Unit)? = null,
    padding: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val background = if (tone == Tone.Neutral) Palette.Surface else tone.tint
    val border = if (tone == Tone.Neutral) Palette.Border else tone.accent.copy(alpha = 0.35f)
    Column(
        modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(background)
            .border(1.dp, border, CardShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

@Composable
fun Eyebrow(text: String, color: Color = Palette.Muted, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = color, modifier = modifier)
}

@Composable
fun ScreenTitle(title: String, subtitle: String? = null) {
    Column(Modifier.padding(top = 8.dp, bottom = 16.dp)) {
        Text(title, style = MaterialTheme.typography.displaySmall, color = Palette.Text)
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
        }
    }
}

/** Dot that gently pulses while something is live. */
@Composable
fun StatusDot(color: Color, pulsing: Boolean, size: Dp = 12.dp) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val scale by transition.animateFloat(
        initialValue = 1f, targetValue = 2.4f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Restart), label = "scale",
    )
    val fade by transition.animateFloat(
        initialValue = 0.5f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Restart), label = "fade",
    )
    Box(Modifier.size(size * 2.6f), contentAlignment = Alignment.Center) {
        if (pulsing) {
            Box(
                Modifier.size(size * scale).alpha(fade).clip(CircleShape).background(color)
            )
        }
        Box(Modifier.size(size).clip(CircleShape).background(color))
    }
}

/** Small rounded label: "Zoom", "3 sarcini", "Ascult". */
@Composable
fun Pill(text: String, tone: Tone = Tone.Neutral, icon: ImageVector? = null) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(if (tone == Tone.Neutral) Palette.SurfaceHigh else tone.tint)
            .border(1.dp, tone.accent.copy(alpha = 0.3f), CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = tone.accent, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = if (tone == Tone.Neutral) Palette.TextSoft else tone.accent)
    }
}

enum class ButtonStyle { Filled, Tonal, Ghost }

@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ButtonStyle = ButtonStyle.Filled,
    tone: Tone = Tone.Success,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    height: Dp = 52.dp,
) {
    val accent = tone.accent
    val (background, foreground, border) = when (style) {
        ButtonStyle.Filled -> Triple(accent, Color(0xFF06110C), Color.Transparent)
        ButtonStyle.Tonal -> Triple(accent.copy(alpha = 0.14f), accent, accent.copy(alpha = 0.3f))
        ButtonStyle.Ghost -> Triple(Color.Transparent, Palette.TextSoft, Palette.Border)
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        color = background,
        border = BorderStroke(1.dp, border),
        modifier = modifier.heightIn(min = height).alpha(if (enabled) 1f else 0.45f),
    ) {
        Row(
            Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, null, tint = foreground, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge, color = foreground, textAlign = TextAlign.Center)
        }
    }
}

/** Big square-ish action with icon, title and hint: the two main buttons. */
@Composable
fun RowScope.ActionTile(
    title: String,
    hint: String,
    icon: ImageVector,
    tone: Tone,
    filled: Boolean,
    onClick: () -> Unit,
) {
    val background = if (filled) tone.accent else Palette.Surface
    val foreground = if (filled) Color(0xFF06110C) else Palette.Text
    val sub = if (filled) Color(0xFF06110C).copy(alpha = 0.7f) else Palette.Muted
    Surface(
        onClick = onClick,
        shape = CardShape,
        color = background,
        border = if (filled) null else BorderStroke(1.dp, Palette.Border),
        modifier = Modifier.weight(1f).heightIn(min = 128.dp),
    ) {
        Column(Modifier.padding(18.dp)) {
            Box(
                Modifier.size(40.dp).clip(CircleShape)
                    .background(if (filled) Color.Black.copy(alpha = 0.12f) else tone.accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = if (filled) foreground else tone.accent, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = foreground, fontWeight = FontWeight.Bold)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = sub)
        }
    }
}

/** Bullet list item with a coloured dot. */
@Composable
fun Bullet(text: String, color: Color = Palette.Blue, style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Box(Modifier.padding(top = 10.dp).size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(12.dp))
        Text(text, style = style, color = Palette.TextSoft)
    }
}

/** Grouped settings card with an icon header. */
@Composable
fun SettingsGroup(title: String, icon: ImageVector, tone: Tone = Tone.Info, content: @Composable ColumnScope.() -> Unit) {
    AppCard(padding = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(tone.accent.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = tone.accent, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = Palette.Text)
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    lines: Int = 1,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: androidx.compose.foundation.text.KeyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default,
    trailing: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, color = Palette.Faint) } },
        singleLine = lines == 1,
        minLines = lines,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        trailingIcon = trailing,
        shape = InnerShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Palette.Green,
            unfocusedBorderColor = Palette.Border,
            focusedLabelColor = Palette.Green,
            unfocusedLabelColor = Palette.Muted,
            focusedContainerColor = Palette.Background,
            unfocusedContainerColor = Palette.Background,
            cursorColor = Palette.Green,
        ),
        modifier = modifier.fillMaxWidth().padding(vertical = 5.dp),
    )
}

@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(InnerShape).clickable { onChange(!checked) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = Palette.Text)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        }
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF06110C), checkedTrackColor = Palette.Green,
                uncheckedThumbColor = Palette.Muted, uncheckedTrackColor = Palette.SurfaceHigh, uncheckedBorderColor = Palette.Border,
            ),
        )
    }
}

/** Two-option toggle, e.g. Gemini / OpenRouter. */
@Composable
fun Segmented(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(InnerShape).background(Palette.Background).border(1.dp, Palette.Border, InnerShape).padding(4.dp),
    ) {
        options.forEach { (key, label) ->
            val active = key == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                    .background(if (active) Palette.SurfaceHigh else Color.Transparent)
                    .clickable { onSelect(key) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = if (active) Palette.Text else Palette.Muted)
            }
        }
    }
}

/** Setup step: numbered circle (or check), title, hint and an action. */
@Composable
fun StepRow(number: Int, done: Boolean, title: String, hint: String?, action: String?, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(34.dp).clip(CircleShape)
                .background(if (done) Palette.Green else Palette.SurfaceHigh)
                .border(1.dp, if (done) Palette.Green else Palette.Border, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (done) Icon(Icons.Filled.Check, null, tint = Color(0xFF06110C), modifier = Modifier.size(19.dp))
            else Text("$number", style = MaterialTheme.typography.labelLarge, color = Palette.TextSoft)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = if (done) Palette.Muted else Palette.Text)
            if (hint != null && !done) Text(hint, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        }
        if (!done && action != null) {
            Spacer(Modifier.width(8.dp))
            AppButton(action, onAction, style = ButtonStyle.Tonal, height = 40.dp)
        }
    }
}

@Composable
fun ProgressBar(fraction: Float, color: Color = Palette.Green) {
    Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Palette.SurfaceHigh)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(6.dp).clip(CircleShape).background(color))
    }
}

/** Calm concentric rings for the "no meeting" state. */
@Composable
fun RingsIllustration(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(120.dp)) {
        val center = this.center
        listOf(1f to 0.08f, 0.72f to 0.14f, 0.46f to 0.24f).forEach { (r, a) ->
            drawCircle(color.copy(alpha = a), radius = size.minDimension / 2 * r, center = center)
        }
        drawCircle(color, radius = size.minDimension * 0.1f, center = center)
    }
}

val ScreenPadding = PaddingValues(horizontal = 18.dp)
