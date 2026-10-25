package com.rerun.tv.player

import android.content.Context
import android.hardware.display.DisplayManager
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.view.Display
import com.rerun.core.plex.DynamicRange
import com.rerun.core.plex.VideoDetails

/** Query the current display on each tune: HDMI capabilities can change while the app is running. */
fun supportsHdr(context: Context, details: VideoDetails): Boolean {
    if (details.range == DynamicRange.SDR) return true
    val display = context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY) ?: return false
    val type = when (details.range) {
        DynamicRange.DOLBY_VISION -> Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION
        DynamicRange.HLG -> Display.HdrCapabilities.HDR_TYPE_HLG
        else -> Display.HdrCapabilities.HDR_TYPE_HDR10
    }
    val supportedTypes = display.hdrCapabilities?.supportedHdrTypes ?: return false
    if (type !in supportedTypes) return false
    val mime = when {
        details.range == DynamicRange.DOLBY_VISION -> "video/dolby-vision"
        details.codec == "hevc" -> "video/hevc"
        details.codec == "av1" -> "video/av01"
        details.codec == "vp9" -> "video/x-vnd.on2.vp9"
        else -> return false
    }
    val profiles = when (mime) {
        "video/hevc" -> setOf(MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10, MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10)
        "video/av01" -> setOf(MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10, MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10HDR10)
        "video/x-vnd.on2.vp9" -> setOf(MediaCodecInfo.CodecProfileLevel.VP9Profile2, MediaCodecInfo.CodecProfileLevel.VP9Profile2HDR)
        else -> when (details.dolbyVisionProfile) {
            5 -> setOf(MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvheStn)
            7 -> setOf(MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvheDtb)
            8 -> setOf(MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvheSt)
            else -> return false
        }
    }
    return MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filterNot { it.isEncoder }.any { codec ->
        codec.supportedTypes.firstOrNull { it.equals(mime, ignoreCase = true) }?.let { supported ->
            codec.getCapabilitiesForType(supported).profileLevels.any { it.profile in profiles }
        } == true
    }
}
