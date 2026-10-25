package com.rerun.tv.ui.player

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import com.rerun.core.plex.PlexParse
import com.rerun.tv.ui.theme.ColorTheme
import com.rerun.tv.ui.theme.ProvideColorTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w1920dp-h1080dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MusicScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun musicShowsMetadataAndDriftsWithinTheScreenInEveryTheme() {
        val track = PlexParse.tracks(
            """{"MediaContainer":{"Metadata":[{"ratingKey":"t","title":"Night Drive","grandparentTitle":"The Artist",
              "parentTitle":"The Album","year":1975,"duration":240000,"Genre":[{"tag":"Soul"}]}]}}""",
        ).single()
        val theme = mutableStateOf(ColorTheme.STANDARD_BLUE)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ProvideColorTheme(theme.value) { MusicScreen(track, "Soul", null, 81_000) }
        }
        compose.mainClock.advanceTimeByFrame()
        val initial = compose.onNodeWithTag("music-card").fetchSemanticsNode().boundsInRoot
        for (choice in ColorTheme.entries) {
            compose.runOnIdle { theme.value = choice }
            compose.mainClock.advanceTimeBy(30_000)
            compose.onNodeWithText("Night Drive").assertIsDisplayed()
            compose.onNodeWithText("The Artist").assertIsDisplayed()
            compose.onNodeWithText("The Album").assertIsDisplayed()
            compose.onNodeWithText("1:21 / 4:00").assertIsDisplayed()
            compose.onNodeWithText("Soul  /  1970s  /  GROOVE").assertIsDisplayed()
            compose.onNodeWithText("NO ALBUM ART").assertIsDisplayed()
            val card = compose.onNodeWithTag("music-card").fetchSemanticsNode().boundsInRoot
            val screen = compose.onRoot().fetchSemanticsNode().boundsInRoot
            assertTrue(card.left > screen.left && card.top > screen.top)
            assertTrue(card.right < screen.right && card.bottom < screen.bottom)
            assertNotEquals(initial.left, card.left)
            assertNotEquals(initial.top, card.top)
        }
    }

    @Test
    fun musicHasNoDecorativeTicksBelowTheArtwork() {
        val track = PlexParse.tracks(
            """{"MediaContainer":{"Metadata":[{"ratingKey":"t","title":"Night Drive","duration":240000}]}}""",
        ).single()
        compose.mainClock.autoAdvance = false
        compose.setContent { MusicScreen(track, "Rock", null, 0) }
        compose.mainClock.advanceTimeByFrame()
        val card = compose.onNodeWithTag("music-card")
        val cardBounds = card.fetchSemanticsNode().boundsInRoot
        val artworkBounds = compose.onNodeWithTag("music-artwork").fetchSemanticsNode().boundsInRoot
        val pixels = card.captureToImage().toPixelMap()
        val belowArtwork = (artworkBounds.bottom - cardBounds.top).toInt() + 8
        assertTrue(belowArtwork < pixels.height)
        for (y in belowArtwork until pixels.height) {
            for (x in 0 until pixels.width) {
                assertEquals("Unexpected decoration at ($x, $y)", Color.Black, pixels[x, y])
            }
        }
    }

    @Test
    fun genreCuesAndDurationFormattingHandleMissingAndLongTracks() {
        assertEquals(MusicCue.GROOVE, MusicCue.from("Soul"))
        assertEquals(MusicCue.AMPLIFIED, MusicCue.from("Punk Rock"))
        assertEquals(MusicCue.LOUNGE, MusicCue.from("Jazz"))
        assertEquals(MusicCue.ELECTRONIC, MusicCue.from("Electronic"))
        assertEquals(MusicCue.CLASSICAL, MusicCue.from("Classical"))
        assertEquals(MusicCue.MIX, MusicCue.from("Unclassified"))
        assertEquals("0:00", musicTime(-1))
        assertEquals("0:00", musicTime(0))
        assertEquals("61:01", musicTime(3_661_000))
    }

    @Test
    fun artworkDriftDoesNotRecomposeTheMusicScreen() {
        val track = PlexParse.tracks(
            """{"MediaContainer":{"Metadata":[{"ratingKey":"t","title":"Night Drive","duration":240000}]}}""",
        ).single()
        compose.mainClock.autoAdvance = false
        compose.setContent { MusicScreen(track, "Jazz", null, 0) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        val before = Recomposer.runningRecomposers.value.sumOf { it.changeCount }
        val initial = compose.onNodeWithTag("music-card").fetchSemanticsNode().boundsInRoot
        compose.mainClock.advanceTimeBy(10_000)
        compose.waitForIdle()
        assertEquals(before, Recomposer.runningRecomposers.value.sumOf { it.changeCount })
        assertNotEquals(initial, compose.onNodeWithTag("music-card").fetchSemanticsNode().boundsInRoot)
    }

    @Test
    fun liveMusicProgressStillUpdatesWithoutAParentClock() {
        val track = PlexParse.tracks(
            """{"MediaContainer":{"Metadata":[{"ratingKey":"t","title":"Night Drive","duration":240000}]}}""",
        ).single()
        var position = 0L
        compose.mainClock.autoAdvance = false
        compose.setContent { MusicScreen(track, "Jazz", null, 0) { position } }
        compose.onNodeWithText("0:00 / 4:00").assertExists()
        compose.runOnIdle { position = 81_000 }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("1:21 / 4:00").assertExists()
        compose.onNodeWithText("Night Drive").assertIsDisplayed()
    }
}
