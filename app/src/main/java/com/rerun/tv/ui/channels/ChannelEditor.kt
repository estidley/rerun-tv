package com.rerun.tv.ui.channels

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.rerun.core.catalog.Catalog
import com.rerun.core.catalog.Facets
import com.rerun.core.catalog.Lineup
import com.rerun.core.catalog.validateDraft
import com.rerun.core.model.AdBreakLength
import com.rerun.core.model.ChannelDef
import com.rerun.core.model.ChannelKind
import com.rerun.core.model.MediaKind
import com.rerun.core.model.MediaItem
import com.rerun.core.model.PlayOrder
import com.rerun.core.model.Rules
import com.rerun.core.resolve.Resolver
import com.rerun.tv.RerunApp
import com.rerun.tv.data.ChannelRow
import com.rerun.tv.ui.Screen
import com.rerun.tv.ui.TvController
import com.rerun.tv.ui.common.TvButton
import com.rerun.tv.ui.common.TvFrame
import com.rerun.tv.ui.theme.Retro
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/** Build or change a channel: what feeds it, filters, play order. */
@Composable
fun ChannelEditor(controller: TvController, channelId: String?, app: RerunApp = RerunApp.instance) {
    val scope = rememberCoroutineScope()
    val minimumLevel by controller.minimumContent.collectAsState()
    var existing by remember { mutableStateOf<ChannelRow?>(null) }
    var originalDraft by remember { mutableStateOf<ChannelDef?>(null) }
    var draft by remember { mutableStateOf<ChannelDef?>(null) }
    var facets by remember { mutableStateOf<Facets?>(null) }
    var library by remember { mutableStateOf<List<MediaItem>?>(null) }
    var numberText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var poolMs by remember { mutableStateOf<Long?>(null) }
    var showUnsavedChanges by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }

    fun save() {
        if (saving) return
        saving = true
        scope.launch {
            try {
                val toSave = draft ?: return@launch
                val errs = validateDraft(toSave, app.schedule.allChannelRows().map { it.def })
                if (errs.isNotEmpty()) { error = errs.joinToString("   "); return@launch }
                app.schedule.saveChannel(
                    ChannelRow.of(toSave, builtIn = existing?.builtIn ?: false),
                )
                app.schedule.resetFuture(toSave.id, System.currentTimeMillis())
                controller.reconcileChannels()
                controller.go(Screen.Channels)
            } finally {
                saving = false
            }
        }
    }

    fun requestLeave() {
        if (saving) return
        val current = draft
        val original = originalDraft
        if (current != null && original != null &&
            (current != original || numberText != original.number.toString())
        ) {
            showUnsavedChanges = true
        } else {
            controller.go(Screen.Channels)
        }
    }

    BackHandler { requestLeave() }

    LaunchedEffect(channelId) {
        val rows = app.schedule.allChannelRows()
        val row = channelId?.let { id -> rows.firstOrNull { it.id == id } }
        existing = row
        val d = row?.def ?: run {
            val used = rows.map { it.number }.toSet()
            ChannelDef(
                id = "custom:" + UUID.randomUUID().toString().take(8),
                number = (401..9999).first { it !in used && it != Lineup.RESTART_NUMBER },
                name = "", kind = ChannelKind.CUSTOM, rules = Rules(genres = emptySet()),
            )
        }
        val contents = withContext(Dispatchers.IO) { app.schedule.libraryItems() }
        facets = withContext(Dispatchers.Default) { Facets.from(contents) }
        library = contents
        originalDraft = d
        draft = d
        numberText = d.number.toString()
        runCatching { first.requestFocus() }
    }
    LaunchedEffect(draft?.rules, draft?.blocks, library, minimumLevel) {
        val d = draft ?: return@LaunchedEffect
        val contents = library ?: return@LaunchedEffect
        poolMs = withContext(Dispatchers.Default) { Resolver.pool(d, contents, minimumLevel).sumOf { it.durationMs } }
    }

    val d = draft
    val f = facets
    TvFrame(if (existing == null) "New channel" else "Edit ${d?.name ?: ""}", "OK toggle   ▲▼ move   BACK cancel") {
        if (d == null || f == null) {
            BasicText("Loading…", style = Retro.Title)
            return@TvFrame
        }
        fun update(r: Rules) { draft = d.copy(rules = r) }
        val minimum = Lineup.minimumContentMs(d, minimumLevel) / 3_600_000L
        val poolHours = poolMs?.let { ms ->
            "%.1f hours of content".format(ms / 3_600_000.0) +
                if (ms <= 0) "  (needs playable content)"
                else if (ms < Lineup.minimumContentMs(d, minimumLevel)) "  (needs $minimum to go on the air)" else ""
        } ?: "Checking content..."
        com.rerun.tv.ui.common.TvPanel(Modifier.fillMaxHeight().testTag("channel-editor-panel")) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Field("Name", d.name, Modifier.width(520.dp).focusRequester(first)) { draft = d.copy(name = it) }
                    Field("Number", numberText, Modifier.width(160.dp), numeric = true) { t ->
                        numberText = t.filter(Char::isDigit).take(4)
                        numberText.toIntOrNull()?.let { draft = d.copy(number = it) }
                    }
                    BasicText(poolHours, style = Retro.Caption.copy(color = Retro.Cyan))
                }
            }
            item {
                Section("What plays") {
                    BasicText(com.rerun.core.catalog.ChannelDescriptions.of(d), style = Retro.Body)
                    BasicText(
                        "Network, genre, show, collection, label and metadata-ID matches are alternatives; other filters narrow the pool.",
                        style = Retro.Caption,
                    )
                }
            }
            item {
                Section("Plays") {
                    chipRow(listOf("Movies + shows", "Movies only", "Shows only")) { label ->
                        val kinds = when (label) {
                            "Movies only" -> setOf(MediaKind.MOVIE)
                            "Shows only" -> setOf(MediaKind.EPISODE)
                            else -> setOf(MediaKind.MOVIE, MediaKind.EPISODE)
                        }
                        TvButton(label, { update(d.rules.copy(kinds = kinds)) }, selected = d.rules.kinds == kinds)
                    }
                    chipRow(PlayOrder.entries.toList()) { o ->
                        val label = when (o) {
                            PlayOrder.SHUFFLE -> "Shuffle"
                            PlayOrder.IN_ORDER -> "In order"
                            PlayOrder.CHRONOLOGICAL -> "Universe timeline"
                        }
                        TvButton(label, { draft = d.copy(order = o) }, selected = d.order == o)
                    }
                    chipRow(listOf("Kids-safe", "Disabled")) { label ->
                        if (label == "Kids-safe") TvButton(label, { draft = d.copy(kidsSafe = !d.kidsSafe) }, selected = d.kidsSafe)
                        else TvButton(label, { draft = d.copy(hidden = !d.hidden) }, selected = d.hidden)
                    }
                }
            }
            if (d.kind != ChannelKind.MUSIC) item {
                Section("Scheduling") {
                    BasicText(
                        if (d.nonClockAligned) {
                            "Each video starts right after the previous program and any selected ad break."
                        } else {
                            "Video programs start on local 15-minute clock marks."
                        },
                        style = Retro.Caption,
                    )
                    chipRow(listOf(false, true)) { nonClock ->
                        val label = if (nonClock) "Non-clock aligned" else "15-minute clock"
                        TvButton(label, { draft = d.copy(nonClockAligned = nonClock) }, selected = d.nonClockAligned == nonClock)
                    }
                }
            }
            if (d.kind != ChannelKind.MUSIC) item {
                Section("Advertising") {
                    BasicText(
                        when {
                            !d.showAds && d.nonClockAligned -> "Programs play back-to-back without ad breaks."
                            !d.showAds -> "Breaks show Up Next instead of ads."
                            d.nonClockAligned -> "With an assigned Ad library, full clips determine each variable-length break."
                            else -> "With an assigned Ad library, ads fill the break before the next clock-aligned program."
                        },
                        style = Retro.Caption,
                    )
                    chipRow(listOf(true, false)) { show ->
                        val label = if (show) "Show ads" else "No ads"
                        TvButton(label, { draft = d.copy(showAds = show) }, selected = d.showAds == show)
                    }
                    if (d.nonClockAligned && d.showAds) {
                        chipRow(AdBreakLength.entries.toList()) { length ->
                            val label = when (length) {
                                AdBreakLength.SHORT -> "Short · about 2 min"
                                AdBreakLength.MEDIUM -> "Medium · about 5 min"
                                AdBreakLength.LONG -> "Long · about 10 min"
                            }
                            TvButton(label, { draft = d.copy(adBreakLength = length) },
                                selected = d.adBreakLength == length, sub = "Up to ${length.maxAds} ads")
                        }
                    }
                }
            }
            item {
                Section("Minimum programming") {
                    BasicText(
                        "Default: ${Lineup.minimumContentMs(d.copy(minimumContentHours = null), minimumLevel) / 3_600_000L} hours. " +
                            "An override allows a smaller pool to repeat more often; an empty pool stays off air.",
                        style = Retro.Caption,
                    )
                    chipRow(listOf<Int?>(null, 0, 1, 4, 12, 24, 48)) { hours ->
                        val label = when (hours) {
                            null -> "Channel default"
                            0 -> "No minimum"
                            else -> "$hours hours"
                        }
                        TvButton(label, { draft = d.copy(minimumContentHours = hours) }, selected = d.minimumContentHours == hours)
                    }
                }
            }
            item {
                Section("Networks") {
                    chipRow(f.networks) { id ->
                        val network = Catalog.networks.firstOrNull { it.id == id }
                        TvButton(network?.name ?: id, { update(d.rules.copy(networks = d.rules.networks.toggle(id))) },
                            selected = id in d.rules.networks, sub = network?.realName)
                    }
                }
            }
            item { Section("Genres") { chipRow(f.genres, COLLAPSED_CHIP_COUNT, "expand-genres") { g -> TvButton(g, { update(d.rules.copy(genres = d.rules.genres.toggle(g))) }, selected = g in d.rules.genres) } } }
            item { Section("Leave out genres") { chipRow(f.genres, COLLAPSED_CHIP_COUNT, "expand-exclude-genres") { g -> TvButton(g, { update(d.rules.copy(excludeGenres = d.rules.excludeGenres.toggle(g))) }, selected = g in d.rules.excludeGenres) } } }
            item { Section("Shows") { chipRow(f.shows, COLLAPSED_CHIP_COUNT, "expand-shows") { s -> TvButton(s, { update(d.rules.copy(titles = d.rules.titles.toggle(s))) }, selected = s in d.rules.titles) } } }
            if (f.collections.isNotEmpty()) item {
                Section("Collections") { chipRow(f.collections, COLLAPSED_CHIP_COUNT, "expand-collections") { c -> TvButton(c, { update(d.rules.copy(collections = d.rules.collections.toggle(c))) }, selected = c in d.rules.collections) } }
            }
            if (f.labels.isNotEmpty()) item {
                Section("Labels") { chipRow(f.labels) { l -> TvButton(l, { update(d.rules.copy(labels = d.rules.labels.toggle(l))) }, selected = l in d.rules.labels) } }
            }
            item { Section("Decades") { chipRow(f.decades) { y -> TvButton("${y}s", { update(d.rules.copy(decades = d.rules.decades.toggle(y))) }, selected = y in d.rules.decades) } } }
            item { Section("Ratings") { chipRow(f.ratings) { r -> TvButton(r, { update(d.rules.copy(ratings = d.rules.ratings.toggle(r))) }, selected = r in d.rules.ratings) } } }
            item {
                Section("Comic universes") {
                    chipRow(listOf("marvel", "dc")) { franchise ->
                        TvButton(if (franchise == "marvel") "Marvel" else "DC",
                            { update(d.rules.copy(franchises = d.rules.franchises.toggle(franchise))) },
                            selected = franchise in d.rules.franchises)
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    error?.let { BasicText(it, style = Retro.Body.copy(color = Color(0xFFFF6B6B))) }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        TvButton("Save", { save() }, enabled = !saving)
                        TvButton("Cancel", { requestLeave() }, enabled = !saving)
                        if (existing != null && existing?.builtIn == false) {
                            TvButton("Delete channel", {
                                scope.launch {
                                    app.schedule.deleteChannel(d.id)
                                    controller.reconcileChannels()
                                    controller.go(Screen.Channels)
                                }
                            }, enabled = !saving)
                        }
                    }
                }
            }
        }
        }
    }
    if (showUnsavedChanges) {
        UnsavedChangesDialog(
            onSave = {
                showUnsavedChanges = false
                save()
            },
            onDiscard = {
                showUnsavedChanges = false
                controller.go(Screen.Channels)
            },
            onKeepEditing = { showUnsavedChanges = false },
        )
    }
}

