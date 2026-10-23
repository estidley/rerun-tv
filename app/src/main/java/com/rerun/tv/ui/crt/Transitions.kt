package com.rerun.tv.ui.crt

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.offset

/**
 * Old-set power transitions. On first composition: a bright horizontal line opens to full
 * height (350ms). When [on] turns false: the picture collapses to a line, then to a dot (300ms),
 * then [onOffDone] runs.
 */
@Composable
fun PowerTransition(on: Boolean, onOffDone: () -> Unit) {
    val open = remember { Animatable(0f) }
    val close = remember { Animatable(0f) }
    LaunchedEffect(Unit) { open.animateTo(1f, tween(350, easing = FastOutSlowInEasing)) }
    LaunchedEffect(on) {
        if (!on) {
            close.animateTo(1f, tween(300, easing = LinearEasing))
            onOffDone()
        }
    }
    val p = open.value
    val q = close.value
    if (p >= 1f && q <= 0f) return
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (q > 0f) {
            // 0–0.7: collapse vertically to a 2px line; 0.7–1: shrink the line to a dot.
            val v = (q / 0.7f).coerceAtMost(1f)
            val lineH = maxOf(2f, h * (1f - v))
            val horiz = ((q - 0.7f) / 0.3f).coerceIn(0f, 1f)
            val lineW = maxOf(4f, w * (1f - horiz))
            drawRect(Color.Black)
            drawRect(
                Color.White.copy(alpha = 0.35f + 0.65f * v),
                topLeft = Offset((w - lineW) / 2, (h - lineH) / 2),
                size = Size(lineW, lineH),
            )
        } else {
            val bandH = maxOf(2f, h * p)
            val top = (h - bandH) / 2
            drawRect(Color.Black, size = Size(w, top))
            drawRect(Color.Black, topLeft = Offset(0f, top + bandH), size = Size(w, h - top - bandH))
            drawRect(Color.White.copy(alpha = (1f - p) * 0.85f), topLeft = Offset(0f, top), size = Size(w, bandH))
        }
    }
}

/**
 * Lost vertical hold: a dark band rolls down the screen while a channel is tuning.
 * Replays every time [key] changes.
 */
@Composable
fun VerticalRoll(active: Boolean, key: Long) {
    val roll = remember(key) { Animatable(0f) }
    LaunchedEffect(key, active) {
        if (active) roll.animateTo(1f, tween(250, easing = LinearEasing))
    }
    if (!active || roll.value >= 1f) return
    Canvas(Modifier.fillMaxSize()) {
        val bandH = size.height * 0.15f
        val y = -bandH + (size.height + bandH) * roll.value
        drawRect(Color.Black.copy(alpha = 0.85f), topLeft = Offset(0f, y), size = Size(size.width, bandH))
        drawRect(Color.White.copy(alpha = 0.18f), topLeft = Offset(0f, y + bandH), size = Size(size.width, 3f))
    }
}

/** On-screen text that picks up a slight red/blue fringe when the CRT level is FULL. */
@Composable
fun RetroText(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    val level = LocalCrtLevel.current
    Box(modifier) {
        if (level == CrtLevel.FULL) {
            BasicText(text, style = style.copy(color = Color(0x99FF2020), shadow = null), modifier = Modifier.offset { IntOffset(-1.dp.roundToPx(), 0) })
            BasicText(text, style = style.copy(color = Color(0x992050FF), shadow = null), modifier = Modifier.offset { IntOffset(1.dp.roundToPx(), 0) })
        }
        BasicText(text, style = style)
    }
}

/** Provides the current CRT level to [RetroText] below it. */
@Composable
fun ProvideCrtLevel(level: CrtLevel, content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalCrtLevel provides level, content = content)
