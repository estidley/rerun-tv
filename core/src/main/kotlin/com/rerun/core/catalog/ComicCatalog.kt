package com.rerun.core.catalog

import com.rerun.core.model.ChannelDef
import com.rerun.core.model.ChannelKind
import com.rerun.core.model.MediaItem
import com.rerun.core.model.MediaKind
import com.rerun.core.model.PlayOrder
import com.rerun.core.model.Rules
import com.rerun.core.model.titleKey

/** Franchise membership is independent of the network that aired a title. */
object ComicCatalog {
    const val MARVEL = "marvel"
    const val DC = "dc"

    val channels: List<ChannelDef> = listOf(
        ChannelDef("marvel:tv", 380, "Marvelous TV", ChannelKind.THEME,
            Rules(franchises = setOf(MARVEL), kinds = setOf(MediaKind.EPISODE))),
        ChannelDef("marvel:movies", 381, "Marvelous Movies", ChannelKind.THEME,
            Rules(franchises = setOf(MARVEL), kinds = setOf(MediaKind.MOVIE))),
        ChannelDef("marvel:timeline", 382, "Marvelous Timeline", ChannelKind.THEME,
            Rules(franchises = setOf(MARVEL)), order = PlayOrder.CHRONOLOGICAL),
        ChannelDef("dc:tv", 390, "Detective Club TV", ChannelKind.THEME,
            Rules(franchises = setOf(DC), kinds = setOf(MediaKind.EPISODE))),
        ChannelDef("dc:movies", 391, "Detective Pictures", ChannelKind.THEME,
            Rules(franchises = setOf(DC), kinds = setOf(MediaKind.MOVIE))),
    )

    private data class Entry(
        val title: String, val kind: MediaKind, val year: Int?, val universe: String,
        val position: Int, val season: Int? = null,
    ) {
        val key = key(title)
    }

