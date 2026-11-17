package com.kithandkeen.qonverse.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sqrt

// ---------------------------------------------------------------- wallpaper

/** Seamless monoline isometric cubes, fading to nothing towards the top. */
@Composable
fun CubeWallpaper(modifier: Modifier = Modifier) {
    val tint = Q.c.wallpaper
    Canvas(
        modifier
            .fillMaxSize()
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    ) {
        val s = 17.dp.toPx()
        val c = cos(Math.PI / 6).toFloat()
        val w = 2 * s * c
        val stroke = Stroke(width = 1.dp.toPx(), join = StrokeJoin.Round, cap = StrokeCap.Round)
        val path = Path()
        var j = -1
        while (j * 1.5f * s < size.height + 2 * s) {
            var i = -1
            while (i * w < size.width + w) {
                val x = i * w + if (j % 2 != 0) w / 2 else 0f
                val y = j * 1.5f * s
                path.moveTo(x - s * c, y - s / 2); path.lineTo(x, y - s); path.lineTo(x + s * c, y - s / 2)
                path.lineTo(x + s * c, y + s / 2); path.lineTo(x, y + s); path.lineTo(x - s * c, y + s / 2); path.close()
                path.moveTo(x - s * c, y - s / 2); path.lineTo(x, y); path.lineTo(x + s * c, y - s / 2)
                path.moveTo(x, y); path.lineTo(x, y + s)
                i++
            }
            j++
        }
        drawPath(path, tint.copy(alpha = 0.18f), style = stroke)
        drawRect(
            Brush.verticalGradient(
                0f to Color.Transparent, 0.12f to Color.Transparent, 0.4f to Color.Black.copy(alpha = 0.25f),
                0.7f to Color.Black.copy(alpha = 0.75f), 1f to Color.Black,
            ),
            blendMode = BlendMode.DstIn,
        )
    }
}

// ---------------------------------------------------------------- logo

/** The bubble-Q: a round speech bubble with a curved tail. */
@Composable
fun BubbleQ(size: Dp, color: Color, strokeUnits: Float = 5.5f) {
    Canvas(Modifier.size(size)) {
        val u = this.size.width / 40f
        drawCircle(color, radius = 13.5f * u, center = Offset(19f * u, 19f * u), style = Stroke(strokeUnits * u))
        val tail = Path().apply {
            moveTo(24f * u, 31.2f * u)
            quadraticTo(28.6f * u, 37.2f * u, 38f * u, 37.6f * u)
            quadraticTo(32.6f * u, 33.2f * u, 31.4f * u, 24f * u)
            close()
        }
        drawPath(tail, color)
        drawPath(tail, color, style = Stroke(2f * u, join = StrokeJoin.Round))
    }
}

@Composable
fun Logo(textSize: Int = 24, qColor: Color = if (Q.c.dark) Q.c.boxText else Q.c.box, textColor: Color = Q.c.ink) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "Qonverse" },
    ) {
        BubbleQ((textSize * 1.08f).dp, qColor)
        Text(
            "onverse",
            color = textColor, fontSize = textSize.sp, fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.02 * textSize).sp,
            modifier = Modifier.offset(x = -(textSize * 0.06f).dp),
        )
    }
}

// ---------------------------------------------------------------- speech bubble

/** Rounded bubble with a small curved tail under one corner. Leaves [tail] of space at the bottom for it. */
class BubbleShape(private val mine: Boolean, private val radius: Dp = 18.dp, val tail: Dp = 12.dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        with(density) {
            val r = radius.toPx(); val t = tail.toPx()
            val h = size.height - t
            val body = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, h, CornerRadius(r, r))) }
            val x0 = if (mine) size.width - 16.dp.toPx() else 16.dp.toPx()
            val dir = if (mine) -1f else 1f
            val tp = Path().apply {
                moveTo(x0 + dir * 0f, h - 2f)
                lineTo(x0 + dir * 14.dp.toPx(), h - 2f)
                quadraticTo(x0 + dir * 4.dp.toPx(), h + t * 0.35f, x0 - dir * 4.dp.toPx(), h + t)
                quadraticTo(x0 + dir * 2.dp.toPx(), h + t * 0.4f, x0, h - 2f)
                close()
            }
            val all = Path().apply { op(body, tp, PathOperation.Union) }
            return Outline.Generic(all)
        }
    }
}

