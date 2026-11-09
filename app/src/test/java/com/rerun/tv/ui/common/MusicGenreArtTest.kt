package com.rerun.tv.ui.common

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MusicGenreArtTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun musicGenresGetDistinctIconsAndTheirOwnWordmarkNames() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Column(Modifier.testTag("genre-gallery").background(Color(0xFF071341)).padding(8.dp)) {
                    Row {
                        Box(Modifier.size(96.dp).testTag("jazz-icon")) {
                            ChannelIcon("music:jazz", "Jazz")
                        }
                        Box(Modifier.size(96.dp).testTag("rock-icon")) {
                            ChannelIcon("music:progressive-rock", "Progressive Rock")
                        }
                    }
                    Box(Modifier.width(400.dp).height(120.dp)) {
                        ChannelWordmark("music:progressive-rock", "Progressive Rock")
                    }
                    Box(Modifier.width(400.dp).height(120.dp)) {
                        ChannelWordmark("music:jazz", "Jazz")
                    }
                }
            }
        }
        compose.onNodeWithText("PROGRESSIVE ROCK").assertIsDisplayed()
        compose.onNodeWithText("JAZZ").assertIsDisplayed()
        val jazz = compose.onNodeWithTag("jazz-icon").captureToImage().asAndroidBitmap()
        val rock = compose.onNodeWithTag("rock-icon").captureToImage().asAndroidBitmap()
        assertFalse("Genre icons must not be identical generic artwork", jazz.sameAs(rock))
        val output = java.io.File("build/reports/music-genre-art.png")
        output.parentFile.mkdirs()
        output.outputStream().use {
            compose.onNodeWithTag("genre-gallery").captureToImage().asAndroidBitmap()
                .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
