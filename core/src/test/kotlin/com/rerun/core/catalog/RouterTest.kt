package com.rerun.core.catalog

import com.rerun.core.episode
import com.rerun.core.movie
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RouterTest {
    @Test
    fun disneyPlusKidsShowGoesToDisneyChannel() =
        assertEquals("disney", Router.route(episode("e", "Big City Greens Live", network = "Disney+", rating = "TV-G", genres = setOf("Comedy", "Family"))))

    @Test
    fun disneyPreschoolGoesToJunior() =
        assertEquals("disneyjr", Router.route(episode("e", "Bluey-ish", network = "Disney+", rating = "TV-Y", genres = setOf("Animation"))))

    @Test
    fun marvelAnimationGoesToDisneyXd() =
        assertEquals("disneyxd", Router.route(episode("e", "Spidey Toon", studio = "Marvel Animation", rating = "TV-Y7", genres = setOf("Animation", "Action"))))

    @Test
    fun lucasfilmFamilyCartoonGoesToDisneyXd() =
        assertEquals("disneyxd", Router.route(episode("e", "Clone Toon", network = "Disney+", studio = "Lucasfilm Animation", rating = "TV-PG", genres = setOf("Animation"))))

    @Test
    fun marvelMovieGoesToTouchstone() =
        assertEquals("touchstone", Router.route(movie("m", studio = "Marvel Studios", rating = "PG-13", genres = setOf("Action"))))

    @Test
    fun paramountPlusAvatarGoesToNickelodeon() =
        assertEquals("nick", Router.route(episode("e", "Avatar Next", network = "Paramount+", rating = "TV-Y7", genres = setOf("Animation", "Action"))))

    @Test
    fun paramountPlusDramaGoesToParamountNetwork() =
        assertEquals("paramountnet", Router.route(episode("e", "Ranch Saga", network = "Paramount+", rating = "TV-MA", genres = setOf("Drama", "Western"))))

    @Test
    fun maxDramaGoesToHbo() =
        assertEquals("hbo", Router.route(episode("e", "Dragon House", network = "Max", rating = "TV-MA", genres = setOf("Drama"))))

    @Test
    fun warnerKidsGoesToCartoonNetwork() =
        assertEquals("cn", Router.route(episode("e", "Toon", network = "Max", rating = "TV-Y7", genres = setOf("Animation"))))

    @Test
    fun huluGoesToFx() =
        assertEquals("fx", Router.route(episode("e", "Kitchen Show", network = "Hulu", rating = "TV-MA", genres = setOf("Comedy"))))

    @Test
    fun peacockSciFiGoesToSyfy() =
        assertEquals("syfy", Router.route(episode("e", "Space Show", network = "Peacock", rating = "TV-14", genres = setOf("Science Fiction"))))

    @Test
    fun netflixSciFiGoesToSyfy() =
        assertEquals("syfy", Router.route(episode("e", "Upside Show", network = "Netflix", rating = "TV-14", genres = setOf("Sci-Fi & Fantasy", "Drama"))))

    @Test
    fun netflixKidsAnimationGoesToCartoonNetwork() =
        assertEquals("cn", Router.route(episode("e", "Kid Toon", network = "Netflix", rating = "TV-Y7", genres = setOf("Animation"))))

    @Test
    fun netflixPreschoolGoesToPbsKids() =
        assertEquals("pbskids", Router.route(episode("e", "Tiny Toon", network = "Netflix", rating = "TV-Y", genres = setOf("Animation"))))

    @Test
    fun appleComedyGoesToComedyCentral() =
        assertEquals("comedycentral", Router.route(episode("e", "Coach Show", network = "Apple TV+", rating = "TV-MA", genres = setOf("Comedy"))))

    @Test
    fun amazonHistoryDocGoesToHistory() =
        assertEquals("history", Router.route(movie("m", studio = "Amazon Studios", rating = "TV-14", genres = setOf("Documentary", "History"))))

    @Test
    fun netflixDramaFallsBackToUsa() =
        assertEquals("usa", Router.route(episode("e", "Crime Show", network = "Netflix", rating = "TV-MA", genres = setOf("Crime", "Drama"))))

    @Test
    fun unknownStudioRoutesNowhere() =
        assertNull(Router.route(movie("m", studio = "Universal Pictures", rating = "PG-13")))
}
