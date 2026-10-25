package com.rerun.tv.ui.channels

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.rerun.core.catalog.Catalog
import com.rerun.core.catalog.Facets
import com.rerun.core.catalog.Lineup
import com.rerun.core.catalog.validateDraft
import com.rerun.core.model.ChannelDef
import com.rerun.core.model.ChannelKind
import com.rerun.core.model.MediaKind
import com.rerun.core.model.MediaItem
import com.rerun.core.model.PlayOrder
import com.rerun.core.model.Rules
import com.rerun.core.resolve.Resolver
import com.rerun.tv.RerunApp
import com.rerun.tv.data.ChannelRow
import com.rerun.tv.data.Settings
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
    var existing by remember { mutableStateOf<ChannelRow?>(null) }
    var draft by remember { mutableStateOf<ChannelDef?>(null) }
    var facets by remember { mutableStateOf<Facets?>(null) }
    var library by remember { mutableStateOf<List<MediaItem>?>(null) }
    var numberText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var poolHours by remember { mutableStateOf("") }
    val first = remember { FocusRequester() }

    BackHandler { controller.go(Screen.Channels) }

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
        draft = d
        numberText = d.number.toString()
        runCatching { first.requestFocus() }
    }
    LaunchedEffect(draft?.rules, draft?.blocks, library) {
        val d = draft ?: return@LaunchedEffect
        val contents = library ?: return@LaunchedEffect
        val ms = withContext(Dispatchers.Default) { Resolver.pool(d, contents).sumOf { it.durationMs } }
        poolHours = "%.1f hours of content".format(ms / 3_600_000.0) + if (ms < Resolver.DORMANT_MS) "  (needs 4 to go on the air)" else ""
    }

    val d = draft
    val f = facets
    TvFrame(if (existing == null) "New channel" else "Edit ${d?.name ?: ""}", "OK toggle   ▲▼ move   BACK cancel") {
        if (d == null || f == null) {
            BasicText("Loading…", style = Retro.Title)
            return@TvFrame
        }
        fun update(r: Rules) { draft = d.copy(rules = r) }
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
                    chipRow(listOf("Kids-safe", "Hidden")) { label ->
                        if (label == "Kids-safe") TvButton(label, { draft = d.copy(kidsSafe = !d.kidsSafe) }, selected = d.kidsSafe)
                        else TvButton(label, { draft = d.copy(hidden = !d.hidden) }, selected = d.hidden)
                    }
                }
            }
            item {
                Section("Networks") {
                    chipRow(f.networks) { id ->
                        val name = Catalog.networks.firstOrNull { it.id == id }?.name ?: id
                        TvButton(name, { update(d.rules.copy(networks = d.rules.networks.toggle(id))) }, selected = id in d.rules.networks)
                    }
                }
            }
            item { Section("Genres") { chipRow(f.genres) { g -> TvButton(g, { update(d.rules.copy(genres = d.rules.genres.toggle(g))) }, selected = g in d.rules.genres) } } }
            item { Section("Leave out genres") { chipRow(f.genres) { g -> TvButton(g, { update(d.rules.copy(excludeGenres = d.rules.excludeGenres.toggle(g))) }, selected = g in d.rules.excludeGenres) } } }
            item { Section("Shows") { chipRow(f.shows) { s -> TvButton(s, { update(d.rules.copy(titles = d.rules.titles.toggle(s))) }, selected = s in d.rules.titles) } } }
            if (f.collections.isNotEmpty()) item {
                Section("Collections") { chipRow(f.collections) { c -> TvButton(c, { update(d.rules.copy(collections = d.rules.collections.toggle(c))) }, selected = c in d.rules.collections) } }
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
                        TvButton("Save", {
                            scope.launch {
                                val toSave = draft ?: return@launch
                                val errs = validateDraft(toSave, app.schedule.allChannelRows().map { it.def })
                                if (errs.isNotEmpty()) { error = errs.joinToString("   "); return@launch }
                                val contents = withContext(Dispatchers.IO) { app.schedule.libraryItems() }
                                val ms = withContext(Dispatchers.Default) { Resolver.pool(toSave, contents).sumOf { it.durationMs } }
                                app.schedule.saveChannel(
                                    ChannelRow.of(toSave, builtIn = existing?.builtIn ?: false, dormant = ms < Resolver.DORMANT_MS, poolMs = ms),
                                )
                                if (!Lineup.isWest(toSave.id) && app.settings.isOn(Settings.EAST_WEST)) {
                                    app.sync.refreshChannels()
                                }
                                app.schedule.resetFuture(toSave.id, System.currentTimeMillis())
                                controller.reconcileChannels()
                                controller.go(Screen.Channels)
                            }
                        })
                        TvButton("Cancel", { controller.go(Screen.Channels) })
                        if (existing != null && existing?.builtIn == false) {
                            TvButton("Delete channel", {
                                scope.launch {
                                    app.schedule.deleteChannel(d.id)
                                    controller.reconcileChannels()
                                    controller.go(Screen.Channels)
                                }
                            })
                        }
                    }
                }
            }
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

@Composable
private fun <T> chipRow(values: List<T>, chip: @Composable (T) -> Unit) {
    if (values.isEmpty()) { BasicText("Nothing in your library yet", style = Retro.Caption); return }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) { items(values) { chip(it) } }
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
