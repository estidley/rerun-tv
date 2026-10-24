package com.rerun.tv.ui.guide

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.rerun.core.model.MediaItem
import com.rerun.core.model.MediaKind
import com.rerun.core.schedule.GRID_MS
import com.rerun.core.schedule.GuideBlock
import com.rerun.core.schedule.gridFloor
import com.rerun.core.schedule.guideBlocks
import com.rerun.tv.RerunApp
import com.rerun.tv.ui.Screen
import com.rerun.tv.ui.TvController
import com.rerun.tv.ui.common.RerunWordmark
import com.rerun.tv.ui.player.clockTime
import com.rerun.tv.ui.player.describe
import com.rerun.tv.ui.theme.Retro
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val WINDOW_MS = 3 * 3_600_000L
private const val VISIBLE_ROWS = 7
private val ROW_H = 60.dp
private val CHANNEL_W = 230.dp

/** 90s cable-guide grid: channels down the side, half-hours across, a cyan now-line. */
@Composable
fun GuideScreen(controller: TvController) {
    val app = RerunApp.instance
    val rows by controller.channels.collectAsState()
    val focus = remember { FocusRequester() }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var windowStart by remember { mutableLongStateOf(gridFloor(now)) }
    var focusRow by remember { mutableIntStateOf(0) }
    var focusTime by remember { mutableLongStateOf(now) }
    var headerIndex by remember { mutableIntStateOf(0) }
    var blocks by remember { mutableStateOf<Map<String, List<GuideBlock>>>(emptyMap()) }
    var items by remember { mutableStateOf<Map<String, MediaItem>>(emptyMap()) }

    LaunchedEffect(rows) {
        val idx = rows.indexOfFirst { it.id == controller.currentChannelId() }
        if (idx >= 0) focusRow = idx
    }
    LaunchedEffect(windowStart, rows) {
        now = System.currentTimeMillis()
        val (b, i) = withContext(Dispatchers.IO) {
            app.schedule.ensure(now)
            val all = app.schedule.allEntries(windowStart, windowStart + WINDOW_MS)
            val mapped = rows.associate { row ->
                row.id to guideBlocks(all[row.id].orEmpty(), windowStart, windowStart + WINDOW_MS)
            }
            val keys = mapped.values.flatten().mapNotNull { it.itemKey }.toSet()
            mapped to keys.mapNotNull { k -> app.schedule.item(k)?.let { k to it } }.toMap()
        }
        blocks = b
        items = i
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    BackHandler { controller.closeGuideWithBack() }

    val focusedChannel = rows.getOrNull(focusRow)
    val focusedBlock = focusedChannel?.let { ch -> blocks[ch.id]?.firstOrNull { focusTime >= it.startMs && focusTime < it.endMs } }
    val focusedItem = focusedBlock?.itemKey?.let { items[it] }
    val header = focusRow < 0

    fun move(dRow: Int) {
        if (rows.isEmpty()) return
        focusRow = (focusRow + dRow).coerceIn(-1, rows.size - 1)
    }

    fun moveTime(forward: Boolean) {
        val b = focusedBlock
        val minTime = maxOf(System.currentTimeMillis(), gridFloor(System.currentTimeMillis()))
        focusTime = if (forward) (b?.endMs ?: (focusTime + GRID_MS)) else maxOf(minTime, (b?.startMs ?: focusTime) - 1)
        if (focusTime >= windowStart + WINDOW_MS - GRID_MS) windowStart += GRID_MS
        if (focusTime < windowStart) windowStart = maxOf(gridFloor(System.currentTimeMillis()), windowStart - GRID_MS)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Retro.GuideBlue, Retro.GuideBlueDeep)))
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionUp -> move(-1)
                    Key.DirectionDown -> move(+1)
                    Key.DirectionRight -> if (header) headerIndex = 1 else moveTime(true)
                    Key.DirectionLeft -> if (header) headerIndex = 0 else moveTime(false)
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> when {
                        header -> controller.go(if (headerIndex == 0) Screen.Channels else Screen.Settings)
                        focusedChannel != null -> { controller.go(Screen.Player); controller.tuneTo(focusedChannel.id) }
                    }
                    Key.Menu -> focusedChannel?.let { controller.go(Screen.Editor(it.id)) }
                    else -> return@onPreviewKeyEvent false
                }
                true
            },
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 32.dp)) {
            // Header: brand, clock, and the two menu buttons.
            Row(verticalAlignment = Alignment.CenterVertically) {
                RerunWordmark()
                Spacer(Modifier.width(24.dp))
                BasicText(clockTime(now), style = Retro.Title.copy(color = Retro.Yellow))
                Spacer(Modifier.weight(1f))
                HeaderButton("CHANNELS", header && headerIndex == 0)
                Spacer(Modifier.width(16.dp))
                HeaderButton("SETTINGS", header && headerIndex == 1)
            }
            Spacer(Modifier.height(12.dp))
            DetailPanel(focusedChannel?.name, focusedItem, focusedBlock, controller.posterUrl(focusedItem))
            Spacer(Modifier.height(12.dp))
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                val timelineW = maxWidth - CHANNEL_W
                val pxPerMs = timelineW.value / WINDOW_MS
                Column {
                    TimeHeader(windowStart, timelineW)
                    val first = (focusRow - VISIBLE_ROWS / 2).coerceIn(0, maxOf(0, rows.size - VISIBLE_ROWS))
                    rows.drop(first).take(VISIBLE_ROWS).forEachIndexed { i, ch ->
                        val rowIndex = first + i
                        Row(Modifier.height(ROW_H).fillMaxWidth()) {
                            Row(
                                Modifier.width(CHANNEL_W).fillMaxHeight().background(Retro.GuideBlueDeep).border(1.dp, Color(0x334A6BC8)).padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                BasicText(ch.number.toString(), style = Retro.GuideNumber, modifier = Modifier.width(70.dp))
                                BasicText(ch.name, style = Retro.GuideCell, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Box(Modifier.width(timelineW).fillMaxHeight()) {
                                for (b in blocks[ch.id].orEmpty()) {
                                    val x = ((b.startMs - windowStart) * pxPerMs).dp
                                    val w = ((b.endMs - b.startMs) * pxPerMs).dp
                                    val sel = rowIndex == focusRow && focusTime >= b.startMs && focusTime < b.endMs
                                    Cell(b, b.itemKey?.let { items[it] }, x, w, sel)
                                }
                                val nowX = ((now - windowStart) * pxPerMs).dp
                                if (now in windowStart until windowStart + WINDOW_MS) {
                                    Box(Modifier.offset(x = nowX).width(3.dp).fillMaxHeight().background(Retro.Cyan))
                                }
                            }
                        }
                    }
                }
            }
            BasicText("▲▼ channels   ◀▶ time   OK watch   ☰ edit channel   BACK return", style = Retro.Caption)
        }
    }
}

