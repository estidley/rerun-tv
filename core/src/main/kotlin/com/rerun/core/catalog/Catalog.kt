package com.rerun.core.catalog

import com.rerun.core.model.ChannelDef
import com.rerun.core.model.ChannelKind
import com.rerun.core.model.MediaKind
import com.rerun.core.model.Rules
import com.rerun.core.model.TimeBlock
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * One built-in network channel.
 *
 * [name] is the parody shown on screen. [realName] is the real network it stands for and
 * never appears on screen. [aliases] are the real network names as Plex and TMDB spell them;
 * they match Plex metadata and pick the TMDB networks whose show lists feed the channel.
 * [tmdbCompanies] are TMDB production companies whose movies (made-for-TV films, mostly)
 * belong on the channel.
 */
data class NetworkDef(
    val id: String,
    val name: String,
    val number: Int,
    val aliases: List<String>,
    val kidsSafe: Boolean,
    val realName: String,
    val tmdbCompanies: List<String> = emptyList(),
)

/** Built-in channel lineup. See [NetworkDef] for which names show on screen. */
object Catalog {
    val networks: List<NetworkDef> = listOf(
        NetworkDef("disney", "Dinky Channel", 101, listOf("Disney Channel", "Toon Disney"), true,
            realName = "Disney Channel", tmdbCompanies = listOf("Disney Channel"),
        ),
        NetworkDef("nick", "Slimelodeon", 102, listOf("Nickelodeon", "Nicktoons", "Nick", "TeenNick"), true,
            realName = "Nickelodeon", tmdbCompanies = listOf("Nickelodeon Movies", "Nickelodeon Productions"),
        ),
        NetworkDef("cn", "Cartoon Notwork", 103, listOf("Cartoon Network", "CN"), true,
            realName = "Cartoon Network", tmdbCompanies = listOf("Cartoon Network Studios"),
        ),
        NetworkDef("boomerang", "Kaboomerang", 104, listOf("Boomerang"), true,
            realName = "Boomerang",
        ),
        NetworkDef("toonami", "Toonuki", 105, listOf("Toonami"), false,
            realName = "Toonami",
        ),
        NetworkDef("pbs", "PBJ", 106, listOf("PBS"), false,
            realName = "PBS", tmdbCompanies = listOf("PBS"),
        ),
        NetworkDef("pbskids", "PBJ Kids", 107, listOf("PBS Kids"), true,
            realName = "PBS Kids",
        ),
        NetworkDef("disneyxd", "Dinky XD", 108, listOf("Disney XD", "Jetix"), true,
            realName = "Disney XD",
        ),
        NetworkDef("touchstone", "The Backlot", 109, emptyList(), false,
            realName = "Touchstone",
        ),
        NetworkDef("syfy", "Sigh-Fi Channel", 110, listOf("Syfy", "Sci Fi Channel", "SciFi", "Sci-Fi Channel"), false,
            realName = "Syfy", tmdbCompanies = listOf("Syfy"),
        ),
        NetworkDef("usa", "USB Network", 111, listOf("USA Network", "USA"), false,
            realName = "USA Network",
        ),
        NetworkDef("tnt", "DNT", 112, listOf("TNT"), false,
            realName = "TNT",
        ),
        NetworkDef("tbs", "Ted's TV", 113, listOf("TBS"), false,
            realName = "TBS",
        ),
        NetworkDef("fx", "SFX", 114, listOf("FX", "FXX", "FX Networks"), false,
            realName = "FX",
        ),
        NetworkDef("amc", "PMC", 115, listOf("AMC"), false,
            realName = "AMC",
        ),
        NetworkDef("mtv", "Remote Control", 116, listOf("MTV"), false,
            realName = "MTV", tmdbCompanies = listOf("MTV Films"),
        ),
        NetworkDef("comedycentral", "Tragedy Central", 117, listOf("Comedy Central"), false,
            realName = "Comedy Central", tmdbCompanies = listOf("Comedy Central Films"),
        ),
        NetworkDef("freeform", "XYZ Family", 118, listOf("ABC Family", "Freeform", "Fox Family"), false,
            realName = "ABC Family", tmdbCompanies = listOf("ABC Family"),
        ),
        NetworkDef("paramountnet", "Spork TV", 119, listOf("Paramount Network", "Spike", "Spike TV"), false,
            realName = "Spike TV",
        ),
        NetworkDef("discovery", "Recovery Channel", 120, listOf("Discovery", "Discovery Channel"), false,
            realName = "Discovery Channel",
        ),
        NetworkDef("history", "Hysteria Channel", 121, listOf("History", "History Channel", "The History Channel"), false,
            realName = "History Channel",
        ),
        NetworkDef("food", "Feud Network", 122, listOf("Food Network"), false,
            realName = "Food Network",
        ),
        NetworkDef("hgtv", "GGTV", 123, listOf("HGTV"), false,
            realName = "HGTV",
        ),
        NetworkDef("adultswim", "Adult Whim", 124, listOf("Adult Swim", "[adult swim]"), false,
            realName = "Adult Swim", tmdbCompanies = listOf("Williams Street"),
        ),
        NetworkDef("nickjr", "Slime Jr.", 125, listOf("Nick Jr.", "Nick Jr"), true,
            realName = "Nick Jr.",
        ),
        NetworkDef("disneyjr", "Playhouse Dinky", 126, listOf("Disney Junior", "Disney Jr.", "Playhouse Disney"), true,
            realName = "Playhouse Disney",
        ),
        NetworkDef("hbo", "HMO", 130, listOf("HBO", "Cinemax"), false,
            realName = "HBO", tmdbCompanies = listOf("HBO Films"),
        ),
    )

