package com.rerun.tv.ui.guide

import kotlin.math.floor

enum class GuideDensity(val label: String, val minimumHeight: Float) {
    COMPACT("Compact", 52f), COMFORTABLE("Comfortable", 72f), LARGE("Large", 104f);

    companion object {
        fun from(value: String?): GuideDensity = entries.firstOrNull { it.name == value } ?: COMFORTABLE
    }
}

internal data class GuideRows(
    val visibleCount: Int,
    val rowHeight: Float,
    val firstIndex: Int,
    val channelNameLines: Int,
    val fillViewport: Boolean,
)

/** Fill the viewport with whole rows while keeping short lineups at a readable height. */
internal fun guideRows(availableHeight: Float, channelCount: Int, focusedIndex: Int, density: GuideDensity = GuideDensity.COMFORTABLE): GuideRows {
    val height = availableHeight.coerceAtLeast(0f)
    val capacity = floor(height / density.minimumHeight).toInt().coerceAtLeast(1)
    val visible = channelCount.coerceAtLeast(0).coerceAtMost(capacity)
    val rowHeight = when {
        visible == 0 -> 0f
        channelCount >= capacity -> height / visible
        else -> density.minimumHeight
    }
    val first = (focusedIndex - visible / 2).coerceIn(0, (channelCount - visible).coerceAtLeast(0))
    val lines = floor((rowHeight - 8f) / 28f).toInt().coerceIn(1, 3)
    return GuideRows(visible, rowHeight, first, lines, channelCount >= capacity)
}
