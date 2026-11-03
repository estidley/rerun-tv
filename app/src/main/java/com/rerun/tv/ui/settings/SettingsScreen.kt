package com.rerun.tv.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.platform.testTag
import com.rerun.tv.RerunApp
import com.rerun.tv.data.Settings
import com.rerun.tv.ui.Effects
import com.rerun.tv.ui.Screen
import com.rerun.tv.ui.TvController
import com.rerun.tv.ui.common.TvButton
import com.rerun.tv.ui.common.TvStage
import com.rerun.tv.ui.crt.CrtLevel
import com.rerun.tv.ui.player.clockTime
import com.rerun.tv.ui.player.SubtitleSize
import com.rerun.tv.ui.player.SubtitleColor
import com.rerun.tv.ui.theme.Retro
import com.rerun.tv.ui.theme.ColorTheme
import com.rerun.tv.ui.guide.GuideDensity
import com.rerun.core.plex.HdrMode
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

@Composable
fun SettingsScreen(controller: TvController, app: RerunApp = RerunApp.instance) {
    val scope = rememberCoroutineScope()
    val crt by controller.crtLevel.collectAsState()
    val fx by controller.effects.collectAsState()
    val theme by controller.colorTheme.collectAsState()
    val density by controller.guideDensity.collectAsState()
    val subtitleLanguage by controller.subtitleLanguage.collectAsState()
    val subtitleSize by controller.subtitleSize.collectAsState()
    val subtitleColor by controller.subtitleColor.collectAsState()
    val audioLanguage by controller.audioLanguage.collectAsState()
    val hdrMode by controller.hdrMode.collectAsState()
    var server by remember { mutableStateOf("") }
    var lastSync by remember { mutableStateOf(0L) }
    var localStatus by remember { mutableStateOf<String?>(null) }
    val update by controller.lineupUpdate.collectAsState()
    val lineupBusy = update.busy
    val syncStatus = localStatus ?: update.error ?: update.message
    var signingOut by remember { mutableStateOf(false) }
    val musicOn by remember(app.settings) { app.settings.flag(Settings.MUSIC, false) }.collectAsState(false)
    val subtitlesOn by remember(app.settings) { app.settings.flag(Settings.SUBTITLES, false) }.collectAsState(false)
    val normalizeLoudness by remember(app.settings) { app.settings.flag(Settings.NORMALIZE_LOUDNESS, false) }.collectAsState(false)
    val shownMusic = update.music ?: musicOn
    val focusRows = remember { listOf(2, 4, 3, 3, 3, 4, 2, 4, 1, 3, 3).map { count -> List(count) { FocusRequester() } } }
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

    BackHandler { if (!signingOut) controller.go(Screen.Guide) }
    LaunchedEffect(Unit) {
        server = app.settings.serverName() ?: "—"
        lastSync = app.settings.lastSyncMs()
        if (app.settings.serverBase() == null) {
            controller.go(Screen.Onboarding)
            return@LaunchedEffect
        }
        focusRows.first().first().requestFocus()
    }

    TvStage("BACK guide") {
        Column(
            modifier = Modifier.widthIn(max = 1600.dp).fillMaxWidth().testTag("settings-panel")
                .background(Retro.GuideBlueDeep.copy(alpha = 0.4f))
                .border(1.dp, Retro.Border.copy(alpha = 0.4f)).padding(24.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
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
                TvButton("Size: ${SubtitleSize.from(subtitleSize).label}", {
                    scope.launch {
                        try {
                            val choices = SubtitleSize.entries
                            app.settings.setSubtitleSize(choices[(SubtitleSize.from(subtitleSize).ordinal + 1) % choices.size].name)
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            localStatus = "Couldn't change subtitle size: ${e.message}"
                        }
                    }
                }, modifier = buttonFocus(5, 2))
                TvButton("Color: ${SubtitleColor.from(subtitleColor).label}", {
                    scope.launch {
                        try {
                            val choices = SubtitleColor.entries
                            app.settings.setSubtitleColor(choices[(SubtitleColor.from(subtitleColor).ordinal + 1) % choices.size].name)
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            localStatus = "Couldn't change subtitle color: ${e.message}"
                        }
                    }
                }, modifier = buttonFocus(5, 3))
            }
            BasicText(
                "Text subtitles use a semi-transparent black background. Size and color apply to text rendered by Rerun, not image-based or Plex-burned subtitles.",
                style = Retro.Caption,
            )
            BasicText("LINEUP", style = Retro.GuideNumber)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton(
                    "Music",
                    {
                        if (!lineupBusy) {
                            localStatus = null
                            controller.setMusicEnabled(!shownMusic)
                        }
                    },
                    modifier = buttonFocus(6, 0),
                    selected = shownMusic,
                    sub = if (shownMusic) "on - Plex audio libraries" else "off - Plex audio libraries",
                )
                TvButton("Shared scheduling pilot", { controller.go(Screen.SharedPilot) },
                    modifier = buttonFocus(6, 1), sub = "Share a schedule code")
            }
            BasicText(
                "Music uses audio tracks from your included Plex music libraries, with a channel for each genre that has 4 hours. Include music libraries in Manage libraries. Subtitles stay off until you turn them on.",
                style = Retro.Caption,
            )
            BasicText(
                "Hold Left for 2 seconds during an episode to start it over. It plays on its own channel, then the TV returns. A short Left still jumps to the last channel.",
                style = Retro.Caption,
            )

            BasicText("LIBRARY", style = Retro.GuideNumber)
            val shownLastSync = update.lastSyncMs ?: lastSync
            BasicText("Plex server: $server" + if (shownLastSync > 0) "   ·   last synced ${clockTime(shownLastSync)}" else "", style = Retro.Body)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton("Manage libraries", { controller.go(Screen.Libraries) }, modifier = buttonFocus(7, 0), enabled = !lineupBusy)
                TvButton("Re-sync now", {
                    if (!lineupBusy) {
                        localStatus = null
                        controller.resyncLibrary()
                    }
                }, modifier = buttonFocus(7, 1))
                TvButton("Channels", { controller.go(Screen.Channels) }, modifier = buttonFocus(7, 2))
                TvButton("Sign out", {
                    if (!signingOut) {
                        signingOut = true
                        localStatus = "Signing out..."
                        scope.launch {
                            try {
                                controller.signOut()
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                localStatus = "Sign out failed: ${e.message}"
                            } finally {
                                signingOut = false
                            }
                        }
                    }
                }, modifier = buttonFocus(7, 3), sub = if (signingOut) "Signing out..." else null)
            }
            syncStatus?.let { BasicText(it, style = Retro.Body.copy(color = Retro.Cyan)) }
            TvButton("Ad library", { controller.go(Screen.AdLibrary) }, modifier = buttonFocus(8, 0))

            BasicText("AUDIO", style = Retro.GuideNumber)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton("Audio language: ${languageName(audioLanguage)}", { controller.go(Screen.AudioLanguage) }, modifier = buttonFocus(9, 0))
                TvButton("Current program audio tracks", { controller.go(Screen.AudioTracks) }, modifier = buttonFocus(9, 1))
                TvButton("Normalize loudness", {
                    scope.launch {
                        try {
                            controller.setNormalizeLoudness(!normalizeLoudness)
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            localStatus = "Couldn't change loudness normalization: ${e.message}"
                        }
                    }
                }, modifier = buttonFocus(9, 2), selected = normalizeLoudness)
            }
            BasicText(
                "Level programs and ads using Plex audio transcoding. Requires Plex Pass, a recent server, and " +
                    "Analyze audio tracks for loudness in Plex Server > Library for both libraries. " +
                    "Unavailable tracks, video conversion, Dolby Vision, and selected subtitles play at original volume. " +
                    "Does not normalize music stations.",
                style = Retro.Caption,
            )
            BasicText("HDR PLAYBACK", style = Retro.GuideNumber)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HdrMode.entries.forEachIndexed { index, mode ->
                    TvButton(mode.label, {
                        scope.launch {
                            try {
                                controller.setHdrMode(mode)
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                localStatus = "Couldn't change HDR playback: ${e.message}"
                            }
                        }
                    }, modifier = buttonFocus(10, index), selected = HdrMode.from(hdrMode) == mode)
                }
            }
            BasicText(
                "Auto checks the display and HDR decoder. Choose Convert HDR to SDR for flashing or incorrect colors. " +
                    "Enable HDR tone mapping in Plex Server > Transcoder (Plex Pass required). Conversion uses server resources; " +
                    "some Dolby Vision profiles need an SDR version.",
                style = Retro.Caption,
            )

            BasicText("ABOUT", style = Retro.GuideNumber)
            BasicText("Rerun ${com.rerun.tv.BuildConfig.VERSION_NAME} · plays your own Plex library like 90s cable. Nothing you watch is reported to Plex.", style = Retro.Caption)
            BasicText("VT323 font © The VT323 Project Authors, SIL Open Font License 1.1.", style = Retro.Caption)
        }
    }
}
