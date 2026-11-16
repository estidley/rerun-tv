package com.rerun.tv.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rerun.tv.R
import com.rerun.tv.ui.theme.Retro
import java.util.Locale
import kotlin.math.min

private val CustomInk = Color(0xFF161327)
private val CustomCream = Color(0xFFFFF0CE)
private val CustomPink = Color(0xFFE66CB6)

private fun customInitials(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.any(Char::isLetterOrDigit) }
    val initials = if (words.size > 1) {
        words.mapNotNull { word -> word.firstOrNull(Char::isLetterOrDigit) }.joinToString("")
    } else {
        name.filter(Char::isLetterOrDigit)
    }
    return initials.take(2).uppercase(Locale.ROOT).ifEmpty { "C" }
}

@Composable
internal fun CustomChannelIcon(name: String, modifier: Modifier = Modifier) {
    val initials = remember(name) { customInitials(name) }
    BoxWithConstraints(modifier) {
        Image(painterResource(R.drawable.channel_custom_generic), contentDescription = null, modifier = Modifier.fillMaxSize())
        val fontSize = min(maxWidth.value, maxHeight.value) * 0.17f
        BasicText(
            initials,
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(CustomCream)
                .padding(horizontal = 2.dp).clearAndSetSemantics {},
            style = Retro.Body.copy(color = CustomInk, fontWeight = FontWeight.Medium,
                fontSize = fontSize.sp, textAlign = TextAlign.Center, shadow = null),
            maxLines = 1, softWrap = false,
        )
    }
}

@Composable
internal fun CustomChannelWordmark(name: String, modifier: Modifier = Modifier) {
    val label = remember(name) { name.trim().uppercase(Locale.ROOT).ifEmpty { "CUSTOM" } }
    BoxWithConstraints(modifier.fillMaxSize()) {
        Image(
            painterResource(R.drawable.channel_custom_generic),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(0.32f).aspectRatio(1f),
        )
        val fontSize = minOf(maxHeight.value * 0.19f, maxWidth.value * 0.78f / (label.length * 0.30f))
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val banner = Path().apply {
                moveTo(w * 0.035f, h * 0.548f)
                lineTo(w * 0.935f, h * 0.504f)
                lineTo(w * 0.965f, h * 0.867f)
                lineTo(w * 0.06f, h * 0.904f)
                close()
            }
            drawPath(banner, CustomPink)
            drawPath(banner, CustomCream, style = Stroke(width = 2.dp.toPx()))
        }
        BasicText(
            label,
            Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                .padding(horizontal = maxWidth * 0.07f).padding(bottom = maxHeight * 0.15f),
            style = Retro.Body.copy(color = CustomInk, fontWeight = FontWeight.Medium,
                fontSize = fontSize.sp, textAlign = TextAlign.Center, shadow = null),
            maxLines = 2, overflow = TextOverflow.Clip, softWrap = true,
        )
    }
}
