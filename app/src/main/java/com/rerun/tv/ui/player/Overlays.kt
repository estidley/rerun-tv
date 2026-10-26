package com.rerun.tv.ui.player

import androidx.compose.foundation.Canvas
import com.rerun.tv.ui.common.ChannelIcon
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.style.TextAlign
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
import androidx.compose.ui.unit.sp
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

/** Set by the application so time formats honour the device's 12/24-hour setting; null falls back to the locale default. */
@Volatile
var clockContext: android.content.Context? = null

internal data class ClockFormatSettings(val locale: Locale, val use24Hour: Boolean?, val zoneId: String)

internal fun clockFormatSettings() = ClockFormatSettings(
    Locale.getDefault(),
    clockContext?.let { android.text.format.DateFormat.is24HourFormat(it) },
    java.util.TimeZone.getDefault().id,
)

private class LocaleFormats(val locale: Locale, val use24Hour: Boolean?) {
    val time = SimpleDateFormat(
        android.text.format.DateFormat.getBestDateTimePattern(locale, if (use24Hour == true) "Hmm" else if (use24Hour == false) "hmm a" else "jmm"),
        locale,
    )
    val date = SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEMMMd"), locale)
}

private var cachedFormats: LocaleFormats? = null

@Synchronized
private fun formats(): LocaleFormats {
    val locale = Locale.getDefault()
    val use24Hour = clockContext?.let { android.text.format.DateFormat.is24HourFormat(it) }
    return cachedFormats?.takeIf { it.locale == locale && it.use24Hour == use24Hour }
        ?: LocaleFormats(locale, use24Hour).also { cachedFormats = it }
}

/** Time of day in the device's locale and 12/24-hour convention; used everywhere a time is shown. */
@Synchronized
fun clockTime(ms: Long): String = formats().time.apply { timeZone = java.util.TimeZone.getDefault() }.format(Date(ms))

/** Weekday and date in the device's locale, e.g. "Mon, Oct 26". */
@Synchronized
fun clockDate(ms: Long): String = formats().date.apply { timeZone = java.util.TimeZone.getDefault() }.format(Date(ms))

/** "8:00 PM · in 12 min" for a program starting at [startMs]. */
fun upNextTiming(startMs: Long, nowMs: Long): String {
    val minutes = ((startMs - nowMs).coerceAtLeast(0) + 59_999) / 60_000
    val until = when {
        minutes <= 0 -> "starting now"
        minutes < 60 -> "in $minutes min"
        else -> "in ${minutes / 60} hr" + if (minutes % 60 > 0) " ${minutes % 60} min" else ""
    }
    return "${clockTime(startMs)}  ·  $until"
}

/** Channel icon with a small gray number beneath it in the top-right corner. */
@Composable
fun ChannelOsd(channel: ChannelRow?, modifier: Modifier = Modifier) {
    if (channel == null) return
    Column(modifier.padding(top = 36.dp, end = 56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        ChannelIcon(channel.id, size = 96.dp)
        RetroText(channel.number.toString(), style = Retro.ChannelNumber)
    }
}

/** Bottom info bar: what's on, how long is left, what's next. */
@Composable
fun InfoBanner(channel: ChannelRow?, item: MediaItem?, endsMs: Long?, next: MediaItem?, nextStartMs: Long? = endsMs, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 56.dp, vertical = 40.dp)
            .background(Brush.verticalGradient(listOf(Retro.GuideBlueLight, Retro.BannerBg), startY = 0f, endY = 18f))
            .border(1.dp, Retro.Vcr.copy(alpha = 0.35f))
            .padding(horizontal = 28.dp, vertical = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(130.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (channel != null) ChannelIcon(channel.id, size = 96.dp)
                BasicText(channel?.number?.toString() ?: "", style = Retro.ChannelNumber)
                BasicText(channel?.name ?: "", style = Retro.Caption.copy(fontSize = 16.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            }
            if (next != null) {
                var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
                LaunchedEffect(nextStartMs) {
                    while (true) { now = System.currentTimeMillis(); delay(15_000) }
                }
                Spacer(Modifier.width(24.dp))
                Column(Modifier.widthIn(max = 420.dp), horizontalAlignment = Alignment.End) {
                    BasicText("UP NEXT", style = Retro.Caption.copy(color = Retro.Yellow), maxLines = 1)
                    BasicText(describe(next), style = Retro.Body.copy(color = Retro.Cyan, textAlign = TextAlign.End), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    nextStartMs?.let { BasicText(upNextTiming(it, now), style = Retro.Caption, maxLines = 1) }
                }
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
