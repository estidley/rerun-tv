package com.rerun.tv.ui.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import coil3.compose.AsyncImage
import com.rerun.core.model.MediaItem
import com.rerun.tv.ui.theme.Retro
import kotlinx.coroutines.delay

internal enum class MusicCue(val label: String) {
    GROOVE("GROOVE"), AMPLIFIED("AMPLIFIED"), LOUNGE("LOUNGE"),
    ELECTRONIC("ELECTRONIC"), CLASSICAL("CLASSICAL"), MIX("MIX");

    companion object {
        fun from(genre: String): MusicCue = when {
            listOf("soul", "funk", "disco", "r&b", "hip").any { genre.contains(it, ignoreCase = true) } -> GROOVE
            listOf("rock", "metal", "punk").any { genre.contains(it, ignoreCase = true) } -> AMPLIFIED
            listOf("jazz", "blues", "country").any { genre.contains(it, ignoreCase = true) } -> LOUNGE
            listOf("electro", "dance", "techno", "ambient").any { genre.contains(it, ignoreCase = true) } -> ELECTRONIC
            listOf("classical", "opera", "orchestral").any { genre.contains(it, ignoreCase = true) } -> CLASSICAL
            else -> MIX
        }
    }
}

internal fun musicTime(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1000
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

@Composable
private fun MusicProgress(item: MediaItem, positionMs: Long, position: (() -> Long)?, accent: Color, secondary: Color) {
    var playingPosition by remember(item.key, position) { mutableLongStateOf(position?.invoke() ?: positionMs) }
    LaunchedEffect(item.key, position) {
        if (position != null) {
            while (true) {
                playingPosition = position()
                delay(250)
            }
        }
    }
    val current = if (position == null) positionMs else playingPosition
    BasicText(
        "${musicTime(current.coerceIn(0, item.durationMs.coerceAtLeast(0)))} / ${musicTime(item.durationMs)}",
        style = Retro.Body.copy(color = secondary, shadow = null),
    )
    Box(Modifier.fillMaxWidth().height(4.dp).background(Retro.GuideBlueLight)) {
        val progress = if (item.durationMs > 0) (current.toFloat() / item.durationMs).coerceIn(0f, 1f) else 0f
        Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(accent))
    }
}

/** All artwork, text, and decoration drift together; nothing bright stays fixed on the CRT. */
@Composable
internal fun MusicScreen(item: MediaItem, channelName: String?, art: String?, positionMs: Long, position: (() -> Long)? = null) {
    val genre = channelName ?: item.genres.firstOrNull() ?: "Music"
    val cue = MusicCue.from(genre)
    val decade = item.year?.let { it / 10 * 10 }
    val accent = if (cue.ordinal % 2 == 0) Retro.Yellow else Retro.Cyan
    val secondary = if (cue.ordinal % 2 == 0) Retro.Cyan else Retro.Yellow
    val motion = rememberInfiniteTransition(label = "music-drift")
    val x by motion.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(90_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "horizontal-drift",
    )
    val y by motion.animateFloat(
        initialValue = 1f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(117_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "vertical-drift",
    )
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val cardWidth = maxWidth * 0.7f
        val cardHeight = maxHeight * 0.62f
        val artSize = minOf(cardWidth * 0.34f, cardHeight * 0.67f)
        Column(
            Modifier.offset {
                IntOffset(
                    ((maxWidth - cardWidth).toPx() * (0.08f + x * 0.84f)).roundToInt(),
                    ((maxHeight - cardHeight).toPx() * (0.08f + y * 0.84f)).roundToInt(),
                )
            }.size(cardWidth, cardHeight).testTag("music-card").padding(16.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            BasicText(
                listOfNotNull(genre, decade?.let { "${it}s" }, cue.label).joinToString("  /  "),
                style = Retro.Body.copy(color = accent, shadow = null),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(artSize).border(2.dp, accent).padding(6.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val radius = size.minDimension * 0.46f
                        drawCircle(secondary.copy(alpha = 0.35f), radius)
                        for (ring in 1..5) {
                            drawCircle(Color.Black, radius * ring / 6f, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                        }
                        drawCircle(accent, radius * 0.25f)
                        drawCircle(Color.Black, radius * 0.045f)
                    }
                    var failed by remember(art) { mutableStateOf(false) }
                    if (art != null && !failed) {
                        AsyncImage(
                            art, "Album art: ${item.showTitle ?: item.title}",
                            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit,
                            onError = { failed = true },
                        )
                    } else {
                        BasicText("NO ALBUM ART", style = Retro.Caption.copy(color = accent), modifier = Modifier.align(Alignment.BottomCenter))
                    }
                }
                Spacer(Modifier.width(28.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    BasicText(
                        item.title, style = Retro.Title.copy(fontSize = 48.sp, color = accent, shadow = null),
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    BasicText(
                        item.studio ?: "Unknown artist", style = Retro.Title.copy(shadow = null),
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    BasicText(
                        item.showTitle ?: "Unknown album", style = Retro.Body.copy(color = Retro.Dim, shadow = null),
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    MusicProgress(item, positionMs, position, accent, secondary)
                }
            }
            Spacer(Modifier.height(16.dp))
            Canvas(Modifier.fillMaxWidth().height(20.dp)) {
                val count = when (cue) {
                    MusicCue.GROOVE -> 12
                    MusicCue.AMPLIFIED -> 24
                    MusicCue.LOUNGE -> 6
                    MusicCue.ELECTRONIC -> 32
                    MusicCue.CLASSICAL -> 4
                    MusicCue.MIX -> 16
                }
                for (i in 0 until count) {
                    val px = size.width * (i + 0.5f) / count
                    if (decade != null && decade < 1980 || cue == MusicCue.GROOVE || cue == MusicCue.LOUNGE) {
                        drawCircle(secondary.copy(alpha = 0.5f), size.height * 0.2f, Offset(px, size.height / 2))
                        if (decade == 1960) {
                            drawCircle(secondary.copy(alpha = 0.35f), size.height * 0.4f, Offset(px, size.height / 2),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                        }
                    } else {
                        val slant = if (decade == 1980) size.height * 0.4f else 0f
                        drawLine(secondary.copy(alpha = 0.5f), Offset(px, size.height), Offset(px + slant, size.height * (i % 3) / 3f), 2.dp.toPx())
                    }
                }
            }
        }
    }
}
