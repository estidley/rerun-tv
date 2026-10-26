package com.rerun.tv.data

import android.app.Application
import androidx.room.Room
import com.rerun.core.schedule.Scheduler
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class LibrarySyncTest {
    private lateinit var db: RerunDb
    private lateinit var settings: Settings
    private lateinit var api: PlexApi
    private lateinit var sync: LibrarySync
    private val base = "http://127.0.0.1:32400"
    private val token = "test-only-placeholder"

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), RerunDb::class.java)
            .allowMainThreadQueries().build()
        settings = mock(Settings::class.java)
        api = mock(PlexApi::class.java)
        `when`(settings.serverBase()).thenReturn(base)
        `when`(settings.serverToken).thenReturn(token)
        `when`(settings.isOn(Settings.MUSIC)).thenReturn(false)
        `when`(api.get(base, "/library/sections", token)).thenReturn(
            """{"MediaContainer":{"Directory":[
              {"key":"1","type":"movie","title":"Movies"},
              {"key":"2","type":"show","title":"TV"},
              {"key":"3","type":"artist","title":"Music"},
              {"key":"4","type":"photo","title":"Photos"},
              {"key":"5","type":"movie","title":"Private movies"}
            ]}}""",
        )
        page("/library/sections/1/all?type=1&includeGuids=1", "movie")
        page("/library/sections/5/all?type=1&includeGuids=1", "private")
        page("/library/sections/2/all?type=2&includeGuids=1", "show")
        page("/library/sections/2/all?type=4", "episode")
        page("/library/sections/3/all?type=10", "track")
        page("/library/sections/3/all?type=8", "artist")
        page("/library/sections/3/all?type=9", "album")
        sync = LibrarySync(api, settings, ScheduleRepo(db, Scheduler(ZoneId.of("UTC"))))
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun startupRebuildRunsOnlyWhenTheInstallIdentityChangesOrWasInvalidated() = runBlocking<Unit> {
        `when`(settings.lineupAppVersion()).thenReturn("3:100")
        sync.refreshChannelsForAppVersion("3:100")
        verify(settings, never()).setLineupAppVersion("3:100")
        sync.refreshChannelsForAppVersion("3:200")
        verify(settings).setLineupAppVersion("3:200")
        `when`(settings.lineupAppVersion()).thenReturn(null)
        sync.refreshChannelsForAppVersion("3:200")
        verify(settings, times(2)).setLineupAppVersion("3:200")
    }

    @Test
    fun pickerListsOnlySupportedLibraries() = runBlocking {
        assertEquals(listOf("1", "2", "3", "5"), sync.libraries().map { it.key })
    }

    @Test
    fun adPickerListsVideoLibrariesAndChosenAdsStayOutOfRegularImports() = runBlocking<Unit> {
        `when`(settings.adLibrary()).thenReturn("5")
        assertEquals(listOf("1", "5"), sync.adLibraries().map { it.key })
        assertEquals(listOf("1", "2", "3"), sync.libraries().map { it.key })
        sync.sync().collect()
        assertEquals(setOf("movie", "episode"), db.items().all().map { it.key }.toSet())
        verify(api, never()).get(base, "/library/sections/5/all?type=1&includeGuids=1", token, 0, 200)
    }

    @Test
    fun excludingAnAdLibraryRemovesStaleSchedulesButPreservesUnaffectedChannels() = runBlocking {
        sync.sync().collect()
        val start = System.currentTimeMillis()
        val channels = listOf("ads-one", "ads-two", "regular")
        for ((index, id) in channels.withIndex()) {
            db.channels().upsert(ChannelRow.of(
                com.rerun.core.model.ChannelDef(
                    id, 3000 + index, id, com.rerun.core.model.ChannelKind.CUSTOM, com.rerun.core.model.Rules(),
                ), builtIn = false, dormant = false,
            ))
            db.states().put(StateRow(id, com.rerun.core.codec.StateCodec.encode(com.rerun.core.schedule.ChannelState(seed = 1))))
            db.entries().insertAll(listOf(EntryRow.of(
                com.rerun.core.schedule.ScheduleEntry(
                    id, start, start + 60_000, com.rerun.core.schedule.EntryKind.PROGRAM,
                    if (id == "regular") "movie" else "private",
                ),
            )))
        }
        `when`(settings.adLibrary()).thenReturn("5")
        sync.sync().collect()
        for (id in channels.take(2)) {
            assertTrue(db.entries().range(id, start, start + 60_000).none { it.itemKey == "private" })
        }
        assertEquals("movie", db.entries().range("regular", start, start + 60_000).single().itemKey)
        assertNotNull(db.states().get("regular"))
    }

    @Test
    fun selectedLibrariesExcludeOtherAndNewLibrariesOnEverySync() = runBlocking<Unit> {
        `when`(settings.selectedLibraries()).thenReturn(setOf("2"))
        repeat(2) { sync.sync().collect() }
        assertEquals(listOf("episode"), db.items().all().map { it.key })
        verify(api, never()).get(base, "/library/sections/1/all?type=1&includeGuids=1", token, 0, 200)
        verify(api, never()).get(base, "/library/sections/5/all?type=1&includeGuids=1", token, 0, 200)
        verify(api, never()).get(base, "/library/sections/3/all?type=10", token, 0, 200)
    }

    @Test
    fun legacyInstallationWithoutSelectionKeepsAllVideoLibraries() = runBlocking {
        sync.sync().collect()
        assertEquals(setOf("movie", "private", "episode"), db.items().all().map { it.key }.toSet())
    }

    @Test
    fun musicSwitchDoesNotBypassLibrarySelection() = runBlocking {
        `when`(settings.isOn(Settings.MUSIC)).thenReturn(true)
        `when`(settings.selectedLibraries()).thenReturn(setOf("1"))
        sync.sync().collect()
        assertEquals(listOf("movie"), db.items().all().map { it.key })
        verify(api, never()).get(base, "/library/sections/3/all?type=10", token, 0, 200)

        `when`(settings.selectedLibraries()).thenReturn(setOf("3"))
        sync.sync().collect()
        assertEquals(listOf("track"), db.items().all().map { it.key })
    }

    @Test
    fun musicUsesAudioLibrariesAndInheritsGenresFromArtists() = runBlocking<Unit> {
        `when`(settings.isOn(Settings.MUSIC)).thenReturn(true)
        `when`(settings.selectedLibraries()).thenReturn(setOf("3"))
        `when`(api.get(base, "/library/sections/3/all?type=8", token, 0, 200)).thenReturn(
            """{"MediaContainer":{"totalSize":1,"Metadata":[{"ratingKey":"a","title":"Artist","Genre":[{"tag":"Soul"}]}]}}""",
        )
        `when`(api.get(base, "/library/sections/3/all?type=9", token, 0, 200)).thenReturn(
            """{"MediaContainer":{"totalSize":1,"Metadata":[{"ratingKey":"b","title":"Album","year":1975,"thumb":"/library/metadata/b/thumb"}]}}""",
        )
        `when`(api.get(base, "/library/sections/3/all?type=10", token, 0, 200)).thenReturn(
            """{"MediaContainer":{"totalSize":1,"Metadata":[{"ratingKey":"t","title":"Song","duration":14400000,
              "grandparentRatingKey":"a","parentRatingKey":"b","Media":[{"container":"mp3","Part":[{"key":"/library/parts/t/file.mp3"}]}]}]}}""",
        )
        sync.sync().collect()
        val track = db.items().all().single().toItem()
        assertEquals(com.rerun.core.model.MediaKind.TRACK, track.kind)
        assertEquals(setOf("Soul"), track.genres)
        assertEquals("Artist", track.studio)
        assertEquals("Album", track.showTitle)
        assertEquals(1975, track.year)
        assertTrue(db.channels().active().any { it.def.kind == com.rerun.core.model.ChannelKind.MUSIC })
        verify(api, never()).get(base, "/library/sections/3/all?type=1", token, 0, 200)
        verify(api, never()).get(base, "/library/sections/1/all?type=1&includeGuids=1", token, 0, 200)
    }

    @Test
    fun missingSelectedLibrariesFailWithoutErasingSavedLibrary() = runBlocking {
        `when`(settings.selectedLibraries()).thenReturn(setOf("1"))
        sync.sync().collect()
        `when`(settings.selectedLibraries()).thenReturn(setOf("removed"))
        try {
            sync.sync().collect()
            fail("Unavailable selection must fail")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("selected libraries"))
        }
        assertEquals(listOf("movie"), db.items().all().map { it.key })
    }

    @Test
    fun savingNewSelectionRebuildsSchedulesAndPersistsOnlyAfterSuccessfulSync() = runBlocking {
        sync.sync(setOf("1")).collect()
        val repo = ScheduleRepo(db, Scheduler(ZoneId.of("UTC")))
        repo.ensure(System.currentTimeMillis())
        assertTrue(db.entries().rangeAll(0, Long.MAX_VALUE).isNotEmpty())
        sync.sync(setOf("2")).collect()
        assertEquals(listOf("episode"), db.items().all().map { it.key })
        assertEquals(setOf("2"), com.rerun.core.codec.SetCodec.decode(db.settings().get(Settings.SELECTED_LIBRARIES)!!))
        assertTrue(db.entries().rangeAll(0, Long.MAX_VALUE).isEmpty())
        try {
            sync.sync(setOf("gone")).collect()
            fail("Unavailable selection must fail")
        } catch (_: IllegalArgumentException) {
            assertEquals(setOf("2"), com.rerun.core.codec.SetCodec.decode(db.settings().get(Settings.SELECTED_LIBRARIES)!!))
        }
        assertEquals(listOf("episode"), db.items().all().map { it.key })
    }

    @Test
    fun failedLibraryFetchKeepsPreviousSelectionAndSchedules() = runBlocking {
        sync.sync(setOf("1")).collect()
        ScheduleRepo(db, Scheduler(ZoneId.of("UTC"))).ensure(System.currentTimeMillis())
        val entries = db.entries().rangeAll(0, Long.MAX_VALUE)
        `when`(api.get(base, "/library/sections/2/all?type=4", token, 0, 200))
            .thenAnswer { throw java.io.IOException("Server unavailable") }
        try {
            sync.sync(setOf("2")).collect()
            fail("Fetch must fail")
        } catch (_: java.io.IOException) {
            assertEquals(setOf("1"), com.rerun.core.codec.SetCodec.decode(db.settings().get(Settings.SELECTED_LIBRARIES)!!))
        }
        assertEquals(entries, db.entries().rangeAll(0, Long.MAX_VALUE))
        assertEquals(listOf("movie"), db.items().all().map { it.key })
    }

    private suspend fun page(path: String, key: String) {
        `when`(api.get(base, path, token, 0, 200)).thenReturn(
            """{"MediaContainer":{"totalSize":1,"Metadata":[
              {"ratingKey":"$key","title":"$key","duration":14400000,"Genre":[{"tag":"Jazz"}]}
            ]}}""",
        )
    }
}
