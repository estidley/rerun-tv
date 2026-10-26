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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.rerun.core.model.MediaItem
import com.rerun.core.catalog.Lineup
import com.rerun.core.model.ChannelKind
import com.rerun.core.model.MediaKind
import com.rerun.core.schedule.GRID_MS
import com.rerun.core.schedule.GuideBlock
import com.rerun.core.schedule.gridFloor
import com.rerun.core.schedule.guideBlocks
import com.rerun.core.schedule.hourlyGuideBlocks
import com.rerun.tv.RerunApp
import com.rerun.tv.data.ScheduleRepo
import com.rerun.tv.ui.Screen
import com.rerun.tv.ui.TvController
import com.rerun.tv.ui.common.RerunWordmark
import com.rerun.tv.ui.common.ChannelIcon
import com.rerun.tv.ui.player.clockTime
import com.rerun.tv.ui.player.describe
import com.rerun.tv.ui.theme.Retro
import com.rerun.tv.ui.touch.GuideTouchAction
import com.rerun.tv.ui.touch.TouchControls
import com.rerun.tv.ui.touch.guideTouchScroll
import com.rerun.tv.ui.touch.guideTouchTap
import com.rerun.tv.ui.touch.rememberTouchControlsEnabled
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val WINDOW_MS = 3 * 3_600_000L
private val CHANNEL_W = 230.dp
private val TIME_HEADER_H = 36.dp

