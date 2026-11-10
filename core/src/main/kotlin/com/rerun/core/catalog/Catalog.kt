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
 * [name] is the parody shown on the dial. [realName] is the real network it stands for and
 * appears beside the parody in the network editor. [aliases] are the real network names as Plex and TMDB spell them;
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
        NetworkDef("abc", "Alphabet", 101,
            listOf("ABC", "American Broadcasting Company", "ABC Network", "ABC-TV"), false, realName = "ABC"),
        NetworkDef("cbs", "The Eye", 102,
            listOf("CBS", "Columbia Broadcasting System", "CBS Network", "CBS-TV"), false, realName = "CBS"),
        NetworkDef("nbc", "Peacock Place", 103,
            listOf("NBC", "National Broadcasting Company", "NBC Network", "NBC-TV"), false, realName = "NBC"),
        NetworkDef("fox", "The Den", 104,
            listOf("FOX", "Fox Broadcasting Company", "Fox Network", "FOX-TV"), false, realName = "FOX"),
        NetworkDef("cbc", "True North", 105,
            listOf("CBC", "Canadian Broadcasting Corporation", "CBC Television", "CBC TV", "CBC News Network"), false,
            realName = "CBC"),
        NetworkDef("pbs", "PBJ", 106, listOf("PBS"), false,
            realName = "PBS", tmdbCompanies = listOf("PBS"),
        ),
        NetworkDef("bbc", "Auntie", 107,
            listOf("BBC", "BBC Television", "BBC Two", "BBC Three", "BBC Four", "CBBC", "CBeebies"), false,
            realName = "BBC"),
        NetworkDef("bbcone", "The One", 108,
            listOf("BBC One", "BBC1", "BBC One HD", "BBC1 HD"), false, realName = "BBC One"),
        NetworkDef("bbcamerica", "The Beeb", 109, listOf("BBC America", "BBC America HD"), false,
            realName = "BBC America",
        ),
        NetworkDef("sky", "The Dish", 110,
            listOf("Sky", "Sky TV", "Sky One", "Sky Atlantic", "Sky Max", "Sky Witness", "Sky Showcase", "Sky Comedy",
                "Sky Sci-Fi", "Sky Documentaries", "Sky News", "Sky Sports"), false, realName = "Sky"),
        NetworkDef("channel4", "Four Sure", 111,
            listOf("Channel 4", "Channel4", "Channel Four", "C4", "E4", "More4", "Film4"), false, realName = "Channel 4"),
        NetworkDef("tbsjapan", "Tokyo Tower", 112,
            listOf("Tokyo Broadcasting System", "Tokyo Broadcasting Station", "Tokyo Broadcasting", "TBS Japan", "TBS Television"), false,
            realName = "Tokyo Broadcasting System",
        ),
        NetworkDef("usa", "USB Network", 120, listOf("USA Network", "USA"), false,
            realName = "USA Network",
        ),
        NetworkDef("tnt", "DNT", 121, listOf("TNT"), false,
            realName = "TNT",
        ),
        NetworkDef("tbs", "Ted's TV", 122,
            listOf("TBS", "TBS TV", "TBS Network", "Turner Broadcasting System", "Turner Broadcasting"), false,
            realName = "TBS",
        ),
        NetworkDef("fx", "SFX", 123, listOf("FX", "FXX", "FX Networks"), false,
            realName = "FX",
        ),
        NetworkDef("amc", "PMC", 124, listOf("AMC"), false,
            realName = "AMC",
        ),
        NetworkDef("mtv", "Remote Control", 125, listOf("MTV"), false,
            realName = "MTV", tmdbCompanies = listOf("MTV Films"),
        ),
        NetworkDef("comedycentral", "Tragedy Central", 126, listOf("Comedy Central"), false,
            realName = "Comedy Central", tmdbCompanies = listOf("Comedy Central Films"),
        ),
        NetworkDef("freeform", "XYZ Family", 127, listOf("ABC Family", "Freeform", "Fox Family"), false,
            realName = "ABC Family", tmdbCompanies = listOf("ABC Family"),
        ),
        NetworkDef("paramountnet", "Spork TV", 128, listOf("Paramount Network", "Spike", "Spike TV"), false,
            realName = "Spike TV",
        ),
        NetworkDef("syfy", "Sigh-Fi Channel", 129, listOf("Syfy", "Sci Fi Channel", "SciFi", "Sci-Fi Channel"), false,
            realName = "Syfy", tmdbCompanies = listOf("Syfy"),
        ),
        NetworkDef("touchstone", "The Backlot", 130, emptyList(), false,
            realName = "Touchstone",
        ),
        NetworkDef("gsn", "The Game Room", 131, listOf("Game Show Network", "GSN"), false,
            realName = "Game Show Network",
        ),
        NetworkDef("bravo", "The Salon", 132, listOf("Bravo", "Bravo TV"), false,
            realName = "Bravo",
        ),
        NetworkDef("hallmark", "The Hearth", 133, listOf("Hallmark Channel", "Hallmark"), false,
            realName = "Hallmark Channel",
        ),
        NetworkDef("hbo", "HMO", 134, listOf("HBO", "Cinemax"), false,
            realName = "HBO", tmdbCompanies = listOf("HBO Films"),
        ),
        NetworkDef("discovery", "Recovery Channel", 140, listOf("Discovery", "Discovery Channel"), false,
            realName = "Discovery Channel",
        ),
        NetworkDef("history", "Hysteria Channel", 141, listOf("History", "History Channel", "The History Channel"), false,
            realName = "History Channel",
        ),
        NetworkDef("natgeo", "The Atlas", 142,
            listOf("National Geographic", "National Geographic Channel", "Nat Geo", "NatGeo", "NGC"), false,
            realName = "National Geographic",
        ),
        NetworkDef("animalplanet", "The Wild", 143, listOf("Animal Planet"), false,
            realName = "Animal Planet",
        ),
        NetworkDef("tlc", "The Learning Lounge", 144, listOf("TLC"), false,
            realName = "TLC",
        ),
        NetworkDef("food", "Feud Network", 145, listOf("Food Network"), false,
            realName = "Food Network",
        ),
        NetworkDef("hgtv", "GGTV", 146, listOf("HGTV"), false,
            realName = "HGTV",
        ),
        NetworkDef("disney", "Dinky Channel", 160, listOf("Disney Channel", "Toon Disney"), true,
            realName = "Disney Channel", tmdbCompanies = listOf("Disney Channel"),
        ),
        NetworkDef("nick", "Slimelodeon", 161, listOf("Nickelodeon", "Nicktoons", "Nick", "TeenNick"), true,
            realName = "Nickelodeon", tmdbCompanies = listOf("Nickelodeon Movies", "Nickelodeon Productions"),
        ),
        NetworkDef("cn", "Cartoon Notwork", 162, listOf("Cartoon Network", "CN"), true,
            realName = "Cartoon Network", tmdbCompanies = listOf("Cartoon Network Studios"),
        ),
        NetworkDef("boomerang", "Kaboomerang", 163, listOf("Boomerang"), true,
            realName = "Boomerang",
        ),
        NetworkDef("toonami", "Toonuki", 164, listOf("Toonami"), false,
            realName = "Toonami",
        ),
        NetworkDef("pbskids", "PBJ Kids", 165, listOf("PBS Kids"), true,
            realName = "PBS Kids",
        ),
        NetworkDef("disneyxd", "Dinky XD", 166, listOf("Disney XD", "Jetix"), true,
            realName = "Disney XD",
        ),
        NetworkDef("disneyjr", "Playhouse Dinky", 167, listOf("Disney Junior", "Disney Jr.", "Playhouse Disney"), true,
            realName = "Playhouse Disney",
        ),
        NetworkDef("nickjr", "Slime Jr.", 168, listOf("Nick Jr.", "Nick Jr"), true,
            realName = "Nick Jr.",
        ),
        NetworkDef("adultswim", "Adult Whim", 169, listOf("Adult Swim", "[adult swim]"), false,
            realName = "Adult Swim", tmdbCompanies = listOf("Williams Street"),
        ),
    )

    private val previousNetworkNumbers = mapOf(
        "disney" to 101, "nick" to 102, "cn" to 103, "boomerang" to 104, "toonami" to 105,
        "pbs" to 106, "pbskids" to 107, "disneyxd" to 108, "touchstone" to 109, "syfy" to 110,
        "usa" to 111, "tnt" to 112, "tbs" to 113, "fx" to 114, "amc" to 115, "mtv" to 116,
        "comedycentral" to 117, "freeform" to 118, "paramountnet" to 119, "discovery" to 120,
        "history" to 121, "food" to 122, "hgtv" to 123, "adultswim" to 124, "nickjr" to 125,
        "disneyjr" to 126, "bbcamerica" to 127, "natgeo" to 128, "animalplanet" to 129, "hbo" to 130,
        "hallmark" to 131, "gsn" to 132, "tbsjapan" to 133, "tlc" to 134, "bravo" to 135,
    )

    fun previousNetworkNumber(id: String): Int? = previousNetworkNumbers[id]

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
                "pbs" -> ChannelDef(
                    n.id, n.number, n.name, ChannelKind.CURATED,
                    Rules(networks = setOf(n.id), titles = Curated.pbsShows.toSet()),
                )
                "pbskids" -> ChannelDef(
                    n.id, n.number, n.name, ChannelKind.CURATED,
                    Rules(networks = setOf(n.id), titles = Curated.pbsKidsShows.toSet()), kidsSafe = true,
                )
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
                "family", 305, "living room", ChannelKind.THEME,
                Rules(genres = setOf("Family", "Animation"), ratings = kidRatings), kidsSafe = true,
            ),
            ChannelDef("80s", 306, "Neon Decade", ChannelKind.THEME, Rules(decades = setOf(1980), kinds = setOf(MediaKind.MOVIE))),
            ChannelDef("90s", 307, "Dial-Up Cinema", ChannelKind.THEME, Rules(decades = setOf(1990), kinds = setOf(MediaKind.MOVIE))),
            ChannelDef(
                "saturday", 308, "Cereal Hour", ChannelKind.THEME,
                Rules(genres = setOf("Animation"), ratings = setOf("TV-Y", "TV-Y7", "TV-Y7-FV", "TV-G"), kinds = setOf(MediaKind.EPISODE)),
                kidsSafe = true,
            ),
            ChannelDef(
                "filmvault", 363, "The Film Vault", ChannelKind.THEME,
                Rules(decades = (1900..1980 step 10).toSet(), kinds = setOf(MediaKind.MOVIE)),
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
        "family" to setOf("Family", "The Living Room"),
        "80s" to setOf("80s Movies"),
        "90s" to setOf("90s Movies"),
        "saturday" to setOf("Saturday Morning"),
    )
}
