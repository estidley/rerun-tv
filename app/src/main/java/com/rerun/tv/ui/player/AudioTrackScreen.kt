package com.rerun.tv.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.rerun.tv.ui.Screen
import com.rerun.tv.ui.TvController
import com.rerun.tv.ui.common.TvButton
import com.rerun.tv.ui.common.TvStage
import com.rerun.tv.ui.theme.Retro
import kotlinx.coroutines.CancellationException

@Composable
fun AudioTrackScreen(controller: TvController) {
    val state by controller.engine.state.collectAsState()
    val first = remember { FocusRequester() }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { first.requestFocus() }
    BackHandler { controller.go(Screen.Player) }
    fun select(id: Int?) {
        try {
            controller.engine.selectAudioTrack(id, state.tuneId)
            controller.go(Screen.Player)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            error = "Couldn't change audio: ${e.message}"
        }
    }
    TvStage("BACK picture") {
        BasicText("AUDIO TRACKS", style = Retro.Title)
        BasicText(describe(state.item), style = Retro.Body)
        BasicText("This choice applies to this airing only. Alternate tracks may require Plex audio conversion.", style = Retro.Caption)
        error?.let { BasicText(it, style = Retro.Caption) }
        TvButton("Use preferred language", { select(null) }, Modifier.focusRequester(first))
        if (state.audioTracks.isEmpty()) BasicText("No selectable audio tracks for this program.", style = Retro.Body)
        state.audioTracks.forEach { track ->
            TvButton(
                track.title, { select(track.id) }, selected = track.id == state.selectedAudioId,
                sub = listOfNotNull(track.language, track.codec, track.channels?.let { "$it channels" }).joinToString(" - "),
            )
        }
    }
}
