package com.rerun.tv.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

/** Standard full-screen frame for the menu screens: blue gradient, title bar, hint footer. */
@Composable
fun TvFrame(title: String, hint: String, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Retro.GuideBlue, Retro.GuideBlueDeep)))) {
        Column(Modifier.fillMaxSize().padding(horizontal = 64.dp, vertical = 40.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText("RERUN", style = Retro.Osd.copy(color = Retro.Vcr))
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
