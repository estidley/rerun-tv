package com.rerun.tv.ui.guide

import kotlin.math.floor

/** Row and program-panel sizing. [textScale] multiplies guide cell and program-panel text. */
enum class GuideDensity(val label: String, val minimumHeight: Float, val detailHeight: Float, val textScale: Float) {
    COMPACT("Compact", 72f, 170f, 0.9f),
    DEFAULT("Default", 104f, 170f, 1f),
    LARGE("Large", 150f, 260f, 1.35f);

    companion object {
        fun from(value: String?): GuideDensity = entries.firstOrNull { it.name == value } ?: DEFAULT

        /** Maps the pre-0.3 stored names (Compact/Comfortable/Large) onto the current sizes. */
        fun fromLegacy(value: String?): String? = when (value) {
            "COMPACT", "COMFORTABLE" -> COMPACT.name
            "LARGE" -> DEFAULT.name
            else -> null
        }
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
internal fun guideRows(availableHeight: Float, channelCount: Int, focusedIndex: Int, density: GuideDensity = GuideDensity.DEFAULT): GuideRows {
    val height = availableHeight.coerceAtLeast(0f)
    val capacity = floor(height / density.minimumHeight).toInt().coerceAtLeast(1)
    val visible = channelCount.coerceAtLeast(0).coerceAtMost(capacity)
    val rowHeight = when {
        visible == 0 -> 0f
        channelCount >= capacity -> height / visible
        else -> density.minimumHeight
    }
    val first = (focusedIndex - visible / 2).coerceIn(0, (channelCount - visible).coerceAtLeast(0))
    val lines = floor((rowHeight - 8f) / (28f * density.textScale)).toInt().coerceIn(1, 2)
    return GuideRows(visible, rowHeight, first, lines, channelCount >= capacity)
}