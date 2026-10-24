package com.rerun.tv.ui

import android.app.Application
import android.os.Looper
import com.rerun.core.catalog.Lineup
import com.rerun.core.model.ChannelDef
import com.rerun.core.model.ChannelKind
import com.rerun.core.model.Rules
import com.rerun.tv.data.ChannelRow
import com.rerun.tv.RerunApp
import com.rerun.tv.data.ScheduleRepo
import com.rerun.tv.data.Settings
import com.rerun.tv.player.TunerEngine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class TvControllerTest {
    private lateinit var controller: TvController
    private lateinit var engine: TunerEngine
    private val rows = MutableStateFlow<List<ChannelRow>>(emptyList())

    @Before
    fun setUp() = runBlocking {
        val app = mock(RerunApp::class.java)
        val repo = mock(ScheduleRepo::class.java)
        val settings = mock(Settings::class.java)
        engine = mock(TunerEngine::class.java)
        `when`(app.settings).thenReturn(settings)
        `when`(app.schedule).thenReturn(repo)
        `when`(repo.activeChannels()).thenReturn(rows)
        `when`(settings.crtLevel()).thenReturn(flowOf("OFF"))
        `when`(settings.colorTheme()).thenReturn(flowOf("STANDARD_BLUE"))
        `when`(settings.guideDensity()).thenReturn(flowOf("COMFORTABLE"))
        `when`(settings.subtitleLanguage()).thenReturn(flowOf("auto"))
        `when`(settings.flag(anyString(), anyBoolean())).thenReturn(flowOf(false))
        controller = TvController(app, engine)
    }

    @After
    fun tearDown() = controller.dispose()

    @Test
    fun editingWestOpensItsSourceChannel() {
        controller.go(Screen.Editor("custom:one:w"))
        assertEquals(Screen.Editor("custom:one"), controller.screen.value)
    }

    @Test
    fun leavingRestartNeverAddsTheHiddenChannelToLastChannelHistory() {
        controller.tuneTo("east", sound = false)
        controller.tuneTo(Lineup.RESTART_ID, sound = false)
        controller.tuneTo("other", sound = false)
        controller.lastChannel()
        assertEquals("east", controller.currentChannelId())
    }

    @Test
    fun normalLastChannelStillToggles() {
        controller.tuneTo("east", sound = false)
        controller.tuneTo("other", sound = false)
        controller.lastChannel()
        assertEquals("east", controller.currentChannelId())
        controller.lastChannel()
        assertEquals("other", controller.currentChannelId())
    }

    @Test
    fun guideAndChannelSurfingKeepWestNextToEastDespiteRenumbering() {
        fun channel(id: String, number: Int) =
            ChannelRow.of(ChannelDef(id, number, id, ChannelKind.CUSTOM, Rules()), builtIn = false, dormant = false)
        rows.value = listOf(
            channel("one", 2), channel("two", 4), channel("three", 6),
            channel("two:w", 504), channel("orphan:w", 800), channel("one:w", 1001),
        )
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(
            listOf("one", "one:w", "two", "two:w", "three", "orphan:w"),
            controller.channels.value.map { it.id },
        )
        controller.tuneTo("one", sound = false)
        controller.channelUp()
        assertEquals("one:w", controller.currentChannelId())
        controller.channelUp()
        assertEquals("two", controller.currentChannelId())
        controller.channelDown()
        assertEquals("one:w", controller.currentChannelId())
        rows.value = rows.value.filterNot { Lineup.isWest(it.id) }
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf("one", "two", "three"), controller.channels.value.map { it.id })
    }
}
