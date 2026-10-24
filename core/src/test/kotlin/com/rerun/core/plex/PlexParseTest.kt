package com.rerun.core.plex

import com.rerun.core.model.MediaKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

fun fixture(name: String): String =
    requireNotNull(PlexParseTest::class.java.getResource("/plex/$name")) { "missing fixture $name" }.readText()

class PlexParseTest {
    @Test
    fun parsesPinToken() {
        val pin = PlexParse.pin(fixture("pin.json"))
        assertEquals(Pin(2310582993, "K7QZ", "abc123token"), pin)
    }

    @Test
    fun pinWithoutTokenIsNull() {
        assertNull(PlexParse.pin("""{"id": 5, "code": "ABCD", "authToken": null}""").authToken)
    }

    @Test
    fun serverConnectionsLocalFirst() {
        val servers = PlexParse.servers(fixture("resources.json"))
        assertEquals(listOf("Basement Server"), servers.map { it.name })
        val order = Playback.connectionOrder(servers.single())
        assertEquals(
            listOf(
                "https://192-168-1-20.abc.plex.direct:32400",
                "https://203-0-113-5.abc.plex.direct:32400",
                "https://relay-1.abc.plex.direct:8443",
            ),
            order.map { it.uri },
        )
        assertEquals("server-token-1", servers.single().accessToken)
    }

    @Test
    fun sectionsKeepVideoTypes() {
        val sections = PlexParse.sections(fixture("sections.json"))
        assertEquals(listOf(Section("1", "movie", "Movies"), Section("2", "show", "TV Shows"), Section("3", "artist", "Music")), sections)
    }

    @Test
    fun movieFieldsAndTmdbGuid() {
        val m = PlexParse.movies(fixture("movies.json")).first()
        assertEquals("1001", m.key)
        assertEquals(MediaKind.MOVIE, m.kind)
        assertEquals("Galaxy Defenders", m.title)
        assertEquals(2019, m.year)
        assertEquals(8_820_000L, m.durationMs)
        assertEquals(setOf("Action", "Science Fiction"), m.genres)
        assertEquals("Marvel Studios", m.studio)
        assertNull(m.network)
        assertEquals("PG-13", m.contentRating)
        assertEquals("299534", m.tmdbId)
        assertEquals(setOf("Favorites"), m.labels)
        assertEquals(setOf("Marvel Cinematic Universe"), m.collections)
        assertEquals("/library/parts/77/1700000000/file.mkv", m.partKey)
        assertEquals("mkv", m.container)
        assertEquals("dca", m.audioCodec)
        assertEquals("/library/metadata/1001/thumb/1700000000", m.thumb)
    }

    @Test
    fun movieWithMissingFieldsStillParses() {
        val m = PlexParse.movies(fixture("movies.json"))[1]
        assertNull(m.year)
        assertEquals(emptySet(), m.genres)
        assertNull(m.tmdbId)
        assertNull(m.contentRating)
    }

    @Test
    fun episodeInheritsShowNetworkAndGenres() {
        val shows = PlexParse.shows(fixture("shows.json")).associateBy { it.key }
        val eps = PlexParse.episodes(fixture("episodes.json"), shows)
        val dbz = eps.first()
        assertEquals(MediaKind.EPISODE, dbz.kind)
        assertEquals("500", dbz.showKey)
        assertEquals("Dragon Ball Z", dbz.showTitle)
        assertEquals(1, dbz.season)
        assertEquals(1, dbz.episode)
        assertEquals("Fuji TV", dbz.network)
        assertEquals(setOf("Animation", "Action & Adventure"), dbz.genres)
        assertEquals("TV-PG", dbz.contentRating) // inherited from show
        assertEquals("12971", dbz.tmdbId)
        assertEquals(setOf("Toonami"), dbz.labels)
        val avatar = eps[1]
        assertEquals("TV-Y7-FV", avatar.contentRating) // episode's own rating wins
        assertEquals("Paramount+", avatar.network)
        assertEquals(2026, avatar.year) // falls back to show year
    }

    @Test
    fun tracksKeepAlbumAndSkipUntagged() {
        val parsed = PlexParse.tracks(
            """
            {"MediaContainer":{"Metadata":[
              {"ratingKey":"9","title":"Night Drive","year":1994,"duration":180000,
               "parentRatingKey":"8","parentTitle":"Midnight","grandparentTitle":"The Jets",
               "parentIndex":1,"index":3,"parentThumb":"/library/metadata/8/thumb/1",
               "Genre":[{"tag":"Jazz"},{"tag":"Soul"}],
               "Media":[{"container":"mp3","audioCodec":"mp3","Part":[{"key":"/library/parts/9/file.mp3"}]}]},
              {"ratingKey":"10","title":"No Genre","duration":180000,"Genre":[]}
            ]}}
            """.trimIndent(),
        )
        assertEquals(1, parsed.size)
        val track = parsed.single()
        assertEquals(MediaKind.TRACK, track.kind)
        assertEquals("Night Drive", track.title)
        assertEquals(180_000L, track.durationMs)
        assertEquals(setOf("Jazz", "Soul"), track.genres)
        assertEquals("8", track.showKey)
        assertEquals("Midnight", track.showTitle)
        assertEquals("The Jets", track.studio)
        assertEquals(1, track.season)
        assertEquals(3, track.episode)
        assertEquals("/library/parts/9/file.mp3", track.partKey)
        assertEquals("mp3", track.container)
        assertEquals("/library/metadata/8/thumb/1", track.thumb)
    }

    @Test
    fun totalSizeReadsContainer() {
        assertEquals(412, PlexParse.totalSize(fixture("movies.json")))
        assertEquals(3, PlexParse.totalSize(fixture("sections.json")))
    }

    @Test
    fun subtitlesUseTheFirstPartAndKeepExternalKeys() {
        val streams = PlexParse.subtitles(
            """{"MediaContainer":{"Metadata":[{"Media":[{"Part":[{"Stream":[
              {"id":1,"streamType":1,"codec":"h264"},
              {"id":2,"streamType":3,"codec":"srt","key":"/library/streams/2","languageCode":"eng","selected":1},
              {"id":3,"streamType":3,"codec":"pgs"}
            ]},{"Stream":[{"id":4,"streamType":3,"codec":"srt"}]}]}]}]}}""",
        )
        assertEquals(listOf(2, 3), streams.map { it.id })
        assertEquals("/library/streams/2", streams.first().key)
        assertEquals("eng", streams.first().language)
        assertEquals(true, streams.first().selected)
        assertEquals("application/x-subrip", streams.first().mimeType)
        assertNull(streams[1].mimeType)
        assertEquals(emptyList(), PlexParse.subtitles("""{"MediaContainer":{}}"""))
    }
}
