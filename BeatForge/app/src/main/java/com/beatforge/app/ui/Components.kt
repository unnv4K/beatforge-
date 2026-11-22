package com.beatforge.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

object BF {
    val bg = Color(0xFF17181A)
    val panel = Color(0xFF232529)
    val panel2 = Color(0xFF2E3137)
    val orange = Color(0xFFFF7A1A)
    val teal = Color(0xFF22D3C5)
    val text = Color(0xFFE8E8EA)
    val dim = Color(0xFF8D9096)
    val red = Color(0xFFFF4D4D)
}

@Composable
fun BeatTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = BF.orange, secondary = BF.teal, background = BF.bg,
            surface = BF.panel, onSurface = BF.text, onBackground = BF.text, onPrimary = Color.Black
        ),
        content = content
    )
}

@Composable
fun sliderColors() = SliderDefaults.colors(
    thumbColor = BF.orange, activeTrackColor = BF.orange, inactiveTrackColor = BF.panel2
)

@Composable
fun Chip(
    text: String, selected: Boolean, modifier: Modifier = Modifier,
    accent: Color = BF.orange, onClick: () -> Unit
) {
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) accent else BF.panel2)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text, color = if (selected) Color.Black else BF.text,
            fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center
        )
    }
}

@Composable
fun Card(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BF.panel)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title, color = BF.teal, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        content()
    }
}

/** Крутилка: тянуть вверх/вниз, двойной тап — сброс. */
@Composable
fun Knob(
    label: String, value: Float, display: String,
    modifier: Modifier = Modifier,
    min: Float = 0f, max: Float = 1f, default: Float = min,
    color: Color = BF.orange, size: Dp = 62.dp,
    onChange: (Float) -> Unit
) {
    val frac = ((value - min) / (max - min)).coerceIn(0f, 1f)
    val cur by rememberUpdatedState(frac)
    Column(modifier.padding(2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(size)
                .pointerInput(min, max) {
                    detectVerticalDragGestures { change, dy ->
                        change.consume()
                        val nf = (cur - dy / 260f).coerceIn(0f, 1f)
                        onChange(min + nf * (max - min))
                    }
                }
                .pointerInput(default) { detectTapGestures(onDoubleTap = { onChange(default) }) }
        ) {
            Canvas(Modifier.size(size)) {
                val sw = this.size.minDimension * 0.12f
                val inset = sw / 2 + 2f
                val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
                drawArc(BF.panel2, 135f, 270f, false, Offset(inset, inset), arcSize,
                    style = Stroke(sw, cap = StrokeCap.Round))
                drawArc(color, 135f, 270f * frac, false, Offset(inset, inset), arcSize,
                    style = Stroke(sw, cap = StrokeCap.Round))
                val ang = Math.toRadians((135f + 270f * frac).toDouble())
                val r = arcSize.width / 2 - sw
                val c = Offset(this.size.width / 2, this.size.height / 2)
                drawLine(
                    BF.text, c + Offset((cos(ang) * r * 0.35).toFloat(), (sin(ang) * r * 0.35).toFloat()),
                    c + Offset((cos(ang) * r).toFloat(), (sin(ang) * r).toFloat()),
                    strokeWidth = 4f, cap = StrokeCap.Round
                )
            }
        }
        Text(display, color = BF.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Text(label, color = BF.dim, fontSize = 11.sp)
    }
}

/** Вертикальный фейдер 0..1. */
@Composable
fun VFader(
    label: String, value: Float, display: String,
    modifier: Modifier = Modifier, height: Dp = 130.dp,
    color: Color = BF.orange, onChange: (Float) -> Unit
) {
    val cur by rememberUpdatedState(value)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(display, color = BF.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Box(
            Modifier
                .size(44.dp, height)
                .pointerInput(Unit) {
                    detectVerticalDragGestures { change, dy ->
                        change.consume()
                        onChange((cur - dy / (this.size.height * 0.9f)).coerceIn(0f, 1f))
                    }
                }
                .pointerInput(Unit) { detectTapGestures(onDoubleTap = { onChange(1f) }) }
        ) {
            Canvas(Modifier.size(44.dp, height)) {
                val w = this.size.width; val h = this.size.height
                drawRoundRect(BF.panel2, Offset(w / 2 - 4f, 0f), Size(8f, h),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f))
                val y = h * (1f - value)
                drawRoundRect(color, Offset(w / 2 - 4f, y), Size(8f, h - y),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f))
                drawRoundRect(BF.text, Offset(2f, y - 9f), Size(w - 4f, 18f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(5f, 5f))
                drawLine(Color.Black, Offset(8f, y), Offset(w - 8f, y), strokeWidth = 2f)
            }
        }
        Text(label, color = BF.dim, fontSize = 11.sp)
    }
}

/** Кнопка «пока держишь». */
@Composable
fun HoldButton(
    text: String, active: Boolean, modifier: Modifier = Modifier,
    onDown: () -> Unit, onUp: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val lit = pressed || active
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (lit) BF.teal else BF.panel2)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    pressed = true; onDown()
                    waitForUpOrCancellation()
                    pressed = false; onUp()
                }
            }
            .padding(vertical = 14.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (lit) Color.Black else BF.text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}
