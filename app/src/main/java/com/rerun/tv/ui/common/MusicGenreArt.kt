package com.rerun.tv.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rerun.tv.ui.theme.Retro
import java.util.Locale
import kotlin.math.min

private val Ink = Color(0xFF161327)
private val Cream = Color(0xFFFFF0CE)
private val genreAccents = listOf(
    Color(0xFF5185EF), Color(0xFFEC4A3E), Color(0xFF9A63ED), Color(0xFF36D3BD),
    Color(0xFFFFBE43), Color(0xFFE66CB6), Color(0xFFD8FA3E), Color(0xFFE88A44),
)

private fun genreAccent(channelId: String) = genreAccents[Math.floorMod(channelId.hashCode(), genreAccents.size)]

private fun genreInitials(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.any(Char::isLetterOrDigit) }
    val initials = if (words.size > 1) words.mapNotNull { word -> word.firstOrNull(Char::isLetterOrDigit) }.joinToString("")
    else name.filter(Char::isLetterOrDigit)
    return initials.take(2).uppercase(Locale.ROOT).ifEmpty { "M" }
}

private fun DrawScope.genreRecord(center: Offset, radius: Float, accent: Color) {
    drawCircle(Ink, radius + 2.dp.toPx(), center)
    drawCircle(accent, radius, center)
    drawCircle(Cream, radius, center, style = Stroke(width = radius * 0.055f))
    drawCircle(Ink, radius * 0.64f, center)
    drawCircle(Cream, radius * 0.30f, center)
    drawCircle(Ink, radius * 0.075f, center)
    drawArc(
        Cream, startAngle = 210f, sweepAngle = 100f, useCenter = false,
        topLeft = Offset(center.x - radius * 0.79f, center.y - radius * 0.79f),
        size = androidx.compose.ui.geometry.Size(radius * 1.58f, radius * 1.58f),
        style = Stroke(width = radius * 0.045f),
    )
}

@Composable
internal fun MusicGenreIcon(channelId: String, name: String, modifier: Modifier = Modifier) {
    val accent = remember(channelId) { genreAccent(channelId) }
    val initials = remember(name) { genreInitials(name) }
    BoxWithConstraints(modifier) {
        val fontSize = min(maxWidth.value, maxHeight.value) * 0.23f
        Canvas(Modifier.fillMaxSize()) {
            genreRecord(Offset(size.width / 2, size.height / 2), min(size.width, size.height) * 0.43f, accent)
        }
        BasicText(
            initials, Modifier.align(Alignment.Center).clearAndSetSemantics {},
            style = Retro.Body.copy(fontFamily = Retro.ReadableFont, fontWeight = FontWeight.Medium, color = Ink,
                fontSize = fontSize.sp, shadow = null),
            maxLines = 1, softWrap = false,
        )
    }
}

@Composable
internal fun MusicGenreWordmark(channelId: String, name: String, modifier: Modifier = Modifier) {
    val accent = remember(channelId) { genreAccent(channelId) }
    val label = remember(name) { name.trim().uppercase(Locale.ROOT).ifEmpty { "MUSIC" } }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val fontSize = minOf(maxHeight.value * 0.19f, maxWidth.value * 0.78f / (label.length * 0.60f))
            .coerceAtLeast(10f)
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            genreRecord(Offset(w / 2, h * 0.26f), h * 0.235f, accent)
            val banner = Path().apply {
                moveTo(w * 0.035f, h * 0.548f)
                lineTo(w * 0.935f, h * 0.504f)
                lineTo(w * 0.965f, h * 0.867f)
                lineTo(w * 0.06f, h * 0.904f)
                close()
            }
            drawPath(banner, accent)
            drawPath(banner, Cream, style = Stroke(width = 2.dp.toPx()))
        }
        BasicText(
            label,
            Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                .padding(horizontal = maxWidth * 0.07f).padding(bottom = maxHeight * 0.15f),
            style = Retro.Body.copy(color = Ink, fontWeight = FontWeight.Medium,
                fontSize = fontSize.sp, textAlign = TextAlign.Center, shadow = null),
            maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false,
        )
    }
}
