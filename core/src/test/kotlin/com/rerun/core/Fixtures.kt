package com.rerun.core

import com.rerun.core.model.MediaItem
import com.rerun.core.model.MediaKind

const val MIN = 60_000L
const val HOUR = 60 * MIN

fun movie(
    key: String,
    title: String = key,
    year: Int? = 1999,
    durationMs: Long = 100 * MIN,
    genres: Set<String> = emptySet(),
    network: String? = null,
    studio: String? = null,
    rating: String? = "PG",
    labels: Set<String> = emptySet(),
) = MediaItem(
    key = key, kind = MediaKind.MOVIE, title = title, year = year, durationMs = durationMs,
    genres = genres, network = network, studio = studio, contentRating = rating,
    showKey = null, showTitle = null, season = null, episode = null, tmdbId = null,
    labels = labels, collections = emptySet(), partKey = "/library/parts/$key/file.mkv",
    container = "mkv", videoCodec = "h264", audioCodec = "aac", thumb = null,
)

fun episode(
    key: String,
    show: String,
    season: Int = 1,
    episode: Int = 1,
    durationMs: Long = 22 * MIN,
    genres: Set<String> = emptySet(),
    network: String? = null,
    studio: String? = null,
    rating: String? = "TV-Y7",
    year: Int? = 1998,
) = MediaItem(
    key = key, kind = MediaKind.EPISODE, title = "$show S${season}E$episode", year = year,
    durationMs = durationMs, genres = genres, network = network, studio = studio,
    contentRating = rating, showKey = "show:$show", showTitle = show, season = season,
    episode = episode, tmdbId = null, labels = emptySet(), collections = emptySet(),
    partKey = "/library/parts/$key/file.mkv", container = "mkv", videoCodec = "h264",
    audioCodec = "aac", thumb = null,
)
