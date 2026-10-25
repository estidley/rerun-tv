package com.rerun.tv.ui.channels

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.rerun.tv.RerunApp
import com.rerun.core.catalog.Lineup
import com.rerun.core.model.ChannelKind
import com.rerun.tv.ui.Screen
import com.rerun.tv.ui.TvController
import com.rerun.tv.ui.common.TvButton
import com.rerun.tv.ui.common.TvFrame
import com.rerun.tv.ui.theme.Retro

/** Every channel: active ones first, then dormant ones waiting for content. */
@Composable
fun ChannelManager(controller: TvController) {
    val all by RerunApp.instance.schedule.allChannels().collectAsState(initial = emptyList())
    val first = remember { FocusRequester() }
    BackHandler { controller.go(Screen.Guide) }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    val active = all.filter { !it.dormant && !it.hidden }
    val hidden = all.filter { it.hidden && it.id != Lineup.RESTART_ID }
    val dormant = all.filter { it.dormant && !it.hidden }

    TvFrame("Channels", "OK edit   BACK guide") {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                TvButton("＋  New channel", onClick = { controller.go(Screen.Editor(null)) }, modifier = Modifier.focusRequester(first))
            }
            item { BasicText("ON THE AIR (${active.size})", style = Retro.GuideNumber) }
            items(active, key = { it.id }) { ch ->
                TvButton("${ch.number}   ${ch.name}", onClick = { controller.go(Screen.Editor(ch.id)) }, modifier = Modifier.fillMaxWidth(), sub = hours(ch.poolMs))
            }
            if (hidden.isNotEmpty()) {
                item { BasicText("HIDDEN (${hidden.size})", style = Retro.GuideNumber) }
                items(hidden, key = { it.id }) { ch ->
                    TvButton("${ch.number}   ${ch.name}", onClick = { controller.go(Screen.Editor(ch.id)) }, modifier = Modifier.fillMaxWidth(), sub = "hidden · ${hours(ch.poolMs)}")
                }
            }
            item { BasicText("WAITING FOR CONTENT (${dormant.size})", style = Retro.GuideNumber) }
            items(dormant, key = { it.id }) { ch ->
                val minimum = Lineup.minimumContentMs(ch.def) / 3_600_000L
                TvButton("${ch.number}   ${ch.name}", onClick = { controller.go(Screen.Editor(ch.id)) }, modifier = Modifier.fillMaxWidth(), enabled = true,
                    sub = "${hours(ch.poolMs)} · minimum $minimum hours" +
                        if (ch.def.kind == ChannelKind.MUSIC) "; enable Music in Settings" else "")
            }
        }
    }
}

private fun hours(ms: Long): String {
    val h = ms / 3_600_000.0
    return if (h < 1) "%d min of content".format(ms / 60_000) else "%.1f hours of content".format(h)
}
