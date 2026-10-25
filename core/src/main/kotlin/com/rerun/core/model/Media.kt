package com.rerun.core.model

enum class MediaKind { MOVIE, EPISODE, TRACK }

/** One playable thing from the Plex library: a movie, a single episode, or a music track. */
data class MediaItem(
    val key: String,
    val kind: MediaKind,
    val title: String,
    val year: Int?,
    val durationMs: Long,
    val genres: Set<String>,
    /** For episodes: the show's network as Plex reports it (Plex stores it in the show's `studio`). */
    val network: String?,
    val studio: String?,
    val contentRating: String?,
    val showKey: String?,
    val showTitle: String?,
    val season: Int?,
    val episode: Int?,
    val tmdbId: String?,
    val labels: Set<String>,
    val collections: Set<String>,
    val partKey: String?,
    val container: String?,
    val videoCodec: String?,
    val audioCodec: String?,
    val thumb: String?,
)

/** Show-level metadata that episodes inherit (network, genres, rating). */
data class ShowMeta(
    val key: String,
    val title: String,
    val year: Int?,
    val studio: String?,
    val genres: Set<String>,
    val contentRating: String?,
    val tmdbId: String?,
    val labels: Set<String>,
    val collections: Set<String>,
)
