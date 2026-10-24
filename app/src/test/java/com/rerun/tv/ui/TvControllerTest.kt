package com.rerun.tv.ui

import android.app.Application
import com.rerun.core.catalog.Lineup
import com.rerun.tv.RerunApp
import com.rerun.tv.data.ScheduleRepo
import com.rerun.tv.data.Settings
import com.rerun.tv.player.TunerEngine
import kotlinx.coroutines.flow.flowOf
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
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class TvControllerTest {
    private lateinit var controller: TvController
    private lateinit var engine: TunerEngine

    @Before
    fun setUp() = runBlocking {
        val app = mock(RerunApp::class.java)
        val repo = mock(ScheduleRepo::class.java)
        val settings = mock(Settings::class.java)
        engine = mock(TunerEngine::class.java)
        `when`(app.settings).thenReturn(settings)
        `when`(app.schedule).thenReturn(repo)
        `when`(repo.activeChannels()).thenReturn(flowOf(emptyList()))
        `when`(settings.crtLevel()).thenReturn(flowOf("OFF"))
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
    fun steppingAndEffectiveChannelFromRestartUseOriginChannel() = runBlocking {
        val ch1 = com.rerun.tv.data.ChannelRow.of(com.rerun.core.model.ChannelDef("one", 101, "One", com.rerun.core.model.ChannelKind.CUSTOM, com.rerun.core.model.Rules()), false, false)
        val ch2 = com.rerun.tv.data.ChannelRow.of(com.rerun.core.model.ChannelDef("two", 102, "Two", com.rerun.core.model.ChannelKind.CUSTOM, com.rerun.core.model.Rules()), false, false)
        val ch3 = com.rerun.tv.data.ChannelRow.of(com.rerun.core.model.ChannelDef("three", 103, "Three", com.rerun.core.model.ChannelKind.CUSTOM, com.rerun.core.model.Rules()), false, false)
        val app = mock(RerunApp::class.java)
        val repo = mock(ScheduleRepo::class.java)
        val settings = mock(Settings::class.java)
        `when`(app.settings).thenReturn(settings)
        `when`(app.schedule).thenReturn(repo)
        `when`(repo.activeChannels()).thenReturn(flowOf(listOf(ch1, ch2, ch3)))
        `when`(settings.crtLevel()).thenReturn(flowOf("OFF"))
        `when`(settings.flag(anyString(), anyBoolean())).thenReturn(flowOf(false))
        val ctrl = TvController(app, engine)
        try {
            ctrl.tuneTo("two", sound = false)
            ctrl.tuneTo(Lineup.RESTART_ID, sound = false)
            assertEquals("two", ctrl.effectiveChannelId())
            ctrl.channelUp()
            assertEquals("three", ctrl.currentChannelId())
            ctrl.lastChannel()
            assertEquals("two", ctrl.currentChannelId())
        } finally {
            ctrl.dispose()
        }
    }
}
