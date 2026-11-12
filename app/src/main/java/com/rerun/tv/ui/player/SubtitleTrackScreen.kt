package com.rerun.tv.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.rerun.core.plex.SubtitleStream
import com.rerun.tv.ui.Screen
import com.rerun.tv.ui.TvController
import com.rerun.tv.ui.common.TvButton
import com.rerun.tv.ui.common.TvStage
import com.rerun.tv.ui.settings.languageName
import com.rerun.tv.ui.theme.Retro
import kotlinx.coroutines.CancellationException

@Composable
fun SubtitleTrackScreen(controller: TvController) {
    val state by controller.engine.state.collectAsState()
    val preferredLanguage by controller.subtitleLanguage.collectAsState()
    val first = remember { FocusRequester() }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { first.requestFocus() }
    BackHandler { controller.go(Screen.Player) }

    fun action(run: () -> Unit) {
        try {
            run()
            controller.go(Screen.Player)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            error = "Couldn't change subtitles: ${e.message}"
        }
    }

    TvStage("BACK picture") {
        BasicText("SUBTITLES", style = Retro.Title)
        BasicText(describe(state.item), style = Retro.Body)
        BasicText(
            "Off, Automatic and track choices apply to this airing only. Plex watch history and saved track selections are unchanged.",
            style = Retro.Caption,
        )
        error?.let { BasicText(it, style = Retro.Caption) }
        TvButton(
            "Off",
            { action { controller.engine.selectSubtitleTrack(null, state.tuneId) } },
            selected = !state.subtitlesEnabled,
            modifier = Modifier.focusRequester(first),
            sub = "No subtitles for this airing",
        )
        TvButton(
            "Automatic",
            { action { controller.engine.useAutomaticSubtitles(state.tuneId) } },
            selected = state.subtitlesEnabled && state.subtitleAutomatic,
            sub = "Plex selected track, or the first available",
        )
        TvButton(
            "Refresh from Plex",
            { action { controller.engine.refreshSubtitles(state.tuneId) } },
            sub = "Check for newly downloaded subtitles",
        )
        TvButton(
            "Preferred language: ${languageName(preferredLanguage)}",
            { controller.go(Screen.SubtitleLanguage) },
            sub = "Global preference",
        )
        if (state.subtitleTracks.isEmpty()) {
            BasicText("No subtitles found for this program.", style = Retro.Body)
        } else {
            BasicText("AVAILABLE TRACKS", style = Retro.GuideNumber)
        }
        state.subtitleTracks.forEach { track ->
            TvButton(
                subtitleTrackTitle(track),
                { action { controller.engine.selectSubtitleTrack(track.id, state.tuneId) } },
                selected = state.subtitlesEnabled && track.id == state.selectedSubtitleId,
                sub = subtitleTrackDetails(track),
            )
        }
    }
}

internal fun subtitleTrackTitle(track: SubtitleStream): String {
    val language = track.language?.let(::languageName) ?: "Unknown language"
    return track.title?.takeIf { it.isNotBlank() }?.let { "$language — $it" } ?: language
}

internal fun subtitleTrackDetails(track: SubtitleStream): String = listOfNotNull(
    track.codec?.uppercase() ?: "Unknown format",
    if (track.key != null) "External" else "Embedded",
    if (track.forced) "Forced" else null,
    if (track.hearingImpaired) "SDH / hearing impaired" else null,
    if (track.selected) "Plex selected" else null,
).joinToString(" - ")
