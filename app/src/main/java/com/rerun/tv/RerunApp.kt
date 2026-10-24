package com.rerun.tv

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.rerun.core.schedule.Scheduler
import com.rerun.tv.data.LibrarySync
import com.rerun.tv.data.PlexApi
import com.rerun.tv.data.RerunDb
import com.rerun.tv.data.ScheduleRepo
import com.rerun.tv.data.Settings
import com.rerun.tv.data.plexHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.time.ZoneId

/** Hand-wired singletons. Small app, no DI framework. */
class RerunApp : Application(), SingletonImageLoader.Factory {
    lateinit var db: RerunDb private set
    lateinit var settings: Settings private set
    lateinit var http: OkHttpClient private set
    lateinit var plex: PlexApi private set
    lateinit var schedule: ScheduleRepo private set
    lateinit var sync: LibrarySync private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        db = RerunDb.open(this)
        settings = Settings(this, db.settings())
        // One local read so the first image or playback request already knows the pinned host.
        runBlocking(Dispatchers.IO) { settings.serverBase() }
        http = plexHttpClient(settings)
        plex = PlexApi({ settings.clientId }, http)
        schedule = ScheduleRepo(db, Scheduler(ZoneId.systemDefault()))
        sync = LibrarySync(plex, settings, schedule)
        // Only run a full channel lineup rebuild on startup if items exist but channels haven't been built yet.
        // Avoids blocking the schedule mutex for 20+ seconds on large libraries during cold launch.
        appScope.launch {
            if (db.items().count() > 0 && db.channels().active().isEmpty()) {
                sync.refreshChannels()
            }
        }
    }

    override fun newImageLoader(context: Context): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { http })) }
            .build()

    companion object {
        lateinit var instance: RerunApp private set
        private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
