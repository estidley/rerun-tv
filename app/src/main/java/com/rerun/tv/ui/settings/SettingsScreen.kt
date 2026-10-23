package com.rerun.tv.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.rerun.tv.RerunApp
import com.rerun.tv.ui.Effects
import com.rerun.tv.ui.Screen
import com.rerun.tv.ui.TvController
import com.rerun.tv.ui.common.TvButton
import com.rerun.tv.ui.common.TvFrame
import com.rerun.tv.ui.crt.CrtLevel
import com.rerun.tv.ui.player.clockTime
import com.rerun.tv.ui.theme.Retro
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(controller: TvController) {
    val app = RerunApp.instance
    val scope = rememberCoroutineScope()
    val crt by controller.crtLevel.collectAsState()
    val fx by controller.effects.collectAsState()
    var server by remember { mutableStateOf("") }
    var lastSync by remember { mutableStateOf(0L) }
    var syncStatus by remember { mutableStateOf<String?>(null) }
    val first = remember { FocusRequester() }

    BackHandler { controller.go(Screen.Guide) }
    LaunchedEffect(Unit) {
        server = app.settings.serverName() ?: "—"
        lastSync = app.settings.lastSyncMs()
        runCatching { first.requestFocus() }
    }

    TvFrame("Settings", "BACK guide") {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            BasicText("EFFECTS", style = Retro.GuideNumber)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton("All effects on", { controller.setAllEffects(true) })
                TvButton("All effects off", { controller.setAllEffects(false) })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton("Static", { controller.setEffect(Effects.STATIC, !fx.static) }, selected = fx.static, sub = if (fx.static) "on" else "off")
                TvButton("Static sound", { controller.setEffect(Effects.SOUND, !fx.sound) }, selected = fx.sound, sub = if (fx.sound) "on" else "off")
                TvButton("Picture roll", { controller.setEffect(Effects.ROLL, !fx.roll) }, selected = fx.roll, sub = if (fx.roll) "on" else "off")
                TvButton("Power on/off", { controller.setEffect(Effects.POWER, !fx.power) }, selected = fx.power, sub = if (fx.power) "on" else "off")
            }
            BasicText("CRT LOOK", style = Retro.GuideNumber)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CrtLevel.entries.forEachIndexed { i, level ->
                    val label = when (level) { CrtLevel.OFF -> "CRT Off"; CrtLevel.SUBTLE -> "CRT Subtle"; CrtLevel.FULL -> "CRT Full" }
                    TvButton(label, { controller.setCrt(level) }, selected = CrtLevel.from(crt) == level,
                        modifier = if (i == 0) Modifier.focusRequester(first) else Modifier)
                }
            }
            BasicText("Tip: hold OK for 2 seconds while watching to flip between these.", style = Retro.Caption)

            BasicText("LIBRARY", style = Retro.GuideNumber)
            BasicText("Plex server: $server" + if (lastSync > 0) "   ·   last synced ${clockTime(lastSync)}" else "", style = Retro.Body)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton("Re-sync now", {
                    if (syncStatus == null || syncStatus == "Done") scope.launch {
                        try {
                            app.sync.sync().collect { p -> syncStatus = if (p.finished) "Done" else "${p.stage} ${p.done}/${p.total}" }
                            lastSync = app.settings.lastSyncMs()
                        } catch (e: Exception) {
                            syncStatus = "Sync failed: ${e.message}"
                        }
                    }
                })
                TvButton("Channels", { controller.go(Screen.Channels) })
                TvButton("Sign out", {
                    scope.launch {
                        controller.engine.stop()
                        app.settings.signOut()
                        controller.go(Screen.Onboarding)
                    }
                })
            }
            syncStatus?.let { BasicText(it, style = Retro.Body.copy(color = Retro.Cyan)) }

            BasicText("ABOUT", style = Retro.GuideNumber)
            BasicText("Rerun 0.1 · plays your own Plex library like 90s cable. Nothing you watch is reported to Plex.", style = Retro.Caption)
            BasicText("VT323 font © The VT323 Project Authors, SIL Open Font License 1.1.", style = Retro.Caption)
        }
    }
}
