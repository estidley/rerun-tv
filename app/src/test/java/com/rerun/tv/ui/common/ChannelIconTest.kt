package com.rerun.tv.ui.common

import android.app.Application
import android.graphics.BitmapFactory
import com.rerun.core.catalog.Catalog
import com.rerun.core.catalog.Lineup
import com.rerun.tv.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChannelIconTest {
    @Test fun everyStockNetworkComicThemeAndDecadeHasDedicatedArtwork() {
        for (channel in Catalog.builtInChannels() + Lineup.adsChannel) {
            assertNotNull("Missing ${channel.id}", channelIconResources[channel.id])
        }
        for (decade in 1900..2090 step 10) for (kind in listOf("tv", "music")) {
            assertNotNull(channelIconResources["decade:$kind:$decade"])
        }
    }

    @Test fun dynamicIdsResolveWithoutDependingOnDisplayNames() {
        assertEquals(R.drawable.channel_ads, channelIconResource(Lineup.ADS_ID))
        assertEquals(R.drawable.channel_music_generic, channelIconResource("music:progressive-rock"))
        assertEquals(R.drawable.channel_custom_generic, channelIconResource("custom:my-favorites"))
        assertEquals(R.drawable.channel_wordmark_ads, channelWordmarkResource(Lineup.ADS_ID))
        assertEquals(R.drawable.channel_wordmark_music_generic, channelWordmarkResource("music:progressive-rock"))
        assertEquals(R.drawable.channel_wordmark_custom_generic, channelWordmarkResource("custom:my-favorites"))
    }

    @Test fun everyIconHasAReadableWordmarkWithTheOriginalAspectRatio() {
        assertEquals(channelIconResources.keys, channelWordmarkResources.keys)
        val resources = RuntimeEnvironment.getApplication().resources
        for ((id, resource) in channelWordmarkResources) {
            assertEquals(resource, channelWordmarkResource(id))
            val bitmap = BitmapFactory.decodeResource(resources, resource)
            assertNotNull("Cannot decode wordmark $id", bitmap)
            assertEquals(400, bitmap.width)
            assertEquals(270, bitmap.height)
            assertTrue("Lost wordmark transparency for $id", bitmap.hasAlpha())
            bitmap.recycle()
        }
    }

    @Test fun allBundledArtDecodesAsTransparent256PixelImages() {
        val resources = RuntimeEnvironment.getApplication().resources
        for ((id, resource) in channelIconResources) {
            val bitmap = BitmapFactory.decodeResource(resources, resource)
            assertNotNull("Cannot decode $id", bitmap)
            assertEquals(256, bitmap.width)
            assertEquals(256, bitmap.height)
            assertTrue("Lost transparency for $id", bitmap.hasAlpha())
            bitmap.recycle()
        }
    }
}
