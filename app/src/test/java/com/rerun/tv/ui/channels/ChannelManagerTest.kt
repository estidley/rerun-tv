package com.rerun.tv.ui.channels

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import com.rerun.core.catalog.MinimumContentLevel
import com.rerun.core.model.ChannelDef
import com.rerun.core.model.ChannelKind
import com.rerun.core.model.Rules
import com.rerun.tv.RerunApp
import com.rerun.tv.data.ChannelRow
import com.rerun.tv.data.ScheduleRepo
import com.rerun.tv.ui.LineupUpdate
import com.rerun.tv.ui.TvController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w1920dp-h1080dp-mdpi")
class ChannelManagerTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun longPressDisablesAndRestoresOrdinaryChannels() {
        val app = mock(RerunApp::class.java)
        val schedule = mock(ScheduleRepo::class.java)
        val controller = mock(TvController::class.java)
        val active = ChannelRow.of(
            ChannelDef("custom:comedy", 420, "Comedy", ChannelKind.CUSTOM, Rules()),
            builtIn = false, dormant = false, poolMs = 3_600_000,
        )
        val disabled = ChannelRow.of(
            ChannelDef("custom:jazz", 2000, "Jazz", ChannelKind.MUSIC, Rules(), hidden = true),
            builtIn = true, dormant = false, poolMs = 3_600_000,
        )
        `when`(app.schedule).thenReturn(schedule)
        `when`(schedule.allChannels()).thenReturn(flowOf(listOf(active, disabled)))
        `when`(controller.minimumContent).thenReturn(MutableStateFlow(MinimumContentLevel.MEDIUM))
        `when`(controller.lineupUpdate).thenReturn(MutableStateFlow(LineupUpdate()))
        compose.setContent { ChannelManager(controller, app) }

        compose.onNodeWithText("420   Comedy").performTouchInput { longClick() }
        verify(controller).setChannelHidden("custom:comedy", true)
        compose.onNodeWithText("2000   Jazz").performTouchInput { longClick() }
        verify(controller).setChannelHidden("custom:jazz", false)
    }
}