    private fun key(title: String): String = titleKey(java.text.Normalizer.normalize(title, java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").trim()
        .replace(Regex("(?i)^marvel(?:['’]s)?\\s+"), "")
        .replace(Regex("(?i)^studios?\\s+"), "")
        .replace(Regex("(?i)^one[- ]shot:\\s*"), ""))

    // The user's selected source, verified 2026-10-25. TV seasons have independent positions.
    // https://www.marvel.com/articles/movies/mcu-timeline-order-disney-plus
    // Format: movie release year (0 for a series), title, optional season.
    private val mcu = """
        0|Eyes of Wakanda
        2011|Captain America: The First Avenger
        2013|Agent Carter
        2019|Captain Marvel
        2008|Iron Man
        2010|Iron Man 2
        2008|The Incredible Hulk
        2011|A Funny Thing Happened on the Way to Thor's Hammer
        2011|Thor
        2011|The Consultant
        2012|The Avengers
        2012|Item 47
        2013|Thor: The Dark World
        2013|Iron Man 3
        2014|All Hail the King
        2014|Captain America: The Winter Soldier
        2014|Guardians of the Galaxy
        2017|Guardians of the Galaxy Vol. 2
        0|I Am Groot|1
        0|I Am Groot|2
        0|Daredevil|1
        0|Jessica Jones|1
        2015|Avengers: Age of Ultron
        2015|Ant-Man
        0|Daredevil|2
        0|Luke Cage|1
        0|Iron Fist|1
        0|The Defenders
        2016|Captain America: Civil War
        2021|Black Widow
        2018|Black Panther
        2017|Spider-Man: Homecoming
        0|The Punisher|1
        2016|Doctor Strange
        0|Jessica Jones|2
        0|Luke Cage|2
        0|Iron Fist|2
        0|Daredevil|3
        2017|Thor: Ragnarok
        0|The Punisher|2
        0|Jessica Jones|3
        2018|Ant-Man and the Wasp
        2018|Avengers: Infinity War
        2019|Avengers: Endgame
        0|Loki|1
        0|What If...?|1
        0|Marvel Zombies
        0|WandaVision
        2021|Shang-Chi and the Legend of the Ten Rings
        0|The Falcon and the Winter Soldier
        2019|Spider-Man: Far From Home
        2021|Eternals
        2021|Spider-Man: No Way Home
        2022|Doctor Strange in the Multiverse of Madness
        0|Hawkeye
        0|Moon Knight
        2022|Black Panther: Wakanda Forever
        0|Echo
        0|She-Hulk: Attorney at Law
        0|Ms. Marvel
        2022|Thor: Love and Thunder
        0|Ironheart
        2022|Werewolf by Night
        2022|The Guardians of the Galaxy Holiday Special
        2023|Ant-Man and the Wasp: Quantumania
        2023|Guardians of the Galaxy Vol. 3
        0|Secret Invasion
        2023|The Marvels
        0|Loki|2
        0|What If...?|2
        2024|Deadpool & Wolverine
        0|Agatha All Along
        0|What If...?|3
        0|Daredevil: Born Again|1
        2025|Captain America: Brave New World
        2025|Thunderbolts*
        2025|The Fantastic Four: First Steps
        0|Wonder Man
        0|Daredevil: Born Again|2
        2026|The Punisher: One Last Kill
        0|VisionQuest
    """.trimIndent().lines().mapIndexed { index, line ->
        val fields = line.split('|')
        val year = fields[0].toInt()
        Entry(fields[1], if (year == 0) MediaKind.EPISODE else MediaKind.MOVIE,
            year.takeIf { it > 0 }, "00-mcu", index, fields.getOrNull(2)?.toInt())
    }

    // Other continuities follow the MCU as separate groups. Time-travel films use one whole-film
    // placement; this is an editorial viewing order, not a claim of one canonical multiverse date.
    private val legacyMarvel = listOf(
        movies("10-xmen", "2011|X-Men: First Class", "2014|X-Men: Days of Future Past",
            "2009|X-Men Origins: Wolverine", "2016|X-Men: Apocalypse", "2019|Dark Phoenix",
            "2000|X-Men", "2003|X2", "2006|X-Men: The Last Stand", "2013|The Wolverine",
            "2016|Deadpool", "2018|Deadpool 2", "2020|The New Mutants", "2017|Logan"),
        movies("20-raimi-spider-man", "2002|Spider-Man", "2004|Spider-Man 2", "2007|Spider-Man 3"),
        movies("21-amazing-spider-man", "2012|The Amazing Spider-Man", "2014|The Amazing Spider-Man 2"),
        movies("22-sony-spider-universe", "2024|Madame Web", "2018|Venom", "2021|Venom: Let There Be Carnage",
            "2022|Morbius", "2024|Venom: The Last Dance", "2024|Kraven the Hunter"),
        movies("23-spider-verse", "2018|Spider-Man: Into the Spider-Verse", "2023|Spider-Man: Across the Spider-Verse"),
        movies("30-fantastic-four", "2005|Fantastic Four", "2007|Fantastic Four: Rise of the Silver Surfer"),
        movies("31-fantastic-four-2015", "2015|Fantastic Four"),
        movies("32-fantastic-four-1994", "1994|The Fantastic Four"),
        movies("40-blade", "1998|Blade", "2002|Blade II", "2004|Blade: Trinity"),
        movies("41-ghost-rider", "2007|Ghost Rider", "2011|Ghost Rider: Spirit of Vengeance"),
        movies("42-daredevil", "2003|Daredevil", "2005|Elektra"),
        movies("43-punisher-1989", "1989|The Punisher"),
        movies("44-punisher-2004", "2004|The Punisher"),
        movies("45-punisher-2008", "2008|Punisher: War Zone"),
        movies("46-hulk-2003", "2003|Hulk"),
        movies("47-howard", "1986|Howard the Duck"),
        movies("47a-captain-america-1990", "1990|Captain America"),
        movies("47b-captain-america-1979", "1979|Captain America", "1979|Captain America II: Death Too Soon"),
        movies("47c-doctor-strange-1978", "1978|Dr. Strange"),
        movies("47d-nick-fury", "1998|Nick Fury: Agent of S.H.I.E.L.D."),
        movies("47e-man-thing", "2005|Man-Thing"),
        movies("48-big-hero-6", "2014|Big Hero 6"),
        movies("49a-ultimate-avengers", "2006|Ultimate Avengers", "2006|Ultimate Avengers 2"),
        movies("49b-animated-iron-man", "2007|The Invincible Iron Man"),
        movies("49c-animated-doctor-strange", "2007|Doctor Strange"),
        movies("49d-next-avengers", "2008|Next Avengers: Heroes of Tomorrow"),
        movies("49e-hulk-vs", "2009|Hulk Vs."),
        movies("49f-planet-hulk", "2010|Planet Hulk"),
        movies("49g-thor", "2011|Thor: Tales of Asgard"),
        movies("49h-technovore", "2013|Iron Man: Rise of Technovore"),
        movies("49i-heroes-united", "2013|Iron Man & Hulk: Heroes United", "2014|Iron Man & Captain America: Heroes United"),
    ).flatten()

    private fun movies(universe: String, vararg titles: String): List<Entry> = titles.mapIndexed { index, row ->
        val fields = row.split('|', limit = 2)
        Entry(fields[1], MediaKind.MOVIE, fields[0].toInt(), universe, index)
    }

    private val dcMovies = movies("dc", "1978|Superman", "1980|Superman II", "1983|Superman III",
        "1987|Superman IV: The Quest for Peace", "2006|Superman Returns", "2013|Man of Steel",
        "2016|Batman v Superman: Dawn of Justice", "2017|Justice League", "2021|Zack Snyder's Justice League",
        "1989|Batman", "1992|Batman Returns", "1995|Batman Forever", "1997|Batman & Robin",
        "2005|Batman Begins", "2008|The Dark Knight", "2012|The Dark Knight Rises", "2022|The Batman",
        "2017|Wonder Woman", "2020|Wonder Woman 1984", "2018|Aquaman", "2023|Aquaman and the Lost Kingdom",
        "2019|Shazam!", "2023|Shazam! Fury of the Gods", "2016|Suicide Squad", "2021|The Suicide Squad",
        "2020|Birds of Prey", "2022|Black Adam", "2023|The Flash", "2023|Blue Beetle",
        "2019|Joker", "2024|Joker: Folie a Deux", "2009|Watchmen", "2005|Constantine",
        "2006|V for Vendetta", "2011|Green Lantern", "2010|Jonah Hex", "1982|Swamp Thing",
        "1989|The Return of Swamp Thing", "1984|Supergirl", "2004|Catwoman", "2010|The Losers",
        "2002|Road to Perdition", "2005|A History of Violence", "2019|The Kitchen",
        "2022|DC League of Super-Pets", "2025|Superman", "2026|Supergirl",
        "1993|Batman: Mask of the Phantasm", "1998|Batman & Mr. Freeze: SubZero", "2000|Batman Beyond: Return of the Joker",
        "2003|Batman: Mystery of the Batwoman", "2005|The Batman vs. Dracula", "2007|Superman: Doomsday",
        "2008|Justice League: The New Frontier", "2009|Wonder Woman", "2009|Green Lantern: First Flight",
        "2010|Superman/Batman: Apocalypse", "2010|Batman: Under the Red Hood", "2011|All-Star Superman",
        "2011|Green Lantern: Emerald Knights", "2012|Justice League: Doom", "2012|Superman vs. The Elite",
        "2013|Superman: Unbound", "2014|Son of Batman", "2014|Batman: Assault on Arkham",
        "2016|Batman: Bad Blood", "2016|Batman: The Killing Joke", "2017|Batman and Harley Quinn",
        "2018|The Death of Superman", "2019|Reign of the Supermen", "2018|Teen Titans Go! To the Movies",
        "2018|Batman Ninja", "2019|Batman vs. Teenage Mutant Ninja Turtles", "2020|Superman: Red Son",
        "2021|Injustice", "2022|Catwoman: Hunted", "2022|Green Lantern: Beware My Power")

    private val marvelShows = setOf(
        "Agents of S.H.I.E.L.D.", "Agent Carter", "Inhumans", "Runaways", "Cloak & Dagger", "Legion", "The Gifted",
        "Spider-Man", "Spider-Man and His Amazing Friends", "Spider-Man Unlimited", "Spider-Man: The New Animated Series",
        "The Spectacular Spider-Man", "Ultimate Spider-Man", "Your Friendly Neighborhood Spider-Man",
        "Spidey and His Amazing Friends", "X-Men", "X-Men: Evolution", "X-Men '97", "Wolverine and the X-Men",
        "Iron Man", "Iron Man: Armored Adventures", "Fantastic Four", "Fantastic Four: World's Greatest Heroes",
        "The Incredible Hulk", "Hulk and the Agents of S.M.A.S.H.", "The Avengers: Earth's Mightiest Heroes",
        "Avengers Assemble", "The Avengers: United They Stand", "The Super Hero Squad Show", "Silver Surfer",
        "Guardians of the Galaxy", "M.O.D.O.K.", "Hit-Monkey", "Moon Girl and Devil Dinosaur", "Big Hero 6: The Series",
        "Baymax!", "Blade: The Series", "Marvel's Future Avengers", "Marvel Disk Wars: The Avengers",
        "Marvel Super Hero Adventures", "The Marvel Super Heroes", "The Super Hero Squad Show",
    ).mapTo(HashSet(), ::key)
    private val dcShows = setOf(
        "Batman", "Batman: The Animated Series", "The New Batman Adventures", "Batman Beyond", "The Batman",
        "Batman: The Brave and the Bold", "Beware the Batman", "Batman: Caped Crusader", "Batwheels",
        "Superman", "Superman: The Animated Series", "Adventures of Superman", "Lois & Clark: The New Adventures of Superman",
        "Smallville", "Superman & Lois", "My Adventures with Superman", "Krypto the Superdog",
        "Justice League", "Justice League Unlimited", "Justice League Action", "Young Justice",
        "Teen Titans", "Teen Titans Go!", "Titans", "Doom Patrol", "Harley Quinn", "Kite Man: Hell Yeah!",
        "Arrow", "The Flash", "Supergirl", "DC's Legends of Tomorrow", "Legends of Tomorrow", "Batwoman",
        "Black Lightning", "Stargirl", "Gotham", "Pennyworth", "Krypton", "Peacemaker", "Creature Commandos",
        "Watchmen", "The Penguin", "Constantine", "Swamp Thing", "Lucifer", "The Sandman", "Dead Boy Detectives",
        "Sweet Tooth", "iZombie", "Human Target", "Powerless", "Static Shock", "Green Lantern: The Animated Series",
        "Super Friends", "Wonder Woman", "Shazam!", "The New Adventures of Batman", "Aquaman", "Birds of Prey",
    ).mapTo(HashSet(), ::key)

    private val marvelEntries = (mcu + legacyMarvel).groupBy { it.key }
    private val dcEntries = dcMovies.groupBy { it.key }
    private val aliases = mapOf("x2xmenunited" to "x2", "xmen2" to "x2", "xmendarkphoenix" to "darkphoenix",
        "deadpoolandwolverine" to "deadpoolwolverine", "batmanandrobin" to "batmanrobin", "fant4stic" to "fantasticfour",
        "birdsofpreyandthefantabulousemancipationofoneharleyquinn" to "birdsofprey", "dclegendsoftomorrow" to "legendsoftomorrow")
    private fun title(item: MediaItem): String {
        val k = key(if (item.kind == MediaKind.EPISODE) item.showTitle ?: item.title else item.title)
        return aliases[k] ?: k
    }
    private fun entries(item: MediaItem, catalog: Map<String, List<Entry>>): List<Entry> =
        catalog[title(item)].orEmpty().filter { it.kind == item.kind &&
            (item.kind == MediaKind.EPISODE || item.year == null || it.year == item.year) }

    fun matches(item: MediaItem, franchise: String): Boolean {
        if (item.kind == MediaKind.TRACK) return false
        val labels = item.labels + item.collections
        val studio = item.studio?.trim()?.lowercase().orEmpty()
        val tags = labels.map { it.trim().lowercase() }
        return when (franchise.trim().lowercase()) {
            MARVEL -> studio == "marvel" || studio.startsWith("marvel ") ||
                tags.any { it == "marvel" || it == "mcu" || it.startsWith("marvel ") } ||
                entries(item, marvelEntries).isNotEmpty() || (item.kind == MediaKind.EPISODE && title(item) in marvelShows)
            DC -> studio in setOf("dc", "dc comics", "dc entertainment", "dc films", "dc studios", "dc animation") ||
                tags.any { it == "dc" || it == "dcu" || it == "dceu" || it.startsWith("dc ") || it == "detective comics" } ||
                entries(item, dcEntries).isNotEmpty() || (item.kind == MediaKind.EPISODE && title(item) in dcShows) ||
                (item.kind == MediaKind.MOVIE && Regex("(?i)^(batman|superman|justice league|teen titans|suicide squad|wonder woman|green lantern|justice society|constantine|deathstroke|catwoman)[:/]")
                    .containsMatchIn(item.title))
            else -> false
        }
    }

    private data class Position(val universe: String, val order: Int)
    private fun position(item: MediaItem): Position {
        val candidates = entries(item, marvelEntries)
        val ambiguousMovie = item.kind == MediaKind.MOVIE && item.year == null && candidates.map { it.universe }.distinct().size > 1
        val ranked = if (ambiguousMovie) null else candidates.firstOrNull { it.season == item.season }
            ?: candidates.firstOrNull { it.season == null }
        if (ranked != null) return Position(ranked.universe, ranked.position)
        // Unmapped seasons of a known MCU series remain in the MCU group, after published positions.
        if (!ambiguousMovie && candidates.any { it.universe == "00-mcu" }) return Position("00-mcu", 10_000 + (item.year ?: 9999))
        // Animation and unmapped continuities stay grouped by Plex show identity, with season/episode order.
        val group = if (item.kind == MediaKind.EPISODE) "80-series:${title(item)}:${item.showKey.orEmpty()}" else "90-other-marvel"
        return Position(group, if (item.kind == MediaKind.EPISODE) 0 else item.year ?: 9999)
    }

    /** One combined sequence of movies and episodes. Missing library entries simply do not appear. */
    fun ordered(items: List<MediaItem>): List<MediaItem> = items.map { it to position(it) }.sortedWith(
        compareBy<Pair<MediaItem, Position>>({ it.second.universe }, { it.second.order },
            { it.first.season ?: 0 }, { it.first.episode ?: 0 }, { it.first.year ?: 9999 },
            { title(it.first) }, { it.first.key }),
    ).map { it.first }
}
