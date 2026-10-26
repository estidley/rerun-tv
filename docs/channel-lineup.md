# Cable dial: identities, programming and eligibility

The stock dial nods to cable television from the 1970s through the 2010s, rather than
pretending to be a live feed of the original networks. Names are parodies; eligibility
comes from the user's included Plex libraries. A nostalgic name alone never classifies a title.

## Matching and minimum programming

- Network stations match a recognized Plex network, then studio, then the existing
  owner/audience/genre routing fallback. Their names describe the cable inspiration,
  **not a mandatory genre**. Studio-only matches can legitimately include documentaries.
- Genre stations match Plex genre tags. Include sources (network, show, genre, collection,
  label or metadata ID) are alternatives; exclusions, years, ratings and media kinds
  narrow that match. Selecting a network and a genre in the editor does not mean AND.
- Curated stations add named series; Toon Tower also has a weekday 4–7 PM action-cartoon block.
- Only eligible items lasting at least one minute count toward ordinary station pools.
  Unknown years do not count toward decades. Music year falls back to album year;
  episode year falls back to show year.
- All ordinary stations require **4 hours**. Second Feature requires **24 hours** and
  Late Seating **48 hours**. TV decades require **24 hours** and music decades **12 hours**.
- Editing a name, number or rules does not implicitly waive that station's minimum.
  **Channels > Edit > Minimum programming** offers an explicit override: no minimum,
  1, 4, 12, 24 or 48 hours, or return to the channel default. No minimum still needs
  a nonempty eligible pool. Stored overrides support whole hours from 0 through 168.
- Music must remain enabled regardless of override. Hidden stations remain hidden.
- Unqualified stock generated stations disappear. Edited ones retain their configuration
  but remain dormant until their effective minimum is met. Built-in network/theme entries
  remain available in Channels while waiting for content.
- Buy Me Maybe intentionally requires only one valid synced ad, including clips under one
  minute. From the Top is a hidden single-episode start-over exception.

Minimums are duration guards, not guarantees of diversity: four hours could be one long
title. Normal scheduling preserves episode pointers, shuffles movies, and appends rather
than reshuffles locked entries. No extra genre-by-decade cross-product or network requests
are needed for this review; decade qualification retains its one-pass aggregation.

## Network and curated stations

All rows below use the four-hour default. Existing custom names/rules are preserved;
only recognized old stock names are replaced automatically.

| Number | Name | Cable-era nod and actual programming |
|---|---|---|
| 101 | Wonderland | Family cable/fantasy; Disney Channel and Toon Disney metadata plus owner-family routing |
| 102 | Kid Castle | Kids' cable clubhouse; Nickelodeon-family metadata and routing |
| 103 | Toon Tower | All-cartoon cable; Cartoon Network metadata plus the weekday curated action block |
| 104 | The Attic | Rerun cartoon vault; Boomerang metadata or the curated classic-series list |
| 105 | Midnight Block | After-school/late-night action-cartoon blocks; Toonami metadata or curated series |
| 106 | Public Square | Public broadcasting on the cable dial; PBS metadata |
| 107 | Story Yard | Children's public broadcasting; PBS Kids metadata and preschool routing |
| 108 | Action Club | Action-oriented children's cable; Disney XD/Jetix metadata and routing |
| 109 | The Backlot | Studio-library cable; mature Disney-family and live-action franchise routing |
| 110 | Warp Nine | Space-age cable; Syfy metadata and science-fiction routing |
| 111 | Coast to Coast | General entertainment cable; USA metadata and general streamer/NBCU routing |
| 112 | Boomstick | TNT wordplay/action-movie atmosphere; TNT metadata, not an Action-genre restriction |
| 113 | Ted's TV | Ted Turner's superstation heritage; TBS metadata, including non-comedy programming |
| 114 | The Edge | Edgier basic cable; FX/FXX metadata and Hulu routing |
| 115 | The Marquee | Movie-house cable heritage; AMC metadata, including later original series |
| 116 | Remote Control | MTV-era remote/quiz-show nostalgia; MTV metadata, including reality and scripted shows, not an invented music-video source |
| 117 | Punchline | Comedy cable inspiration; Comedy Central metadata/routing, distinct from genre matching |
| 118 | Family Hour | Family-branded cable evolution; ABC Family/Freeform/Fox Family metadata |
| 119 | The Mountain | Studio-peak imagery; Paramount Network/Spike metadata and Paramount-family routing |
| 120 | The Expedition | Documentary/discovery cable atmosphere; Discovery metadata and documentary routing |
| 121 | Yesteryear | History-channel heritage; History metadata and historical-documentary routing |
| 122 | The Kitchen | Cable cooking shows; Food Network metadata |
| 123 | Home Base | Home-and-garden cable; HGTV metadata |
| 124 | After Hours | Late-night animation blocks; Adult Swim metadata and adult-animation routing |
| 125 | Tiny Town | Preschool cable; Nick Jr. metadata/routing |
| 126 | Little Castle | Preschool fantasy cable; Disney Junior/Playhouse Disney metadata/routing |
| 130 | The Premium | Subscription movie-channel atmosphere; HBO/Cinemax metadata and Warner adult routing |

