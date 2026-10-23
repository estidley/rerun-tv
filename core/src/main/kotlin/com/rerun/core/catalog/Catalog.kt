package com.rerun.core.catalog

import com.rerun.core.model.ChannelDef
import com.rerun.core.model.ChannelKind
import com.rerun.core.model.MediaKind
import com.rerun.core.model.Rules
import com.rerun.core.model.TimeBlock
import java.time.DayOfWeek
import java.time.LocalTime

data class NetworkDef(
    val id: String,
    val name: String,
    val number: Int,
    val aliases: List<String>,
    val kidsSafe: Boolean,
)

/** Built-in channel lineup: classic networks, curated block channels, and themes. */
object Catalog {
    val networks: List<NetworkDef> = listOf(
        NetworkDef("disney", "Disney Channel", 101, listOf("Disney Channel", "Toon Disney"), true),
        NetworkDef("nick", "Nickelodeon", 102, listOf("Nickelodeon", "Nicktoons", "Nick", "TeenNick"), true),
        NetworkDef("cn", "Cartoon Network", 103, listOf("Cartoon Network", "CN"), true),
        NetworkDef("boomerang", "Boomerang", 104, listOf("Boomerang"), true),
        NetworkDef("toonami", "Toonami", 105, listOf("Toonami"), true),
        NetworkDef("pbs", "PBS", 106, listOf("PBS"), false),
        NetworkDef("pbskids", "PBS Kids", 107, listOf("PBS Kids"), true),
        NetworkDef("disneyxd", "Disney XD", 108, listOf("Disney XD", "Jetix"), true),
        NetworkDef("touchstone", "Touchstone", 109, emptyList(), false),
        NetworkDef("syfy", "SYFY", 110, listOf("Syfy", "Sci Fi Channel", "SciFi", "Sci-Fi Channel"), false),
        NetworkDef("usa", "USA", 111, listOf("USA Network", "USA"), false),
        NetworkDef("tnt", "TNT", 112, listOf("TNT"), false),
        NetworkDef("tbs", "TBS", 113, listOf("TBS"), false),
        NetworkDef("fx", "FX", 114, listOf("FX", "FXX", "FX Networks"), false),
        NetworkDef("amc", "AMC", 115, listOf("AMC"), false),
        NetworkDef("mtv", "MTV", 116, listOf("MTV"), false),
        NetworkDef("comedycentral", "Comedy Central", 117, listOf("Comedy Central"), false),
        NetworkDef("freeform", "ABC Family", 118, listOf("ABC Family", "Freeform", "Fox Family"), false),
        NetworkDef("paramountnet", "Paramount Network", 119, listOf("Paramount Network", "Spike", "Spike TV"), false),
        NetworkDef("discovery", "Discovery", 120, listOf("Discovery", "Discovery Channel"), false),
        NetworkDef("history", "History", 121, listOf("History", "History Channel", "The History Channel"), false),
        NetworkDef("food", "Food Network", 122, listOf("Food Network"), false),
        NetworkDef("hgtv", "HGTV", 123, listOf("HGTV"), false),
        NetworkDef("adultswim", "Adult Swim", 124, listOf("Adult Swim", "[adult swim]"), false),
        NetworkDef("nickjr", "Nick Jr.", 125, listOf("Nick Jr.", "Nick Jr"), true),
        NetworkDef("disneyjr", "Disney Junior", 126, listOf("Disney Junior", "Disney Jr.", "Playhouse Disney"), true),
        NetworkDef("hbo", "HBO", 130, listOf("HBO", "Cinemax"), false),
    )

    private val aliasIndex: Map<String, String> = buildMap {
        for (n in networks) for (a in n.aliases) put(aliasKey(a), n.id)
    }

    private fun aliasKey(s: String) = s.trim().lowercase()

    fun canonicalNetwork(raw: String?): String? = raw?.let { aliasIndex[aliasKey(it)] }

    private val weekdays = setOf(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
    )

    private val kidRatings = setOf("G", "PG", "TV-Y", "TV-Y7", "TV-Y7-FV", "TV-G", "TV-PG")

    fun builtInChannels(): List<ChannelDef> {
        val net = networks.map { n ->
            when (n.id) {
                "boomerang" -> ChannelDef(
                    n.id, n.number, n.name, ChannelKind.CURATED,
                    Rules(networks = setOf(n.id), titles = Curated.boomerangShows.toSet()), kidsSafe = true,
                )
                "toonami" -> ChannelDef(
                    n.id, n.number, n.name, ChannelKind.CURATED,
                    Rules(networks = setOf(n.id), titles = Curated.toonamiShows.toSet()), kidsSafe = true,
                )
                "cn" -> ChannelDef(
                    n.id, n.number, n.name, ChannelKind.NETWORK, Rules(networks = setOf(n.id)),
                    blocks = listOf(
                        TimeBlock(weekdays, LocalTime.of(16, 0), LocalTime.of(19, 0), Rules(titles = Curated.toonamiShows.toSet())),
                    ),
                    kidsSafe = true,
                )
                else -> ChannelDef(n.id, n.number, n.name, ChannelKind.NETWORK, Rules(networks = setOf(n.id)), kidsSafe = n.kidsSafe)
            }
        }
        val themes = listOf(
            ChannelDef("horror", 301, "Horror", ChannelKind.THEME, Rules(genres = setOf("Horror", "Thriller"))),
            ChannelDef("scifi", 302, "Sci-Fi", ChannelKind.THEME, Rules(genres = setOf("Science Fiction", "Sci-Fi & Fantasy"))),
            ChannelDef("comedy", 303, "Comedy", ChannelKind.THEME, Rules(genres = setOf("Comedy"))),
            ChannelDef("action", 304, "Action", ChannelKind.THEME, Rules(genres = setOf("Action", "Adventure"))),
            ChannelDef(
                "family", 305, "Family", ChannelKind.THEME,
                Rules(genres = setOf("Family", "Animation"), ratings = kidRatings), kidsSafe = true,
            ),
            ChannelDef("80s", 306, "80s Movies", ChannelKind.THEME, Rules(decades = setOf(1980), kinds = setOf(MediaKind.MOVIE))),
            ChannelDef("90s", 307, "90s Movies", ChannelKind.THEME, Rules(decades = setOf(1990), kinds = setOf(MediaKind.MOVIE))),
            ChannelDef(
                "saturday", 308, "Saturday Morning", ChannelKind.THEME,
                Rules(genres = setOf("Animation"), ratings = setOf("TV-Y", "TV-Y7", "TV-Y7-FV", "TV-G"), kinds = setOf(MediaKind.EPISODE)),
                kidsSafe = true,
            ),
        )
        return net + themes
    }

    fun validateNumbers(defs: List<ChannelDef>): List<String> =
        defs.groupBy { it.number }
            .filterValues { it.size > 1 }
            .toSortedMap()
            .map { (number, clash) -> "Channel $number is used by ${clash.joinToString(" and ") { it.name }}" }
}
