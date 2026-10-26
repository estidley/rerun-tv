package com.rerun.tv.ui.touch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TouchControlsTest {
    private val min = 100f

    @Test
    fun shortOrDiagonalDragsAreNotSwipes() {
        assertNull(TouchControls.classifySwipe(40f, -60f, min))
        assertNull(TouchControls.classifySwipe(0f, 0f, min))
        assertNull(TouchControls.classifySwipe(120f, 110f, min))
    }

    @Test
    fun dominantAxisPicksTheSwipeDirection() {
        assertEquals(TouchGesture.SWIPE_UP, TouchControls.classifySwipe(10f, -150f, min))
        assertEquals(TouchGesture.SWIPE_DOWN, TouchControls.classifySwipe(-20f, 150f, min))
        assertEquals(TouchGesture.SWIPE_LEFT, TouchControls.classifySwipe(-150f, 30f, min))
        assertEquals(TouchGesture.SWIPE_RIGHT, TouchControls.classifySwipe(150f, -30f, min))
    }

    @Test
    fun playerGesturesMirrorTheRemote() {
        assertEquals(PlayerTouchAction.TOGGLE_BANNER, TouchControls.playerAction(TouchGesture.TAP))
        assertEquals(PlayerTouchAction.CHANNEL_UP, TouchControls.playerAction(TouchGesture.SWIPE_UP))
        assertEquals(PlayerTouchAction.CHANNEL_DOWN, TouchControls.playerAction(TouchGesture.SWIPE_DOWN))
        assertEquals(PlayerTouchAction.LAST_CHANNEL, TouchControls.playerAction(TouchGesture.SWIPE_LEFT))
        assertEquals(PlayerTouchAction.SHOW_INFO, TouchControls.playerAction(TouchGesture.SWIPE_RIGHT))
        assertEquals(PlayerTouchAction.OPEN_GUIDE, TouchControls.playerAction(TouchGesture.LONG_PRESS))
    }

    @Test
    fun guideDragStepsFollowTheFingerAndKeepTheRemainder() {
        // Finger moving up reveals later rows, like scrolling a list.
        assertEquals(DragSteps(2, -30f), TouchControls.dragSteps(-230f, 100f))
        assertEquals(DragSteps(-1, 50f), TouchControls.dragSteps(150f, 100f))
        assertEquals(DragSteps(0, 99f), TouchControls.dragSteps(99f, 100f))
    }

    @Test
    fun guideTapSelectsFirstAndWatchesOnSecondTap() {
        assertEquals(GuideTouchAction.SELECT, TouchControls.guideTap(alreadySelected = false))
        assertEquals(GuideTouchAction.WATCH, TouchControls.guideTap(alreadySelected = true))
    }
}
