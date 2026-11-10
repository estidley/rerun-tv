package com.rerun.core.catalog

/**
 * Shows that aired on block-era channels whose Plex network points somewhere else
 * (Dragon Ball Z → Fuji TV, Scooby-Doo → CBS). Matched by title, ignoring case,
 * punctuation and a leading "The". Editable on the TV.
 */
object Curated {
    val pbsShows: List<String> = listOf(
        "Austin City Limits", "Masterpiece", "Masterpiece Theatre", "Masterpiece Theater", "Great Performances",
        "Agatha Christie's Poirot", "Poirot", "Agatha Christie's Miss Marple", "Miss Marple", "Sherlock", "Sherlock Holmes",
        "Inspector Morse", "Morse", "Inspector Lewis", "Endeavour", "Downton Abbey", "Victoria", "Poldark", "Wolf Hall",
        "NOVA", "Nature", "Frontline", "American Experience", "American Masters", "Antiques Roadshow", "PBS NewsHour",
        "Washington Week", "The French Chef", "Julia Child", "This Old House", "Ask This Old House", "MotorWeek",
    )

    val pbsKidsShows: List<String> = listOf(
        "Sesame Street", "Mister Rogers' Neighborhood", "Arthur", "Curious George", "Daniel Tiger's Neighborhood",
        "Wild Kratts", "Odd Squad", "Cyberchase", "WordGirl", "Super Why!", "Clifford the Big Red Dog", "Dragon Tales",
        "The Electric Company", "Zoom", "Reading Rainbow", "Wishbone", "Bill Nye the Science Guy",
    )

    val boomerangShows: List<String> = listOf(
        "Scooby-Doo, Where Are You!", "The Scooby-Doo Show", "The New Scooby-Doo Movies",
        "Scooby-Doo and Scrappy-Doo", "The 13 Ghosts of Scooby-Doo", "A Pup Named Scooby-Doo",
        "The Flintstones", "The Jetsons", "Looney Tunes", "The Looney Tunes Show",
        "Merrie Melodies", "The Bugs Bunny Show", "Tom and Jerry", "The Tom and Jerry Show",
        "The Yogi Bear Show", "Huckleberry Hound", "The Huckleberry Hound Show", "Top Cat",
        "Wacky Races", "Jonny Quest", "Space Ghost", "Space Ghost Coast to Coast",
        "The Pink Panther Show", "Pink Panther", "Popeye the Sailor", "Popeye",
        "Dexter's Laboratory", "Johnny Bravo", "The Smurfs", "Captain Caveman",
        "Josie and the Pussycats", "The Magilla Gorilla Show", "Quick Draw McGraw",
        "The Atom Ant Show", "Dastardly and Muttley in Their Flying Machines", "The Banana Splits",
        "Hong Kong Phooey", "Jabberjaw", "Speed Buggy", "The Herculoids", "Birdman and the Galaxy Trio",
        "Laff-A-Lympics", "Snagglepuss", "Wally Gator", "The Funky Phantom", "Inch High, Private Eye",
    )

    val toonamiShows: List<String> = listOf(
        "Dragon Ball", "Dragon Ball Z", "Dragon Ball GT", "Dragon Ball Super", "Dragon Ball Z Kai",
        "Sailor Moon", "Mobile Suit Gundam Wing", "Mobile Suit Gundam 0080: War in the Pocket",
        "Mobile Suit Gundam", "Mobile Fighter G Gundam", "G Gundam",
        "Tenchi Muyo!", "Tenchi Universe", "Outlaw Star", "Yu Yu Hakusho", "Rurouni Kenshin",
        "Cowboy Bebop", "The Big O", "Zoids", "Zoids: Chaotic Century", "Blue Submarine No. 6",
        "Ronin Warriors", "ThunderCats", "Voltron", "Voltron: Defender of the Universe",
        "ReBoot", "Beast Wars", "Beast Wars: Transformers", "Robotech", "Naruto", "One Piece",
        "Bleach", "Hikaru no Go", "IGPX", "Samurai Jack", "Teen Titans", "Megas XLR",
        "Duck Dodgers", "Inuyasha", "Fullmetal Alchemist", "Fullmetal Alchemist: Brotherhood",
        "Gurren Lagann", "Space Dandy", "Attack on Titan", "Sword Art Online", "Black Clover",
        "My Hero Academia",
    )
}
