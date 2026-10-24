package com.rerun.tv.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
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
import androidx.compose.ui.unit.dp
import com.rerun.core.plex.PlexServer
import com.rerun.tv.RerunApp
import com.rerun.tv.ui.TvController
import com.rerun.tv.ui.common.TvButton
import com.rerun.tv.ui.common.TvStage
import com.rerun.tv.ui.theme.Retro
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private sealed interface Step {
    data object Starting : Step
    data class Code(val code: String) : Step
    data class Servers(val list: List<PlexServer>) : Step
    data class Syncing(val stage: String, val done: Int, val total: Int) : Step
    data class Failed(val message: String) : Step
}

/** Link code → pick server → first library sync → start watching. */
@Composable
fun OnboardingScreen(controller: TvController) {
    val app = RerunApp.instance
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf<Step>(Step.Starting) }
    var attempt by remember { mutableStateOf(0) }

    suspend fun chooseServer(server: PlexServer) {
        step = Step.Syncing("Connecting to ${server.name}", 0, 0)
        val base = app.plex.pickConnection(server)
        if (base == null) { step = Step.Failed("Couldn't reach ${server.name}. Is it on and on this network?"); return }
        app.settings.setServerBase(base)
        app.settings.setServerName(server.name)
        app.settings.setServerId(server.clientId)
        app.settings.serverToken = server.accessToken ?: app.settings.accountToken
        app.sync.sync().collect { p -> step = Step.Syncing(p.stage, p.done, p.total) }
        controller.startWatching()
    }

    LaunchedEffect(attempt) {
        try {
            if (app.settings.accountToken == null) {
                val pin = app.plex.createPin()
                step = Step.Code(pin.code)
                var token: String? = null
                while (token == null) {
                    delay(2_000)
                    token = app.plex.checkPin(pin.id).authToken
                }
                app.settings.accountToken = token
            }
            val servers = app.plex.servers(requireNotNull(app.settings.accountToken))
            when (servers.size) {
                0 -> step = Step.Failed("No Plex servers found on this account.")
                1 -> chooseServer(servers.single())
                else -> step = Step.Servers(servers)
            }
        } catch (e: Exception) {
            step = Step.Failed(e.message ?: "Something went wrong")
        }
    }

    TvStage("Rerun only reads your library. Nothing you watch here is reported to Plex.") {
        when (val s = step) {
            Step.Starting -> BasicText("Contacting Plex…", style = Retro.Title)
            is Step.Code -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                BasicText("On your phone or computer, go to", style = Retro.Title)
                BasicText("plex.tv/link", style = Retro.Osd.copy(color = Retro.Cyan))
                BasicText("and enter this code:", style = Retro.Title)
                BasicText(s.code, style = Retro.Huge.copy(color = Retro.Yellow), maxLines = 1)
            }
            is Step.Servers -> {
                val first = remember { FocusRequester() }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    BasicText("Which Plex server?", style = Retro.Title)
                    Spacer(Modifier.height(16.dp))
                    s.list.forEach { server ->
                        TvButton(
                            server.name, onClick = { scope.launch { chooseServer(server) } },
                            modifier = Modifier.width(640.dp).let { if (server == s.list.first()) it.focusRequester(first) else it },
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                }
                LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
            }
            is Step.Syncing -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                BasicText("Tuning in your library…", style = Retro.Title)
                BasicText(s.stage, style = Retro.Body)
                if (s.total > 0) {
                    val pct = (s.done * 100 / s.total).coerceIn(0, 100)
                    val bar = "█".repeat(pct / 4) + "░".repeat(25 - pct / 4)
                    BasicText("$bar  $pct%", style = Retro.Osd.copy(fontSize = Retro.Body.fontSize))
                }
            }
            is Step.Failed -> {
                val retry = remember { FocusRequester() }
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    BasicText(s.message, style = Retro.Title)
                    TvButton("Try again", onClick = { attempt++ }, modifier = Modifier.focusRequester(retry))
                }
                LaunchedEffect(Unit) { runCatching { retry.requestFocus() } }
            }
        }
    }
}
