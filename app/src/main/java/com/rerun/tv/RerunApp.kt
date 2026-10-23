package com.rerun.tv

import android.app.Application
import com.rerun.core.schedule.Scheduler
import com.rerun.tv.data.LibrarySync
import com.rerun.tv.data.PlexApi
import com.rerun.tv.data.RerunDb
import com.rerun.tv.data.ScheduleRepo
import com.rerun.tv.data.Settings
import okhttp3.OkHttpClient
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** Hand-wired singletons. Small app, no DI framework. */
class RerunApp : Application() {
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
        http = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
        plex = PlexApi({ settings.clientId }, http)
        schedule = ScheduleRepo(db, Scheduler(ZoneId.systemDefault()))
        sync = LibrarySync(plex, db, settings) { schedule.invalidateLibrary() }
    }

    companion object {
        lateinit var instance: RerunApp private set
    }
}
