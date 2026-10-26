package com.rerun.tv.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.rerun.core.codec.ChannelCodec
import com.rerun.core.codec.SetCodec
import com.rerun.core.model.ChannelDef
import com.rerun.core.model.MediaItem
import com.rerun.core.model.MediaKind
import com.rerun.core.schedule.EntryKind
import com.rerun.core.schedule.ScheduleEntry
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "items")
data class ItemRow(
    @PrimaryKey val key: String,
    val kind: String,
    val title: String,
    val year: Int?,
    val durationMs: Long,
    val genres: String,
    val network: String?,
    val studio: String?,
    val contentRating: String?,
    val showKey: String?,
    val showTitle: String?,
    val season: Int?,
    val episode: Int?,
    val tmdbId: String?,
    val labels: String,
    val collections: String,
    val partKey: String?,
    val container: String?,
    val videoCodec: String?,
    val audioCodec: String?,
    val thumb: String?,
) {
    fun toItem() = MediaItem(
        key, MediaKind.valueOf(kind), title, year, durationMs, SetCodec.decode(genres), network, studio,
        contentRating, showKey, showTitle, season, episode, tmdbId, SetCodec.decode(labels),
        SetCodec.decode(collections), partKey, container, videoCodec, audioCodec, thumb,
    )

    companion object {
        fun of(i: MediaItem) = ItemRow(
            i.key, i.kind.name, i.title, i.year, i.durationMs, SetCodec.encode(i.genres), i.network, i.studio,
            i.contentRating, i.showKey, i.showTitle, i.season, i.episode, i.tmdbId, SetCodec.encode(i.labels),
            SetCodec.encode(i.collections), i.partKey, i.container, i.videoCodec, i.audioCodec, i.thumb,
        )
    }
}

@Entity(tableName = "ads")
data class AdRow(
    @PrimaryKey val key: String,
    val title: String,
    val durationMs: Long,
    val year: Int?,
    val partKey: String,
    val container: String?,
    val videoCodec: String?,
    val audioCodec: String?,
    val thumb: String?,
) {
    fun toItem() = MediaItem(
        key = key, kind = MediaKind.MOVIE, title = title, year = year, durationMs = durationMs,
        genres = emptySet(), network = null, studio = null, contentRating = null,
        showKey = null, showTitle = null, season = null, episode = null, tmdbId = null,
        labels = emptySet(), collections = emptySet(), partKey = partKey, container = container,
        videoCodec = videoCodec, audioCodec = audioCodec, thumb = thumb,
    )

    companion object {
        fun of(item: MediaItem) = AdRow(
            item.key, item.title, item.durationMs, item.year, requireNotNull(item.partKey),
            item.container, item.videoCodec, item.audioCodec, item.thumb,
        )
    }
}

@Dao
interface AdDao {
    @Query("SELECT * FROM ads WHERE `key` = :key")
    suspend fun byKey(key: String): AdRow?

    @Query("SELECT * FROM ads ORDER BY `key`")
    suspend fun all(): List<AdRow>

    @Query("DELETE FROM ads")
    suspend fun clear()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<AdRow>)
}

@Entity(tableName = "channels")
data class ChannelRow(
    @PrimaryKey val id: String,
    val number: Int,
    val name: String,
    val defJson: String,
    val hidden: Boolean,
    val dormant: Boolean,
    val builtIn: Boolean,
    val poolMs: Long = 0,
) {
    val def: ChannelDef get() = ChannelCodec.decode(defJson)

    companion object {
        fun of(def: ChannelDef, builtIn: Boolean, dormant: Boolean = true, poolMs: Long = 0) =
            ChannelRow(def.id, def.number, def.name, ChannelCodec.encode(def), def.hidden, dormant, builtIn, poolMs)
    }
}

