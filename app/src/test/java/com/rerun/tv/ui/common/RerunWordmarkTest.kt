package com.rerun.tv.ui.common

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import com.rerun.tv.ui.theme.Retro
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RerunWordmarkTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun beretSpansREAndOverlapsTheTopOfTheLettersAtBothSizes() {
        val fontSize = mutableStateOf(32)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Column {
                    Box(Modifier.background(Color.Black).testTag("wordmark")) {
                        RerunWordmark(Retro.Osd.copy(fontSize = fontSize.value.sp))
                    }
                    Box(Modifier.background(Color.Black).testTag("plain")) {
                        BasicText("RERUN", style = Retro.Osd.copy(fontSize = fontSize.value.sp))
                    }
                }
            }
        }
        for (size in listOf(32, 64)) {
            compose.runOnIdle { fontSize.value = size }
            val image = compose.onNodeWithTag("wordmark").captureToImage()
            val pixels = image.toPixelMap()
            val plain = compose.onNodeWithTag("plain").captureToImage().toPixelMap()
            var visibleInk = 0
            var plainInk = 0
            var brimBottom = -1
            var hatRight = -1
            var lettersTop = pixels.height
            for (y in 0 until pixels.height) {
                for (x in 0 until pixels.width) {
                    when (pixels[x, y].toArgb()) {
                        0xFFD0122D.toInt(), 0xFF7A0C1C.toInt() -> {
                            brimBottom = maxOf(brimBottom, y)
                            hatRight = maxOf(hatRight, x)
                        }
                        0xFF39FF14.toInt() -> {
                            lettersTop = minOf(lettersTop, y)
                            visibleInk++
                        }
                    }
                }
            }
            for (y in 0 until plain.height) for (x in 0 until plain.width) {
                if (plain[x, y].toArgb() == 0xFF39FF14.toInt()) plainInk++
            }
            val output = java.io.File("build/reports/wordmark-$size.png")
            output.parentFile.mkdirs()
            output.outputStream().use { image.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            assertTrue("The hat must not hide the lettering at $size sp ($visibleInk vs $plainInk)", visibleInk >= plainInk * 0.95f)
            assertTrue("Brim should sit on the letter tops at $size sp", brimBottom >= lettersTop - 1)
            assertTrue("Brim should not cover the letter faces at $size sp", brimBottom <= lettersTop + size * 0.08f)
            assertTrue("Beret should cover RE at $size sp", hatRight >= pixels.width * 0.35f)
            assertTrue("Beret should stay over RE at $size sp", hatRight < pixels.width * 0.5f)
        }
    }
}
