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
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.layout
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
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Arrangement
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
import com.rerun.tv.data.ChannelRow
import com.rerun.tv.data.ScheduleRepo
import com.rerun.tv.ui.ClockWatch
import com.rerun.tv.ui.Screen
import com.rerun.tv.ui.TvController
import com.rerun.tv.ui.common.RerunWordmark
import com.rerun.tv.ui.common.ChannelWordmark
import com.rerun.tv.ui.player.clockDate
import com.rerun.tv.ui.player.clockTime
import com.rerun.tv.ui.player.clockFormatSettings
import com.rerun.tv.ui.player.ClockFormatSettings
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
private const val ENSURE_INTERVAL_MS = 10 * 60_000L
private val TIME_HEADER_H = 36.dp

/** Bookkeeping for what the guide has already loaded; plain fields so updating them never recomposes. */
private class LoadMarks {
    var dataKey: Any? = null
    var window = 0L
    var lastEnsureMs = 0L
    var ids: Set<String> = emptySet()
}

/** Grows the preloaded row range only when the viewport nears its edge, so scrolling a row rarely reloads. */
internal fun wantRange(current: IntRange, first: Int, count: Int, size: Int): IntRange {
    if (size <= 0) return IntRange.EMPTY
    val last = (first + count - 1).coerceAtMost(size - 1)
    val margin = count.coerceAtLeast(1)
    val slack = margin / 2
    val ok = !current.isEmpty() && current.last <= size - 1 &&
        (first >= current.first + slack || current.first == 0) &&
        (last <= current.last - slack || current.last == size - 1)
    return if (ok) current else (first - margin).coerceAtLeast(0)..(last + margin).coerceAtMost(size - 1)
}

/** Lays a single line out as if rotated a quarter turn; pair with [rotate] (-90°) so it reads bottom to top. */
private fun Modifier.vertical(): Modifier = layout { measurable, constraints ->
    // Measure along the rotated axis so the available height bounds the text length.
    val placeable = measurable.measure(
        Constraints(
            minWidth = constraints.minHeight, maxWidth = constraints.maxHeight,
            minHeight = constraints.minWidth, maxHeight = constraints.maxWidth,
        ),
    )
    layout(placeable.height, placeable.width) {
        placeable.place(
            x = -(placeable.width / 2 - placeable.height / 2),
            y = -(placeable.height / 2 - placeable.width / 2),
        )
    }
}

