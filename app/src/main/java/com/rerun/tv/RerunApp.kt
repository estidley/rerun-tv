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
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
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
    private lateinit var lineupRefresh: Deferred<Unit>

    suspend fun awaitLineupRefresh() = lineupRefresh.await()

    override fun onCreate() {
        super.onCreate()
        instance = this
        com.rerun.tv.ui.player.clockContext = this
        db = RerunDb.open(this)
        settings = Settings(this, db.settings())
        // One local read so the first image or playback request already knows the pinned host.
        runBlocking(Dispatchers.IO) { settings.serverBase() }
        http = plexHttpClient(settings)
        plex = PlexApi({ settings.clientId }, http)
        schedule = ScheduleRepo(db, Scheduler(ZoneId.systemDefault()))
        sync = LibrarySync(plex, settings, schedule)
        // An update can rename stock channels and add extras from the library already saved.
        // Music tracks are fetched only after that switch is turned on.
        lineupRefresh = appScope.async {
            if (db.items().count() > 0 || settings.adLibrary() != null) sync.refreshChannelsForAppVersion(appVersion())
        }
    }

    // lastUpdateTime changes on every sideloaded reinstall, even when versionCode does not.
    private fun appVersion(): String = runCatching {
        val info = packageManager.getPackageInfo(packageName, 0)
        "${androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(info)}:${info.lastUpdateTime}"
    }.getOrDefault("unknown")

    override fun newImageLoader(context: Context): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { http })) }
            .build()

    companion object {
        lateinit var instance: RerunApp private set
        private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
