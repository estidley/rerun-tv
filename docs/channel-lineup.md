# Cable dial: identities, programming and eligibility

The stock dial nods to cable television from the 1970s through the 2010s, rather than
pretending to be a live feed of the original networks. Names are parodies; eligibility
comes from the user's included Plex libraries. A nostalgic name alone never classifies a title.

## Matching and minimum programming

- Network stations match TMDB reference lists first (a show can match multiple networks),
  then the network Plex reports. Owner-family routing applies only when neither matches.
  Their names describe the cable inspiration, **not a mandatory genre**.
- Genre stations match Plex genre tags. Include sources (network, show, genre, collection,
  label or metadata ID) are alternatives; exclusions, years, ratings and media kinds
  narrow that match. Selecting a network and a genre in the editor does not mean AND.
- Curated stations add named series; Cartoon Notwork also has a weekday 4–7 PM action-cartoon block.
- Only eligible items lasting at least one minute count toward ordinary station pools.
  Unknown years do not count toward decades. Music year falls back to album year;
  episode year falls back to show year.
- **Settings > Lineup > Minimum programming** offers Low (**4 hours**), Medium
  (**12 hours**, the default), and High (**24 hours**) for ordinary stations. Special
  station thresholds use whichever is higher: Second Feature starts at **24 hours**, Late
  Seating **48 hours**, TV decades **24 hours**, and music decades **12 hours**. Changing
  the preset rebuilds eligibility from the saved library without another Plex scan.
- Editing a name, number or rules does not implicitly waive that station's minimum.
  **Channels > Edit > Minimum programming** offers an explicit override: no minimum,
  1, 4, 12, 24 or 48 hours, or return to the channel default. No minimum still needs
  a nonempty eligible pool. Stored overrides support whole hours from 0 through 168.
- Music stations require tracks from an included Plex music library regardless of override; there is no separate Music toggle. Clearly spoken-word/non-music genres, including Podcast and Audiobook, do not create stock genre or decade stations. Explicitly edited stations remain preserved.
- In Channels, holding OK on an ordinary station disables or restores it without deleting its schedule. Disabled stations remain hidden.
- Unqualified stock generated stations disappear. Edited ones retain their configuration
  but remain dormant until their effective minimum is met. Built-in network/theme entries
  remain available in Channels while waiting for content.
- Buy Me Maybe intentionally requires only one valid synced ad, including clips under one
  minute. From the Top is a hidden single-episode start-over exception.

Minimums are duration guards, not guarantees of diversity: a small pool could be one long
title. Normal scheduling preserves episode pointers, shuffles movies, and appends rather
than reshuffles locked entries. No extra genre-by-decade cross-product or network requests
are needed for this review; decade qualification retains its one-pass aggregation.

## Network and curated stations

All rows below use the selected minimum-content preset (12 hours by default). Existing custom names/rules are preserved;
only recognized old stock names are replaced automatically. The network editor shows both the parody name and the
real Plex/TMDB network name. Alias matching ignores case and surrounding whitespace; normal TMDB/Plex/reference
precedence still applies.

The Channels list and **Edit > What plays** derive plain-language summaries from the same rules used for playback. New broadcaster aliases work immediately with Plex metadata; their initially empty TMDB reference lists are populated only by a later validated, credential-backed refresh.

Preferred stock numbers group broadcast (101–112), entertainment (120–134), factual/lifestyle (140–146), and kids/animation (160–169). Untouched stock entries migrate into these groups without changing stable IDs or schedules. Renamed, rule-edited, manually renumbered and custom stations retain their configuration; collisions move to the next free number.

