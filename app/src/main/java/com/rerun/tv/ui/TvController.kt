package com.rerun.tv.ui

import com.rerun.core.model.MediaItem
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
import java.net.URLEncoder

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

    /** Set to true when the user asks to leave the app; the activity plays power-off and finishes. */
    private val _exiting = MutableStateFlow(false)
    val exiting: StateFlow<Boolean> = _exiting.asStateFlow()

    private var currentId: String? = null
    private var previousId: String? = null
    private var guideClosedAt = 0L

    @Volatile var serverBase: String? = null
        private set
    private val token: String? get() = app.settings.serverToken ?: app.settings.accountToken

    init {
        scope.launch { serverBase = app.settings.serverBase() }
    }

    fun go(screen: Screen) {
        if (_screen.value == Screen.Guide && screen == Screen.Player) guideClosedAt = System.currentTimeMillis()
        _screen.value = screen
    }

    /** Start watching: last channel if still active, else the lowest-numbered one. */
    fun startWatching() {
        scope.launch {
            serverBase = app.settings.serverBase()
            app.schedule.invalidateLibrary()
            val list = app.schedule.active()
            val last = app.settings.lastChannel()
            val pick = list.firstOrNull { it.id == last } ?: list.firstOrNull()
            _screen.value = Screen.Player
            if (pick != null) tuneTo(pick.id, sound = false)
        }
    }

    fun resume() { currentId?.let { engine.tune(it) } ?: startWatching() }

    fun tuneTo(id: String, sound: Boolean = true) {
        if (id != currentId) previousId = currentId
        currentId = id
        if (sound) NoiseBurst.play()
        engine.tune(id)
        showBanner()
    }

    fun channelUp() = step(+1)
    fun channelDown() = step(-1)

    private fun step(dir: Int) {
        val list = channels.value
        if (list.isEmpty()) return
        val i = list.indexOfFirst { it.id == currentId }
        val next = if (i < 0) list.first() else list[Math.floorMod(i + dir, list.size)]
        tuneTo(next.id)
    }

    fun lastChannel() { previousId?.let { tuneTo(it) } }

    fun showBanner(ms: Long = 5_000) { _bannerUntil.value = System.currentTimeMillis() + ms }
    fun hideBanner() { _bannerUntil.value = 0 }

    /** Back on the player opens the guide; Back again right after closing the guide exits. */
    fun backFromPlayer() {
        if (System.currentTimeMillis() - guideClosedAt < 1_500) _exiting.value = true else go(Screen.Guide)
    }

    fun posterUrl(item: MediaItem?): String? {
        val thumb = item?.thumb ?: return null
        val base = serverBase ?: return null
        val t = token ?: return null
        val enc = { s: String -> URLEncoder.encode(s, "UTF-8") }
        return "$base/photo/:/transcode?width=480&height=720&minSize=1&upscale=1&url=${enc(thumb)}&X-Plex-Token=${enc(t)}"
    }

    fun currentChannelId(): String? = currentId

    fun dispose() {
        scope.cancel()
    }
}
