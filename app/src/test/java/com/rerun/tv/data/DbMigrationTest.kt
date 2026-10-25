package com.rerun.tv.data

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import com.rerun.core.codec.ChannelCodec
import com.rerun.core.model.ChannelDef
import com.rerun.core.model.ChannelKind
import com.rerun.core.model.Rules
import com.rerun.core.schedule.EntryKind
import com.rerun.core.schedule.ScheduleEntry
import com.rerun.core.schedule.Scheduler
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class DbMigrationTest {
    @Test
    fun versionTwoUpgradeRetiresDelayedFeedsAndPreservesOriginalChannelsAndSchedules() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "regional-feed-migration-test.db"
        val def = ChannelDef(
            "custom:one", 420, "My Movies E", ChannelKind.CUSTOM,
            Rules(titles = setOf("favorite")), minimumContentHours = 1,
        )
        val other = def.copy(id = "custom:two", number = 421, name = "My Other Movies", hidden = true)
        val entries = listOf(
            ScheduleEntry(def.id, 1_000, 5_000, EntryKind.PROGRAM, "favorite"),
            ScheduleEntry(def.id, 5_000, 6_000, EntryKind.FILLER, null),
        )
        val state = StateRow(def.id, """{"seed":42}""")
        val preservedSettings = mapOf(
            Settings.MUSIC to "true",
            Settings.COLOR_THEME to "SEVENTIES",
            Settings.LAST_SYNC to "123456",
            Settings.SELECTED_LIBRARIES to """["tv","movies"]""",
        )
        try {
            val original = Room.databaseBuilder(context, RerunDb::class.java, name).allowMainThreadQueries().build()
            try {
                original.channels().upsert(ChannelRow.of(def, false, false, 3_600_000))
                original.channels().upsert(ChannelRow.of(other, false, false, 3_600_000))
                original.channels().upsert(ChannelRow.of(
                    def.copy(id = "${def.id}:w", number = 920, name = "My Movies-W"), true, false, 3_600_000,
                ))
                original.entries().insertAll((entries + entries.map { it.copy(channelId = "${def.id}:w") } +
                    entries.map { it.copy(channelId = "orphan:w") }).map(EntryRow::of))
                original.states().put(state)
                original.states().put(StateRow("${def.id}:w", state.json))
                original.states().put(StateRow("orphan:w", state.json))
                original.settings().put(SettingRow("opt_east_west", "true"))
                original.settings().put(SettingRow(Settings.LAST_CHANNEL, "${def.id}:w"))
                preservedSettings.forEach { (key, value) -> original.settings().put(SettingRow(key, value)) }
                original.ads().insertAll(listOf(AdRow("ad", "Ad", 15_000, null, "/ad", null, null, null, null)))
            } finally {
                original.close()
            }
            SQLiteDatabase.openDatabase(
                context.getDatabasePath(name).absolutePath, null, SQLiteDatabase.OPEN_READWRITE,
            ).use { it.version = 2 }
            val upgraded = Room.databaseBuilder(context, RerunDb::class.java, name)
                .addMigrations(RerunDb.MIGRATION_2_3).allowMainThreadQueries().build()
            try {
                val expected = def.copy(name = "My Movies")
                assertEquals(ChannelRow.of(expected, false, false, 3_600_000), upgraded.channels().byId(def.id))
                assertEquals(other, upgraded.channels().byId(other.id)?.def)
                assertEquals(setOf(def.id, other.id), upgraded.channels().all().map { it.id }.toSet())
                assertEquals(entries, upgraded.entries().range(def.id, 0, Long.MAX_VALUE).map { it.toEntry() })
                assertEquals(entries.size, upgraded.entries().rangeAll(0, Long.MAX_VALUE).size)
                assertEquals(state, upgraded.states().get(def.id))
                assertNull(upgraded.states().get("${def.id}:w"))
                assertNull(upgraded.states().get("orphan:w"))
                assertNull(upgraded.settings().get("opt_east_west"))
                assertEquals(def.id, upgraded.settings().get(Settings.LAST_CHANNEL))
                preservedSettings.forEach { (key, value) -> assertEquals(value, upgraded.settings().get(key)) }
                assertEquals(listOf("ad"), upgraded.ads().all().map { it.key })
                val repo = ScheduleRepo(upgraded, Scheduler(ZoneId.of("UTC")))
                repo.ensure("${def.id}:w", 1_000)
                assertTrue(upgraded.entries().range("${def.id}:w", 0, Long.MAX_VALUE).isEmpty())
                assertEquals(ChannelCodec.encode(expected), upgraded.channels().byId(def.id)?.defJson)
            } finally {
                upgraded.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }
}