@Entity(tableName = "entries", indices = [Index(value = ["channelId", "startMs"], unique = true)])
data class EntryRow(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val channelId: String,
    val startMs: Long,
    val endMs: Long,
    val kind: String,
    val itemKey: String?,
) {
    fun toEntry() = ScheduleEntry(channelId, startMs, endMs, EntryKind.valueOf(kind), itemKey)

    companion object {
        fun of(e: ScheduleEntry) = EntryRow(channelId = e.channelId, startMs = e.startMs, endMs = e.endMs, kind = e.kind.name, itemKey = e.itemKey)
    }
}

@Entity(tableName = "states")
data class StateRow(@PrimaryKey val channelId: String, val json: String)

@Entity(tableName = "settings")
data class SettingRow(@PrimaryKey val key: String, val value: String)

@Dao
abstract class ItemDao {
    @Query("SELECT * FROM items")
    abstract suspend fun all(): List<ItemRow>

    @Query("SELECT * FROM items WHERE `key` = :key")
    abstract suspend fun byKey(key: String): ItemRow?

    @Query("SELECT COUNT(*) FROM items")
    abstract suspend fun count(): Int

    @Query("DELETE FROM items")
    abstract suspend fun clear()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(rows: List<ItemRow>)

    @Transaction
    open suspend fun replaceAll(rows: List<ItemRow>) {
        clear()
        rows.chunked(500).forEach { insertAll(it) }
    }
}

@Dao
interface ChannelDao {
    @Query("SELECT * FROM channels ORDER BY number")
    suspend fun all(): List<ChannelRow>

    @Query("SELECT * FROM channels ORDER BY number")
    fun observeAll(): Flow<List<ChannelRow>>

    @Query("SELECT * FROM channels WHERE hidden = 0 AND dormant = 0 ORDER BY number")
    fun observeActive(): Flow<List<ChannelRow>>

    @Query("SELECT * FROM channels WHERE hidden = 0 AND dormant = 0 ORDER BY number")
    suspend fun active(): List<ChannelRow>

    @Query("SELECT * FROM channels WHERE id = :id")
    suspend fun byId(id: String): ChannelRow?

    @Query("SELECT * FROM channels WHERE id IN (:ids)")
    suspend fun byIds(ids: List<String>): List<ChannelRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: ChannelRow)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<ChannelRow>)

    @Query("DELETE FROM channels WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface EntryDao {
    @Query("SELECT DISTINCT channelId FROM entries WHERE itemKey IS NOT NULL AND itemKey NOT IN (SELECT `key` FROM items)")
    suspend fun channelsWithMissingItems(): List<String>

    @Query("SELECT MIN(startMs) FROM entries WHERE channelId = :channelId AND startMs >= :fromMs AND itemKey IS NOT NULL AND itemKey NOT IN (SELECT `key` FROM items)")
    suspend fun firstMissingStart(channelId: String, fromMs: Long): Long?

    @Query("DELETE FROM entries")
    suspend fun clear()

    @Query("SELECT MAX(endMs) FROM entries WHERE channelId = :channelId")
    suspend fun lastEnd(channelId: String): Long?

    @Query("SELECT * FROM entries WHERE channelId = :channelId AND endMs > :fromMs AND startMs < :toMs ORDER BY startMs")
    suspend fun range(channelId: String, fromMs: Long, toMs: Long): List<EntryRow>

    @Query("SELECT * FROM entries WHERE endMs > :fromMs AND startMs < :toMs ORDER BY channelId, startMs")
    suspend fun rangeAll(fromMs: Long, toMs: Long): List<EntryRow>

    @Query("SELECT * FROM entries WHERE channelId IN (:channelIds) AND endMs > :fromMs AND startMs < :toMs ORDER BY channelId, startMs")
    suspend fun rangeFor(channelIds: List<String>, fromMs: Long, toMs: Long): List<EntryRow>

    @Query(
        "SELECT p.* FROM entries p INNER JOIN entries f ON p.channelId = f.channelId AND p.endMs = f.startMs " +
            "WHERE p.channelId IN (:channelIds) AND p.kind = 'PROGRAM' AND f.kind = 'FILLER' " +
            "AND f.startMs <= :fromMs AND f.endMs > :fromMs",
    )
    suspend fun programsBeforeBreakFor(channelIds: List<String>, fromMs: Long): List<EntryRow>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(rows: List<EntryRow>)

    @Query("DELETE FROM entries WHERE endMs < :beforeMs")
    suspend fun pruneBefore(beforeMs: Long)

    @Query("DELETE FROM entries WHERE channelId = :channelId AND startMs >= :fromMs")
    suspend fun deleteFrom(channelId: String, fromMs: Long)

    @Query("DELETE FROM entries WHERE channelId = :channelId")
    suspend fun deleteChannel(channelId: String)
}

