package com.rerun.core.plex

enum class HdrMode(val label: String) {
    AUTO("Auto HDR"),
    CONVERT("Convert HDR to SDR"),
    NATIVE("Prefer native HDR");

    companion object {
        fun from(value: String?) = entries.firstOrNull { it.name == value } ?: AUTO
    }
}

enum class DynamicRange { SDR, HDR10, HLG, DOLBY_VISION }

data class VideoDetails(
    val range: DynamicRange = DynamicRange.SDR,
    val codec: String? = null,
    val dolbyVisionProfile: Int? = null,
    val hdr10Compatible: Boolean = false,
    val audio: List<AudioStream> = emptyList(),
)

data class AudioStream(
    val id: Int,
    val language: String?,
    val title: String,
    val codec: String?,
    val channels: Int?,
    val selected: Boolean,
    val canNormalizeLoudness: Boolean = false,
)

fun selectAudio(streams: List<AudioStream>, language: String): AudioStream? {
    val preferred = if (language == "auto") streams else streams.filter {
        it.language != null && subtitleLanguageCode(it.language) == subtitleLanguageCode(language)
    }
    return preferred.firstOrNull { it.selected } ?: preferred.firstOrNull()
        ?: streams.firstOrNull { it.selected } ?: streams.firstOrNull()
}

fun requiresSdr(details: VideoDetails, mode: HdrMode, supported: Boolean): Boolean =
    details.range != DynamicRange.SDR && when (mode) {
        HdrMode.AUTO -> !supported
        HdrMode.CONVERT -> true
        HdrMode.NATIVE -> false
    }
