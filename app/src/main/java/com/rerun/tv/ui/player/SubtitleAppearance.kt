package com.rerun.tv.ui.player

import android.graphics.Color
import android.graphics.Typeface
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView

@androidx.annotation.OptIn(UnstableApi::class)
enum class SubtitleSize(val label: String, val fraction: Float) {
    SMALL("Small", 0.04f),
    MEDIUM("Medium", SubtitleView.DEFAULT_TEXT_SIZE_FRACTION),
    LARGE("Large", 0.07f);

    companion object {
        fun from(value: String): SubtitleSize = entries.firstOrNull { it.name == value } ?: MEDIUM
    }
}

enum class SubtitleColor(val label: String, val argb: Int) {
    WHITE("White", Color.WHITE),
    YELLOW("Yellow", Color.YELLOW),
    CYAN("Cyan", Color.CYAN),
    GREEN("Green", Color.GREEN);

    companion object {
        fun from(value: String): SubtitleColor = entries.firstOrNull { it.name == value } ?: WHITE
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
internal fun SubtitleView.applyAppearance(size: SubtitleSize, color: SubtitleColor) {
    setApplyEmbeddedStyles(false)
    setApplyEmbeddedFontSizes(false)
    setFractionalTextSize(size.fraction)
    setStyle(CaptionStyleCompat(
        color.argb,
        0x99000000.toInt(),
        Color.TRANSPARENT,
        CaptionStyleCompat.EDGE_TYPE_OUTLINE,
        Color.BLACK,
        Typeface.DEFAULT,
    ))
}
