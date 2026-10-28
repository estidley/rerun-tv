package com.rerun.core.codec

import com.rerun.core.catalog.Catalog
import com.rerun.core.model.ChannelDef
import com.rerun.core.model.ChannelKind
import com.rerun.core.model.MediaKind
import com.rerun.core.model.PlayOrder
import com.rerun.core.model.Rules
import com.rerun.core.model.TimeBlock
import com.rerun.core.schedule.ChannelState
import java.time.DayOfWeek
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

class CodecTest {
    @Test
    fun everyBuiltInChannelRoundTrips() {
        for (def in Catalog.builtInChannels()) {
            assertEquals(def, ChannelCodec.decode(ChannelCodec.encode(def)), def.id)
        }
    }

    @Test
    fun customChannelRoundTrips() {
        val def = ChannelDef(
            id = "custom:fright", number = 413, name = "Fright Night", kind = ChannelKind.CUSTOM,
            rules = Rules(
                genres = setOf("Horror"), excludeGenres = setOf("Comedy"), decades = setOf(1980, 1990),
                ratings = setOf("R"), kinds = setOf(MediaKind.MOVIE), labels = setOf("Halloween"),
                collections = setOf("Friday the 13th"), tmdbIds = setOf("948"), titles = setOf("Halloween"),
                networks = setOf("syfy"), bucket = 1, bucketCount = 3,
                franchises = setOf("marvel", "dc"),
            ),
            order = PlayOrder.IN_ORDER,
            blocks = listOf(TimeBlock(setOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY), LocalTime.of(21, 0), LocalTime.of(3, 0), Rules(ratings = setOf("R"), bucket = 2, bucketCount = 4))),
            kidsSafe = false, hidden = true, movieEveryMs = 3_600_000,
        )
        assertEquals(def, ChannelCodec.decode(ChannelCodec.encode(def)))
        for (hours in listOf(0, 1, 24, 168)) {
            val overridden = def.copy(minimumContentHours = hours)
            assertEquals(overridden, ChannelCodec.decode(ChannelCodec.encode(overridden)))
        }
        assertEquals(null, ChannelCodec.decode(ChannelCodec.encode(def)).minimumContentHours)
    }

    @Test
    fun stateRoundTrips() {
        val st = ChannelState(seed = -42, deck = listOf("a", "b"), recent = listOf("c"), episodePointers = mapOf("show:x" to 3), msSinceMovie = 99, rngCalls = 7)
        assertEquals(st, StateCodec.decode(StateCodec.encode(st)))
        assertEquals(ChannelState(seed = 1), StateCodec.decode(StateCodec.encode(ChannelState(seed = 1))))
    }

    @Test
    fun missingKindsStayMoviesAndEpisodes() {
        val encoded = ChannelCodec.encode(ChannelDef("x", 1, "X", ChannelKind.CUSTOM, Rules()))
        val stripped = encoded.replace(""","kinds":["MOVIE","EPISODE"]""", "")
        assertEquals(setOf(MediaKind.MOVIE, MediaKind.EPISODE), ChannelCodec.decode(stripped).rules.kinds)
        val withoutFranchises = encoded.replace(""","franchises":[]""", "")
        assertEquals(emptySet(), ChannelCodec.decode(withoutFranchises).rules.franchises)
    }

    @Test
    fun setCodecRoundTrips() {
        val s = setOf("Action & Adventure", "Sci-Fi \"Quoted\"")
        assertEquals(s, SetCodec.decode(SetCodec.encode(s)))
        assertEquals(emptySet(), SetCodec.decode(""))
    }

    @Test
    fun userRenamedRoundTripsAndDefaultsToFalse() {
        val def = ChannelDef("disney", 101, "Disney Channel", ChannelKind.THEME, Rules(), userRenamed = true)
        assertEquals(def, ChannelCodec.decode(ChannelCodec.encode(def)))
        assertEquals(false, ChannelCodec.decode(ChannelCodec.encode(def.copy(userRenamed = false))).userRenamed)
    }
}
