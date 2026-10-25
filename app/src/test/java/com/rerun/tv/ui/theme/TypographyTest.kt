package com.rerun.tv.ui.theme

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class TypographyTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun readableTextAndRetroDisplayStylesRemainDistinctAcrossThemes() {
        compose.setContent {
            for (theme in ColorTheme.entries) {
                ProvideColorTheme(theme) {
                    assertNotEquals(Retro.Font, Retro.ReadableFont)
                    for (style in listOf(Retro.Body, Retro.Caption, Retro.GuideCell)) {
                        assertEquals(Retro.ReadableFont, style.fontFamily)
                        assertNull(style.shadow)
                    }
                    assertEquals(FontWeight.Medium, Retro.Body.fontWeight)
                    assertEquals(FontWeight.Medium, Retro.GuideCell.fontWeight)
                    for (style in listOf(Retro.Title, Retro.Osd, Retro.OsdSmall, Retro.GuideNumber, Retro.Huge)) {
                        assertEquals(Retro.Font, style.fontFamily)
                    }
                }
            }
        }
        compose.waitForIdle()
    }
}
