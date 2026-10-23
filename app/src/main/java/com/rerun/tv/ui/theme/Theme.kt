package com.rerun.tv.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.rerun.tv.R

/** The whole app's look: VCR green on-screen text and a 90s cable-guide palette. */
object Retro {
    val Vcr = Color(0xFF39FF14)
    val GuideBlue = Color(0xFF0B1E5B)
    val GuideBlueDeep = Color(0xFF071341)
    val GuideBlueLight = Color(0xFF1B3A8C)
    val Yellow = Color(0xFFFFD200)
    val Cyan = Color(0xFF00E5FF)
    val White = Color(0xFFF2F2F2)
    val Dim = Color(0xFF9AA6C8)
    val Black = Color(0xFF000000)
    val BannerBg = Color(0xCC050814)

    val Font = FontFamily(Font(R.font.vt323))

    private val shadow = Shadow(Color.Black, Offset(2f, 2f), 0f)

    val Osd = TextStyle(fontFamily = Font, color = Vcr, fontSize = 64.sp, shadow = shadow)
    val OsdSmall = TextStyle(fontFamily = Font, color = Vcr, fontSize = 32.sp, shadow = shadow)
    val Title = TextStyle(fontFamily = Font, color = White, fontSize = 40.sp, shadow = shadow)
    val Body = TextStyle(fontFamily = Font, color = White, fontSize = 28.sp, shadow = shadow)
    val Caption = TextStyle(fontFamily = Font, color = Dim, fontSize = 22.sp)
    val GuideNumber = TextStyle(fontFamily = Font, color = Yellow, fontSize = 30.sp, shadow = shadow)
    val GuideCell = TextStyle(fontFamily = Font, color = White, fontSize = 26.sp)
    val Huge = TextStyle(fontFamily = Font, color = White, fontSize = 120.sp, shadow = shadow)
}