Network metadata is not a content-rating guarantee. Existing kids-safe markers do not
automatically screen every title or ad; the family/kids theme rules below have explicit
rating filters. Review Plex tags, channel rules and the ad library for household suitability.

## Genre and movie-era stations

These names describe actual genre/year rules, rather than a network association.
All use the four-hour default.

| Number | Name | Programming and nostalgic cue |
|---|---|---|
| 301 | Fright Night | Horror or Thriller; late-night creature features |
| 302 | The Void | Science Fiction or Sci-Fi & Fantasy; speculative cable marathons |
| 303 | Laugh Track | **Comedy genre only**, across networks/studios; sitcom/comedy cable atmosphere |
| 304 | Adrenaline | Action or Adventure; weekend action blocks |
| 305 | The Living Room | Family or Animation, with G/PG and TV-Y through TV-PG family ratings |
| 306 | Neon Decade | 1980s movies; neon/VHS-era film nights |
| 307 | Dial-Up Cinema | 1990s movies; modem-era movie nights |
| 308 | Cereal Hour | Animated episodes rated TV-Y, TV-Y7, TV-Y7-FV or TV-G; Saturday-morning nostalgia |

The former TBS name **Laugh Track** moves to the existing Comedy station, formerly
**Laughing Stock**. TBS keeps ID `tbs`/number 113 and its existing schedule as **Ted's TV**.
Comedy keeps ID `comedy`/number 303 and its genre rules. The documentary *A Brief History
of Time* may therefore remain on Ted's TV when its studio is TBS; it cannot qualify for
the default Laugh Track unless Plex also tags it Comedy.

## Content-qualified extra themes

Each appears at four hours of eligible video, unless explicitly overridden on a saved
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

## Other generated stations

| Station family | Default minimum | Configuration |
|---|---|---|
| The Big Picture / Second Feature / Late Seating (360–362) | 4 / 24 / 48 hours | Same movie library, independent shuffled schedules; premium-movie multiplex nod |
| Genre music (2000+) | 4 hours | One station per qualifying music genre; cable audio-tier atmosphere |
| Decade TV (1100+) | 24 hours | Episodes by decade, alongside genre stations |
| Decade music (3000+) | 12 hours | Tracks by decade; hourly guide blocks rather than song cells |
| Buy Me Maybe (preferred 998) | One valid synced ad | Commercials-only novelty station; back-to-back clips, hourly guide cells |
| From the Top (reserved 999) | One selected episode | Hidden one-shot start-over; not an ordinary lineup station |

Decades span 1900s–2090s based on library years, including the requested 1970s–2010s cable
eras. Preferred numbers resolve collisions without changing IDs. No live channels, new
streaming sources, or expanded genre combinations were added as part of this catalog review.
