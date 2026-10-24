package com.rerun.tv.ui

import com.rerun.core.catalog.Lineup
import com.rerun.core.model.MediaItem
import com.rerun.core.model.MediaKind
import com.rerun.core.plex.ServerUri
import com.rerun.core.schedule.EntryKind
import com.rerun.tv.RerunApp
import com.rerun.tv.data.ChannelRow
import com.rerun.tv.player.TunerEngine
import com.rerun.tv.ui.player.NoiseBurst
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

sealed interface Screen {
    data object Onboarding : Screen
    data object Player : Screen
    data object Guide : Screen
    data object Channels : Screen
    data class Editor(val channelId: String?) : Screen
    data object Settings : Screen
}

/** App-level state and remote-control actions. One per activity. */
class TvController(private val app: RerunApp, val engine: TunerEngine) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val channels: StateFlow<List<ChannelRow>> =
        app.schedule.activeChannels().stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val _screen = MutableStateFlow<Screen>(if (app.settings.isSignedIn) Screen.Player else Screen.Onboarding)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    /** Epoch ms until which the info banner shows (0 = hidden). */
    private val _bannerUntil = MutableStateFlow(0L)
    val bannerUntil: StateFlow<Long> = _bannerUntil.asStateFlow()

    val crtLevel: StateFlow<String> = app.settings.crtLevel().stateIn(scope, SharingStarted.Eagerly, "SUBTLE")

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

    @Volatile var serverBase: String? = null
        private set
    private val token: String? get() = app.settings.serverToken ?: app.settings.accountToken

    init {
        engine.onReturned = { id ->
            currentId = id
            previousId = channelBeforeRestart
            channelBeforeRestart = null
            showBanner()
        }
        scope.launch { serverBase = app.settings.serverBase() }
    }

    fun go(screen: Screen) {
        _screen.value = if (screen is Screen.Editor && screen.channelId != null) {
            Screen.Editor(Lineup.eastOf(screen.channelId))
        } else screen
    }

    /** Back from the guide: return to the picture, and arm "Back again exits". */
    fun closeGuideWithBack() {
        guideClosedAt = System.currentTimeMillis()
        _screen.value = Screen.Player
    }

    /** Start watching: last channel if still active, else the lowest-numbered one. */
    fun startWatching(randomize: Boolean = true) {
        scope.launch {
            serverBase = app.settings.serverBase()
            if (serverBase == null) { _screen.value = Screen.Onboarding; return@launch }
            withContext(Dispatchers.IO) {
                app.schedule.invalidateLibrary()
                if (randomize) app.schedule.resetForStartup()
            }
            val list = withContext(Dispatchers.IO) { app.schedule.active() }
            val last = effectiveChannelId() ?: app.settings.lastChannel()
            val pick = list.firstOrNull { it.id == last } ?: list.firstOrNull()
            _screen.value = Screen.Player
            if (pick != null) {
                tuneTo(pick.id, sound = false)
                // Warm up the remaining channels in the background so the Guide is ready immediately.
                launch(Dispatchers.IO) {
                    app.schedule.ensure(System.currentTimeMillis())
                }
            }
        }
    }

    fun resume() { currentId?.let { engine.tune(it) } ?: startWatching(randomize = true) }

    fun tuneTo(id: String, sound: Boolean = true) {
        if (currentId == Lineup.RESTART_ID && id != Lineup.RESTART_ID) {
            previousId = previousId?.takeUnless { it == id } ?: channelBeforeRestart
            channelBeforeRestart = null
        } else if (id != currentId) previousId = currentId
        currentId = id
        if (sound && effects.value.sound) NoiseBurst.play()
        engine.tune(id)
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
            val item = snap.item ?: return@launch
            if (item.kind != MediaKind.EPISODE) return@launch
            if (snap.located?.entry?.kind != EntryKind.PROGRAM) return@launch
            if (snap.channel?.id != returnTo) return@launch
            channelBeforeRestart = previousId
            app.schedule.airFromStart(item, System.currentTimeMillis())
            if (currentId != returnTo || engine.state.value.tuneId != snap.tuneId) return@launch
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
        return "${base.trimEnd('/')}/photo/:/transcode?width=480&height=720&minSize=1&upscale=1&url=${enc(thumb)}"
    }

    fun currentChannelId(): String? = currentId

    fun dispose() {
        scope.cancel()
    }
}
