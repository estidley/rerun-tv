package com.rerun.tv.ui

import com.rerun.core.catalog.Lineup
import com.rerun.core.model.MediaItem
import com.rerun.core.model.MediaKind
import com.rerun.core.plex.PlexParse
import com.rerun.core.plex.ServerUri
import com.rerun.core.schedule.EntryKind
import com.rerun.tv.RerunApp
import com.rerun.tv.data.ChannelRow
import com.rerun.tv.player.TunerEngine
import com.rerun.tv.ui.player.NoiseBurst
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URLEncoder

data class Effects(
    val static: Boolean = true,
    val sound: Boolean = true,
    val roll: Boolean = true,
    val power: Boolean = true,
) {
    companion object {
        const val STATIC = "fx_static"
        const val SOUND = "fx_sound"
        const val ROLL = "fx_roll"
        const val POWER = "fx_power"
        val KEYS = listOf(STATIC, SOUND, ROLL, POWER)
    }
}

data class LineupUpdate(
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val music: Boolean? = null,
    val lastSyncMs: Long? = null,
)

data class StartupState(
    val loading: Boolean = false,
    val empty: Boolean = false,
    val error: String? = null,
)

sealed interface Screen {
    data object Onboarding : Screen
    data object Player : Screen
    data object Guide : Screen
    data object Channels : Screen
    data class Editor(val channelId: String?) : Screen
    data object Settings : Screen
    data object Libraries : Screen
    data object AdLibrary : Screen
    data object SubtitleLanguage : Screen
    data object AudioLanguage : Screen
    data object AudioTracks : Screen
}

/** App-level state and remote-control actions. One per activity. */
private const val SUMMARY_CAPACITY = 64
private const val SUMMARY_TIMEOUT_MS = 5_000L