| Number | Name | Cable-era nod and actual programming |
|---|---|---|
| 101 | Alphabet | ABC / American Broadcasting Company / ABC Network / ABC-TV; all genres |
| 102 | The Eye | CBS / Columbia Broadcasting System / CBS Network / CBS-TV; all genres |
| 103 | Peacock Place | NBC / National Broadcasting Company / NBC Network / NBC-TV; all genres |
| 104 | The Den | FOX / Fox Broadcasting Company / Fox Network / FOX-TV; all genres |
| 105 | True North | CBC / Canadian Broadcasting Corporation / CBC Television / CBC TV / CBC News Network; all genres |
| 106 | PBJ | PBS; public broadcasting across genres |
| 107 | Auntie | BBC, BBC Television, BBC Two/Three/Four, CBBC and CBeebies; all genres |
| 108 | The One | BBC One / BBC1 and their HD variants; all genres |
| 109 | The Beeb | BBC America / BBC America HD; British programming on U.S. cable, all genres |
| 110 | The Dish | Sky, Sky TV, Sky One, Sky Atlantic, Sky Max, Sky Witness, Sky Showcase, Sky Comedy, Sky Sci-Fi, Sky Documentaries, Sky News and Sky Sports; all genres |
| 111 | Four Sure | Channel 4 / Channel4 / Channel Four / C4, plus E4, More4 and Film4; all genres |
| 112 | Tokyo Tower | Tokyo Broadcasting System / Tokyo Broadcasting Station / Tokyo Broadcasting / TBS Japan / TBS Television; all genres |
| 120 | USB Network | USA Network / USA; general entertainment plus existing streamer/NBCU fallback routing |
| 121 | DNT | TNT; all genres, not an Action-only filter |
| 122 | Ted's TV | TBS / TBS TV / TBS Network / Turner Broadcasting System / Turner Broadcasting; all genres |
| 123 | SFX | FX / FXX / FX Networks and existing Hulu fallback routing; all genres |
| 124 | PMC | AMC; all genres, including later original series |
| 125 | Remote Control | MTV; reality, scripted and other programming, not an invented music-video source |
| 126 | Tragedy Central | Comedy Central network metadata; distinct from the Comedy genre station |
| 127 | XYZ Family | ABC Family / Freeform / Fox Family; all genres |
| 128 | Spork TV | Paramount Network / Spike / Spike TV and existing Paramount-family fallback routing |
| 129 | Sigh-Fi Channel | Syfy / Sci Fi Channel / SciFi / Sci-Fi Channel and existing science-fiction fallback routing |
| 130 | The Backlot | Existing mature Disney-family/live-action fallback routing; no invented network aliases |
| 131 | The Game Room | Game Show Network / GSN; all genres |
| 132 | The Salon | Bravo / Bravo TV; style, reality, conversation and other programming |
| 133 | The Hearth | Hallmark Channel / Hallmark; all genres |
| 134 | HMO | HBO / Cinemax and existing Warner adult fallback routing; all genres |
| 140 | Recovery Channel | Discovery / Discovery Channel and existing documentary fallback routing |
| 141 | Hysteria Channel | History / History Channel / The History Channel and existing historical-documentary fallback routing |
| 142 | The Atlas | National Geographic / National Geographic Channel / Nat Geo / NatGeo / NGC; exploration and other programming |
| 143 | The Wild | Animal Planet; animal, nature and other programming |
| 144 | The Learning Lounge | TLC; learning, lifestyle and other programming |
| 145 | Feud Network | Food Network; cooking and other programming |
| 146 | GGTV | HGTV; home, garden and other programming |
| 160 | Dinky Channel | Disney Channel / Toon Disney and existing Disney-family fallback routing |
| 161 | Slimelodeon | Nickelodeon / Nicktoons / Nick / TeenNick and existing family fallback routing |
| 162 | Cartoon Notwork | Cartoon Network / CN, plus the weekday 4–7 PM curated action-cartoon block |
| 163 | Kaboomerang | Boomerang network metadata or the curated classic-series list |
| 164 | Toonuki | Anime-tagged movies and episodes, regardless of network, title or rating; animation alone does not qualify |
| 165 | PBJ Kids | PBS Kids and existing preschool fallback routing |
| 166 | Dinky XD | Disney XD / Jetix and existing family/action fallback routing |
| 167 | Playhouse Dinky | Disney Junior / Disney Jr. / Playhouse Disney and existing preschool fallback routing |
| 168 | Slime Jr. | Nick Jr. / Nick Jr and existing preschool fallback routing |
| 169 | Adult Whim | Adult Swim / [adult swim] and existing adult-animation fallback routing |

Raw **TBS** remains assigned to Ted's TV for compatibility with the existing U.S. reference and Plex libraries.
Tokyo Tower deliberately does not claim that ambiguous acronym; use Tokyo Broadcasting System, TBS Japan or
TBS Television metadata for Japanese content. The two definitions have separate stable IDs and schedules.

Network metadata is not a content-rating guarantee. Existing kids-safe markers do not
automatically screen every title or ad; the family/kids theme rules below have explicit
rating filters. Review Plex tags, channel rules and the ad library for household suitability.

## Genre and movie-era stations

These names describe actual genre/year rules, rather than a network association.
All use the selected minimum-content preset (12 hours by default).

