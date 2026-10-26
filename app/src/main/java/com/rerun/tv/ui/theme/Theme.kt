package com.rerun.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.rerun.tv.R

data class RetroPalette(
    val background: Color,
    val deep: Color,
    val light: Color,
    val alternate: Color,
    val accent: Color,
    val secondary: Color,
    val text: Color,
    val dim: Color,
    val border: Color,
    val osd: Color,
)

enum class ColorTheme(val label: String, val palette: RetroPalette) {
    STANDARD_BLUE("Standard blue", RetroPalette(
        background = Color(0xFF0B1E5B), deep = Color(0xFF071341),
        light = Color(0xFF1B3A8C), alternate = Color(0xFF142F70),
        accent = Color(0xFFFFD200), secondary = Color(0xFF00E5FF),
        text = Color(0xFFF2F2F2), dim = Color(0xFFB0BCDC),
        border = Color(0xFF4A6BC8), osd = Color(0xFF39FF14),
    )),
    SEVENTIES("70s", RetroPalette(
        background = Color(0xFF49321F), deep = Color(0xFF2B2117),
        light = Color(0xFF76502C), alternate = Color(0xFF5D4328),
        accent = Color(0xFFFFD166), secondary = Color(0xFFDFE8A1),
        text = Color(0xFFFFF2D5), dim = Color(0xFFE0C9A3),
        border = Color(0xFFA88150), osd = Color(0xFFFFD166),
    )),
    FIFTIES("50s", RetroPalette(
        background = Color(0xFF153E43), deep = Color(0xFF0C282D),
        light = Color(0xFF29606A), alternate = Color(0xFF214F55),
        accent = Color(0xFFFFB7CB), secondary = Color(0xFF9FF0DF),
        text = Color(0xFFFFF7E7), dim = Color(0xFFC0DDD8),
        border = Color(0xFF67A19D), osd = Color(0xFF9FF0DF),
    ));

    companion object {
        fun from(value: String?): ColorTheme = entries.firstOrNull { it.name == value } ?: STANDARD_BLUE
    }
}

private val LocalRetroPalette = staticCompositionLocalOf { ColorTheme.STANDARD_BLUE.palette }

@Composable
fun ProvideColorTheme(theme: ColorTheme, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalRetroPalette provides theme.palette, content = content)
}

/** Semantic colors shared by the guide, settings, and overlays. */
object Retro {
    val Vcr: Color @Composable get() = LocalRetroPalette.current.osd
    val GuideBlue: Color @Composable get() = LocalRetroPalette.current.background
    val GuideBlueDeep: Color @Composable get() = LocalRetroPalette.current.deep
    val GuideBlueLight: Color @Composable get() = LocalRetroPalette.current.light
    val AlternateRow: Color @Composable get() = LocalRetroPalette.current.alternate
    val Border: Color @Composable get() = LocalRetroPalette.current.border
    val Yellow: Color @Composable get() = LocalRetroPalette.current.accent
    val Cyan: Color @Composable get() = LocalRetroPalette.current.secondary
    val White: Color @Composable get() = LocalRetroPalette.current.text
    val Dim: Color @Composable get() = LocalRetroPalette.current.dim
    val Black = Color(0xFF000000)
    val BannerBg: Color @Composable get() = GuideBlueDeep.copy(alpha = 0.9f)

    val Font = FontFamily(Font(R.font.vt323))
    val ReadableFont = FontFamily(
        Font(R.font.barlow_semi_condensed_regular, FontWeight.Normal),
        Font(R.font.barlow_semi_condensed_medium, FontWeight.Medium),
    )

    private val shadow = Shadow(Color.Black, Offset(2f, 2f), 0f)

    val Osd: TextStyle @Composable get() = TextStyle(fontFamily = Font, color = Vcr, fontSize = 64.sp, shadow = shadow)
    val ChannelNumber: TextStyle @Composable get() = TextStyle(fontFamily = Font, color = Dim.copy(alpha = 0.75f), fontSize = 26.sp, shadow = shadow)
    val OsdSmall: TextStyle @Composable get() = TextStyle(fontFamily = Font, color = Vcr, fontSize = 32.sp, shadow = shadow)
    val Title: TextStyle @Composable get() = TextStyle(fontFamily = Font, color = White, fontSize = 40.sp, shadow = shadow)
    val Body: TextStyle @Composable get() = TextStyle(fontFamily = ReadableFont, fontWeight = FontWeight.Medium, color = White, fontSize = 28.sp)
    val Caption: TextStyle @Composable get() = TextStyle(fontFamily = ReadableFont, color = Dim, fontSize = 22.sp)
    val GuideNumber: TextStyle @Composable get() = TextStyle(fontFamily = Font, color = Yellow, fontSize = 30.sp, shadow = shadow)
    val GuideCell: TextStyle @Composable get() = TextStyle(fontFamily = ReadableFont, fontWeight = FontWeight.Medium, color = White, fontSize = 26.sp)
    val Huge: TextStyle @Composable get() = TextStyle(fontFamily = Font, color = White, fontSize = 120.sp, shadow = shadow)
}