class TvController(private val app: RerunApp, val engine: TunerEngine) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val channels: StateFlow<List<ChannelRow>> =
        app.schedule.activeChannels().map { rows ->
            rows.sortedWith(compareBy<ChannelRow> { it.number }.thenBy { it.id })
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val _screen = MutableStateFlow<Screen>(if (app.settings.isSignedIn) Screen.Player else Screen.Onboarding)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    /** Epoch ms until which the info banner shows (0 = hidden). */
    private val _bannerUntil = MutableStateFlow(0L)
    val bannerUntil: StateFlow<Long> = _bannerUntil.asStateFlow()

    val crtLevel: StateFlow<String> = app.settings.crtLevel().stateIn(scope, SharingStarted.Eagerly, "SUBTLE")
    val colorTheme: StateFlow<String> = app.settings.colorTheme().stateIn(scope, SharingStarted.Eagerly, "STANDARD_BLUE")
    val guideDensity: StateFlow<String> = app.settings.guideDensity().stateIn(scope, SharingStarted.Eagerly, "DEFAULT")
    val subtitleLanguage: StateFlow<String> = app.settings.subtitleLanguage().stateIn(scope, SharingStarted.Eagerly, "auto")
    val subtitleSize: StateFlow<String> = app.settings.subtitleSize().stateIn(scope, SharingStarted.Eagerly, "MEDIUM")
    val subtitleColor: StateFlow<String> = app.settings.subtitleColor().stateIn(scope, SharingStarted.Eagerly, "WHITE")
    val audioLanguage: StateFlow<String> = app.settings.audioLanguage().stateIn(scope, SharingStarted.Eagerly, "auto")
    val hdrMode: StateFlow<String> = app.settings.hdrMode().stateIn(scope, SharingStarted.Eagerly, "AUTO")

    private val _lineupUpdate = MutableStateFlow(LineupUpdate())
    val lineupUpdate = _lineupUpdate.asStateFlow()
    private var lineupJob: Job? = null
    private var signingOut = false
    private val _startup = MutableStateFlow(StartupState(loading = app.settings.isSignedIn))
    val startup = _startup.asStateFlow()
    private var startupJob: Job? = null
    private var foreground = true

    fun setMusicEnabled(enabled: Boolean) {
        updateLineup("Updating music...", "Sync failed", music = enabled) {
            if (enabled) {
                val included = app.settings.selectedLibraries()
                require(app.sync.libraries().any { it.type == "artist" && (included == null || it.key in included) }) {
                    "Include a Plex music library in Manage libraries first."
                }
            }
            changeLineupFlag(com.rerun.tv.data.Settings.MUSIC, enabled) {
                if (enabled) syncLibrary() else {
                    app.sync.refreshChannels()
                    _lineupUpdate.value = _lineupUpdate.value.copy(message = "Music channels off")
                }
            }
            reconcileChannels()
        }
    }

    fun resyncLibrary() {
        updateLineup("Reading libraries", "Sync failed") {
            syncLibrary()
            reconcileChannels()
        }
    }

    fun saveLibraries(selected: Set<String>) {
        val selection = selected.toSet()
        updateLineup("Reading libraries", "Sync failed") {
            syncLibrary(selection)
            val previous = currentId
            reconcileChannels()
            if (previous != null && currentId == previous && foreground) engine.tune(previous)
        }
    }

    fun setAdLibrary(source: String?) {
        updateLineup("Syncing ads...", "Ad sync failed") {
            app.sync.syncAds(source).collect { p ->
                _lineupUpdate.value = _lineupUpdate.value.copy(
                    message = if (p.finished) p.stage else "${p.stage} ${p.done}/${p.total}",
                )
            }
            val previous = currentId
            reconcileChannels()
            if (previous == Lineup.ADS_ID && currentId == previous && foreground) engine.tune(previous)
        }
    }

    /** Navigation must not cancel a library update or roll back the user's lineup switch. */
    private fun updateLineup(
        message: String, failure: String, music: Boolean? = null,
        operation: suspend () -> Unit,
    ) {
        if (_lineupUpdate.value.busy || signingOut) return
        _lineupUpdate.value = LineupUpdate(
            true, message, music = music, lastSyncMs = _lineupUpdate.value.lastSyncMs,
        )
        lineupJob = scope.launch {
            try {
                operation()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _lineupUpdate.value = _lineupUpdate.value.copy(message = null, error = "$failure: ${e.message}")
            } finally {
                _lineupUpdate.value = _lineupUpdate.value.copy(busy = false, music = null)
            }
        }
    }

    private suspend fun changeLineupFlag(key: String, enabled: Boolean, operation: suspend () -> Unit) {
        val previous = app.settings.isOn(key)
        val cleanVersion = app.settings.lineupAppVersion()
        try {
            // Persist the dirty marker first so an interrupted rebuild is repaired on the next launch.
            app.settings.invalidateLineupAppVersion()
            app.settings.setFlag(key, enabled)
            operation()
            // Only a completed rebuild restores the marker; failure or cancellation leaves it invalidated.
            cleanVersion?.let { app.settings.setLineupAppVersion(it) }
        } catch (e: Exception) {
            withContext(NonCancellable) { app.settings.setFlag(key, previous) }
            throw e
        }
    }

    private suspend fun syncLibrary(selected: Set<String>? = null) {
        app.sync.sync(selected).collect { p ->
            _lineupUpdate.value = _lineupUpdate.value.copy(
                message = if (p.finished) "Library synced: ${p.total} items" else "${p.stage} ${p.done}/${p.total}",
            )
        }
        _lineupUpdate.value = _lineupUpdate.value.copy(lastSyncMs = app.settings.lastSyncMs())
    }

    suspend fun setAudioLanguage(value: String) {
        app.settings.setAudioLanguage(value)
        if (foreground) engine.refreshPlaybackPreferences()
    }

    suspend fun setNormalizeLoudness(enabled: Boolean) {
        app.settings.setFlag(com.rerun.tv.data.Settings.NORMALIZE_LOUDNESS, enabled)
        if (foreground) engine.refreshPlaybackPreferences()
    }

    suspend fun setHdrMode(value: com.rerun.core.plex.HdrMode) {
        app.settings.setHdrMode(value.name)
        if (foreground) engine.refreshPlaybackPreferences()
    }

    fun setGuideDensity(value: com.rerun.tv.ui.guide.GuideDensity) {
        scope.launch { app.settings.setGuideDensity(value.name) }
    }

    suspend fun setSubtitleLanguage(value: String) {
        app.settings.setSubtitleLanguage(value)
        if (foreground) engine.setSubtitles(app.settings.isOn(com.rerun.tv.data.Settings.SUBTITLES))
    }

    fun setColorTheme(theme: com.rerun.tv.ui.theme.ColorTheme) {
        scope.launch { app.settings.setColorTheme(theme.name) }
    }

    fun cycleCrt() {
        scope.launch { app.settings.setCrtLevel(com.rerun.tv.ui.crt.CrtLevel.from(crtLevel.value).next().name) }
    }

    fun setCrt(level: com.rerun.tv.ui.crt.CrtLevel) {
        scope.launch { app.settings.setCrtLevel(level.name) }
    }

    /** Each retro effect can be switched off on its own (Settings → Effects). */
    val effects: StateFlow<Effects> = kotlinx.coroutines.flow.combine(
        app.settings.flag(Effects.STATIC), app.settings.flag(Effects.SOUND),
        app.settings.flag(Effects.ROLL), app.settings.flag(Effects.POWER),
    ) { st, so, ro, po -> Effects(st, so, ro, po) }.stateIn(scope, SharingStarted.Eagerly, Effects())

    fun setEffect(key: String, on: Boolean) { scope.launch { app.settings.setFlag(key, on) } }

    /** Everything off (CRT included), or everything back on with CRT at Subtle. */
    fun setAllEffects(on: Boolean) {
        scope.launch {
            Effects.KEYS.forEach { app.settings.setFlag(it, on) }
            app.settings.setCrtLevel(if (on) "SUBTLE" else "OFF")
        }
    }

    /** Set to true when the user asks to leave the app; the activity plays power-off and finishes. */
    private val _exiting = MutableStateFlow(false)
    val exiting: StateFlow<Boolean> = _exiting.asStateFlow()

    private var currentId: String? = null
    private var previousId: String? = null
    private var channelBeforeRestart: String? = null
    private var guideClosedAt = 0L
    private var connectionGeneration = 0L

    @Volatile var serverBase: String? = null
        private set
    private val token: String? get() = app.settings.serverToken ?: app.settings.accountToken

    init {
        engine.setVisible(_screen.value == Screen.Player)
        engine.onReturned = { id ->
            currentId = id
            previousId = channelBeforeRestart
            channelBeforeRestart = null
            showBanner()
        }
        scope.launch { serverBase = app.settings.serverBase() }
    }

    fun go(screen: Screen) {
        engine.setVisible(foreground && screen == Screen.Player)
        _screen.value = if (screen is Screen.Editor && screen.channelId == Lineup.ADS_ID) {
            Screen.AdLibrary
        } else screen
    }

    suspend fun signOut() {
        signingOut = true
        try {
            connectionGeneration++
            startupJob?.cancelAndJoin()
            lineupJob?.cancelAndJoin()
            _startup.value = StartupState()
            _lineupUpdate.value = LineupUpdate()
            engine.clear()
            app.sync.signOut()
            currentId = null
            previousId = null
            channelBeforeRestart = null
            serverBase = null
            synchronized(summaries) { summaries.clear() }
            go(Screen.Onboarding)
        } finally {
            signingOut = false
        }
    }

    /** Back from the guide: return to the picture, and arm "Back again exits". */
    fun closeGuideWithBack() {
        guideClosedAt = System.currentTimeMillis()
        go(Screen.Player)
    }

    /** Start watching: last channel if still active, else the lowest-numbered one. */
    fun startWatching() {
        if (signingOut) return
        startupJob?.cancel()
        _startup.value = StartupState(loading = true)
        val generation = connectionGeneration
        startupJob = scope.launch {
            try {
                val base = app.settings.serverBase()
                val lastSync = app.settings.lastSyncMs()
                if (generation != connectionGeneration) return@launch
                serverBase = base
                if (serverBase == null || lastSync == 0L) {
                    engine.clear()
                    _startup.value = StartupState()
                    go(Screen.Onboarding)
                    return@launch
                }
                app.awaitLineupRefresh()
                app.schedule.invalidateLibrary()
                val list = app.schedule.active()
                val last = app.settings.lastChannel()
                if (generation != connectionGeneration) return@launch
                val pick = list.firstOrNull { it.id == last } ?: list.firstOrNull()
                _startup.value = StartupState(empty = list.isEmpty())
                if (screen.value == Screen.Onboarding) go(Screen.Player)
                currentId = currentId?.takeIf { id -> list.any { it.id == id } }
                previousId = previousId?.takeIf { id -> list.any { it.id == id } }
                if (pick != null) {
                    if (foreground) tuneTo(pick.id, sound = false) else currentId = pick.id
                } else {
                    currentId = null
                    engine.clear()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation == connectionGeneration) {
                    _startup.value = StartupState(error = "Couldn't load channels. Open Settings to retry the library sync.")
                    android.util.Log.e("TvController", "Unable to load startup lineup (${e.javaClass.simpleName})")
                }
            }
        }
    }

    fun resume() {
        foreground = true
        engine.setVisible(screen.value == Screen.Player)
        if (!app.settings.isSignedIn) {
            engine.clear()
            go(Screen.Onboarding)
        } else currentId?.let { engine.tune(it) } ?: startWatching()
    }

    fun stop() {
        foreground = false
        engine.setVisible(false)
        engine.stop()
    }

    /** Keep menus open while replacing a channel that a lineup change removed. */
    suspend fun reconcileChannels() {
        val generation = connectionGeneration
        val list = app.schedule.active()
        if (generation != connectionGeneration) return
        val ids = list.map { it.id }.toSet()
        _startup.value = _startup.value.copy(empty = list.isEmpty(), error = null)
        previousId = previousId?.takeIf { it in ids }
        channelBeforeRestart = channelBeforeRestart?.takeIf { it in ids }
        val current = currentId
        if (current in ids || (current == Lineup.RESTART_ID && app.schedule.channel(current) != null)) return
        if (generation != connectionGeneration) return
        val replacement = list.firstOrNull()
        currentId = replacement?.id
        if (replacement == null) engine.clear() else if (foreground) engine.tune(replacement.id)
    }

    fun tuneTo(id: String, sound: Boolean = true) {
        if (currentId == Lineup.RESTART_ID && id != Lineup.RESTART_ID) {
            previousId = previousId?.takeUnless { it == id } ?: channelBeforeRestart
            channelBeforeRestart = null
        } else if (id != currentId) previousId = currentId
        currentId = id
        if (foreground) {
            if (sound && effects.value.sound) NoiseBurst.play()
            engine.tune(id)
        }
        showBanner()
    }

    fun channelUp() = step(+1)
    fun channelDown() = step(-1)

    private fun step(dir: Int) {
        val list = channels.value
        if (list.isEmpty()) return
        val anchor = effectiveChannelId()
        val i = list.indexOfFirst { it.id == anchor }
        val next = if (i < 0) list.first() else list[Math.floorMod(i + dir, list.size)]
        tuneTo(next.id)
    }

    /** The channel the viewer is on; the hidden start-over channel counts as the one it came from. */
    fun effectiveChannelId(): String? =
        if (currentId == Lineup.RESTART_ID) previousId ?: currentId else currentId

    fun lastChannel() { previousId?.let { tuneTo(it) } }

    /**
     * Hold Left while an episode is on. The episode starts over on a hidden channel
     * so the one you were watching keeps its schedule. When it ends, the TV comes back.
     */
    fun startEpisodeOver() {
        scope.launch {
            val returnTo = currentId ?: return@launch
            if (returnTo == Lineup.RESTART_ID) return@launch
            val snap = engine.state.value
            val item = snap.item
            val restartable = returnTo != Lineup.ADS_ID &&
                item != null && (item.kind == MediaKind.EPISODE || item.kind == MediaKind.MOVIE) &&
                snap.located?.entry?.kind == EntryKind.PROGRAM && snap.channel?.id == returnTo
            if (!restartable || item == null) {
                engine.notify("Start over works while a show or movie is playing.")
                return@launch
            }
            try {
                app.schedule.airFromStart(item, System.currentTimeMillis())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                engine.notify("Couldn't start over: ${e.message}")
                return@launch
            }
            if (!foreground || currentId != returnTo || engine.state.value.tuneId != snap.tuneId) return@launch
            channelBeforeRestart = previousId
            engine.armReturn(returnTo)
            tuneTo(Lineup.RESTART_ID)
        }
    }

    fun showBanner(ms: Long = 5_000) { _bannerUntil.value = System.currentTimeMillis() + ms }
    fun hideBanner() { _bannerUntil.value = 0 }

    /** Back on the player opens the guide; Back again right after closing the guide exits. */
    fun backFromPlayer() {
        if (System.currentTimeMillis() - guideClosedAt < 1_500) _exiting.value = true else go(Screen.Guide)
    }

    fun posterUrl(item: MediaItem?): String? {
        val thumb = item?.thumb ?: return null
        if (!ServerUri.isLibraryPath(thumb) && !ServerUri.isAcceptable(thumb)) return null
        val base = serverBase ?: return null
        if (!ServerUri.isAcceptable(base) || token == null) return null
        val enc = { s: String -> URLEncoder.encode(s, "UTF-8") }
        val height = if (item.kind == MediaKind.TRACK) 480 else 720
        return "${base.trimEnd('/')}/photo/:/transcode?width=480&height=$height&minSize=1&upscale=1&url=${enc(thumb)}"
    }

    fun currentChannelId(): String? = currentId

    private val summaries = object : LinkedHashMap<String, String>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > SUMMARY_CAPACITY
    }

    /** Program description from Plex, fetched on demand for the focused guide cell. Never persisted; failures are null. */
    suspend fun programSummary(item: MediaItem?): String? {
        if (item == null || item.kind == MediaKind.TRACK) return null
        val base = serverBase ?: return null
        val token = token ?: return null
        val path = "/library/metadata/${item.key}"
        if (!ServerUri.isLibraryPath(path) || !ServerUri.isAcceptable(base)) return null
        val revision = app.schedule.guideRevision.value
        val cacheKey = "$base|$token|$revision|${item.key}"
        synchronized(summaries) { summaries[cacheKey] }?.let { return it }
        val text = try {
            withContext(Dispatchers.IO) { fetchSummary(base, path, token) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } ?: return null
        // A sync or edit during the fetch invalidates the revision; don't cache the stale response.
        if (app.schedule.guideRevision.value == revision) synchronized(summaries) { summaries[cacheKey] = text }
        return text
    }

    // withTimeoutOrNull, not withTimeout: its cancellation exception would be rethrown by the caller's catch.
    private suspend fun fetchSummary(base: String, path: String, token: String): String? {
        repeat(2) {
            val body = try {
                withTimeoutOrNull(SUMMARY_TIMEOUT_MS) { app.plex.getWithTimeout(base, path, token, SUMMARY_TIMEOUT_MS) }
            } catch (_: java.io.InterruptedIOException) {
                null
            }
            if (body != null) return PlexParse.summary(body)
            android.util.Log.w("TvController", "Program description request timed out")
        }
        return null
    }

    fun dispose() {
        scope.cancel()
    }
}
