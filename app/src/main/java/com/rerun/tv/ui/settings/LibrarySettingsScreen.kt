package com.rerun.tv.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.rerun.core.plex.Section
import com.rerun.tv.RerunApp
import com.rerun.tv.ui.Screen
import com.rerun.tv.ui.TvController
import com.rerun.tv.ui.common.TvButton
import com.rerun.tv.ui.common.TvStage
import com.rerun.tv.ui.theme.Retro
import kotlinx.coroutines.CancellationException

@Composable
fun LibrarySettingsScreen(controller: TvController, app: RerunApp = RerunApp.instance) {
    val update by controller.lineupUpdate.collectAsState()
    var libraries by remember { mutableStateOf<List<Section>?>(null) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    val busy = update.busy
    var status by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    val first = remember { FocusRequester() }
    BackHandler { controller.go(Screen.Settings) }
    LaunchedEffect(attempt) {
        try {
            val available = app.sync.libraries()
            require(available.isNotEmpty()) { "No supported libraries found." }
            val saved = app.settings.selectedLibraries()
            selected = available.map { it.key }.filter { saved == null || it in saved }.toSet()
            libraries = available
            status = null
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            status = "Couldn't load libraries: ${e.message}"
        }
    }
    LaunchedEffect(libraries, status) {
        if (libraries != null || status != null) first.requestFocus()
    }
    TvStage("BACK settings") {
        BasicText("INCLUDED LIBRARIES", style = Retro.Title)
        BasicText("Saving rebuilds the guide. Excluded libraries leave the lineup immediately.", style = Retro.Caption)
        val available = libraries
        if (available == null) {
            BasicText(status ?: "Loading libraries...", style = Retro.Body)
            if (status != null) TvButton("Retry", { attempt++ }, Modifier.focusRequester(first))
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                available.forEachIndexed { index, library ->
                    val included = library.key in selected
                    val type = when (library.type) { "movie" -> "Movies"; "show" -> "TV shows"; else -> "Music (requires Music in Settings)" }
                    TvButton(
                        library.title,
                        { selected = if (library.key in selected) selected - library.key else selected + library.key },
                        modifier = Modifier.width(640.dp).let { if (index == 0) it.focusRequester(first) else it },
                        selected = included, enabled = !busy,
                        selectedIndicator = if (included) "+" else "x",
                        sub = "${if (included) "Included" else "Not included"} - $type",
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvButton("Save and sync", {
                        if (!busy) {
                            controller.saveLibraries(selected)
                        }
                    }, enabled = !busy && selected.isNotEmpty())
                    TvButton("Cancel", { controller.go(Screen.Settings) }, enabled = !busy)
                }
                status?.let { BasicText(it, style = Retro.Caption) }
                (update.error ?: update.message)?.let { BasicText(it, style = Retro.Caption) }
            }
        }
    }
}