/** 90s cable-guide grid: channels down the side, half-hours across, a cyan now-line. */
@Composable
fun GuideScreen(
    controller: TvController,
    schedule: ScheduleRepo = RerunApp.instance.schedule,
    clock: () -> Long = System::currentTimeMillis,
) {
    val rows by controller.channels.collectAsState()
    val density by controller.guideDensity.collectAsState()
    val revision by schedule.guideRevision.collectAsState()
    val update by controller.lineupUpdate.collectAsState()
    val startup by controller.startup.collectAsState()
    val focus = remember { FocusRequester() }
    val marks = remember { LoadMarks() }
    val clockWatch = remember { ClockWatch() }
    val touch = rememberTouchControlsEnabled()

    var now by remember { mutableLongStateOf(clock()) }
    var timeFormat by remember { mutableStateOf(clockFormatSettings()) }
    var windowStart by rememberSaveable { mutableLongStateOf(gridFloor(now)) }
    var focusRow by rememberSaveable { mutableIntStateOf(0) }
    var focusTime by rememberSaveable { mutableLongStateOf(now) }
    var headerIndex by rememberSaveable { mutableIntStateOf(0) }
    var blocks by remember { mutableStateOf<Map<String, List<GuideBlock>>>(emptyMap()) }
    var items by remember { mutableStateOf<Map<String, MediaItem>>(emptyMap()) }
    // Rows near the viewport; only these get schedules topped up and blocks built.
    var want by remember { mutableStateOf(IntRange.EMPTY) }
    var clockEpoch by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    val updating = loading || update.busy || startup.loading
    var loadError by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var elapsedSeconds by remember { mutableLongStateOf(0L) }
    var selectCurrentOnOpen by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        while (true) {
            // A clock or time-zone change is noticed here, lazily, instead of through a broadcast receiver.
            val jumped = clockWatch.changed()
            now = clock()
            timeFormat = clockFormatSettings()
            windowStart = if (jumped) gridFloor(now) else maxOf(windowStart, gridFloor(now))
            focusTime = if (jumped) now else maxOf(focusTime, now)
            if (jumped) clockEpoch++
            delay(30_000)
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
        val idx = rows.indexOfFirst { it.id == controller.effectiveChannelId() }
        focusRow = if (idx >= 0) idx else focusRow.coerceIn(-1, rows.lastIndex.coerceAtLeast(-1))
    }
    LaunchedEffect(updating, rows) {
        if (!updating && loadError == null && rows.isNotEmpty() && selectCurrentOnOpen) {
            focusRow = rows.indexOfFirst { it.id == controller.effectiveChannelId() }.coerceAtLeast(0)
            focusTime = clock()
            selectCurrentOnOpen = false
        }
    }
    LaunchedEffect(windowStart, rows, revision, attempt, update.busy, startup.loading, want, clockEpoch) {
        if (update.busy || startup.loading) return@LaunchedEffect
        if (rows.isEmpty()) { loading = false; return@LaunchedEffect }
        // Stay in the loading state until the layout reports which rows are on screen.
        if (want.isEmpty()) return@LaunchedEffect
        val ids = rows.slice(want.first..minOf(want.last, rows.lastIndex)).map { it.id }
        val dataKey = listOf(rows, revision, attempt, clockEpoch)
        val dataChanged = marks.dataKey != dataKey
        val reset = dataChanged || marks.window != windowStart
        // Keep extending schedules for nearby untuned channels while the guide stays open.
        val topUpDue = dataChanged || now - marks.lastEnsureMs >= ENSURE_INTERVAL_MS
        val missing = if (reset) ids else ids.filter { it !in marks.ids }
        val toBuild = if (topUpDue) ids else missing
        if (toBuild.isEmpty()) return@LaunchedEffect
        if (dataChanged) loading = true
        loadError = null
        now = clock()
        try {
            val (b, i) = withContext(Dispatchers.IO) {
                if (topUpDue) schedule.ensure(ids, now) else schedule.ensure(missing, now)
                val all = schedule.entriesFor(toBuild, windowStart, windowStart + WINDOW_MS)
                val byId = rows.associateBy { it.id }
                val mapped = toBuild.associateWith { id ->
                    val row = byId.getValue(id)
                    val entries = all[id].orEmpty()
                    when {
                        id == Lineup.ADS_ID ->
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
            blocks = if (reset) b else blocks + b
            items = if (reset) i else items + i
            marks.ids = if (reset) toBuild.toSet() else marks.ids + toBuild
            marks.dataKey = dataKey
            marks.window = windowStart
            if (topUpDue) marks.lastEnsureMs = now
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
    val focusedBlock = remember(blocks, focusedChannel?.id, focusTime) {
        focusedChannel?.let { ch -> blocks[ch.id]?.firstOrNull { focusTime >= it.startMs && focusTime < it.endMs } }
    }
    val focusedItem = focusedBlock?.itemKey?.let { items[it] }
    val header = updating || rows.isEmpty() || focusRow < 0

    var summary by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(focusedItem?.key, revision) {
        summary = null
        val item = focusedItem ?: return@LaunchedEffect
        delay(300)
        summary = controller.programSummary(item)
    }

    fun move(dRow: Int) {
        if (updating) return
        focusRow = (focusRow + dRow).coerceIn(-1, rows.size - 1)
    }

    fun moveTime(forward: Boolean, b: GuideBlock? = focusedBlock) {
        val minTime = maxOf(clock(), gridFloor(clock()))
        focusTime = if (forward) (b?.endMs ?: (focusTime + GRID_MS)) else maxOf(minTime, (b?.startMs ?: focusTime) - 1)
        if (focusTime >= windowStart + WINDOW_MS - GRID_MS) windowStart += GRID_MS
        if (focusTime < windowStart) windowStart = maxOf(gridFloor(clock()), windowStart - GRID_MS)
    }

    // Touch: the first tap selects a cell like moving focus, tapping the selection is OK.
    fun tapCell(rowIndex: Int, block: GuideBlock?) {
        if (updating) return
        val channel = rows.getOrNull(rowIndex) ?: return
        val selected = rowIndex == focusRow && (block == null || (focusTime >= block.startMs && focusTime < block.endMs))
        when (TouchControls.guideTap(selected)) {
            GuideTouchAction.SELECT -> {
                focusRow = rowIndex
                focusTime = maxOf(block?.startMs ?: 0L, clock())
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
                BasicText("${clockDate(now)}  ${clockTime(now)}", style = Retro.Title.copy(color = Retro.Yellow))
                Spacer(Modifier.weight(1f))
                HeaderButton("CHANNELS", header && headerIndex == 0, Modifier.guideTouchTap(touch, { controller.go(Screen.Channels) }))
                Spacer(Modifier.width(16.dp))
                HeaderButton("SETTINGS", header && headerIndex == 1, Modifier.guideTouchTap(touch, { controller.go(Screen.Settings) }))
            }
            Spacer(Modifier.height(12.dp))
            key(timeFormat) {
                DetailPanel(focusedChannel?.name, focusedItem, focusedBlock, controller.posterUrl(focusedItem), summary, GuideDensity.from(density))
            }
            Spacer(Modifier.height(12.dp))
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                if (loadError != null || rows.isEmpty()) {
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
                val layout = guideRows((maxHeight - TIME_HEADER_H).value, rows.size, focusRow, GuideDensity.from(density))
                LaunchedEffect(layout.firstIndex, layout.visibleCount, rows.size) {
                    want = wantRange(want, layout.firstIndex, layout.visibleCount, rows.size)
                }
                val scale = GuideDensity.from(density).textScale
                val iconDp = (layout.rowHeight - 8f).coerceIn(48f, 120f * scale)
                val channelW = (iconDp * 400f / 270f + 34f + 20f).dp
                val timelineW = maxWidth - channelW
                val pxPerMs = timelineW.value / WINDOW_MS
                // While the skeleton shows, touch reaches only CHANNELS and SETTINGS, like the remote.
                val gridTouch = touch && !updating
                Box(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.fillMaxSize().clipToBounds().testTag(if (updating) "guide-skeleton" else "guide-grid").guideTouchScroll(
                            gridTouch,
                            onRows = { move(it) },
                            onTime = { touchMoveTime(it) },
                        ),
                    ) {
                        TimeHeader(windowStart, timelineW, channelW, timeFormat)
                        for (rowIndex in layout.firstIndex until minOf(rows.size, layout.firstIndex + layout.visibleCount)) {
                            val ch = rows[rowIndex]
                            GuideRow(
                                ch = ch,
                                rowIndex = rowIndex,
                                blocks = if (updating) emptyList() else blocks[ch.id].orEmpty(),
                                items = items,
                                selectedTime = if (rowIndex == focusRow) focusTime else Long.MIN_VALUE,
                                windowStart = windowStart,
                                now = now,
                                channelW = channelW,
                                timelineW = timelineW,
                                pxPerMs = pxPerMs,
                                iconDp = iconDp,
                                scale = scale,
                                skeleton = updating,
                                touch = gridTouch,
                                onTap = { block -> tapCell(rowIndex, block) },
                                onHold = { editChannel(rowIndex) },
                                modifier = if (layout.fillViewport) Modifier.weight(1f) else Modifier.height(layout.rowHeight.dp),
                            )
                        }
                    }
                    if (updating) {
                        Column(
                            Modifier.align(Alignment.Center).background(Retro.GuideBlueDeep.copy(alpha = 0.9f)).padding(24.dp)
                                .testTag("guide-status"),
                        ) {
                            BasicText("Updating guide...", style = Retro.Title)
                            BasicText("Elapsed: ${elapsedSeconds}s", style = Retro.Body)
                            if (update.busy) update.message?.let { BasicText(it, style = Retro.Body) }
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
private fun GuideRow(
    ch: ChannelRow,
    rowIndex: Int,
    blocks: List<GuideBlock>,
    items: Map<String, MediaItem>,
    selectedTime: Long,
    windowStart: Long,
    now: Long,
    channelW: Dp,
    timelineW: Dp,
    pxPerMs: Float,
    iconDp: Float,
    scale: Float,
    skeleton: Boolean,
    touch: Boolean,
    onTap: (GuideBlock?) -> Unit,
    onHold: () -> Unit,
    modifier: Modifier,
) {
    val rowColor = if (rowIndex % 2 == 0) Retro.GuideBlue else Retro.AlternateRow
    val channelColor = if (rowIndex % 2 == 0) Retro.GuideBlueDeep else Retro.GuideBlueLight
    Row(modifier.fillMaxWidth().background(rowColor).testTag("guide-row:${ch.id}")) {
        Row(
            Modifier.width(channelW).fillMaxHeight()
                .background(channelColor).border(2.dp, Retro.Cyan)
                .testTag("guide-channel:${ch.id}")
                .guideTouchTap(touch, { onTap(null) }, onHold)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(34.dp), contentAlignment = Alignment.Center) {
                BasicText(
                    ch.number.toString(),
                    style = Retro.ChannelNumber.copy(fontSize = (22f * scale).sp),
                    maxLines = 1,
                    modifier = Modifier.vertical().rotate(-90f),
                )
            }
            ChannelWordmark(ch.id, ch.name, modifier = Modifier.weight(1f).height(iconDp.dp))
        }
        Box(Modifier.width(timelineW).fillMaxHeight()) {
            if (skeleton) {
                // Static placeholders: infinite animations would stall Compose tests that use a manual clock.
                val widths = listOf(0.3f, 0.45f, 0.25f)
                var x = 0f
                for (frac in widths) {
                    Box(
                        Modifier.offset(x = (timelineW.value * x).dp).width((timelineW.value * frac).dp).fillMaxHeight()
                            .padding(3.dp).background(Retro.GuideBlueDeep.copy(alpha = 0.5f)),
                    )
                    x += frac
                }
            } else {
                for (b in blocks) {
                    val x = ((b.startMs - windowStart) * pxPerMs).dp
                    val w = ((b.endMs - b.startMs) * pxPerMs).dp
                    val sel = selectedTime >= b.startMs && selectedTime < b.endMs
                    Cell(
                        b, b.itemKey?.let { items[it] }, x, w, sel, rowColor, scale,
                        Modifier.guideTouchTap(touch, { onTap(b) }, onHold),
                    )
                }
                if (now in windowStart until windowStart + WINDOW_MS) {
                    Box(Modifier.offset(x = ((now - windowStart) * pxPerMs).dp).width(3.dp).fillMaxHeight().background(Retro.Cyan))
                }
            }
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
private fun TimeHeader(windowStart: Long, timelineW: Dp, channelW: Dp, timeFormat: ClockFormatSettings) {
    Row(Modifier.height(TIME_HEADER_H)) {
        Box(
            Modifier.width(channelW).fillMaxHeight().background(Retro.GuideBlueDeep)
                .border(2.dp, Retro.Cyan).padding(start = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicText(clockDate(windowStart), style = Retro.GuideCell.copy(color = Retro.Cyan), maxLines = 1)
        }
        val cols = (WINDOW_MS / GRID_MS).toInt()
        val labels = remember(windowStart, timeFormat) { List(cols) { clockTime(windowStart + it * GRID_MS) } }
        for (i in 0 until cols) {
            Box(Modifier.width(timelineW / cols).fillMaxHeight().background(Retro.GuideBlueDeep).border(1.dp, Retro.Border.copy(alpha = 0.4f)).padding(start = 8.dp), contentAlignment = Alignment.CenterStart) {
                BasicText(labels[i], style = Retro.GuideCell.copy(color = Retro.Yellow))
            }
        }
    }
}

@Composable
private fun Cell(
    block: GuideBlock,
    item: MediaItem?,
    x: Dp,
    w: Dp,
    selected: Boolean,
    rowColor: Color,
    scale: Float,
    modifier: Modifier = Modifier,
) {
    val title = block.title
    val label = when {
        block.offAir -> "Off Air"
        title != null -> title
        item == null -> "Up Next"
        item.kind == MediaKind.EPISODE -> "${item.showTitle} — S${item.season}E${item.episode}"
        item.kind == MediaKind.MOVIE && item.year != null -> "${item.title} (${item.year})"
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
        BasicText(label, style = Retro.GuideCell.copy(color = if (selected) Retro.GuideBlueDeep else Retro.White, fontSize = Retro.GuideCell.fontSize * scale), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DetailPanel(channelName: String?, item: MediaItem?, block: GuideBlock?, poster: String?, summary: String?, density: GuideDensity) {
    val scale = density.textScale
    val posterH = density.detailHeight - 24f
    Row(Modifier.fillMaxWidth().height(density.detailHeight.dp).background(Retro.GuideBlueDeep.copy(alpha = 0.7f)).border(1.dp, Retro.Border.copy(alpha = 0.4f)).padding(12.dp)) {
        if (poster != null) {
            AsyncImage(poster, null, contentScale = ContentScale.Crop, modifier = Modifier.size(width = (posterH * 0.67f).dp, height = posterH.dp))
            Spacer(Modifier.width(20.dp))
        }
        Column {
            BasicText(if (block?.offAir == true) "Off Air" else block?.title ?: describe(item), style = Retro.Title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val bits = listOfNotNull(
                channelName,
                block?.let { "${clockDate(it.startMs)}  ${clockTime(it.startMs)} – ${clockTime(it.endMs)}" },
                item?.contentRating,
                item?.genres?.take(3)?.joinToString(", ")?.takeIf { it.isNotBlank() },
            )
            BasicText(bits.joinToString("  ·  "), style = Retro.Body.copy(fontSize = Retro.Body.fontSize * scale), maxLines = 1)
            if (item?.kind == MediaKind.EPISODE) BasicText(item.title, style = Retro.Caption.copy(color = Retro.Cyan), maxLines = 1)
            if (!summary.isNullOrBlank()) {
                BasicText(
                    summary,
                    style = Retro.Body.copy(fontSize = Retro.Body.fontSize * scale * 0.85f),
                    maxLines = density.summaryLines,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("guide-summary"),
                )
            }
        }
    }
}
