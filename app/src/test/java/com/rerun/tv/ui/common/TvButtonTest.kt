package com.rerun.tv.ui.common

import android.app.Application
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w1920dp-h1080dp-mdpi")
class TvButtonTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun focusedButtonsKeepBothPanelEdgesInBounds() {
        val leftFocus = FocusRequester()
        val rightFocus = FocusRequester()
        compose.setContent {
            TvFrame("Test", "") {
                TvPanel(Modifier.testTag("panel")) {
                    Row(Modifier.fillMaxWidth()) {
                        TvButton("Left", {}, Modifier.weight(1f).focusRequester(leftFocus).testTag("left"))
                        TvButton("Right", {}, Modifier.weight(1f).focusRequester(rightFocus).testTag("right"))
                    }
                }
            }
        }
        val panel = compose.onNodeWithTag("panel").fetchSemanticsNode().boundsInRoot
        val leftBefore = compose.onNodeWithTag("left").fetchSemanticsNode().boundsInRoot
        val rightBefore = compose.onNodeWithTag("right").fetchSemanticsNode().boundsInRoot

        compose.runOnIdle { leftFocus.requestFocus() }
        compose.onNodeWithTag("left").assertIsFocused()
        val leftAfter = compose.onNodeWithTag("left").fetchSemanticsNode().boundsInRoot
        assertEquals(leftBefore, leftAfter)
        assertTrue(leftAfter.left >= panel.left && leftAfter.right <= panel.right)

        compose.runOnIdle { rightFocus.requestFocus() }
        compose.onNodeWithTag("right").assertIsFocused()
        val rightAfter = compose.onNodeWithTag("right").fetchSemanticsNode().boundsInRoot
        assertEquals(rightBefore, rightAfter)
        assertTrue(rightAfter.left >= panel.left && rightAfter.right <= panel.right)
    }
}
