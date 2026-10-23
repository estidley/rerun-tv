package com.rerun.tv.ui.crt

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp

enum class CrtLevel {
    OFF, SUBTLE, FULL;

    fun next(): CrtLevel = entries[(ordinal + 1) % entries.size]

    companion object {
        fun from(s: String?): CrtLevel = entries.firstOrNull { it.name == s } ?: SUBTLE
    }
}

/** Lets on-screen text add a slight RGB fringe when the tube look is at FULL. */
val LocalCrtLevel = compositionLocalOf { CrtLevel.SUBTLE }

private data class CrtSpec(
    val scanAlpha: Float,
    val vignetteEdge: Float,
    val cornerDp: Float,
    val insetFraction: Float,
    val glow: Float,
)

private fun spec(level: CrtLevel) = when (level) {
    CrtLevel.OFF -> null
    CrtLevel.SUBTLE -> CrtSpec(scanAlpha = 0.12f, vignetteEdge = 0.25f, cornerDp = 24f, insetFraction = 0f, glow = 0f)
    CrtLevel.FULL -> CrtSpec(scanAlpha = 0.28f, vignetteEdge = 0.55f, cornerDp = 48f, insetFraction = 0.015f, glow = 0.04f)
}

/**
 * The tube look, drawn over everything: scanlines, vignette, soft center glow, and a rounded
 * black tube mask. All brushes/paths are built once per size (drawWithCache); nothing here
 * redraws per frame, so video playback isn't affected.
 */
@Composable
fun CrtOverlay(level: CrtLevel, modifier: Modifier = Modifier) {
    val s = spec(level) ?: return
    Spacer(
        modifier.fillMaxSize().drawWithCache {
            // 1px dark line every 3px, tiled.
            val tile = Bitmap.createBitmap(1, 3, Bitmap.Config.ARGB_8888).apply {
                setPixel(0, 0, android.graphics.Color.TRANSPARENT)
                setPixel(0, 1, android.graphics.Color.TRANSPARENT)
                setPixel(0, 2, android.graphics.Color.argb((s.scanAlpha * 255).toInt(), 0, 0, 0))
            }
            val scan = ShaderBrush(ImageShader(tile.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
            val r = maxOf(size.width, size.height) * 0.75f
            val vignette = Brush.radialGradient(
                0.55f to Color.Transparent,
                1f to Color.Black.copy(alpha = s.vignetteEdge),
                center = center, radius = r,
            )
            val glow = Brush.radialGradient(
                0f to Color.White.copy(alpha = s.glow), 1f to Color.Transparent,
                center = center, radius = r * 0.7f,
            )
            val inset = size.minDimension * s.insetFraction
            val corner = s.cornerDp.dp.toPx()
            val mask = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(androidx.compose.ui.geometry.Rect(Offset.Zero, size))
                addRoundRect(
                    RoundRect(inset, inset, size.width - inset, size.height - inset, CornerRadius(corner, corner)),
                )
            }
            onDrawWithContent {
                drawRect(scan)
                drawRect(vignette)
                if (s.glow > 0f) drawRect(glow, blendMode = BlendMode.Screen)
                drawPath(mask, Color.Black)
            }
        },
    )
}