| Number | Name | Programming and nostalgic cue |
|---|---|---|
| 301 | Fright Night | Horror or Thriller; late-night creature features |
| 302 | The Void | Science Fiction or Sci-Fi & Fantasy; speculative cable marathons |
| 303 | Laugh Track | **Comedy genre only**, across networks/studios; sitcom/comedy cable atmosphere |
| 304 | Adrenaline | Action or Adventure; weekend action blocks |
| 305 | living room | Family or Animation, with G/PG and TV-Y through TV-PG family ratings |
| 306 | Neon Decade | 1980s movies; neon/VHS-era film nights |
| 307 | Dial-Up Cinema | 1990s movies; modem-era movie nights |
| 308 | Cereal Hour | Animated episodes rated TV-Y, TV-Y7, TV-Y7-FV or TV-G; Saturday-morning nostalgia |
| 363 | The Film Vault | Movies released before 1990; classic-film vault and Turner Classic Movies-style atmosphere |

The former TBS name **Laugh Track** moves to the existing Comedy station, formerly
**Laughing Stock**. TBS keeps ID `tbs`/number 122 and its existing schedule as **Ted's TV**.
Comedy keeps ID `comedy`/number 303 and its genre rules. The documentary *A Brief History
of Time* may therefore remain on Ted's TV when its studio is TBS; it cannot qualify for
the default Laugh Track unless Plex also tags it Comedy.

## Content-qualified extra themes

Each appears at the selected minimum of eligible video, unless explicitly overridden on a saved
definition. These retain their current names and targeted rules; adding more overlapping
stock stations would increase schedule/storage costs without necessarily improving variety.

| Number | Name | Plex genre/filter and cable nod |
|---|---|---|
| 320 | The Matinee | Drama; daytime movie slots |
| 321 | Case File | Crime; procedural marathons |
| 322 | Whodunit | Mystery; detective reruns |
| 323 | Heartline | Romance; relationship/movie cable |
| 324 | True-ish | Documentary or Docuseries; factual cable with a wink |
| 325 | High Noon | Western; classic western blocks |
| 326 | The Front | War or War & Politics; military-history atmosphere |
| 327 | The Overture | Musical or Music videos in movie/episode libraries, not audio tracks |
| 329 | Unscripted | Reality; 2000s cable evolution |
| 330 | The Arena | Sport or Sports; sports-programming atmosphere, not live sports |
| 331 | This Just In | News; rolling-news atmosphere, not a live news feed |
| 332 | Playroom | Children or Kids, with the explicit family-rating allowlist |
| 333 | Canned Laughter | Sitcom or Situation Comedy episodes; rerun sitcom strips |
| 334 | Sob Story | Dramedy, Comedy-drama or Comedy Drama; comedy/drama crossover |
| 335 | Cold Read | Psychic or Paranormal; supernatural-reality cable |

## Language and unmatched stations

Plex original-language/primary-audio metadata is normalized to language codes and inherited from shows
when an episode has no value. Missing-language markers such as `und`, `zxx`, `Unknown`, and `N/A` fall through
to valid audio or show metadata instead of creating stations. Qualifying libraries can generate Mundo (Spanish),
Seoul (Korean), Tokyo (Japanese), Bombay (Hindi), Berlin (German), and localized stations for other languages.
Language stations start at 4000, use stable `language:<code>` IDs, and follow the selected content minimum.

**The Rest** (1999, before genre music at 2000+, stable ID `catchall`) is the unmatched-video catchall. Its persisted `unmatchedOnly` rule
excludes items covered by stock/generated channels, including decade and language stations qualifying at the selected content minimum. Refresh
revalidates its future bookings against that pool, rebuilding after any invalid entry while preserving the currently airing entry; unchanged bookings
remain locked. Short or empty pools never activate. Language/catchall fields decode to empty/false on old channel JSON; the explicit Room
3→4 migration preserves existing items with null language until the next Plex sync.

## Other generated stations

| Station family | Default minimum | Configuration |
|---|---|---|
| The Big Picture / Second Feature / Late Seating (360–362) | Selected preset / at least 24 / at least 48 hours | Same movie library, independent shuffled schedules; premium-movie multiplex nod |
| Genre music (2000+) | Selected preset (4 / 12 / 24 hours) | One station per qualifying music genre; cable audio-tier atmosphere |
| Decade TV (1100+) | 24 hours | Episodes by decade, alongside genre stations |
| Decade music (3000+) | 12 hours on Low/Medium; 24 on High | Tracks by decade; hourly guide blocks rather than song cells |
| Buy Me Maybe (preferred 998) | One valid synced ad | Commercials-only novelty station; back-to-back clips, hourly guide cells |
| From the Top (reserved 999) | One movie or episode, optionally followed by the remaining episodes of its show | Hidden device-local start-over station; movies require confirmation |

Decades span 1900s–2090s based on library years, including the requested 1970s–2010s cable
eras. Preferred numbers resolve collisions without changing IDs. No live channels, new
streaming sources, or expanded genre combinations were added as part of this catalog review.
