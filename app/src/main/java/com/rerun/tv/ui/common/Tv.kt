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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import com.rerun.tv.R
import com.rerun.tv.ui.theme.Retro
import kotlin.math.ceil

/** A D-pad friendly button: yellow border and lighter fill when focused. */
@Composable
fun TvButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    sub: String? = null,
    selectedIndicator: String? = if (selected) "■" else null,
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val bg = when {
        focused -> Retro.GuideBlueLight
        selected -> Retro.AlternateRow
        else -> Retro.GuideBlueDeep.copy(alpha = 0.4f)
    }
    Box(
        modifier
            .scale(if (focused) 1.04f else 1f)
            .background(bg)
            .border(2.dp, if (focused) Retro.Yellow else if (selected) Retro.Cyan else Retro.Border.copy(alpha = 0.4f))
            .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Column {
            val color = when {
                !enabled -> Retro.Dim
                selected -> Retro.Cyan
                else -> Retro.White
            }
            BasicText((selectedIndicator?.let { "$it " } ?: "") + text, style = Retro.Body.copy(color = color, shadow = null))
            if (sub != null) BasicText(sub, style = Retro.Caption)
        }
    }
}

/** Wordmark with the red beret, a nod to Rerun on What's Happening! */
@Composable
fun RerunWordmark(style: TextStyle = Retro.Osd.copy(color = Retro.Vcr)) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val typeface = remember(context) { ResourcesCompat.getFont(context, R.font.vt323) }
    val measurer = rememberTextMeasurer()
    val text = measurer.measure(AnnotatedString("RERUN"), style = style, maxLines = 1, softWrap = false)
    val fontPx = with(density) { style.fontSize.toPx() }
    val capBounds = remember(typeface, fontPx) {
        android.graphics.Rect().also { bounds ->
            android.graphics.Paint().apply {
                this.typeface = typeface
                textSize = fontPx
            }.getTextBounds("RE", 0, 2, bounds)
        }
    }
    val hatWidth = text.getHorizontalPosition(2, true) * 1.08f
    val hatHeight = hatWidth * 0.34f
    val overlap = fontPx * 0.085f
    // Anchor to the ink's cap height, not the font's invisible top padding.
    val capTop = text.firstBaseline + capBounds.top
    val textY = ceil(maxOf(0f, hatHeight - capTop - overlap))
    val hatY = textY + capTop + overlap - hatHeight
    Canvas(
        Modifier.size(
            with(density) { text.size.width.toDp() },
            with(density) { (text.size.height + textY).toDp() },
        ).semantics { contentDescription = "RERUN" },
    ) {
        val w = hatWidth
        val h = hatHeight
        val crown = Path().apply {
            moveTo(w * 0.02f, hatY + h * 0.79f)
            cubicTo(-w * 0.03f, hatY + h * 0.42f, w * 0.21f, hatY + h * 0.08f, w * 0.51f, hatY + h * 0.2f)
            cubicTo(w * 0.7f, hatY + h * 0.25f, w * 0.86f, hatY + h * 0.37f, w * 0.98f, hatY + h * 0.55f)
            cubicTo(w * 1.04f, hatY + h * 0.76f, w * 0.69f, hatY + h * 0.9f, w * 0.28f, hatY + h * 0.97f)
            cubicTo(w * 0.13f, hatY + h * 1.02f, w * 0.04f, hatY + h * 0.95f, w * 0.02f, hatY + h * 0.79f)
            close()
        }
        drawPath(crown, Color(0xFFD0122D))
        val brim = Path().apply {
            moveTo(w * 0.12f, hatY + h * 0.84f)
            quadraticTo(w * 0.53f, hatY + h * 0.87f, w * 0.9f, hatY + h * 0.63f)
            lineTo(w * 0.88f, hatY + h * 0.8f)
            quadraticTo(w * 0.51f, hatY + h * 1.02f, w * 0.14f, hatY + h)
            close()
        }
        drawPath(brim, Color(0xFF7A0C1C))
        drawLine(
            Color(0xFF7A0C1C),
            Offset(w * 0.53f, hatY + h * 0.23f), Offset(w * 0.5f, hatY + h * 0.07f),
            strokeWidth = w * 0.045f, cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
        drawText(text, topLeft = Offset(0f, textY))
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