@Dao
interface StateDao {
    @Query("DELETE FROM states")
    suspend fun clear()

    @Query("SELECT * FROM states WHERE channelId = :channelId")
    suspend fun get(channelId: String): StateRow?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(row: StateRow)

    @Query("DELETE FROM states WHERE channelId = :channelId")
    suspend fun delete(channelId: String)
}

@Dao
interface SettingDao {
    @Query("DELETE FROM entries")
    suspend fun clearEntries()

    @Query("DELETE FROM states")
    suspend fun clearStates()

    @Query("DELETE FROM channels")
    suspend fun clearChannels()

    @Query("DELETE FROM items")
    suspend fun clearItems()

    @Transaction
    suspend fun clearConnection() {
        clearEntries()
        clearStates()
        clearChannels()
        clearItems()
        clearAds()
        listOf(
            Settings.SERVER_BASE, Settings.SERVER_NAME, Settings.SERVER_ID, Settings.LAST_SYNC,
            Settings.LAST_CHANNEL, Settings.SELECTED_LIBRARIES, Settings.AD_LIBRARY, Settings.AD_LAST_SYNC,
        ).forEach { remove(it) }
    }

    @Query("DELETE FROM ads")
    suspend fun clearAds()

    @Query("SELECT value FROM settings WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Query("SELECT value FROM settings WHERE `key` = :key")
    fun observe(key: String): Flow<String?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(row: SettingRow)

    @Query("DELETE FROM settings WHERE `key` = :key")
    suspend fun remove(key: String)
}

@Database(
    entities = [ItemRow::class, AdRow::class, ChannelRow::class, EntryRow::class, StateRow::class, SettingRow::class],
    version = 3,
    exportSchema = false,
)
abstract class RerunDb : RoomDatabase() {
    abstract fun items(): ItemDao
    abstract fun channels(): ChannelDao
    abstract fun entries(): EntryDao
    abstract fun states(): StateDao
    abstract fun settings(): SettingDao
    abstract fun ads(): AdDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS ads (`key` TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, " +
                        "durationMs INTEGER NOT NULL, year INTEGER, partKey TEXT NOT NULL, container TEXT, " +
                        "videoCodec TEXT, audioCodec TEXT, thumb TEXT)",
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Retire delayed feeds without touching the original channels or locked schedules.
                db.execSQL("DELETE FROM entries WHERE channelId LIKE '%:w'")
                db.execSQL("DELETE FROM states WHERE channelId LIKE '%:w'")
                db.execSQL("DELETE FROM channels WHERE id LIKE '%:w'")
                db.execSQL("DELETE FROM settings WHERE `key` = 'opt_east_west'")
                db.execSQL(
                    "UPDATE settings SET value = substr(value, 1, length(value) - 2) " +
                        "WHERE `key` = 'last_channel' AND value LIKE '%:w'",
                )
                db.query("SELECT id, defJson FROM channels").use { rows ->
                    while (rows.moveToNext()) {
                        val def = ChannelCodec.decode(rows.getString(1))
                        val name = when {
                            def.name.endsWith("-W") || def.name.endsWith(" E") || def.name.endsWith(" W") ->
                                def.name.dropLast(2)
                            else -> continue
                        }
                        db.execSQL(
                            "UPDATE channels SET name = ?, defJson = ? WHERE id = ?",
                            arrayOf(name, ChannelCodec.encode(def.copy(name = name)), rows.getString(0)),
                        )
                    }
                }
            }
        }

        fun open(context: Context): RerunDb =
            Room.databaseBuilder(context, RerunDb::class.java, "rerun.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
