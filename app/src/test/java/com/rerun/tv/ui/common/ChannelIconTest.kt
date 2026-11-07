package com.rerun.tv.ui.common

import android.app.Application
import android.graphics.BitmapFactory
import com.rerun.core.catalog.Catalog
import com.rerun.core.catalog.Lineup
import com.rerun.tv.R
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test fun decadeWordmarksReuseTheLowercaseSGlyph() {
        for (decade in 1900..2090 step 10) for (kind in listOf("tv", "music")) {
            val document = wordmark("decade-$kind-$decade")
            val paths = document.getElementsByTagName("path")
            var lowercaseGlyphs = 0
            for (index in 0 until paths.length) {
                val path = paths.item(index)
                val data = path.attributes.getNamedItem("d").nodeValue
                assertFalse("Uppercase S remains in decade $kind $decade", data.startsWith("M5535 485 5966 512"))
                if (data.startsWith("M2783 294 3187 332") &&
                    path.attributes.getNamedItem("transform")?.nodeValue == "translate(2852.5 0)") lowercaseGlyphs++
            }
            assertEquals("Missing lowercase s in decade $kind $decade", 2, lowercaseGlyphs)
        }
    }

    @Test fun limeBannersUseDarkMainLettering() {
        for (id in listOf(
            "pbskids", "amc", "hgtv", "action", "dc-tv", "opt-war", "opt-dramedy",
            "decade-tv-1910", "decade-tv-1950", "decade-tv-1990", "decade-tv-2030", "decade-tv-2070",
        )) assertDarkMainLettering(id)
    }

    @Test fun reportedBrightBannersUseDarkMainLettering() {
        for (id in listOf(
            "ads", "dc-movies", "movies-3", "opt-news", "opt-sports", "opt-romance", "family",
            "hbo", "discovery", "tnt", "boomerang", "disneyxd",
        )) assertDarkMainLettering(id)
    }

    @Test fun shortAndReportedWordmarksAreEnlarged() {
        for ((id, minimum) in mapOf("hbo" to 0.038f, "tnt" to 0.035f, "syfy" to 0.023f)) {
            val document = wordmark(id)
            val children = document.documentElement.childNodes
            val groups = (0 until children.length).mapNotNull { children.item(it) as? org.w3c.dom.Element }
                .filter { it.tagName == "g" && "scale" in it.getAttribute("transform") }
            assertEquals("Unexpected text layout in $id", 1, groups.size)
            val verticalScale = groups.single().getAttribute("transform")
                .substringAfterLast(' ').dropLast(1).toFloat()
            assertTrue("Lettering is too small in $id", verticalScale < -minimum)
        }
    }

    @Test fun livingRoomIsLowercaseStandaloneVectorArt() {
        val document = wordmark("family")
        assertEquals("living room", document.getElementsByTagName("title").item(0).textContent)
        assertEquals(0, document.getElementsByTagName("text").length)
        assertTrue(document.getElementsByTagName("path").length > 0)
        assertDarkMainLettering("family")
    }

    private fun assertDarkMainLettering(id: String) {
        val document = wordmark(id)
        val children = document.documentElement.childNodes
        var textGroups = 0
        for (index in 0 until children.length) {
            val group = children.item(index) as? org.w3c.dom.Element ?: continue
            if (group.tagName != "g" || "scale" !in group.getAttribute("transform")) continue
            val paths = group.getElementsByTagName("path")
            val fills = (0 until paths.length).map { paths.item(it).attributes.getNamedItem("fill").nodeValue }
            assertTrue("Missing dark main lettering in $id", "#161327" in fills)
            assertFalse("Cream lettering remains on a bright banner in $id", "#FFF0CE" in fills)
            textGroups++
        }
        assertTrue("No main text group found in $id", textGroups > 0)
    }

    private fun wordmark(id: String) = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        .parse(File("../design/channel-icons/wordmarks", "$id.svg"))
}
