package com.rerun.tv.ui.player

import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import coil3.compose.AsyncImage
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.rerun.core.model.MediaKind
import com.rerun.core.schedule.EntryKind
import com.rerun.tv.player.Phase
import com.rerun.tv.ui.TvController
import com.rerun.tv.ui.crt.VerticalRoll
import com.rerun.tv.ui.theme.Retro
import kotlinx.coroutines.delay

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(controller: TvController, modifier: Modifier = Modifier) {
    val state by controller.engine.state.collectAsState()
    val bannerUntil by controller.bannerUntil.collectAsState()
    val channels by controller.channels.collectAsState()
    val fx by controller.effects.collectAsState()

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) { now = System.currentTimeMillis(); delay(250) }
    }
    // The channel number shows for 3 seconds after every change.
    val osdStart = remember(state.tuneId) { System.currentTimeMillis() }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    setKeepContentOnPlayerReset(false)
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    player = controller.engine.player()
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (state.phase == Phase.PLAYING && state.item?.kind == MediaKind.TRACK) {
            val art = controller.posterUrl(state.item)
            Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                if (art != null) {
                    AsyncImage(
                        model = art,
                        contentDescription = state.item?.title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(0.72f),
                    )
                }
            }
        }

        when (state.phase) {
            Phase.OFF_AIR -> OffAirCard(state.channel, state.nextItem, state.holdUntilMs ?: now)
            Phase.UP_NEXT -> {
                val entry = state.located?.entry
                val nextItem = when {
                    state.holdUntilMs != null -> state.nextItem
                    entry?.kind == EntryKind.FILLER -> state.nextItem
                    else -> state.item ?: state.nextItem
                }
                UpNextCard(
                    state.channel,
                    nextItem,
                    state.holdUntilMs ?: entry?.endMs ?: now,
                    controller.posterUrl(nextItem),
                )
            }
            Phase.DIFFICULTIES -> DifficultiesCard()
            Phase.TUNING -> if (fx.static) StaticNoise(Modifier.fillMaxSize()) else Box(Modifier.fillMaxSize().background(Color.Black))
            Phase.PLAYING -> Unit
        }
        if (fx.roll) VerticalRoll(active = state.phase == Phase.TUNING, key = state.tuneId)

        if (channels.isEmpty() && state.channel == null) {
            Box(Modifier.fillMaxSize().background(Retro.GuideBlue), contentAlignment = Alignment.Center) {
                BasicText("No channels yet. Open Settings and run a library sync.", style = Retro.Title)
            }
        }

        AnimatedVisibility(
            visible = now - osdStart < 3_000 || now < bannerUntil,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd),
        ) { ChannelOsd(state.channel) }

        AnimatedVisibility(
            visible = now < bannerUntil && state.phase != Phase.DIFFICULTIES && state.phase != Phase.OFF_AIR,
            enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            val entry = state.located?.entry
            InfoBanner(
                state.channel,
                if (entry?.kind == EntryKind.PROGRAM) state.item else null,
                entry?.takeIf { it.kind == EntryKind.PROGRAM }?.endMs,
                state.nextItem,
            )
        }
    }
}
