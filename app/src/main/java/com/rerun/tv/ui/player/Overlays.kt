package com.rerun.tv.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.rerun.core.model.MediaItem
import com.rerun.core.model.MediaKind
import com.rerun.tv.data.ChannelRow
import com.rerun.tv.ui.crt.RetroText
import com.rerun.tv.ui.theme.Retro
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun describe(item: MediaItem?): String = when {
    item == null -> "Coming up"
    item.kind == MediaKind.EPISODE -> {
        val se = if (item.season != null && item.episode != null) "  S${item.season}E${item.episode}" else ""
        "${item.showTitle ?: item.title}$se"
    }
    item.kind == MediaKind.TRACK -> listOfNotNull(item.studio, item.title).joinToString(" — ")
    else -> item.title + (item.year?.let { " ($it)" } ?: "")
}

private val clock = SimpleDateFormat("h:mm", Locale.US)
fun clockTime(ms: Long): String = clock.format(Date(ms))

/** "103  CN" in the top-right corner, VCR style. */
@Composable
fun ChannelOsd(channel: ChannelRow?, modifier: Modifier = Modifier) {
    if (channel == null) return
    Column(modifier.padding(top = 36.dp, end = 56.dp), horizontalAlignment = Alignment.End) {
        RetroText(channel.number.toString(), style = Retro.Osd)
        RetroText(channel.name.uppercase(), style = Retro.OsdSmall)
    }
}

/** Bottom info bar: what's on, how long is left, what's next. */
@Composable
fun InfoBanner(channel: ChannelRow?, item: MediaItem?, endsMs: Long?, next: MediaItem?, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 56.dp, vertical = 40.dp)
            .background(Brush.verticalGradient(listOf(Color(0xFF26304F), Retro.BannerBg), startY = 0f, endY = 18f))
            .border(1.dp, Color(0x5539FF14))
            .padding(horizontal = 28.dp, vertical = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(150.dp)) {
                BasicText(channel?.number?.toString() ?: "", style = Retro.Osd)
                BasicText(channel?.name ?: "", style = Retro.Caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(24.dp))
            Column(Modifier.weight(1f)) {
                BasicText(describe(item), style = Retro.Title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val bits = listOfNotNull(
                    item?.takeIf { it.kind == MediaKind.EPISODE }?.title,
                    item?.takeIf { it.kind == MediaKind.TRACK }?.showTitle,
                    item?.contentRating,
                    endsMs?.let { "ends ${clockTime(it)}" },
                )
                BasicText(bits.joinToString("  ·  "), style = Retro.Body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (next != null) BasicText("UP NEXT  ${describe(next)}", style = Retro.Caption.copy(color = Retro.Cyan), maxLines = 1)
            }
        }
    }
}

/** Shown during filler gaps (until commercials arrive) and when a slot can't play. */
@Composable
fun UpNextCard(channel: ChannelRow?, next: MediaItem?, untilMs: Long, posterUrl: String?, modifier: Modifier = Modifier) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(untilMs) {
        while (true) { now = System.currentTimeMillis(); delay(500) }
    }
    val left = ((untilMs - now) / 1000).coerceAtLeast(0)
    Box(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Retro.GuideBlue, Retro.GuideBlueDeep))), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(48.dp)) {
            if (posterUrl != null) {
                AsyncImage(
                    model = posterUrl, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(width = 240.dp, height = 360.dp).border(3.dp, Retro.Yellow),
                )
            }
            Column {
                BasicText(channel?.name?.uppercase() ?: "", style = Retro.GuideNumber)
                Spacer(Modifier.height(12.dp))
                BasicText("UP NEXT", style = Retro.Osd.copy(color = Retro.Yellow))
                BasicText(describe(next), style = Retro.Title, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(720.dp))
                Spacer(Modifier.height(24.dp))
                BasicText("%d:%02d".format(left / 60, left % 60), style = Retro.Osd)
            }
        }
    }
}

/** Empty schedule. The next stored program starts at [untilMs]. */
@Composable
fun OffAirCard(channel: ChannelRow?, next: MediaItem?, untilMs: Long, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Retro.GuideBlueDeep, Retro.Black))), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BasicText(channel?.name?.uppercase() ?: "", style = Retro.GuideNumber)
            Spacer(Modifier.height(16.dp))
            BasicText("OFF AIR", style = Retro.Osd.copy(color = Retro.Yellow))
            if (next != null) {
                Spacer(Modifier.height(20.dp))
                BasicText("Next  ${describe(next)}", style = Retro.Title, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(900.dp))
                BasicText("at ${clockTime(untilMs)}", style = Retro.Body.copy(color = Retro.Cyan))
            }
        }
    }
}

/** Classic color bars with the "technical difficulties" message. */
@Composable
fun DifficultiesCard(modifier: Modifier = Modifier) {
    val bars = listOf(
        Color(0xFFC0C0C0), Color(0xFFC0C000), Color(0xFF00C0C0), Color(0xFF00C000),
        Color(0xFFC000C0), Color(0xFFC00000), Color(0xFF0000C0),
    )
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val bw = size.width / bars.size
            bars.forEachIndexed { i, c -> drawRect(c, Offset(i * bw, 0f), Size(bw + 1, size.height * 0.75f)) }
            drawRect(Color(0xFF101010), Offset(0f, size.height * 0.75f), Size(size.width, size.height * 0.25f))
        }
        Column(
            Modifier.background(Color(0xE0000000)).padding(horizontal = 48.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicText("WE ARE EXPERIENCING", style = Retro.Title)
            BasicText("TECHNICAL DIFFICULTIES", style = Retro.Title)
            Spacer(Modifier.height(12.dp))
            BasicText("Please stand by", style = Retro.Body.copy(color = Retro.Vcr))
        }
    }
}