/** 90s cable-guide grid: channels down the side, half-hours across, a cyan now-line. */
@Composable
fun GuideScreen(controller: TvController, schedule: ScheduleRepo = RerunApp.instance.schedule) {
    val rows by controller.channels.collectAsState()
    val density by controller.guideDensity.collectAsState()
    val revision by schedule.guideRevision.collectAsState()
    val update by controller.lineupUpdate.collectAsState()
    val startup by controller.startup.collectAsState()
    val focus = remember { FocusRequester() }
    val touch = rememberTouchControlsEnabled()

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var windowStart by remember { mutableLongStateOf(gridFloor(now)) }
    var focusRow by remember { mutableIntStateOf(0) }
    var focusTime by remember { mutableLongStateOf(now) }
    var headerIndex by remember { mutableIntStateOf(0) }
    var blocks by remember { mutableStateOf<Map<String, List<GuideBlock>>>(emptyMap()) }
    var items by remember { mutableStateOf<Map<String, MediaItem>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    val updating = loading || update.busy || startup.loading
    var loadError by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var elapsedSeconds by remember { mutableLongStateOf(0L) }
    var selectCurrentOnOpen by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
            windowStart = maxOf(windowStart, gridFloor(now))
            focusTime = maxOf(focusTime, now)
        }
    }
    LaunchedEffect(updating) {
        elapsedSeconds = 0
        val started = android.os.SystemClock.elapsedRealtime()
        while (updating) {
            delay(1_000)
            elapsedSeconds = (android.os.SystemClock.elapsedRealtime() - started) / 1_000
        }
    }

    LaunchedEffect(rows) {
        val idx = rows.indexOfFirst { it.id == controller.currentChannelId() }
        focusRow = if (idx >= 0) idx else focusRow.coerceIn(-1, rows.lastIndex.coerceAtLeast(-1))
    }
    LaunchedEffect(updating, rows) {
        if (!updating && loadError == null && rows.isNotEmpty() && selectCurrentOnOpen) {
            focusRow = rows.indexOfFirst { it.id == controller.currentChannelId() }.coerceAtLeast(0)
            focusTime = System.currentTimeMillis()
            selectCurrentOnOpen = false
        }
    }
    LaunchedEffect(windowStart, rows, revision, attempt, update.busy, startup.loading) {
        if (update.busy || startup.loading) return@LaunchedEffect
        loading = true
        loadError = null
        now = System.currentTimeMillis()
        try {
            val (b, i) = withContext(Dispatchers.IO) {
                schedule.ensure(now)
                val all = schedule.allEntries(windowStart, windowStart + WINDOW_MS)
                val mapped = rows.associate { row ->
                    val entries = all[row.id].orEmpty()
                    row.id to when {
                        row.id == Lineup.ADS_ID ->
                            hourlyGuideBlocks(entries, windowStart, windowStart + WINDOW_MS, row.name)
                        row.def.kind == ChannelKind.MUSIC -> {
                            val title = if (row.name.endsWith("Music", ignoreCase = true)) row.name else "${row.name} Music"
                            hourlyGuideBlocks(entries, windowStart, windowStart + WINDOW_MS, title)
                        }
                        else -> guideBlocks(entries, windowStart, windowStart + WINDOW_MS)
                    }
                }
                val keys = mapped.values.flatten().mapNotNull { it.itemKey }.toSet()
                mapped to keys.mapNotNull { k -> schedule.item(k)?.let { k to it } }.toMap()
            }
            blocks = b
            items = i
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            loadError = "Couldn't update guide: ${e.message}"
        } finally {
            loading = false
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    BackHandler { controller.closeGuideWithBack() }

    val focusedChannel = rows.getOrNull(focusRow)
    val focusedBlock = focusedChannel?.let { ch -> blocks[ch.id]?.firstOrNull { focusTime >= it.startMs && focusTime < it.endMs } }
    val focusedItem = focusedBlock?.itemKey?.let { items[it] }
    val header = updating || rows.isEmpty() || focusRow < 0

    fun move(dRow: Int) {
        if (updating) return
        focusRow = (focusRow + dRow).coerceIn(-1, rows.size - 1)
    }

    fun moveTime(forward: Boolean, b: GuideBlock? = focusedBlock) {
        val minTime = maxOf(System.currentTimeMillis(), gridFloor(System.currentTimeMillis()))
        focusTime = if (forward) (b?.endMs ?: (focusTime + GRID_MS)) else maxOf(minTime, (b?.startMs ?: focusTime) - 1)
        if (focusTime >= windowStart + WINDOW_MS - GRID_MS) windowStart += GRID_MS
        if (focusTime < windowStart) windowStart = maxOf(gridFloor(System.currentTimeMillis()), windowStart - GRID_MS)
    }

    // Touch: the first tap selects a cell like moving focus, tapping the selection is OK.
    fun tapCell(rowIndex: Int, block: GuideBlock?) {
        if (updating) return
        val channel = rows.getOrNull(rowIndex) ?: return
        val selected = rowIndex == focusRow && (block == null || (focusTime >= block.startMs && focusTime < block.endMs))
        when (TouchControls.guideTap(selected)) {
            GuideTouchAction.SELECT -> {
                focusRow = rowIndex
                focusTime = maxOf(block?.startMs ?: 0L, System.currentTimeMillis())
            }
            GuideTouchAction.WATCH -> { controller.go(Screen.Player); controller.tuneTo(channel.id) }
        }
    }

    // Touch: one drag can cover several programs before the next recomposition, so look up the
    // selected block from current state on every step instead of the composition-time focusedBlock.
    fun touchMoveTime(steps: Int) {
        // Like the remote, Left/Right never move through time while the header is selected.
        if (updating || rows.isEmpty() || focusRow < 0) return
        repeat(abs(steps)) {
            val block = rows.getOrNull(focusRow)?.let { ch -> blocks[ch.id]?.firstOrNull { focusTime >= it.startMs && focusTime < it.endMs } }
            moveTime(steps > 0, block)
        }
    }

    // Touch: a long press matches the remote's Menu button.
    fun editChannel(rowIndex: Int) {
        if (!updating) rows.getOrNull(rowIndex)?.let { controller.go(Screen.Editor(it.id)) }
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
                        loadError != null -> attempt++
                        !updating && focusedChannel != null -> { controller.go(Screen.Player); controller.tuneTo(focusedChannel.id) }
                    }
                    Key.Menu -> if (!updating) focusedChannel?.let { controller.go(Screen.Editor(it.id)) }
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
                HeaderButton("CHANNELS", header && headerIndex == 0, Modifier.guideTouchTap(touch, { controller.go(Screen.Channels) }))
                Spacer(Modifier.width(16.dp))
                HeaderButton("SETTINGS", header && headerIndex == 1, Modifier.guideTouchTap(touch, { controller.go(Screen.Settings) }))
            }
            Spacer(Modifier.height(12.dp))
            DetailPanel(focusedChannel?.name, focusedItem, focusedBlock, controller.posterUrl(focusedItem))
            Spacer(Modifier.height(12.dp))
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                if (updating || loadError != null || rows.isEmpty()) {
                    Column(Modifier.align(Alignment.Center).testTag("guide-status").guideTouchTap(touch, { if (loadError != null) attempt++ })) {
                        BasicText(
                            if (updating) "Updating guide..." else loadError ?: "No channels yet. Open Settings and run a library sync.",
                            style = Retro.Title,
                        )
                        BasicText(
                            if (updating) "Elapsed: ${elapsedSeconds}s"
                            else if (loadError != null) { if (touch) "Tap to retry, or tap SETTINGS." else "Press OK to retry, or UP for Settings." }
                            else if (touch) "Tap SETTINGS." else "Press UP for Settings.",
                            style = Retro.Body,
                        )
                        if (update.busy) update.message?.let { BasicText(it, style = Retro.Body) }
                    }
                    return@BoxWithConstraints
                }
                val timelineW = maxWidth - CHANNEL_W
                val pxPerMs = timelineW.value / WINDOW_MS
                val layout = guideRows((maxHeight - TIME_HEADER_H).value, rows.size, focusRow, GuideDensity.from(density))
                Column(
                    Modifier.fillMaxSize().clipToBounds().testTag("guide-grid").guideTouchScroll(
                        touch,
                        onRows = { move(it) },
                        onTime = { touchMoveTime(it) },
                    ),
                ) {
                    TimeHeader(windowStart, timelineW)
                    rows.drop(layout.firstIndex).take(layout.visibleCount).forEachIndexed { i, ch ->
                        val rowIndex = layout.firstIndex + i
                        val rowColor = if (rowIndex % 2 == 0) Retro.GuideBlue else Retro.AlternateRow
                        val channelColor = if (rowIndex % 2 == 0) Retro.GuideBlueDeep else Retro.GuideBlueLight
                        val rowSize = if (layout.fillViewport) Modifier.weight(1f) else Modifier.height(layout.rowHeight.dp)
                        Row(rowSize.fillMaxWidth().background(rowColor).testTag("guide-row:${ch.id}")) {
                            Row(
                                Modifier.width(CHANNEL_W).fillMaxHeight()
                                    .background(channelColor).border(2.dp, Retro.Cyan)
                                    .testTag("guide-channel:${ch.id}")
                                    .guideTouchTap(touch, { tapCell(rowIndex, null) }, { editChannel(rowIndex) })
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                BasicText(ch.number.toString(), style = Retro.GuideNumber, modifier = Modifier.width(58.dp))
                                ChannelIcon(ch.id, size = 30.dp)
                                Spacer(Modifier.width(8.dp))
                                BasicText(
                                    ch.name, style = Retro.GuideCell.copy(lineHeight = 28.sp),
                                    maxLines = layout.channelNameLines, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            Box(Modifier.width(timelineW).fillMaxHeight()) {
                                for (b in blocks[ch.id].orEmpty()) {
                                    val x = ((b.startMs - windowStart) * pxPerMs).dp
                                    val w = ((b.endMs - b.startMs) * pxPerMs).dp
                                    val sel = rowIndex == focusRow && focusTime >= b.startMs && focusTime < b.endMs
                                    Cell(
                                        b, b.itemKey?.let { items[it] }, x, w, sel, rowColor,
                                        Modifier.guideTouchTap(touch, { tapCell(rowIndex, b) }, { editChannel(rowIndex) }),
                                    )
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
            update.error?.let { BasicText(it, style = Retro.Body.copy(color = Retro.Yellow)) }
            BasicText(
                when {
                    touch && updating -> "Tap CHANNELS or SETTINGS   BACK return"
                    touch -> "Swipe to browse   Tap to select, tap again to watch   Hold to edit channel   BACK return"
                    updating -> "◀▶ Channels / Settings   OK open   BACK return"
                    else -> "▲▼ channels   ◀▶ time   OK watch   ☰ edit channel   BACK return"
                },
                style = Retro.Caption,
            )
        }
    }
}

@Composable
private fun HeaderButton(text: String, focused: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier.background(if (focused) Retro.GuideBlueLight else Retro.GuideBlueDeep.copy(alpha = 0.4f))
            .border(2.dp, if (focused) Retro.Yellow else Retro.Border.copy(alpha = 0.4f))
            .padding(horizontal = 18.dp, vertical = 6.dp),
    ) { BasicText(text, style = Retro.Body.copy(shadow = null)) }
}

@Composable
private fun TimeHeader(windowStart: Long, timelineW: Dp) {
    Row(Modifier.height(TIME_HEADER_H)) {
        Box(
            Modifier.width(CHANNEL_W).fillMaxHeight().background(Retro.GuideBlueDeep)
                .border(2.dp, Retro.Cyan).padding(start = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicText("CHANNEL", style = Retro.GuideCell.copy(color = Retro.Cyan))
        }
        val cols = (WINDOW_MS / GRID_MS).toInt()
        for (i in 0 until cols) {
            Box(Modifier.width(timelineW / cols).fillMaxHeight().background(Retro.GuideBlueDeep).border(1.dp, Retro.Border.copy(alpha = 0.4f)).padding(start = 8.dp), contentAlignment = Alignment.CenterStart) {
                BasicText(clockTime(windowStart + i * GRID_MS), style = Retro.GuideCell.copy(color = Retro.Yellow))
            }
        }
    }
}

@Composable
private fun Cell(block: GuideBlock, item: MediaItem?, x: Dp, w: Dp, selected: Boolean, rowColor: Color, modifier: Modifier = Modifier) {
    val title = block.title
    val label = when {
        block.offAir -> "Off Air"
        title != null -> title
        item == null -> "Up Next"
        item.kind == MediaKind.EPISODE -> "${item.showTitle} — S${item.season}E${item.episode}"
        else -> item.title
    }
    Box(
        Modifier.offset(x = x).width(w).fillMaxHeight()
            .then(modifier)
            .semantics { this.selected = selected }
            .background(if (selected) Retro.Yellow else rowColor)
            .border(1.dp, if (selected) Retro.White else Retro.Border.copy(alpha = 0.6f))
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicText(label, style = Retro.GuideCell.copy(color = if (selected) Retro.GuideBlueDeep else Retro.White), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DetailPanel(channelName: String?, item: MediaItem?, block: GuideBlock?, poster: String?) {
    Row(Modifier.fillMaxWidth().height(170.dp).background(Retro.GuideBlueDeep.copy(alpha = 0.7f)).border(1.dp, Retro.Border.copy(alpha = 0.4f)).padding(12.dp)) {
        if (poster != null) {
            AsyncImage(poster, null, contentScale = ContentScale.Crop, modifier = Modifier.size(width = 98.dp, height = 146.dp))
            Spacer(Modifier.width(20.dp))
        }
        Column {
            BasicText(if (block?.offAir == true) "Off Air" else block?.title ?: describe(item), style = Retro.Title, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
