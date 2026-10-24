package com.rerun.tv.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.rerun.tv.ui.theme.Retro

/** A D-pad friendly button: yellow border and lighter fill when focused. */
@Composable
fun TvButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    sub: String? = null,
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val bg = when {
        focused -> Retro.GuideBlueLight
        selected -> Color(0xFF14306E)
        else -> Color(0x66071341)
    }
    Box(
        modifier
            .scale(if (focused) 1.04f else 1f)
            .background(bg)
            .border(2.dp, if (focused) Retro.Yellow else if (selected) Retro.Cyan else Color(0x334A6BC8))
            .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Column {
            val color = when {
                !enabled -> Retro.Dim
                selected -> Retro.Cyan
                else -> Retro.White
            }
            BasicText((if (selected) "■ " else "") + text, style = Retro.Body.copy(color = color, shadow = null))
            if (sub != null) BasicText(sub, style = Retro.Caption)
        }
    }
}

/** Wordmark with the red beret, a nod to Rerun on What's Happening! */
@Composable
fun RerunWordmark(style: TextStyle = Retro.Osd.copy(color = Retro.Vcr)) {
    val beret = (style.fontSize.value * 0.62f).dp
    Box(Modifier.padding(top = beret * 0.28f)) {
        BasicText("RERUN", style = style)
        Canvas(
            Modifier
                .align(Alignment.TopStart)
                .offset(x = beret * 0.04f, y = -beret * 0.38f)
                .size(beret, beret * 0.62f),
        ) {
            val w = size.width
            val h = size.height
            val hat = Path().apply {
                moveTo(w * 0.02f, h * 0.78f)
                cubicTo(w * 0.02f, h * 0.4f, w * 0.28f, h * 0.08f, w * 0.55f, h * 0.16f)
                cubicTo(w * 0.9f, h * 0.26f, w * 1.02f, h * 0.62f, w * 0.84f, h * 0.8f)
                cubicTo(w * 0.58f, h * 1.02f, w * 0.14f, h * 0.98f, w * 0.02f, h * 0.78f)
                close()
            }
            drawPath(hat, Color(0xFFD0122D))
            drawCircle(Color(0xFF7A0C1C), radius = w * 0.055f, center = Offset(w * 0.58f, h * 0.22f))
        }
    }
}

/** Standard full-screen frame for the menu screens: blue gradient, title bar, hint footer. */
@Composable
fun TvFrame(title: String, hint: String, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Retro.GuideBlue, Retro.GuideBlueDeep)))) {
        Column(Modifier.fillMaxSize().padding(horizontal = 64.dp, vertical = 40.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RerunWordmark()
                Spacer(Modifier.width(28.dp))
                BasicText(title.uppercase(), style = Retro.Title.copy(color = Retro.Yellow))
            }
            Spacer(Modifier.height(16.dp))
            Box(Modifier.weight(1f).fillMaxWidth()) { content() }
            Spacer(Modifier.height(12.dp))
            BasicText(hint, style = Retro.Caption)
        }
    }
}

/**
 * Link and settings. The block is centered on the screen, and the screen size
 * follows the TV resolution, so 720p and 1080p land in the same place.
 */
@Composable
fun TvStage(hint: String, content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Retro.GuideBlue, Retro.GuideBlueDeep)))
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(horizontal = 64.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            RerunWordmark()
            content()
            BasicText(hint, style = Retro.Caption)
        }
    }
}
