package com.rerun.tv.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.rerun.core.model.MediaKind
import com.rerun.tv.ui.RestartPrompt
import com.rerun.tv.ui.TvController
import com.rerun.tv.ui.common.TvButton
import com.rerun.tv.ui.theme.Retro

@Composable
fun RestartDialog(controller: TvController) {
    val prompt by controller.restartPrompt.collectAsState()
    val busy by controller.restartBusy.collectAsState()
    val error by controller.restartError.collectAsState()
    val current = prompt ?: return
    RestartChoices(current, busy, error, controller::cancelRestartPrompt,
        controller::confirmRestartStart, controller::confirmRestartLeave)
}

@Composable
internal fun RestartChoices(
    prompt: RestartPrompt,
    busy: Boolean,
    error: String?,
    onCancel: () -> Unit,
    onStart: (Boolean) -> Unit,
    onLeave: (Boolean) -> Unit,
) {
    val first = remember { FocusRequester() }
    val titleStyle = Retro.Title.copy(fontSize = 32.sp)
    val bodyStyle = Retro.Body.copy(fontSize = 24.sp)
    Dialog(onDismissRequest = { if (!busy) onCancel() }) {
        LaunchedEffect(prompt, busy) { if (!busy) first.requestFocus() }
        Column(
            Modifier.widthIn(max = 720.dp).background(Retro.GuideBlueDeep).padding(28.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (prompt) {
                is RestartPrompt.Start -> {
                    BasicText("Start from the beginning", style = titleStyle)
                    BasicText(describe(prompt.item), style = bodyStyle)
                    if (prompt.replacing) {
                        BasicText("Starting this program replaces your saved From the Top station.", style = bodyStyle)
                    }
                    if (prompt.item.kind == MediaKind.EPISODE) {
                        BasicText("Queue the remaining available episodes of this show across seasons?", style = bodyStyle)
                        TvButton("Queue next episodes", { onStart(true) }, Modifier.focusRequester(first),
                            enabled = !busy, textStyle = bodyStyle)
                        TvButton("Just this episode", { onStart(false) }, enabled = !busy, textStyle = bodyStyle)
                    } else {
                        BasicText("Do you want to start this from the beginning?", style = bodyStyle)
                        TvButton("Start movie", { onStart(false) }, Modifier.focusRequester(first),
                            enabled = !busy, textStyle = bodyStyle)
                    }
                }
                is RestartPrompt.Leave -> {
                    BasicText("Leave From the Top?", style = titleStyle)
                    BasicText("Pause saves your place and queue in the guide, including after restarting the app.", style = bodyStyle)
                    TvButton("Pause until later", { onLeave(false) }, Modifier.focusRequester(first),
                        enabled = !busy, textStyle = bodyStyle)
                    TvButton("Remove station", { onLeave(true) }, enabled = !busy, textStyle = bodyStyle)
                }
            }
            TvButton("Cancel", onCancel, enabled = !busy, textStyle = bodyStyle)
            if (busy) BasicText("Saving...", style = bodyStyle)
            if (error != null) BasicText(error, style = bodyStyle.copy(color = Retro.Yellow))
        }
    }
}
