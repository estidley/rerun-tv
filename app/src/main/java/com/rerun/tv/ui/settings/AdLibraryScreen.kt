package com.rerun.tv.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import kotlinx.coroutines.launch

@Composable
fun AdLibraryScreen(controller: TvController, app: RerunApp = RerunApp.instance) {
    val scope = rememberCoroutineScope()
    val first = remember { FocusRequester() }
    var libraries by remember { mutableStateOf<List<Section>?>(null) }
    var selected by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var count by remember { mutableIntStateOf(0) }
    var attempt by remember { mutableIntStateOf(0) }
    BackHandler { controller.go(Screen.Settings) }
    LaunchedEffect(attempt) {
        try {
            selected = app.settings.adLibrary()
            libraries = app.sync.adLibraries()
            count = app.sync.adCount()
            error = null
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            error = "Couldn't load ad libraries: ${e.message}"
        }
    }
    LaunchedEffect(libraries, error) {
        if (libraries != null || error != null) first.requestFocus()
    }
    fun save(key: String?) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                error = null
                status = "Syncing ads..."
                app.sync.syncAds(key).collect { p ->
                    status = if (p.finished) p.stage else "${p.stage} ${p.done}/${p.total}"
                }
                selected = key
                count = app.sync.adCount()
                error = null
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                status = null
                error = "Ad sync failed: ${e.message}"
            } finally {
                busy = false
            }
        }
    }
    TvStage("BACK settings") {
        BasicText("AD LIBRARY", style = Retro.Title)
        BasicText("Choose a Plex Other Videos or Movies library containing your commercial clips.", style = Retro.Caption)
        BasicText("Selecting a library syncs its ad catalog. Clips fill breaks without delaying the next program.", style = Retro.Caption)
        BasicText("The chosen library is excluded from regular imports. Re-sync to remove previously imported clips.", style = Retro.Caption)
        error?.let { BasicText(it, style = Retro.Body.copy(color = Retro.Cyan)) }
        status?.let { BasicText(it, style = Retro.Body) }
        val available = libraries
        if (available == null) {
            if (error == null) BasicText("Loading libraries...", style = Retro.Body)
            else TvButton("Retry", { attempt++ }, Modifier.focusRequester(first))
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TvButton("Off", { save(null) }, Modifier.width(640.dp).focusRequester(first), selected = selected == null, enabled = !busy)
                if (available.isEmpty()) BasicText("No video libraries found. Create an Other Videos library in Plex, then reopen this page.", style = Retro.Caption)
                if (selected != null && available.none { it.key == selected }) {
                    BasicText("Saved ad library is unavailable. Choose another library or Off.", style = Retro.Caption)
                }
                available.forEach { library ->
                    TvButton(
                        library.title, { save(library.key) }, Modifier.width(640.dp),
                        selected = selected == library.key, enabled = !busy,
                    )
                }
                BasicText("$count ad clips synced", style = Retro.Caption)
                TvButton("Force ad sync", { save(selected) }, enabled = !busy && selected != null)
            }
        }
    }
}
