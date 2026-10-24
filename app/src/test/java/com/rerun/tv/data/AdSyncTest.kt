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
import java.io.IOException
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class AdSyncTest {
    private lateinit var db: RerunDb
    private lateinit var settings: Settings
    private lateinit var api: PlexApi
    private lateinit var sync: LibrarySync
    private val base = "http://127.0.0.1:32400"
    private val token = "test-placeholder"
    private val path = "/library/sections/ads/all?type=1&includeGuids=1"

    @Before
    fun setUp() = runBlocking<Unit> {
        val context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, RerunDb::class.java).allowMainThreadQueries().build()
        settings = Settings(context, db.settings())
        settings.setServerId("a")
        settings.setServerBase(base)
        settings.accountToken = token
        api = mock(PlexApi::class.java)
        `when`(api.get(base, "/library/sections", token)).thenReturn(
            """{"MediaContainer":{"Directory":[{"key":"ads","type":"movie","title":"Retro Ads"}]}}""",
        )
        sync = LibrarySync(api, settings, ScheduleRepo(db, Scheduler(ZoneId.of("UTC"))))
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun refreshReplacesCatalogAndFiltersUnplayableClipsWithoutImportingRegularItems() = runBlocking {
        `when`(api.get(base, path, token, 0, 200)).thenReturn(
            """{"MediaContainer":{"totalSize":201,"Metadata":[
            {"ratingKey":"first","title":"First","duration":30000,"Media":[{"Part":[{"key":"/library/parts/first/file.mp4"}]}]},
            {"ratingKey":"invalid","title":"Invalid","duration":30000},
            {"ratingKey":"zero","title":"Zero","duration":0,"Media":[{"Part":[{"key":"/library/parts/zero/file.mp4"}]}]}
            ]}}""",
        )
        `when`(api.get(base, path, token, 200, 200)).thenReturn(clip("second", 201))
        sync.syncAds("ads").collect()
        assertEquals(listOf("first", "second"), db.ads().all().map { it.key })
        assertEquals("ads", settings.adLibrary())
        assertTrue(settings.adLastSyncMs() > 0)
        assertTrue(db.items().all().isEmpty())
        assertTrue(db.channels().all().isEmpty())
        `when`(api.get(base, path, token, 0, 200)).thenReturn(clip("replacement"))
        sync.syncAds("ads").collect()
        assertEquals(listOf("replacement"), db.ads().all().map { it.key })
        assertEquals(1, sync.adCount())
    }

    @Test
    fun failedFetchLeavesSourceCatalogAndTimestampUntouched() = runBlocking {
        `when`(api.get(base, path, token, 0, 200)).thenReturn(clip("saved"))
        sync.syncAds("ads").collect()
        val previous = db.ads().all()
        val syncedAt = settings.adLastSyncMs()
        `when`(api.get(base, path, token, 0, 200)).thenReturn(clip("partial", 201))
        `when`(api.get(base, path, token, 200, 200)).thenAnswer { throw IOException("Offline") }
        try {
            sync.syncAds("ads").collect()
            fail("Sync should fail")
        } catch (_: IOException) {
            assertEquals(previous, db.ads().all())
            assertEquals("ads", settings.adLibrary())
            assertEquals(syncedAt, settings.adLastSyncMs())
        }
    }

    @Test
    fun disablingAndChangingServerClearCatalog() = runBlocking {
        `when`(api.get(base, path, token, 0, 200)).thenReturn(clip("saved"))
        sync.syncAds("ads").collect()
        sync.syncAds(null).collect()
        assertTrue(db.ads().all().isEmpty())
        assertNull(settings.adLibrary())
        assertEquals(0L, settings.adLastSyncMs())
        sync.syncAds("ads").collect()
        settings.setServerId("b")
        assertTrue(db.ads().all().isEmpty())
        assertEquals(0L, settings.adLastSyncMs())
        sync.syncAds("ads").collect()
        settings.signOut()
        assertTrue(db.ads().all().isEmpty())
    }

    @Test
    fun versionOneUpgradePreservesExistingSettings() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "ad-migration-test.db"
        try {
            val original = Room.databaseBuilder(context, RerunDb::class.java, name).allowMainThreadQueries().build()
            val preserved = mapOf(
                Settings.SERVER_ID to "server-a",
                Settings.SERVER_BASE to base,
                Settings.SERVER_NAME to "My Plex",
                Settings.SELECTED_LIBRARIES to com.rerun.core.codec.SetCodec.encode(setOf("tv", "movies")),
                Settings.LAST_SYNC to "123456",
                Settings.COLOR_THEME to "SEVENTIES",
            )
            preserved.forEach { (key, value) -> original.settings().put(SettingRow(key, value)) }
            original.close()
            val old = android.database.sqlite.SQLiteDatabase.openDatabase(
                context.getDatabasePath(name).absolutePath, null, android.database.sqlite.SQLiteDatabase.OPEN_READWRITE,
            )
            old.execSQL("DROP TABLE ads")
            old.version = 1
            old.close()
            val upgraded = Room.databaseBuilder(context, RerunDb::class.java, name)
                .addMigrations(RerunDb.MIGRATION_1_2).allowMainThreadQueries().build()
            try {
                preserved.forEach { (key, value) -> assertEquals(value, upgraded.settings().get(key)) }
                assertTrue(upgraded.ads().all().isEmpty())
            } finally {
                upgraded.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
        Unit
    }

    private fun clip(key: String, total: Int = 1) =
        """{"MediaContainer":{"totalSize":$total,"Metadata":[{"ratingKey":"$key","title":"Ad","duration":30000,
        "Media":[{"container":"mp4","Part":[{"key":"/library/parts/$key/file.mp4"}]}]}]}}"""
}
