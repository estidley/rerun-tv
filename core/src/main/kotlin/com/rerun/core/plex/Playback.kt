package com.rerun.core.plex

import com.rerun.core.model.MediaItem
import java.net.URLEncoder

sealed interface PlaybackPlan {
    /** Play the file directly and seek the player to [startMs]. */
    data class Direct(val url: String, val startMs: Long) : PlaybackPlan
    /** Plex transcodes; the start offset is already in the URL, so the player starts at 0. */
    data class Transcoded(val url: String) : PlaybackPlan
    /** Nothing playable for this slot: show the Up Next card. */
    data class UpNext(val reason: String) : PlaybackPlan
}

object Playback {
    private val containers = setOf("mkv", "mp4", "m4v", "mov", "ts")
    private val video = setOf("h264", "hevc", "mpeg2video", "vp9", "av1")
    private val audio = setOf("aac", "ac3", "eac3", "mp3", "opus", "flac", "vorbis")

    fun canDirectPlay(item: MediaItem): Boolean =
        item.partKey != null &&
            item.container?.lowercase() in containers &&
            item.videoCodec?.lowercase() in video &&
            item.audioCodec?.lowercase() in audio

    fun directUrl(base: String, item: MediaItem, token: String): String =
        "$base${requireNotNull(item.partKey)}?X-Plex-Token=${enc(token)}"

    fun transcodeUrl(base: String, item: MediaItem, token: String, clientId: String, session: String, offsetMs: Long): String =
        "$base/video/:/transcode/universal/start.m3u8" +
            "?path=${enc("/library/metadata/${item.key}")}" +
            "&mediaIndex=0&partIndex=0&protocol=hls" +
            "&offset=${offsetMs / 1000}" +
            "&fastSeek=1&directPlay=0&directStream=1&directStreamAudio=0" +
            "&videoResolution=1920x1080&maxVideoBitrate=20000&subtitles=none" +
            "&session=${enc(session)}&X-Plex-Session-Identifier=${enc(session)}" +
            "&X-Plex-Client-Identifier=${enc(clientId)}&X-Plex-Product=Rerun&X-Plex-Platform=Android" +
            "&X-Plex-Token=${enc(token)}"

    /** Local direct connections first, then remote direct, then relays. */
    fun connectionOrder(server: PlexServer): List<PlexConnection> =
        server.connections.sortedBy { c -> if (c.relay) 2 else if (c.local) 0 else 1 }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}

fun planPlayback(item: MediaItem?, base: String, token: String, clientId: String, session: String, offsetMs: Long): PlaybackPlan =
    when {
        item == null -> PlaybackPlan.UpNext("missing")
        Playback.canDirectPlay(item) -> PlaybackPlan.Direct(Playback.directUrl(base, item, token), offsetMs)
        else -> PlaybackPlan.Transcoded(Playback.transcodeUrl(base, item, token, clientId, session, offsetMs))
    }
