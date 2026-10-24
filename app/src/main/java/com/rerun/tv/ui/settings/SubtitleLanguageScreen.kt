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
import com.rerun.tv.ui.Screen
import com.rerun.tv.ui.TvController
import com.rerun.tv.ui.common.TvButton
import com.rerun.tv.ui.common.TvStage
import com.rerun.tv.ui.theme.Retro
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal fun languageName(code: String): String =
    if (code == "auto") "Automatic" else Locale.forLanguageTag(code).getDisplayLanguage(Locale.getDefault())

@Composable
fun SubtitleLanguageScreen(controller: TvController) {
    val selected by controller.subtitleLanguage.collectAsState()
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    val first = remember { FocusRequester() }
    val languages = remember {
        listOf("auto") + Locale.getISOLanguages().sortedBy { languageName(it).lowercase() }
    }
    BackHandler { controller.go(Screen.Settings) }
    LaunchedEffect(Unit) { first.requestFocus() }
    TvStage("BACK settings") {
        BasicText("SUBTITLE LANGUAGE", style = Retro.Title)
        BasicText("Automatic uses Plex's selected track. A chosen language stays off when unavailable.", style = Retro.Caption)
        error?.let { BasicText(it, style = Retro.Caption) }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            languages.forEachIndexed { index, code ->
                TvButton(
                    languageName(code), {
                        scope.launch {
                            try {
                                controller.setSubtitleLanguage(code)
                                controller.go(Screen.Settings)
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                error = "Couldn't save language: ${e.message}"
                            }
                        }
                    },
                    modifier = Modifier.width(640.dp).let { if (index == 0) it.focusRequester(first) else it },
                    selected = code == selected,
                )
            }
        }
    }
}