@Composable
private fun HeaderButton(text: String, focused: Boolean) {
    Box(
        Modifier.background(if (focused) Retro.GuideBlueLight else Color(0x66071341))
            .border(2.dp, if (focused) Retro.Yellow else Color(0x334A6BC8))
            .padding(horizontal = 18.dp, vertical = 6.dp),
    ) { BasicText(text, style = Retro.Body.copy(shadow = null)) }
}

@Composable
private fun TimeHeader(windowStart: Long, timelineW: Dp) {
    Row(Modifier.height(36.dp)) {
        Spacer(Modifier.width(CHANNEL_W))
        val cols = (WINDOW_MS / GRID_MS).toInt()
        for (i in 0 until cols) {
            Box(Modifier.width(timelineW / cols).fillMaxHeight().background(Retro.GuideBlueDeep).border(1.dp, Color(0x334A6BC8)).padding(start = 8.dp), contentAlignment = Alignment.CenterStart) {
                BasicText(clockTime(windowStart + i * GRID_MS), style = Retro.GuideCell.copy(color = Retro.Yellow))
            }
        }
    }
}

@Composable
private fun Cell(block: GuideBlock, item: MediaItem?, x: Dp, w: Dp, selected: Boolean) {
    val label = when {
        block.offAir -> "Off Air"
        item == null -> "Up Next"
        item.kind == MediaKind.EPISODE -> "${item.showTitle} — S${item.season}E${item.episode}"
        else -> item.title
    }
    Box(
        Modifier.offset(x = x).width(w).fillMaxHeight()
            .background(if (selected) Retro.Yellow else if (block.offAir) Color(0xFF06102E) else Retro.GuideBlue)
            .border(1.dp, if (selected) Retro.White else Color(0x554A6BC8))
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicText(label, style = Retro.GuideCell.copy(color = if (selected) Retro.GuideBlueDeep else Retro.White), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DetailPanel(channelName: String?, item: MediaItem?, block: GuideBlock?, poster: String?) {
    Row(Modifier.fillMaxWidth().height(170.dp).background(Color(0x55000000)).border(1.dp, Color(0x334A6BC8)).padding(12.dp)) {
        if (poster != null) {
            AsyncImage(poster, null, contentScale = ContentScale.Crop, modifier = Modifier.size(width = 98.dp, height = 146.dp))
            Spacer(Modifier.width(20.dp))
        }
        Column {
            BasicText(if (block?.offAir == true) "Off Air" else describe(item), style = Retro.Title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val bits = listOfNotNull(
                channelName,
                block?.let { "${clockTime(it.startMs)} – ${clockTime(it.endMs)}" },
                item?.contentRating,
                item?.genres?.take(3)?.joinToString(", ")?.takeIf { it.isNotBlank() },
            )
            BasicText(bits.joinToString("  ·  "), style = Retro.Body, maxLines = 1)
            if (item?.kind == MediaKind.EPISODE) BasicText(item.title, style = Retro.Caption.copy(color = Retro.Cyan), maxLines = 1)
        }
    }
}