@Composable
private fun UnsavedChangesDialog(onSave: () -> Unit, onDiscard: () -> Unit, onKeepEditing: () -> Unit) {
    val keepEditing = remember { FocusRequester() }
    val bodyStyle = Retro.Body.copy(fontSize = 24.sp)
    Dialog(onDismissRequest = onKeepEditing) {
        LaunchedEffect(Unit) { keepEditing.requestFocus() }
        Column(
            Modifier.widthIn(max = 720.dp).background(Retro.GuideBlueDeep).padding(28.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BasicText("Unsaved changes", style = Retro.Title.copy(fontSize = 32.sp))
            BasicText("Save your changes before leaving this screen?", style = bodyStyle)
            TvButton("Save changes", onSave, textStyle = bodyStyle)
            TvButton("Discard changes", onDiscard, textStyle = bodyStyle)
            TvButton("Keep editing", onKeepEditing, Modifier.focusRequester(keepEditing), textStyle = bodyStyle)
        }
    }
}

private fun <T> Set<T>.toggle(v: T): Set<T> = if (v in this) this - v else this + v

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText(title.uppercase(), style = Retro.GuideNumber)
        content()
    }
}

private const val COLLAPSED_CHIP_COUNT = 20

@Composable
private fun <T> chipRow(
    values: List<T>,
    collapsedLimit: Int = COLLAPSED_CHIP_COUNT,
    expansionTag: String? = null,
    chip: @Composable (T) -> Unit,
) {
    if (values.isEmpty()) { BasicText("Nothing in your library yet", style = Retro.Caption); return }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var page by rememberSaveable { mutableStateOf(0) }
    val lastPage = ((values.size - 1) / collapsedLimit - 1).coerceAtLeast(0)
    val currentPage = page.coerceIn(0, lastPage)
    val visible = if (!expanded || values.size <= collapsedLimit) {
        values.take(collapsedLimit)
    } else {
        values.drop((currentPage + 1) * collapsedLimit).take(collapsedLimit)
    }
    FlowRow(
        modifier = Modifier.fillMaxWidth().testTag("chip-flow"),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        visible.forEach { value -> Box(Modifier.widthIn(max = 520.dp)) { chip(value) } }
        if (values.size > collapsedLimit) {
            val tagModifier = expansionTag?.let { Modifier.testTag(it) } ?: Modifier
            if (expanded) {
                if (currentPage > 0) TvButton("Previous 20", { page = currentPage - 1 })
                if (currentPage < lastPage) TvButton("Next 20", { page = currentPage + 1 })
                TvButton("Show fewer", { expanded = false; page = 0 }, modifier = tagModifier)
            } else {
                TvButton("Show all (${values.size})", { expanded = true; page = 0 }, modifier = tagModifier)
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String, modifier: Modifier, numeric: Boolean = false, onChange: (String) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(modifier) {
        BasicText(label, style = Retro.Caption)
        BasicTextField(
            value = value, onValueChange = onChange, singleLine = true,
            textStyle = Retro.Title.copy(shadow = null),
            keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
            modifier = Modifier
                .onFocusChanged { focused = it.isFocused }
                .background(if (focused) Retro.GuideBlueLight else Color(0x66071341))
                .border(2.dp, if (focused) Retro.Yellow else Retro.Border.copy(alpha = 0.4f))
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}
