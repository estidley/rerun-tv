package com.rerun.tv.ui.player

import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.rerun.core.model.MediaKind
import com.rerun.core.schedule.EntryKind
import com.rerun.tv.player.Phase
import com.rerun.tv.player.TunerState
import com.rerun.tv.player.CLIP_FADE_MS
import com.rerun.tv.ui.TvController
import com.rerun.tv.ui.StartupState
import com.rerun.tv.ui.LineupUpdate
import com.rerun.tv.ui.crt.VerticalRoll
import com.rerun.tv.ui.common.RerunWordmark
import com.rerun.tv.ui.theme.Retro
import kotlinx.coroutines.delay

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(controller: TvController, modifier: Modifier = Modifier) {
    val state by controller.engine.state.collectAsState()
    val bannerUntil by controller.bannerUntil.collectAsState()
    val channels by controller.channels.collectAsState()
    val fx by controller.effects.collectAsState()
    val startup by controller.startup.collectAsState()
    val update by controller.lineupUpdate.collectAsState()
    val subtitleSize by controller.subtitleSize.collectAsState()
    val subtitleColor by controller.subtitleColor.collectAsState()

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
            update = { view ->
                view.subtitleView?.applyAppearance(SubtitleSize.from(subtitleSize), SubtitleColor.from(subtitleColor))
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (state.phase == Phase.PLAYING && state.item?.kind == MediaKind.TRACK) {
            state.item?.let { item ->
                val position = remember(controller.engine) { { controller.engine.player().currentPosition } }
                MusicScreen(item, state.channel?.name, controller.posterUrl(item), 0, position)
            }
        }

        when (state.phase) {
            Phase.OFF_AIR -> OffAirCard(state.channel, state.nextItem, state.holdUntilMs ?: System.currentTimeMillis())
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
                    state.holdUntilMs ?: entry?.endMs ?: System.currentTimeMillis(),
                    controller.posterUrl(nextItem),
                )
            }
            Phase.DIFFICULTIES -> {
                DifficultiesCard()
                state.message?.let { message ->
                    BasicText(
                        message, style = Retro.Body,
                        modifier = Modifier.align(Alignment.BottomCenter)
                            .background(Retro.GuideBlueDeep)
                            .padding(horizontal = 48.dp, vertical = 32.dp),
                    )
                }
            }
            Phase.TUNING -> Unit
            Phase.PLAYING -> Unit
        }
        PlaybackTransition(state, fx.static, fx.roll)
        if (state.phase == Phase.PLAYING && state.message != null) {
            BasicText(
                state.message.orEmpty(), style = Retro.Caption,
                modifier = Modifier.align(Alignment.BottomCenter).background(Retro.GuideBlueDeep)
                    .padding(horizontal = 48.dp, vertical = 16.dp),
            )
        }

        if (state.channel == null) {
            StartupMessage(startup, update, channels.isEmpty())
        }

        PlayerOverlays(state, bannerUntil)
    }
}

@Composable
internal fun StartupMessage(startup: StartupState, update: LineupUpdate, channelsEmpty: Boolean) {
    val message = when {
        update.busy -> "Updating guide - please wait.\n${update.message.orEmpty()}"
        startup.loading -> "Loading channels and updating guide - please wait."
        startup.error != null -> startup.error
        startup.empty && channelsEmpty -> "No channels yet. Open Settings and run a library sync."
        else -> null
    }
    if (message != null) {
        Box(Modifier.fillMaxSize().background(Retro.GuideBlue), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.padding(48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                RerunWordmark()
                if (startup.loading || update.busy) StartupSpinner()
                BasicText(message, style = Retro.Body.copy(textAlign = TextAlign.Center))
            }
        }
    }
}

@Composable
private fun StartupSpinner() {
    val trackColor = Retro.Border
    val spinnerColor = Retro.Yellow
    val motion = rememberInfiniteTransition(label = "startup-spinner")
    val angle = motion.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1_000, easing = LinearEasing)),
        label = "startup-spinner-angle",
    )
    Canvas(
        Modifier.size(48.dp).padding(4.dp).semantics {
            contentDescription = "Loading channels"
            progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
        }.testTag("startup-spinner"),
    ) {
        val stroke = 4.dp.toPx()
        drawCircle(trackColor, style = Stroke(stroke))
        rotate(angle.value) {
            drawArc(spinnerColor, -90f, 100f, useCenter = false, style = Stroke(stroke, cap = StrokeCap.Round))
        }
    }
}

@Composable
internal fun PlaybackTransition(state: TunerState, static: Boolean, roll: Boolean) {
    val alpha = animateFloatAsState(
        if (state.sameChannelTransition && (state.fadingOut || state.phase == Phase.TUNING)) 1f else 0f,
        animationSpec = tween(CLIP_FADE_MS), label = "clip-fade",
    )
    if (state.sameChannelTransition) {
        Canvas(Modifier.fillMaxSize().testTag("clip-fade")) { drawRect(Color.Black, alpha = alpha.value) }
    } else {
        if (state.phase == Phase.TUNING) {
            if (static) StaticNoise(Modifier.fillMaxSize().testTag("channel-static"))
            else Box(Modifier.fillMaxSize().background(Color.Black))
        }
        if (roll) VerticalRoll(active = state.phase == Phase.TUNING, key = state.tuneId)
    }
}

@Composable
internal fun BoxScope.PlayerOverlays(state: TunerState, bannerUntil: Long) {
    val osdUntil = remember(state.tuneId) { System.currentTimeMillis() + 3_000 }
    val osdVisible = visibleUntil(osdUntil)
    val bannerVisible = visibleUntil(bannerUntil)
    AnimatedVisibility(
        visible = osdVisible || bannerVisible,
        enter = fadeIn(), exit = fadeOut(),
        modifier = Modifier.align(Alignment.TopEnd),
    ) { ChannelOsd(state.channel) }

    AnimatedVisibility(
        visible = bannerVisible && state.phase != Phase.DIFFICULTIES && state.phase != Phase.OFF_AIR,
        enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut(),
        modifier = Modifier.align(Alignment.BottomCenter),
    ) {
        val entry = state.located?.entry
        InfoBanner(
            state.channel,
            if (entry?.kind == EntryKind.PROGRAM) state.item else null,
            entry?.takeIf { it.kind == EntryKind.PROGRAM }?.endMs,
            state.nextItem,
            state.nextStartMs,
        )
    }
}

@Composable
private fun visibleUntil(deadlineMs: Long): Boolean {
    var visible by remember(deadlineMs) { mutableStateOf(System.currentTimeMillis() < deadlineMs) }
    LaunchedEffect(deadlineMs) {
        delay((deadlineMs - System.currentTimeMillis()).coerceAtLeast(0))
        visible = false
    }
    return visible
}
