package com.rerun.tv.data

import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class LibrarySettingsTest {
    private lateinit var db: RerunDb
    private lateinit var settings: Settings

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), RerunDb::class.java)
            .allowMainThreadQueries().build()
        settings = Settings(RuntimeEnvironment.getApplication(), db.settings())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun choicesPersistAndSameServerKeepsThem() = runBlocking {
        assertNull(settings.selectedLibraries())
        settings.setServerId("server-a")
        settings.setSelectedLibraries(setOf("1", "3"))
        settings.setServerId("server-a")
        val reopened = Settings(RuntimeEnvironment.getApplication(), db.settings())
        assertEquals(setOf("1", "3"), reopened.selectedLibraries())
    }

    @Test
    fun changingServerOrSigningOutClearsSelection() = runBlocking {
        settings.setServerId("server-a")
        settings.setSelectedLibraries(setOf("1"))
        settings.setLastSyncMs(123)
        settings.setServerId("server-b")
        assertNull(settings.selectedLibraries())
        assertEquals(0L, settings.lastSyncMs())
        settings.setSelectedLibraries(setOf("2"))
        settings.signOut()
        assertNull(settings.selectedLibraries())
    }

    @Test
    fun sharedRecipeIsConnectionScopedWhilePresentationPreferencesSurvive() = runBlocking {
        settings.setServerId("server-a")
        settings.setCrtLevel("OFF")
        db.settings().put(SettingRow(Settings.SHARED_CODE, "local-test-recipe"))
        db.settings().put(SettingRow("${Settings.SHARED_PREFIX}break:tbs:0:1", "[]"))
        settings.setServerId("server-a")
        assertEquals("local-test-recipe", db.settings().get(Settings.SHARED_CODE))
        settings.setServerId("server-b")
        assertTrue(db.settings().withPrefix(Settings.SHARED_PREFIX).isEmpty())
        assertEquals("OFF", settings.crtLevel().first())
        db.settings().put(SettingRow(Settings.SHARED_CODE, "local-test-recipe"))
        settings.signOut()
        assertTrue(db.settings().withPrefix(Settings.SHARED_PREFIX).isEmpty())
        assertEquals("OFF", settings.crtLevel().first())
    }

    @Test
    fun emptySelectionCannotBeSaved() = runBlocking {
        settings.setSelectedLibraries(setOf("1"))
        try {
            settings.setSelectedLibraries(emptySet())
            fail("Empty selection must fail")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("at least one"))
        }
        assertEquals(setOf("1"), settings.selectedLibraries())
    }

    @Test
    fun colorThemeDefaultsAndPersistsAcrossRestartAndSignOut() = runBlocking {
        assertEquals("STANDARD_BLUE", settings.colorTheme().first())
        settings.setColorTheme("SEVENTIES")
        val reopened = Settings(RuntimeEnvironment.getApplication(), db.settings())
        assertEquals("SEVENTIES", reopened.colorTheme().first())
        settings.signOut()
        assertEquals("SEVENTIES", settings.colorTheme().first())
        settings.setColorTheme("FIFTIES")
        assertEquals("FIFTIES", reopened.colorTheme().first())
    }

    @Test
    fun subtitleAppearanceDefaultsAndPersists() = runBlocking {
        assertEquals("MEDIUM", settings.subtitleSize().first())
        assertEquals("WHITE", settings.subtitleColor().first())
        settings.setSubtitleSize("LARGE")
        settings.setSubtitleColor("YELLOW")
        val reopened = Settings(RuntimeEnvironment.getApplication(), db.settings())
        assertEquals("LARGE", reopened.subtitleSize().first())
        assertEquals("YELLOW", reopened.subtitleColor().first())
        settings.signOut()
        assertEquals("LARGE", reopened.subtitleSize().first())
        assertEquals("YELLOW", reopened.subtitleColor().first())
        for (invalid in listOf("", "invalid")) {
            try {
                settings.setSubtitleSize(invalid)
                fail("Invalid size must be rejected")
            } catch (_: IllegalArgumentException) {
                assertEquals("LARGE", settings.subtitleSize().first())
            }
            try {
                settings.setSubtitleColor(invalid)
                fail("Invalid color must be rejected")
            } catch (_: IllegalArgumentException) {
                assertEquals("YELLOW", settings.subtitleColor().first())
            }
        }
    }

    @Test
    fun densityAndSubtitleLanguagePersist() = runBlocking {
        assertEquals("DEFAULT", settings.guideDensity().first())
        assertEquals("auto", settings.subtitleLanguageValue())
        settings.setGuideDensity("LARGE")
        settings.setSubtitleLanguage("es")
        val reopened = Settings(RuntimeEnvironment.getApplication(), db.settings())
        assertEquals("LARGE", reopened.guideDensity().first())
        assertEquals("es", reopened.subtitleLanguage().first())
        assertEquals("es", reopened.subtitleLanguageValue())
    }

    @Test
    fun adLibraryPersistsCanBeDisabledAndClearsOnServerChangeAndSignOut() = runBlocking {
        settings.setServerId("a")
        assertNull(settings.adLibrary())
        settings.setAdLibrary("ads")
        val reopened = Settings(RuntimeEnvironment.getApplication(), db.settings())
        assertEquals("ads", reopened.adLibrary())
        settings.setServerId("a")
        assertEquals("ads", settings.adLibrary())
        settings.setAdLibrary(null)
        assertNull(reopened.adLibrary())
        settings.setAdLibrary("ads")
        settings.setServerId("b")
        assertNull(settings.adLibrary())
        settings.setAdLibrary("other")
        settings.signOut()
        assertNull(settings.adLibrary())
    }

    @Test
    fun signOutClearsTheOldAccountAndKeepsDisplayPreferences() = runBlocking {
        settings.accountToken = "test-placeholder"
        settings.serverToken = "test-server-placeholder"
        settings.setServerId("server-a")
        settings.setServerBase("http://127.0.0.1:32400")
        settings.setServerName("Plex")
        settings.setSelectedLibraries(setOf("tv"))
        settings.setLastSyncMs(123)
        settings.setLastChannel("old")
        settings.setColorTheme("FIFTIES")
        settings.setGuideDensity("LARGE")
        val item = com.rerun.core.plex.PlexParse.movies(
            """{"MediaContainer":{"Metadata":[{"ratingKey":"old-item","title":"Old","duration":14400000}]}}""",
        ).single()
        db.items().insertAll(listOf(ItemRow.of(item)))
        db.channels().upsert(ChannelRow.of(
            com.rerun.core.model.ChannelDef("old", 10, "Old", com.rerun.core.model.ChannelKind.CUSTOM, com.rerun.core.model.Rules()),
            builtIn = false, dormant = false,
        ))
        db.entries().insertAll(listOf(EntryRow.of(
            com.rerun.core.schedule.ScheduleEntry("old", 0, 14400000, com.rerun.core.schedule.EntryKind.PROGRAM, item.key),
        )))
        db.states().put(StateRow("old", "{}"))
        LibrarySync(
            org.mockito.Mockito.mock(PlexApi::class.java), settings,
            ScheduleRepo(db, com.rerun.core.schedule.Scheduler(java.time.ZoneId.of("UTC"))),
        ).signOut()
        val reopened = Settings(RuntimeEnvironment.getApplication(), db.settings())
        assertFalse(reopened.isSignedIn)
        assertNull(reopened.serverToken)
        assertNull(reopened.serverBase())
        assertNull(reopened.serverId())
        assertNull(reopened.selectedLibraries())
        assertNull(reopened.lastChannel())
        assertEquals(0L, reopened.lastSyncMs())
        assertTrue(db.items().all().isEmpty())
        assertTrue(db.channels().all().isEmpty())
        assertTrue(db.entries().rangeAll(0, Long.MAX_VALUE).isEmpty())
        assertNull(db.states().get("old"))
        assertEquals("FIFTIES", reopened.colorTheme().first())
        assertEquals("LARGE", reopened.guideDensity().first())
    }

    @Test
    fun hdrAndAudioPreferencesPersistAcrossSignOut() = runBlocking {
        assertEquals("AUTO", settings.hdrModeValue())
        assertEquals("auto", settings.audioLanguageValue())
        settings.setHdrMode("CONVERT")
        settings.setAudioLanguage("fr")
        settings.signOut()
        val reopened = Settings(RuntimeEnvironment.getApplication(), db.settings())
        assertEquals("CONVERT", reopened.hdrMode().first())
        assertEquals("CONVERT", reopened.hdrModeValue())
        assertEquals("fr", reopened.audioLanguage().first())
        assertEquals("fr", reopened.audioLanguageValue())
    }
}
