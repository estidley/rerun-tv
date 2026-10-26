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

/**
 * Built-in channel lineup. [NetworkDef.name] is the on-screen parody; [NetworkDef.aliases]
 * stay the real Plex network strings so matching still works.
 */
object Catalog {
    val networks: List<NetworkDef> = listOf(
        NetworkDef("disney", "Wonderland", 101, listOf("Disney Channel", "Toon Disney"), true),
        NetworkDef("nick", "Kid Castle", 102, listOf("Nickelodeon", "Nicktoons", "Nick", "TeenNick"), true),
        NetworkDef("cn", "Toon Tower", 103, listOf("Cartoon Network", "CN"), true),
        NetworkDef("boomerang", "The Attic", 104, listOf("Boomerang"), true),
        NetworkDef("toonami", "Midnight Block", 105, listOf("Toonami"), true),
        NetworkDef("pbs", "Public Square", 106, listOf("PBS"), false),
        NetworkDef("pbskids", "Story Yard", 107, listOf("PBS Kids"), true),
        NetworkDef("disneyxd", "Action Club", 108, listOf("Disney XD", "Jetix"), true),
        NetworkDef("touchstone", "The Backlot", 109, emptyList(), false),
        NetworkDef("syfy", "Warp Nine", 110, listOf("Syfy", "Sci Fi Channel", "SciFi", "Sci-Fi Channel"), false),
        NetworkDef("usa", "Coast to Coast", 111, listOf("USA Network", "USA"), false),
        NetworkDef("tnt", "Boomstick", 112, listOf("TNT"), false),
        NetworkDef("tbs", "Ted's TV", 113, listOf("TBS"), false),
        NetworkDef("fx", "The Edge", 114, listOf("FX", "FXX", "FX Networks"), false),
        NetworkDef("amc", "The Marquee", 115, listOf("AMC"), false),
        NetworkDef("mtv", "Remote Control", 116, listOf("MTV"), false),
        NetworkDef("comedycentral", "Punchline", 117, listOf("Comedy Central"), false),
        NetworkDef("freeform", "Family Hour", 118, listOf("ABC Family", "Freeform", "Fox Family"), false),
        NetworkDef("paramountnet", "The Mountain", 119, listOf("Paramount Network", "Spike", "Spike TV"), false),
        NetworkDef("discovery", "The Expedition", 120, listOf("Discovery", "Discovery Channel"), false),
        NetworkDef("history", "Yesteryear", 121, listOf("History", "History Channel", "The History Channel"), false),
        NetworkDef("food", "The Kitchen", 122, listOf("Food Network"), false),
        NetworkDef("hgtv", "Home Base", 123, listOf("HGTV"), false),
        NetworkDef("adultswim", "After Hours", 124, listOf("Adult Swim", "[adult swim]"), false),
        NetworkDef("nickjr", "Tiny Town", 125, listOf("Nick Jr.", "Nick Jr"), true),
        NetworkDef("disneyjr", "Little Castle", 126, listOf("Disney Junior", "Disney Jr.", "Playhouse Disney"), true),
        NetworkDef("hbo", "The Premium", 130, listOf("HBO", "Cinemax"), false),
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
            ChannelDef("horror", 301, "Fright Night", ChannelKind.THEME, Rules(genres = setOf("Horror", "Thriller"))),
            ChannelDef("scifi", 302, "The Void", ChannelKind.THEME, Rules(genres = setOf("Science Fiction", "Sci-Fi & Fantasy"))),
            ChannelDef("comedy", 303, "Laugh Track", ChannelKind.THEME, Rules(genres = setOf("Comedy"))),
            ChannelDef("action", 304, "Adrenaline", ChannelKind.THEME, Rules(genres = setOf("Action", "Adventure"))),
            ChannelDef(
                "family", 305, "The Living Room", ChannelKind.THEME,
                Rules(genres = setOf("Family", "Animation"), ratings = kidRatings), kidsSafe = true,
            ),
            ChannelDef("80s", 306, "Neon Decade", ChannelKind.THEME, Rules(decades = setOf(1980), kinds = setOf(MediaKind.MOVIE))),
            ChannelDef("90s", 307, "Dial-Up Cinema", ChannelKind.THEME, Rules(decades = setOf(1990), kinds = setOf(MediaKind.MOVIE))),
            ChannelDef(
                "saturday", 308, "Cereal Hour", ChannelKind.THEME,
                Rules(genres = setOf("Animation"), ratings = setOf("TV-Y", "TV-Y7", "TV-Y7-FV", "TV-G"), kinds = setOf(MediaKind.EPISODE)),
                kidsSafe = true,
            ),
        )
        return net + themes
    }

    /** Previous on-screen names. A refresh may replace these; a name the user typed stays. */
    fun retiredNames(id: String): Set<String> = retired[id].orEmpty()

    /**
     * Name to store for [id]. Stock names take [catalogName]; personal renames are kept.
     */
    fun chooseName(id: String, stored: String?, catalogName: String): String {
        if (stored.isNullOrBlank()) return catalogName
        return if (stored == catalogName || stored in retiredNames(id)) catalogName else stored
    }

    fun validateNumbers(defs: List<ChannelDef>): List<String> =
        defs.groupBy { it.number }
            .filterValues { it.size > 1 }
            .toSortedMap()
            .map { (number, clash) -> "Channel $number is used by ${clash.joinToString(" and ") { it.name }}" }

    private val retired: Map<String, Set<String>> = mapOf(
        "disney" to setOf("Disney Channel"),
        "nick" to setOf("Nickelodeon"),
        "cn" to setOf("Cartoon Network"),
        "boomerang" to setOf("Boomerang"),
        "toonami" to setOf("Toonami"),
        "pbs" to setOf("PBS"),
        "pbskids" to setOf("PBS Kids"),
        "disneyxd" to setOf("Disney XD"),
        "touchstone" to setOf("Touchstone"),
        "syfy" to setOf("SYFY"),
        "usa" to setOf("USA"),
        "tnt" to setOf("TNT"),
        "tbs" to setOf("TBS", "Laugh Track"),
        "fx" to setOf("FX"),
        "amc" to setOf("AMC"),
        "mtv" to setOf("MTV", "The Video Channel"),
        "comedycentral" to setOf("Comedy Central"),
        "freeform" to setOf("ABC Family"),
        "paramountnet" to setOf("Paramount Network"),
        "discovery" to setOf("Discovery"),
        "history" to setOf("History"),
        "food" to setOf("Food Network"),
        "hgtv" to setOf("HGTV"),
        "adultswim" to setOf("Adult Swim"),
        "nickjr" to setOf("Nick Jr."),
        "disneyjr" to setOf("Disney Junior"),
        "hbo" to setOf("HBO"),
        "horror" to setOf("Horror"),
        "scifi" to setOf("Sci-Fi"),
        "comedy" to setOf("Comedy", "The Chuckle", "Laughing Stock"),
        "opt:docs" to setOf("The Reel"),
        "opt:news" to setOf("The Bulletin"),
        "action" to setOf("Action"),
        "family" to setOf("Family"),
        "80s" to setOf("80s Movies"),
        "90s" to setOf("90s Movies"),
        "saturday" to setOf("Saturday Morning"),
    )
}
