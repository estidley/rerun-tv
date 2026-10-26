package com.rerun.tv.ui.touch

import android.content.pm.PackageManager
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** A finished touch gesture, before it is mapped to a screen action. */
enum class TouchGesture { TAP, LONG_PRESS, SWIPE_UP, SWIPE_DOWN, SWIPE_LEFT, SWIPE_RIGHT }

/** Player actions reachable by touch. Each mirrors a remote button. */
enum class PlayerTouchAction { TOGGLE_BANNER, CHANNEL_UP, CHANNEL_DOWN, LAST_CHANNEL, SHOW_INFO, OPEN_GUIDE }

enum class GuideTouchAction { SELECT, WATCH }

data class DragSteps(val steps: Int, val remainder: Float)

/**
 * Touch controls for phones and tablets. The remote keeps its own key handling;
 * these mappings only run on devices that report a touchscreen.
 */
object TouchControls {
    /** Minimum travel, in dp, before a drag on the player counts as a swipe. */
    const val MIN_SWIPE_DP = 48f

    /** Guide drag distance, in dp, that moves the selection one row or one time block. */
    const val GUIDE_ROW_STEP_DP = 64f
    const val GUIDE_TIME_STEP_DP = 160f

    /** The dominant axis must be at least this many times longer than the other one. */
    private const val AXIS_RATIO = 1.5f

    fun classifySwipe(dx: Float, dy: Float, minDistancePx: Float): TouchGesture? {
        val ax = abs(dx)
        val ay = abs(dy)
        return when {
            ay >= minDistancePx && ay >= ax * AXIS_RATIO -> if (dy < 0) TouchGesture.SWIPE_UP else TouchGesture.SWIPE_DOWN
            ax >= minDistancePx && ax >= ay * AXIS_RATIO -> if (dx < 0) TouchGesture.SWIPE_LEFT else TouchGesture.SWIPE_RIGHT
            else -> null
        }
    }

    /**
     * Remote equivalents: Up/Down and CH+/CH- change channel, Left jumps to the last channel,
     * Right and OK show the info banner, Menu/Guide opens the guide.
     */
    fun playerAction(gesture: TouchGesture): PlayerTouchAction = when (gesture) {
        TouchGesture.TAP -> PlayerTouchAction.TOGGLE_BANNER
        TouchGesture.LONG_PRESS -> PlayerTouchAction.OPEN_GUIDE
        TouchGesture.SWIPE_UP -> PlayerTouchAction.CHANNEL_UP
        TouchGesture.SWIPE_DOWN -> PlayerTouchAction.CHANNEL_DOWN
        TouchGesture.SWIPE_LEFT -> PlayerTouchAction.LAST_CHANNEL
        TouchGesture.SWIPE_RIGHT -> PlayerTouchAction.SHOW_INFO
    }

    /**
     * Converts accumulated finger travel into selection steps, list style:
     * moving the finger up (negative) advances to later rows or later times.
     */
    fun dragSteps(accumulated: Float, stepPx: Float): DragSteps {
        val steps = (accumulated / stepPx).toInt()
        return DragSteps(-steps, accumulated - steps * stepPx)
    }

    /** The first tap on a guide cell selects it, like moving focus; tapping the selection is OK. */
    fun guideTap(alreadySelected: Boolean): GuideTouchAction =
        if (alreadySelected) GuideTouchAction.WATCH else GuideTouchAction.SELECT
}

/** True on phones and tablets. TV boxes do not declare a touchscreen. */
@Composable
fun rememberTouchControlsEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) { context.packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN) }
}

/** Player gestures. Pass a remembered [onAction]; the detectors restart when it changes. */
fun Modifier.playerTouchControls(enabled: Boolean, onAction: (PlayerTouchAction) -> Unit): Modifier {
    if (!enabled) return this
    return this
        .pointerInput(onAction) {
            detectTapGestures(
                onTap = { onAction(TouchControls.playerAction(TouchGesture.TAP)) },
                onLongPress = { onAction(TouchControls.playerAction(TouchGesture.LONG_PRESS)) },
            )
        }
        .pointerInput(onAction) {
            val minPx = TouchControls.MIN_SWIPE_DP.dp.toPx()
            var total = Offset.Zero
            detectDragGestures(
                onDragStart = { total = Offset.Zero },
                onDragEnd = {
                    TouchControls.classifySwipe(total.x, total.y, minPx)?.let { onAction(TouchControls.playerAction(it)) }
                },
                onDragCancel = { total = Offset.Zero },
            ) { change, amount ->
                change.consume()
                total += amount
            }
        }
}

/** Guide browsing: drag up/down moves the row selection, drag left/right moves through time. */
fun Modifier.guideTouchScroll(enabled: Boolean, onRows: (Int) -> Unit, onTime: (Int) -> Unit): Modifier =
    if (!enabled) this else composed {
        val rows by rememberUpdatedState(onRows)
        val time by rememberUpdatedState(onTime)
        pointerInput(Unit) {
            val rowStep = TouchControls.GUIDE_ROW_STEP_DP.dp.toPx()
            val timeStep = TouchControls.GUIDE_TIME_STEP_DP.dp.toPx()
            var dy = 0f
            var dx = 0f
            detectDragGestures(onDragStart = { dx = 0f; dy = 0f }) { change, amount ->
                change.consume()
                dy += amount.y
                dx += amount.x
                val rowSteps = TouchControls.dragSteps(dy, rowStep)
                if (rowSteps.steps != 0) { rows(rowSteps.steps); dy = rowSteps.remainder }
                val timeSteps = TouchControls.dragSteps(dx, timeStep)
                if (timeSteps.steps != 0) { time(timeSteps.steps); dx = timeSteps.remainder }
            }
        }
    }

/** Tap and long-press on a guide element. */
fun Modifier.guideTouchTap(enabled: Boolean, onTap: () -> Unit, onLongPress: (() -> Unit)? = null): Modifier =
    if (!enabled) this else composed {
        val tap by rememberUpdatedState(onTap)
        val longPress by rememberUpdatedState(onLongPress)
        pointerInput(onLongPress != null) {
            detectTapGestures(
                onTap = { tap() },
                onLongPress = if (onLongPress != null) { _ -> longPress?.invoke() } else null,
            )
        }
    }