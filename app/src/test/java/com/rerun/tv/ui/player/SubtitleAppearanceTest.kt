package com.rerun.tv.ui.player

import android.app.Application
import android.graphics.Color
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class SubtitleAppearanceTest {
    @Test
    fun mediumWhiteDefaultsUseTranslucentBlackBackground() {
        val view = mock(SubtitleView::class.java)
        view.applyAppearance(SubtitleSize.from("MEDIUM"), SubtitleColor.from("WHITE"))
        verify(view).setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION)
        verify(view).setApplyEmbeddedStyles(false)
        verify(view).setApplyEmbeddedFontSizes(false)
        val style = ArgumentCaptor.forClass(CaptionStyleCompat::class.java)
        verify(view).setStyle(style.capture())
        assertEquals(Color.WHITE, style.value.foregroundColor)
        assertEquals(0x99000000.toInt(), style.value.backgroundColor)
        assertEquals(Color.TRANSPARENT, style.value.windowColor)
    }

    @Test
    fun allSizesAndColorsAreApplied() {
        assertTrue(SubtitleSize.SMALL.fraction < SubtitleSize.MEDIUM.fraction)
        assertTrue(SubtitleSize.MEDIUM.fraction < SubtitleSize.LARGE.fraction)
        for (size in SubtitleSize.entries) for (color in SubtitleColor.entries) {
            val view = mock(SubtitleView::class.java)
            view.applyAppearance(size, color)
            verify(view).setFractionalTextSize(size.fraction)
            val style = ArgumentCaptor.forClass(CaptionStyleCompat::class.java)
            verify(view).setStyle(style.capture())
            assertEquals(color.argb, style.value.foregroundColor)
        }
        assertEquals(SubtitleSize.MEDIUM, SubtitleSize.from("unknown"))
        assertEquals(SubtitleColor.WHITE, SubtitleColor.from("unknown"))
    }
}
