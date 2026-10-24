package com.rerun.tv.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.rerun.tv.RerunApp
import com.rerun.tv.data.Settings
import com.rerun.tv.ui.Effects
import com.rerun.tv.ui.Screen
import com.rerun.tv.ui.TvController
import com.rerun.tv.ui.common.TvButton
import com.rerun.tv.ui.common.TvStage
import com.rerun.tv.ui.crt.CrtLevel
import com.rerun.tv.ui.player.clockTime
import com.rerun.tv.ui.theme.Retro
import com.rerun.tv.ui.theme.ColorTheme
import com.rerun.tv.ui.guide.GuideDensity
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(controller: TvController, app: RerunApp = RerunApp.instance) {
    val scope = rememberCoroutineScope()
    val crt by controller.crtLevel.collectAsState()
    val fx by controller.effects.collectAsState()
    val theme by controller.colorTheme.collectAsState()
    val density by controller.guideDensity.collectAsState()
    val subtitleLanguage by controller.subtitleLanguage.collectAsState()
    var server by remember { mutableStateOf("") }
    var lastSync by remember { mutableStateOf(0L) }
    var syncStatus by remember { mutableStateOf<String?>(null) }
    var lineupBusy by remember { mutableStateOf(false) }
    val eastWest by remember(app.settings) { app.settings.flag(Settings.EAST_WEST, false) }.collectAsState(false)
    val musicOn by remember(app.settings) { app.settings.flag(Settings.MUSIC, false) }.collectAsState(false)
    val subtitlesOn by remember(app.settings) { app.settings.flag(Settings.SUBTITLES, false) }.collectAsState(false)
    var pendingEastWest by remember { mutableStateOf<Boolean?>(null) }
    var pendingMusic by remember { mutableStateOf<Boolean?>(null) }
    val shownEastWest = pendingEastWest ?: eastWest
    val shownMusic = pendingMusic ?: musicOn
    val focusRows = remember { listOf(2, 4, 3, 3, 3, 2, 2, 4, 1).map { count -> List(count) { FocusRequester() } } }
    fun buttonFocus(row: Int, column: Int): Modifier {
        val current = focusRows[row][column]
        val availableRows = focusRows.mapIndexed { index, buttons ->
            if (index == 7 && lineupBusy) buttons.drop(1) else buttons
        }
        val order = availableRows.flatten()
        val index = order.indexOf(current)
        fun vertical(target: Int): FocusRequester =
            availableRows.getOrNull(target)?.let { it[column.coerceAtMost(it.lastIndex)] } ?: FocusRequester.Cancel
        return Modifier.focusRequester(current).focusProperties {
            previous = order.getOrNull(index - 1) ?: FocusRequester.Cancel
            next = order.getOrNull(index + 1) ?: FocusRequester.Cancel
            left = availableRows[row].getOrNull(availableRows[row].indexOf(current) - 1) ?: FocusRequester.Cancel
            right = availableRows[row].getOrNull(availableRows[row].indexOf(current) + 1) ?: FocusRequester.Cancel
            up = vertical(row - 1)
            down = vertical(row + 1)
        }
    }

    LaunchedEffect(eastWest) { if (pendingEastWest == eastWest) pendingEastWest = null }
    LaunchedEffect(musicOn) { if (pendingMusic == musicOn) pendingMusic = null }

    BackHandler { controller.go(Screen.Guide) }
    LaunchedEffect(Unit) {
        server = app.settings.serverName() ?: "—"
        lastSync = app.settings.lastSyncMs()
        focusRows.first().first().requestFocus()
    }

    TvStage("BACK guide") {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            BasicText("EFFECTS", style = Retro.GuideNumber)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton("All effects on", { controller.setAllEffects(true) }, modifier = buttonFocus(0, 0))
                TvButton("All effects off", { controller.setAllEffects(false) }, modifier = buttonFocus(0, 1))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton("Static", { controller.setEffect(Effects.STATIC, !fx.static) }, modifier = buttonFocus(1, 0), selected = fx.static, sub = if (fx.static) "on" else "off")
                TvButton("Static sound", { controller.setEffect(Effects.SOUND, !fx.sound) }, modifier = buttonFocus(1, 1), selected = fx.sound, sub = if (fx.sound) "on" else "off")
                TvButton("Picture roll", { controller.setEffect(Effects.ROLL, !fx.roll) }, modifier = buttonFocus(1, 2), selected = fx.roll, sub = if (fx.roll) "on" else "off")
                TvButton("Power on/off", { controller.setEffect(Effects.POWER, !fx.power) }, modifier = buttonFocus(1, 3), selected = fx.power, sub = if (fx.power) "on" else "off")
            }
            BasicText("CRT LOOK", style = Retro.GuideNumber)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CrtLevel.entries.forEachIndexed { i, level ->
                    val label = when (level) { CrtLevel.OFF -> "CRT Off"; CrtLevel.SUBTLE -> "CRT Subtle"; CrtLevel.FULL -> "CRT Full" }
                    TvButton(label, { controller.setCrt(level) }, selected = CrtLevel.from(crt) == level,
                        modifier = buttonFocus(2, i))
                }
            }
            BasicText("Tip: hold OK for 2 seconds while watching to flip between these.", style = Retro.Caption)

            BasicText("THEME COLORS", style = Retro.GuideNumber)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ColorTheme.entries.forEachIndexed { i, choice ->
                    TvButton(choice.label, { controller.setColorTheme(choice) }, modifier = buttonFocus(3, i), selected = ColorTheme.from(theme) == choice)
                }
            }

            BasicText("GUIDE ROW SIZE", style = Retro.GuideNumber)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GuideDensity.entries.forEachIndexed { i, choice ->
                    TvButton(choice.label, { controller.setGuideDensity(choice) }, modifier = buttonFocus(4, i), selected = GuideDensity.from(density) == choice)
                }
            }
            BasicText("SUBTITLES", style = Retro.GuideNumber)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton(
                    "Subtitles",
                    {
                        scope.launch {
                            val next = !subtitlesOn
                            app.settings.setFlag(Settings.SUBTITLES, next)
                            controller.engine.setSubtitles(next)
                        }
                    },
                    modifier = buttonFocus(5, 0),
                    selected = subtitlesOn,
                    sub = if (subtitlesOn) "on" else "off",
                )
                TvButton("Subtitle language: ${languageName(subtitleLanguage)}", { controller.go(Screen.SubtitleLanguage) }, modifier = buttonFocus(5, 1))
            }
            BasicText("LINEUP", style = Retro.GuideNumber)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton(
                    "East and West",
                    {
                        if (!lineupBusy) {
                            lineupBusy = true
                            val next = !shownEastWest
                            pendingEastWest = next
                            syncStatus = "Updating East and West..."
                            scope.launch {
                                try {
                                    app.settings.setFlag(Settings.EAST_WEST, next)
                                    app.sync.refreshChannels()
                                    syncStatus = "East and West ${if (next) "on" else "off"}"
                                } catch (e: Exception) {
                                    withContext(NonCancellable) { app.settings.setFlag(Settings.EAST_WEST, !next) }
                                    pendingEastWest = null
                                    if (e is CancellationException) throw e
                                    syncStatus = "Lineup failed: ${e.message}"
                                } finally {
                                    lineupBusy = false
                                }
                            }
                        }
                    },
                    modifier = buttonFocus(6, 0),
                    selected = shownEastWest,
                    sub = if (shownEastWest) "on" else "off",
                )
                TvButton(
                    "Music",
                    {
                        if (!lineupBusy) {
                            lineupBusy = true
                            val next = !shownMusic
                            pendingMusic = next
                            syncStatus = "Updating music..."
                            scope.launch {
                                try {
                                    if (next) {
                                        val included = app.settings.selectedLibraries()
                                        require(app.sync.libraries().any { it.type == "artist" && (included == null || it.key in included) }) {
                                            "Include a Plex music library in Manage libraries first."
                                        }
                                    }
                                    app.settings.setFlag(Settings.MUSIC, next)
                                    if (next) {
                                        app.sync.sync().collect { p ->
                                            syncStatus = if (p.finished) "Done" else "${p.stage} ${p.done}/${p.total}"
                                        }
                                        lastSync = app.settings.lastSyncMs()
                                    } else {
                                        app.sync.refreshChannels()
                                        syncStatus = "Music channels off"
                                    }
                                } catch (e: Exception) {
                                    withContext(NonCancellable) { app.settings.setFlag(Settings.MUSIC, !next) }
                                    pendingMusic = null
                                    if (e is CancellationException) throw e
                                    syncStatus = "Sync failed: ${e.message}"
                                } finally {
                                    lineupBusy = false
                                }
                            }
                        }
                    },
                    modifier = buttonFocus(6, 1),
                    selected = shownMusic,
                    sub = if (shownMusic) "on - Plex audio libraries" else "off - Plex audio libraries",
                )
            }
            BasicText(
                "West plays the same schedule 3 hours later and adds -W to the name. A new West feed is off air until East has been on for 3 hours. Music uses audio tracks from your included Plex music libraries, with a channel for each genre that has 4 hours. Include music libraries in Manage libraries. Subtitles stay off until you turn them on.",
                style = Retro.Caption,
            )
            BasicText(
                "Hold Left for 2 seconds during an episode to start it over. It plays on its own channel, then the TV returns. A short Left still jumps to the last channel.",
                style = Retro.Caption,
            )

            BasicText("LIBRARY", style = Retro.GuideNumber)
            BasicText("Plex server: $server" + if (lastSync > 0) "   ·   last synced ${clockTime(lastSync)}" else "", style = Retro.Body)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton("Manage libraries", { controller.go(Screen.Libraries) }, modifier = buttonFocus(7, 0), enabled = !lineupBusy)
                TvButton("Re-sync now", {
                    if (!lineupBusy) scope.launch {
                        lineupBusy = true
                        try {
                            app.sync.sync().collect { p -> syncStatus = if (p.finished) "Done" else "${p.stage} ${p.done}/${p.total}" }
                            lastSync = app.settings.lastSyncMs()
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            syncStatus = "Sync failed: ${e.message}"
                        } finally {
                            lineupBusy = false
                        }
                    }
                }, modifier = buttonFocus(7, 1))
                TvButton("Channels", { controller.go(Screen.Channels) }, modifier = buttonFocus(7, 2))
                TvButton("Sign out", {
                    scope.launch {
                        controller.engine.stop()
                        app.settings.signOut()
                        controller.go(Screen.Onboarding)
                    }
                }, modifier = buttonFocus(7, 3))
            }
            syncStatus?.let { BasicText(it, style = Retro.Body.copy(color = Retro.Cyan)) }
            TvButton("Ad library", { controller.go(Screen.AdLibrary) }, modifier = buttonFocus(8, 0))

            BasicText("ABOUT", style = Retro.GuideNumber)
            BasicText("Rerun 0.1 · plays your own Plex library like 90s cable. Nothing you watch is reported to Plex.", style = Retro.Caption)
            BasicText("VT323 font © The VT323 Project Authors, SIL Open Font License 1.1.", style = Retro.Caption)
        }
    }
}