    private val aliasIndex: Map<String, String> = buildMap {
        for (n in networks) for (a in n.aliases) put(aliasKey(a), n.id)
    }

    private fun aliasKey(s: String) = s.trim().lowercase()

    fun canonicalNetwork(raw: String?): String? = raw?.let { aliasIndex[aliasKey(it)] }

    private val numbers: Map<String, Int> = networks.associate { it.id to it.number }

    /** Channel number of a built-in network, or 999 for an unknown id (sorts last). */
    fun numberOf(id: String): Int = numbers[id] ?: 999

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
                    Rules(genres = setOf("Anime")),
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
        return net + themes + ComicCatalog.channels
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
        "disney" to setOf("Disney Channel", "Wonderland"),
        "nick" to setOf("Nickelodeon", "Kid Castle"),
        "cn" to setOf("Cartoon Network", "Toon Tower"),
        "boomerang" to setOf("Boomerang", "The Attic"),
        "toonami" to setOf("Toonami", "Midnight Block"),
        "pbs" to setOf("PBS", "Public Square"),
        "pbskids" to setOf("PBS Kids", "Story Yard"),
        "disneyxd" to setOf("Disney XD", "Action Club"),
        "touchstone" to setOf("Touchstone"),
        "syfy" to setOf("SYFY", "Warp Nine"),
        "usa" to setOf("USA", "Coast to Coast"),
        "tnt" to setOf("TNT", "Boomstick"),
        "tbs" to setOf("TBS", "Laugh Track", "TBSP"),
        "fx" to setOf("FX", "The Edge"),
        "amc" to setOf("AMC", "The Marquee"),
        "mtv" to setOf("MTV", "The Video Channel", "Music TV"),
        "comedycentral" to setOf("Comedy Central", "Punchline"),
        "freeform" to setOf("ABC Family", "Family Hour"),
        "paramountnet" to setOf("Paramount Network", "The Mountain"),
        "discovery" to setOf("Discovery", "The Expedition"),
        "history" to setOf("History", "Yesteryear"),
        "food" to setOf("Food Network", "The Kitchen"),
        "hgtv" to setOf("HGTV", "Home Base"),
        "adultswim" to setOf("Adult Swim", "After Hours"),
        "nickjr" to setOf("Nick Jr.", "Tiny Town"),
        "disneyjr" to setOf("Disney Junior", "Little Castle"),
        "hbo" to setOf("HBO", "The Premium"),
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