@Composable
fun Bubble(
    mine: Boolean,
    modifier: Modifier = Modifier,
    fill: Color,
    border: Color,
    content: @Composable () -> Unit,
) {
    val shape = BubbleShape(mine)
    Box(
        modifier
            .clip(shape)
            .background(fill, shape)
            .border(1.5.dp, border, shape)
            .padding(start = 14.dp, end = 12.dp, top = 10.dp, bottom = 10.dp + shape.tail)
    ) { content() }
}

// ---------------------------------------------------------------- buttons

@Composable
fun SpeakerButton(onClick: () -> Unit, size: Dp = 44.dp, label: String = "Play", big: Boolean = false) {
    val c = Q.c
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(c.audio)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { PlayIcon(if (big) size * 0.42f else size * 0.4f, c.onAudio) }
}

@Composable
fun PlayIcon(size: Dp, color: Color) {
    Canvas(Modifier.size(size)) {
        val p = Path().apply {
            moveTo(this@Canvas.size.width * 0.2f, this@Canvas.size.height * 0.1f)
            lineTo(this@Canvas.size.width * 0.9f, this@Canvas.size.height * 0.5f)
            lineTo(this@Canvas.size.width * 0.2f, this@Canvas.size.height * 0.9f)
            close()
        }
        drawPath(p, color)
    }
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val c = Q.c
    Box(
        modifier
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) c.box else c.line)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (enabled) c.onBox else c.muted, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun SecondaryButton(text: String, modifier: Modifier = Modifier, color: Color = Q.c.boxText, onClick: () -> Unit) {
    val c = Q.c
    Box(
        modifier
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(c.surface)
            .border(1.dp, c.line, RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = color, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center) }
}

@Composable
fun TopBar(title: String, onBack: (() -> Unit)?, trailing: @Composable () -> Unit = {}) {
    val c = Q.c
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClickLabel = "Back", onClick = onBack)
                    .semantics { contentDescription = "Back" },
                contentAlignment = Alignment.Center,
            ) { BackIcon(c.ink) }
        }
        Text(title, color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 4.dp))
        trailing()
    }
}

@Composable
fun BackIcon(color: Color) {
    Canvas(Modifier.size(22.dp)) {
        val w = size.width
        val st = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawLine(color, Offset(w * 0.8f, w / 2), Offset(w * 0.2f, w / 2), st.width, StrokeCap.Round)
        val p = Path().apply { moveTo(w * 0.45f, w * 0.25f); lineTo(w * 0.2f, w / 2); lineTo(w * 0.45f, w * 0.75f) }
        drawPath(p, color, style = st)
    }
}

@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier, height: Dp = 6.dp) {
    val c = Q.c
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height)).background(c.track)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(height).clip(RoundedCornerShape(height)).background(c.box))
    }
}

@Composable
fun Chip(text: String, fill: Color, textColor: Color) {
    Box(Modifier.clip(RoundedCornerShape(999.dp)).background(fill).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(text, color = textColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(text.uppercase(), color = Q.c.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
}

/** Target-language text with its transliteration and meaning, always together. */
@Composable
fun Tri(text: String, rom: String, meaning: String, textSize: Int = 16, color: Color = Q.c.ink, subColor: Color = Q.c.sub, mutedColor: Color = Q.c.muted) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text, color = color, fontSize = textSize.sp, fontWeight = FontWeight.Medium, lineHeight = (textSize * 1.45).sp)
        if (rom.isNotBlank()) Text(rom, color = subColor, fontSize = 13.sp)
        if (meaning.isNotBlank()) Text(meaning, color = mutedColor, fontSize = 13.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
    }
}

@Suppress("unused")
private fun hexLen(a: Float, b: Float) = sqrt(a * a + b * b)

@Composable
fun Spacer(h: Dp) = androidx.compose.foundation.layout.Spacer(Modifier.height(h))

@Composable
fun HSpacer(w: Dp) = androidx.compose.foundation.layout.Spacer(Modifier.width(w))
