# Rerun Phase 1 (Core TV) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A sideloadable Fire TV app that signs into Plex, builds network/curated/theme/custom channels from the library, keeps a locked 48h schedule, and tunes in mid-program with static, info banner, guide and Up Next filler, already wearing its full retro CRT look so testing happens on the real look and feel.

**Architecture:** Two Gradle modules.
- `:core` is pure Kotlin (JVM) and holds every decision that can be tested: models, catalog, resolver, scheduler, tune math, Plex JSON parsing, playback policy.
- `:app` is the Android shell: HTTP, Room, Media3 player, Compose UI.

`:app` only calls `:core` through the interfaces below.

**Tech Stack:** Kotlin 2.x, Jetpack Compose (no Material; `androidx.tv` not required), Media3 ExoPlayer 1.4+, Room 2.6+, OkHttp 4, kotlinx-serialization-json (tree API only, no compiler plugin), Gradle 8.x/AGP 8.x.

**Spec:** `docs/superpowers/specs/2026-10-23-rerun-design.md`

## Global Constraints

- Package `com.rerun.tv` (app), `com.rerun.core` (core). App label "Rerun".
- minSdk 25, targetSdk 34. Leanback launcher intent + 320×180 banner (shows in Fire TV "Your Apps").
- No Google Play Services. Fonts are bundled in `res/font`.
- Never send Plex timeline or scrobble requests.
- Program slots round up to the next :00/:30 (`GRID_MS = 30 min`). Schedule horizon 48h; top up when < 24h remains; written entries are never modified.
- Channel is dormant when its pool totals < 4h (`DORMANT_MS = 4h`).
- Number ranges: network/curated 100–199, theme 300–399, custom 400+.
- Tune-in: a program with < 5s remaining is skipped to the next entry.
- Items with `durationMs < 60_000` are excluded from all pools.
- Time zone for time blocks: device zone (`ZoneId.systemDefault()`); entry times are UTC epoch ms.
- Tests in `:core` use only `kotlin.test` (`@Test`, `assertEquals`, `assertTrue`, `assertNull`, `assertFailsWith`).

## Review Focus

1. **TV wakes after days asleep** (now is past the schedule horizon): tuning must top up first and then play. Test in Task 6.
2. **Item deleted from Plex while scheduled**: that slot plays the Up Next card instead of erroring. Test in Task 9 (`TunerEngine` fallback) via `PlaybackPlanner` in Task 7.
3. **Plex item with missing or zero duration** must never enter a pool or stall a schedule. Test in Task 3.
4. **DST change inside a time block** (block 21:00–03:00 across the fall-back night): the block applies by local wall-clock time. Test in Task 5.
5. **Two channels given the same number** by the user: the save is rejected with a message, never silently duplicated. Test in Task 3 (`Catalog.validateNumbers`).

---

## File structure

```
settings.gradle.kts, build.gradle.kts, gradle/libs.versions.toml
core/build.gradle.kts
core/src/main/kotlin/com/rerun/core/
  model/Media.kt            MediaItem, MediaKind, ShowMeta
  model/Channels.kt         ChannelDef, ChannelKind, Rules, PlayOrder, TimeBlock
  catalog/Catalog.kt        built-in networks, curated lists, themes, alias lookup, number validation
  catalog/Curated.kt        Boomerang + Toonami title lists
  resolve/Resolver.kt       rules → pool, dormant check
  schedule/Schedule.kt      ScheduleEntry, EntryKind, ChannelState
  schedule/Scheduler.kt     extend() — deck shuffle, cyclic shuffle, mix, blocks, padding
  schedule/Tune.kt          locate()
  plex/PlexParse.kt         JSON → pin, servers, sections, shows, items
  plex/Playback.kt          direct-play policy, URL builders
core/src/test/kotlin/com/rerun/core/...   one test file per main file
core/src/test/resources/plex/*.json       fixtures
app/build.gradle.kts, app/src/main/AndroidManifest.xml
app/src/main/java/com/rerun/tv/
  RerunApp.kt               app singleton wiring
  data/Db.kt                Room entities + DAOs (items, channels, schedule, state, settings)
  data/PlexApi.kt           OkHttp calls → PlexParse
  data/LibrarySync.kt       paged sync, enrich episodes with ShowMeta
  data/ScheduleRepo.kt      top-up loop around Scheduler
  player/TunerEngine.kt     tune(), next-entry rollover, error → difficulties
  ui/MainActivity.kt        remote key routing, screen state
  ui/theme/*                colors, VT323 font, OSD styles
  ui/onboarding/*           link code, server pick, sync progress
  ui/player/*               StaticNoise, ChannelOsd, InfoBanner, UpNextCard, DifficultiesCard
  ui/crt/*                  CRT overlay, power on/off, vertical roll
  ui/guide/GuideScreen.kt
  ui/channels/*             ChannelManager, ChannelEditor
  ui/settings/SettingsScreen.kt
app/src/main/res/          banner, icon, font/vt323.ttf, strings
scripts/install.ps1, scripts/restore.ps1, README.md
tools/ktest/               kotlin.test shim + runner for compiling :core without Gradle
```

---

### Task 1: Project scaffold + core test harness

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `core/build.gradle.kts`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `.gitignore`
- Create: `tools/ktest/KotlinTestShim.kt`, `tools/ktest/Runner.kt`, `tools/ktest/run.sh`
- Test: `core/src/test/kotlin/com/rerun/core/SmokeTest.kt`

**Interfaces:**
- Produces: `./gradlew :core:test` (on the developer's PC) and `tools/ktest/run.sh <compiler-jar> <stdlib-jar> <json-jars...>` (in this workspace) both run every `@Test` in `core/src/test`.

- [ ] **Step 1:** Write `SmokeTest.smoke()` asserting `assertEquals(2, 1 + 1)`.
- [ ] **Step 2:** Write the shim: package `kotlin.test` with `annotation class Test`, plus `assertEquals`, `assertTrue`, `assertFalse`, `assertNull`, `assertNotNull`, `assertFailsWith`, `fail`. `Runner.kt` scans compiled classes for `@Test` methods, runs each, and prints `PASS`/`FAIL name: message`. It exits non-zero on any failure. `run.sh` compiles with `-Xallow-kotlin-package`, including `core/src/test/resources` on the classpath.
- [ ] **Step 3:** Run `tools/ktest/run.sh …`. Expected: `1 passed, 0 failed`.
- [ ] **Step 4:** Gradle files:
  - `:core` is `kotlin("jvm")` with `kotlin("test")` for tests.
  - `:app` is `com.android.application` with Compose enabled, depending on `:core`.
  - The manifest has `LEANBACK_LAUNCHER`, `android.software.leanback` required=false, `touchscreen` required=false, the INTERNET permission, and `android:banner`.
- [ ] **Step 5:** Commit `chore: scaffold rerun modules and core test harness`.

### Task 2: Core models

**Files:**
- Create: `core/.../model/Media.kt`, `core/.../model/Channels.kt`
- Test: `core/.../model/RulesTest.kt`

**Interfaces:**
- Produces:
  - `enum class MediaKind { MOVIE, EPISODE }`
  - `data class MediaItem(val key: String, val kind: MediaKind, val title: String, val year: Int?, val durationMs: Long, val genres: Set<String>, val network: String?, val studio: String?, val contentRating: String?, val showKey: String?, val showTitle: String?, val season: Int?, val episode: Int?, val tmdbId: String?, val labels: Set<String>, val collections: Set<String>, val partKey: String?, val container: String?, val videoCodec: String?, val audioCodec: String?, val thumb: String?)`
  - `data class ShowMeta(val key: String, val title: String, val year: Int?, val studio: String?, val genres: Set<String>, val contentRating: String?, val tmdbId: String?, val labels: Set<String>, val collections: Set<String>)`
  - `enum class ChannelKind { NETWORK, CURATED, THEME, CUSTOM }`, `enum class PlayOrder { SHUFFLE, IN_ORDER }`
  - `data class Rules(val networks: Set<String> = emptySet(), val titles: Set<String> = emptySet(), val tmdbIds: Set<String> = emptySet(), val labels: Set<String> = emptySet(), val collections: Set<String> = emptySet(), val genres: Set<String> = emptySet(), val excludeGenres: Set<String> = emptySet(), val decades: Set<Int> = emptySet(), val ratings: Set<String> = emptySet(), val kinds: Set<MediaKind> = MediaKind.entries.toSet())` with `fun matches(item: MediaItem): Boolean`
  - `data class TimeBlock(val days: Set<DayOfWeek>, val start: LocalTime, val end: LocalTime, val rules: Rules)` with `fun activeAt(t: ZonedDateTime): Boolean` (handles `end < start` as overnight; an overnight block belongs to the day it starts)
  - `data class ChannelDef(val id: String, val number: Int, val name: String, val kind: ChannelKind, val rules: Rules, val order: PlayOrder = PlayOrder.SHUFFLE, val blocks: List<TimeBlock> = emptyList(), val kidsSafe: Boolean = false, val hidden: Boolean = false, val movieEveryMs: Long = 6 * 3_600_000L)`

`Rules.matches` semantics:
- **Include sources** are `networks`, `titles`, `tmdbIds`, `labels`, `collections`, `genres`. They are **OR**'d together. If none are set, every item passes the include step.
- **Filters** are `excludeGenres`, `decades`, `ratings`, `kinds`. They are **AND**'d.
- All string comparisons are case-insensitive and trimmed.
- For episodes, `titles` matches `showTitle`.
- Decade = `year / 10 * 10`.

- [ ] **Step 1:** Write tests:
  - `includeSourcesAreOred`: a rule with genres {Horror} and networks {Syfy} matches a Syfy comedy and a non-Syfy horror movie.
  - `filtersAreAnded`: genres {Horror} + decades {1980} rejects a 1994 horror.
  - `episodeTitleMatchesShowTitle`
  - `caseInsensitive`
  - `overnightBlockActiveAfterMidnight`: Fri 21:00–03:00 is active Sat 01:30 and inactive Sat 21:30.
- [ ] **Step 2:** Run; expect compile FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run; expect PASS.
- [ ] **Step 5:** Commit `feat(core): media and channel models`.

### Task 3: Catalog + resolver

**Files:**
- Create: `core/.../catalog/Catalog.kt`, `core/.../catalog/Curated.kt`, `core/.../resolve/Resolver.kt`
- Test: `core/.../catalog/CatalogTest.kt`, `core/.../resolve/ResolverTest.kt`

**Interfaces:**
- Consumes: Task 2 models.
- Produces:
  - `data class NetworkDef(val id: String, val name: String, val number: Int, val aliases: List<String>, val kidsSafe: Boolean)`
  - `object Catalog`:
    - `val networks: List<NetworkDef>`
    - `fun canonicalNetwork(raw: String?): String?` (returns `NetworkDef.id`)
    - `fun builtInChannels(): List<ChannelDef>`
    - `fun validateNumbers(defs: List<ChannelDef>): List<String>` (one error message per duplicated number; empty = OK)
  - `object Curated { val boomerangShows: List<String>; val toonamiShows: List<String> }`
  - `object Resolver`:
    - `const val DORMANT_MS = 4 * 3_600_000L`
    - `const val MIN_ITEM_MS = 60_000L`
    - `fun pool(def: ChannelDef, library: List<MediaItem>): List<MediaItem>`
    - `fun isDormant(pool: List<MediaItem>): Boolean`
    - `fun networkOf(item: MediaItem): String?` (canonical id)

Catalog contents:
- **Networks (100s):** Disney Channel 101, Nickelodeon 102, Cartoon Network 103, Boomerang 104, Toonami 105 (CURATED), PBS 106, PBS Kids 107, SYFY 110, USA 111, TNT 112, TBS 113, FX 114, AMC 115, MTV 116, Comedy Central 117, ABC Family/Freeform 118, Discovery 120, History 121, Food Network 122, HGTV 123, Adult Swim 124, Nick Jr. 125, Disney Junior 126.
- **Aliases** include at least:
  - Disney: "Disney Channel", "Disney XD", "Toon Disney"
  - Nickelodeon: "Nickelodeon", "Nicktoons", "Nick"
  - SYFY: "Syfy", "Sci Fi Channel", "SciFi"
  - ABC Family/Freeform: "ABC Family", "Freeform", "Fox Family"
  - Cartoon Network: "Cartoon Network", "CN"
  - Boomerang: "Boomerang"
  - PBS: "PBS"
  - PBS Kids: "PBS Kids", "PBS KIDS"
  - Adult Swim: "Adult Swim", "[adult swim]"
- **Kids-safe:** Disney, Nick, Cartoon Network, Boomerang, Toonami, PBS Kids, Nick Jr., Disney Junior.
- **Boomerang (curated):** title list (Scooby-Doo variants, The Flintstones, The Jetsons, Looney Tunes, Tom and Jerry, Yogi Bear, Huckleberry Hound, Top Cat, Wacky Races, Jonny Quest, Space Ghost, The Pink Panther Show, Popeye, Dexter's Laboratory reruns, Johnny Bravo, The Smurfs, Captain Caveman, Josie and the Pussycats) **plus** network Boomerang.
- **Toonami (curated):** Dragon Ball, Dragon Ball Z, Dragon Ball GT, Sailor Moon, Mobile Suit Gundam Wing, Gundam 0080, Mobile Suit Gundam, G Gundam, Tenchi Muyo!, Tenchi Universe, Outlaw Star, Yu Yu Hakusho, Rurouni Kenshin, Cowboy Bebop, The Big O, Zoids, Blue Submarine No. 6, Ronin Warriors, ThunderCats, Voltron, ReBoot, Beast Wars, Robotech, Naruto, One Piece, Bleach, Hikaru no Go, IGPX, Samurai Jack, Teen Titans, Megas XLR, Duck Dodgers, Inuyasha, Fullmetal Alchemist, Gurren Lagann, Space Dandy, Dragon Ball Super, Attack on Titan, Sword Art Online, Black Clover, My Hero Academia. The list stays conservative; the owner edits it on the TV.
- **Cartoon Network** has a TimeBlock: Mon–Fri 16:00–19:00 → Toonami titles.
- **Themes (300s):** Horror 301 (genres Horror, Thriller), Sci-Fi 302 (Science Fiction, Sci-Fi & Fantasy), Comedy 303, Action 304 (Action, Adventure), Family 305 (Family, Animation; ratings G, PG, TV-Y, TV-Y7, TV-G, TV-PG; kidsSafe), 80s Movies 306 (MOVIE, decade 1980), 90s Movies 307, Saturday Morning 308 (Animation episodes, TV-Y/TV-Y7/TV-G; kidsSafe).

Steps:
- [ ] **Step 1:** Write tests:
  - `aliasLookup`: "Sci Fi Channel" → "syfy"; "Toon Disney" → "disney"; unknown → null.
  - `validateNumbersRejectsDuplicates`: two defs with 301 → one message containing "301".
  - `builtInsHaveUniqueNumbers`
  - `toonamiFindsDbzByTitle`: an episode with `showTitle` "Dragon Ball Z" and network "Fuji TV" lands in the Toonami pool.
  - `boomerangFindsScooby`
  - `poolExcludesShortAndZeroDuration`: items of 0ms and 30s are dropped. *(Review Focus 3)*
  - `dormantUnderFourHours`: 3h59m is dormant; 4h is not.
  - `cartoonNetworkHasToonamiBlock`
- [ ] **Step 2:** Run; FAIL.
- [ ] **Step 3:** Implement. Resolver: `def.rules.matches(item)` plus duration ≥ `MIN_ITEM_MS`. For NETWORK kind, `rules.networks` holds canonical ids compared against `networkOf(item)`. For CURATED, `titles` comes from `Curated` and `networks` holds its own network id.
- [ ] **Step 4:** Run; PASS.
- [ ] **Step 5:** Commit `feat(core): channel catalog, curated Boomerang/Toonami, resolver`.

### Task 4: Schedule model + padding

**Files:**
- Create: `core/.../schedule/Schedule.kt`
- Test: `core/.../schedule/PaddingTest.kt`

**Interfaces:**
- Produces:
  - `enum class EntryKind { PROGRAM, FILLER }`
  - `data class ScheduleEntry(val channelId: String, val startMs: Long, val endMs: Long, val kind: EntryKind, val itemKey: String?)`
  - `data class ChannelState(val seed: Long, val deck: List<String> = emptyList(), val recent: List<String> = emptyList(), val episodePointers: Map<String, Int> = emptyMap(), val msSinceMovie: Long = 0, val rngCalls: Long = 0)`
  - `const val GRID_MS = 30 * 60_000L`
  - `fun gridFloor(ms: Long): Long`
  - `fun gridCeil(ms: Long): Long`

- [ ] **Step 1:** Write tests:
  - `ceilOfExactBoundaryIsItself`
  - `twentyTwoMinuteEpisodePadsToThirty`: start at 10:00 UTC, end at 10:30.
  - `oneHourFortySevenPadsToTwoHours`
  - `floorAlignsToHalfHour`
- [ ] **Step 2:** Run; FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run; PASS.
- [ ] **Step 5:** Commit `feat(core): schedule model and grid padding`.

### Task 5: Scheduler

**Files:**
- Create: `core/.../schedule/Scheduler.kt`
- Test: `core/.../schedule/SchedulerTest.kt`

**Interfaces:**
- Consumes: Tasks 2–4.
- Produces:
  - `class Scheduler(private val zone: ZoneId)`
  - `data class Extension(val entries: List<ScheduleEntry>, val state: ChannelState)` — new entries only
  - `fun Scheduler.extend(def: ChannelDef, pool: List<MediaItem>, state: ChannelState, lastEndMs: Long?, nowMs: Long, horizonMs: Long = 48 * 3_600_000L): Extension`

Behavior:
- **Start point.** If `lastEndMs` is null or `< gridFloor(nowMs)`, start at `gridFloor(nowMs)`. This is also how a TV waking days later restarts cleanly. Otherwise continue from `lastEndMs`.
- **Loop.** Repeat until the cursor reaches `nowMs + horizonMs`:
  1. Pick the active block at the cursor (first matching `TimeBlock`). The candidate pool is `pool.filter(block.rules::matches)` when a block is active and that filter is non-empty, else `pool`.
  2. Choose movie or episode. If the candidates contain both and `msSinceMovie >= def.movieEveryMs`, take a movie; otherwise take an episode (or whatever kind exists).
  3. **Movies** use a deck. `deck` holds remaining keys. When empty, refill with a seeded shuffle of all candidate movie keys, minus the last `max(1, size/5)` keys in `recent` if that leaves at least one.
  4. **Episodes** use a cyclic shuffle. Pick a show at random, weighted by its episode count among candidates. Play `episodes[pointer % n]` sorted by (season, episode), then advance `episodePointers[showKey]`.
  5. With `PlayOrder.IN_ORDER`: movies go by (year, title); shows go round-robin by title.
  6. Emit a PROGRAM entry `[cursor, cursor+dur)`, then a FILLER entry `[cursor+dur, gridCeil(cursor+dur))` if there's a gap. Advance the cursor and update `recent` (cap 200) and `msSinceMovie`.
- **Randomness.** `java.util.Random(seed + rngCalls)` per draw, incrementing `rngCalls`, so the state alone reproduces the sequence.
- **Empty pool.** Return no entries.

Steps:
- [ ] **Step 1:** Write tests (fixed seed and zone `America/Chicago`):
  - `fillsFortyEightHoursFromGridFloor`: the first start is `gridFloor(now)` and the last end is ≥ now+48h.
  - `entriesAreContiguousAndProgramsStartOnGrid`
  - `deckHasNoRepeatsWithinOneDeck`: 10 movies → the first 10 programs are distinct.
  - `noImmediateRepeatAcrossDeckBoundary`
  - `episodesPlayInOrderPerShow`: S1E1, S1E2, S2E1 order for one show even with another show interleaved.
  - `mixedChannelInsertsMovieAboutEverySixHours`: movie count within 48h is 7–9 for a show-heavy pool.
  - `blockNarrowsPoolDuringWindow`: Cartoon Network Mon 16:00–19:00 Chicago time only schedules Toonami-title episodes.
  - `dstFallBackBlockUsesWallClock`: on 2026-11-01 (fall back), the Sat 21:00–03:00 block covers 01:30 CDT and 01:30 CST. *(Review Focus 4)*
  - `extendContinuesFromLastEnd`: a second call starts exactly at the previous last `endMs` and never returns entries before it.
  - `sameStateSameOutput`
  - `emptyPoolReturnsNothing`
- [ ] **Step 2:** Run; FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run; PASS.
- [ ] **Step 5:** Commit `feat(core): scheduler with deck/cyclic shuffle, blocks, padding`.

### Task 6: Tune math

**Files:**
- Create: `core/.../schedule/Tune.kt`
- Test: `core/.../schedule/TuneTest.kt`

**Interfaces:**
- Produces:
  - `data class Located(val entry: ScheduleEntry, val offsetMs: Long, val next: ScheduleEntry?)`
  - `const val TAIL_SKIP_MS = 5_000L`
  - `fun locate(entries: List<ScheduleEntry>, nowMs: Long): Located?` (entries sorted by start; binary search; a PROGRAM with < `TAIL_SKIP_MS` left yields the next entry at offset 0; null when no entry covers now)
  - `fun needsTopUp(lastEndMs: Long?, nowMs: Long): Boolean` (true when null or `lastEndMs - nowMs < 24h`)

Steps:
- [ ] **Step 1:** Write tests:
  - `midProgramOffset`: an entry starting at 21:00, now 21:47 → offset 47 min.
  - `tailSkip`: 3s left → next entry, offset 0.
  - `fillerIsNotTailSkipped`
  - `nowPastHorizonReturnsNullAndNeedsTopUp` *(Review Focus 1)*
  - `exactBoundaryPicksNewEntry`
- [ ] **Step 2:** Run; FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run; PASS.
- [ ] **Step 5:** Commit `feat(core): tune-in locate and top-up check`.

### Task 7: Plex parsing + playback policy

**Files:**
- Create: `core/.../plex/PlexParse.kt`, `core/.../plex/Playback.kt`, fixtures `core/src/test/resources/plex/{pin,resources,sections,movies,shows,episodes}.json`
- Test: `core/.../plex/PlexParseTest.kt`, `core/.../plex/PlaybackTest.kt`

**Interfaces:**
- Produces:
  - Data classes:
    - `data class Pin(val id: Long, val code: String, val authToken: String?)`
    - `data class PlexConnection(val uri: String, val local: Boolean, val relay: Boolean)`
    - `data class PlexServer(val name: String, val clientId: String, val accessToken: String?, val connections: List<PlexConnection>)`
    - `data class Section(val key: String, val type: String, val title: String)`
  - `object PlexParse` (input is the raw JSON string; Plex JSON uses the `MediaContainer` root):
    - `fun pin(json: String): Pin`
    - `fun servers(json: String): List<PlexServer>`
    - `fun sections(json: String): List<Section>`
    - `fun movies(json: String): List<MediaItem>`
    - `fun shows(json: String): List<ShowMeta>`
    - `fun episodes(json: String, shows: Map<String, ShowMeta>): List<MediaItem>`
    - `fun totalSize(json: String): Int`
  - `object Playback`:
    - `fun canDirectPlay(item: MediaItem): Boolean`
    - `fun directUrl(base: String, item: MediaItem, token: String): String`
    - `fun transcodeUrl(base: String, item: MediaItem, token: String, clientId: String, session: String, offsetMs: Long): String`
    - `fun connectionOrder(server: PlexServer): List<PlexConnection>` (local non-relay → remote non-relay → relay)
  - `sealed interface PlaybackPlan { data class Direct(val url: String, val startMs: Long); data class Transcoded(val url: String); data class UpNext(val reason: String) }`
  - `fun planPlayback(item: MediaItem?, base: String, token: String, clientId: String, session: String, offsetMs: Long): PlaybackPlan` (null item, i.e. deleted from Plex, → `UpNext("missing")`; *Review Focus 2*)

Parsing rules:
- **Movies:** `Genre[].tag`, `Label[].tag`, `Collection[].tag`, `studio`, `contentRating`, `duration`, `Guid[].id` (`tmdb://123` → "123"), `Media[0].container/videoCodec/audioCodec`, `Media[0].Part[0].key`.
- **Episodes:**
  - `grandparentRatingKey` → `showKey`, `grandparentTitle` → `showTitle`, `parentIndex` → season, `index` → episode.
  - Network, genres, labels and collections come from `ShowMeta`. Network is the show's `studio`, run through `Catalog.canonicalNetwork` later in the Resolver.
  - `tmdbId` is the show's.
- **Direct play** if:
  - container ∈ {mkv, mp4, m4v, mov, ts}
  - video ∈ {h264, hevc, mpeg2video, vp9, av1}
  - audio ∈ {aac, ac3, eac3, mp3, opus, flac, vorbis}
- **Transcode URL:** `{base}/video/:/transcode/universal/start.m3u8?path=%2Flibrary%2Fmetadata%2F{key}&mediaIndex=0&partIndex=0&protocol=hls&offset={sec}&fastSeek=1&directPlay=0&directStream=1&directStreamAudio=0&videoResolution=1920x1080&maxVideoBitrate=20000&subtitles=none&session={session}&X-Plex-Session-Identifier={session}&X-Plex-Client-Identifier={clientId}&X-Plex-Product=Rerun&X-Plex-Platform=Android&X-Plex-Token={token}`. The offset is in the URL, so the player starts at 0 for transcodes.

Steps:
- [ ] **Step 1:** Write fixtures (hand-written, shaped like real PMS responses) and tests:
  - `parsesPinToken`
  - `serverConnectionsLocalFirst`
  - `movieFieldsAndTmdbGuid`
  - `episodeInheritsShowNetworkAndGenres`
  - `totalSizeReadsContainer`
  - `dtsForcesTranscode`
  - `h264AacDirectPlays`
  - `transcodeUrlCarriesOffsetSeconds`
  - `missingItemPlansUpNext`
- [ ] **Step 2:** Run; FAIL.
- [ ] **Step 3:** Implement with `kotlinx.serialization.json.Json.parseToJsonElement` (tree walking; missing fields → null/empty).
- [ ] **Step 4:** Run; PASS.
- [ ] **Step 5:** Commit `feat(core): plex json parsing and playback policy`.

### Task 8: Android data layer

**Files:**
- Create: `app/.../RerunApp.kt`, `data/Db.kt`, `data/PlexApi.kt`, `data/LibrarySync.kt`, `data/ScheduleRepo.kt`

**Interfaces:**
- Consumes: Tasks 2–7.
- Produces:
  - `class PlexApi(clientId: String, http: OkHttpClient)`:
    - `suspend fun createPin(): Pin` (POST `https://plex.tv/api/v2/pins?strong=false`)
    - `suspend fun checkPin(id: Long): Pin`
    - `suspend fun servers(token: String): List<PlexServer>` (GET `https://plex.tv/api/v2/resources?includeHttps=1&includeRelay=1`)
    - `suspend fun pickConnection(server: PlexServer): String?` (first `connectionOrder` uri answering `/identity` within 3s)
    - `suspend fun get(base: String, path: String, token: String, start: Int? = null, size: Int? = null): String`
  - Every request sends `Accept: application/json`, `X-Plex-Client-Identifier`, `X-Plex-Product: Rerun`, `X-Plex-Platform: Android`, `X-Plex-Device-Name: Rerun TV`.
  - `class LibrarySync(api, db)` with `fun sync(): Flow<SyncProgress>`. It pages 200 at a time via `X-Plex-Container-Start/Size` over `/library/sections/{k}/all?type=1|2|4&includeGuids=1` for movie and show sections, then replaces the `items` table in one transaction.
  - `class ScheduleRepo(db, scheduler)`:
    - `suspend fun ensure(nowMs: Long)` (for every non-dormant channel where `needsTopUp`: pool → `extend` → insert entries + state)
    - `suspend fun entries(channelId: String, fromMs: Long, toMs: Long): List<ScheduleEntry>`
    - `fun activeChannels(): Flow<List<ChannelRow>>`
  - Room entities:
    - `ItemRow` (MediaItem + JSON for sets)
    - `ChannelRow` (ChannelDef as JSON + number, hidden, dormant)
    - `EntryRow` (index on channelId, startMs)
    - `StateRow`
    - `SettingRow` (key/value: token, clientId, serverBase, serverToken)
  - Token is stored in `EncryptedSharedPreferences` if available, else SettingRow.
  - Built-in channels are seeded on first run from `Catalog.builtInChannels()`. Dormancy is recomputed after each sync.

- [ ] **Step 1:** Implement.
- [ ] **Step 2:** Verify on the PC: `./gradlew :app:assembleDebug` succeeds.
- [ ] **Step 3:** Commit `feat(app): plex api, room db, library sync, schedule repo`.

### Task 9: Tuner engine

**Files:**
- Create: `app/.../player/TunerEngine.kt`

**Interfaces:**
- Consumes: `ScheduleRepo`, `locate`, `planPlayback`.
- Produces: `class TunerEngine(context, repo, api)` with:
  - `val state: StateFlow<TunerState>`, where `TunerState(channel: ChannelRow?, located: Located?, item: MediaItem?, nextItem: MediaItem?, phase: Phase)` and `enum Phase { TUNING, PLAYING, UP_NEXT, DIFFICULTIES }`
  - `fun tune(channelId: String)`
  - `fun player(): ExoPlayer`
  - `fun release()`

`tune()` steps:
1. Record `t0`.
2. `repo.ensure(now)`.
3. `locate`.
4. Look up the item.
5. `planPlayback`.
6. Direct: `setMediaItem` + `seekTo(offset + (now - t0))`. Transcoded: the URL already carries the offset. UpNext: phase UP_NEXT until `entry.endMs`.
7. On the first rendered frame → PLAYING.
8. At `entry.endMs` (a Handler post), tune the same channel again.

Errors: player error or HTTP failure → DIFFICULTIES, retry every 15s. Keep-screen-on while PLAYING.

- [ ] **Step 1:** Implement.
- [ ] **Step 2:** Verify on the TV (Task 13 checklist items 1–3).
- [ ] **Step 3:** Commit `feat(app): tuner engine`.

### Task 10: Player UI + remote

**Files:**
- Create: `ui/MainActivity.kt`, `ui/theme/*`, `ui/player/{PlayerScreen,StaticNoise,ChannelOsd,InfoBanner,UpNextCard,DifficultiesCard}.kt`, `res/font/vt323.ttf`

**Interfaces:**
- Consumes: `TunerEngine`.
- Produces: `MainActivity` key routing:

| Key | Player | Guide |
|---|---|---|
| ▲ / ▼ | channel up / down | move |
| ◀ | last channel | earlier |
| ▶ | info | later |
| OK | info | tune |
| Menu | guide | channel options |
| Back | guide | player; second Back exits |

The player renders:
- a `PlayerView` (no controller)
- **StaticNoise:** a 160×90 random-gray bitmap scaled up, regenerated each frame, visible during TUNING. A 150ms white-noise AudioTrack burst on channel change.
- **ChannelOsd:** top-right, green VT323, "103 CN", 3s.
- **InfoBanner:** bottom, 5s. Title, S/E, year, rating, "ends 9:30", "Up next: …".
- **UpNextCard:** poster + countdown.
- **DifficultiesCard:** color bars + "We're experiencing technical difficulties".

- [ ] **Step 1:** Implement.
- [ ] **Step 2:** Verify on the TV: checklist items 1, 2, 4.
- [ ] **Step 3:** Commit `feat(app): player overlays and remote routing`.

### Task 11: Retro look & feel (CRT, transitions, guide styling)

**Files:**
- Create: `ui/crt/{CrtOverlay,CrtSettings,PowerTransition,VerticalRoll}.kt`, `ui/theme/RetroPalette.kt`
- Modify: `ui/player/PlayerScreen.kt` (stack the overlay above video and OSD), `ui/player/StaticNoise.kt` (add vertical roll)

**Interfaces:**
- Consumes: `TunerState.phase` from Task 9; the settings store from Task 8 (`SettingRow` key `crt_level`).
- Produces:
  - `enum class CrtLevel { OFF, SUBTLE, FULL }`
  - `@Composable fun CrtOverlay(level: CrtLevel)`, drawn over **everything** (video, OSD, guide, menus), so the whole app looks like it's on a tube.
  - `@Composable fun PowerTransition(on: Boolean, onDone: () -> Unit)`
  - `@Composable fun VerticalRoll(active: Boolean)`

| Element | SUBTLE | FULL |
|---|---|---|
| Scanlines (1px dark line every 3px, prebuilt bitmap tiled once) | 12% alpha | 28% alpha |
| Vignette (radial gradient, transparent center → black edges) | 25% at edges | 55% at edges |
| Tube mask (black rounded-corner frame) | radius 24dp | radius 48dp + 1.5% inset |
| Glow (soft white radial bloom at center, additive) | none | 4% |
| RGB fringe on OSD text (red/blue copies offset ±1px) | off | on |

Rules:
- All overlay layers are built once per size change (bitmaps/brushes cached in `remember`). Nothing redraws per frame except `StaticNoise`.
- **Power-on** (app start): black, then a bright horizontal line expands to full height in 350ms, then the brightness settles. **Power-off** (Back from the guide exits): the reverse, collapsing to a line and then a dot, in 300ms.
- **Channel change:** static plus a `VerticalRoll` (the picture slides up ~15% and snaps back over 250ms) while TUNING. A 150ms noise burst.
- **Retro palette/type:**
  - OSD: VCR green `#39FF14` VT323 with a 2px black shadow.
  - Guide: deep blue `#0B1E5B` grid, yellow `#FFD200` channel numbers, white titles, cyan `#00E5FF` now-line. An original 90s cable-guide feel (no copied logos or brand styling).
  - Banner: translucent black bar with beveled top edge.
- Default level: SUBTLE. Settings → "Picture: CRT Off / Subtle / Full". Hold OK on the player for 2s cycles the level live, for quick A/B during testing.

- [ ] **Step 1:** Implement the overlay, transitions and palette, and wire them into `PlayerScreen`, `GuideScreen` and `MainActivity` (power-on at launch, power-off on exit).
- [ ] **Step 2:** Verify on the TV:
  - all three levels are visibly distinct
  - video playback doesn't stutter at FULL (no dropped frames in `adb shell dumpsys gfxinfo com.rerun.tv` over 60s of play)
  - power-on/off and the roll play once each
  - hold-OK cycles the level
- [ ] **Step 3:** Commit `feat(app): retro CRT look, power and channel transitions`.

### Task 12: Onboarding, guide, channel manager/editor, settings

**Files:**
- Create: `ui/onboarding/*`, `ui/guide/GuideScreen.kt`, `ui/channels/{ChannelManager,ChannelEditor}.kt`, `ui/settings/SettingsScreen.kt`

**Interfaces:**
- Consumes: `PlexApi`, `LibrarySync`, `ScheduleRepo`, `Catalog.validateNumbers`.

Screens:
- **Onboarding:**
  1. Big 4-character code + "Go to plex.tv/link". Poll `checkPin` every 2s.
  2. Server list (auto-pick if only one).
  3. Sync progress.
  4. Tune to the lowest active channel.
- **Guide:**
  - Rows: active channels by number. Columns: 30 min, 3h visible.
  - Cells: title (episode titles as "Show — S1E2"). Filler cells merge into the previous program visually.
  - Now-line. Focused-cell detail on top.
- **Channel manager:**
  - Active list, then "Dormant" (grayed, with "needs 4h of content").
  - Per row: hide, renumber, edit.
  - "New channel" opens the editor.
- **Channel editor:**
  - Name and number.
  - Include sources: multi-select from genres, networks, shows and labels/collections found in the library.
  - Filters: decades, ratings, movies/shows.
  - Order: shuffle or in order.
  - Kids-safe toggle.
  - Save runs `Catalog.validateNumbers`. On error, show the message and don't save *(Review Focus 5)*. On success, clear that channel's future entries after the current one and top up.
- **Settings:**
  - Re-sync now.
  - Plex server.
  - Sign out.
  - About (credits + JustWatch note reserved for Phase 4).

- [ ] **Step 1:** Implement.
- [ ] **Step 2:** Verify on the TV: checklist items 5–7.
- [ ] **Step 3:** Commit `feat(app): onboarding, guide, channel manager, settings`.

### Task 13: Install scripts, README, on-TV verification

**Files:**
- Create: `scripts/install.ps1`, `scripts/restore.ps1`, `README.md`

**Interfaces:**
- `install.ps1 -TvIp <ip>`:
  1. `adb connect <ip>:5555`
  2. `./gradlew :app:assembleDebug`
  3. `adb install -r app/build/outputs/apk/debug/app-debug.apk`
  4. `adb shell monkey -p com.rerun.tv 1`
- `restore.ps1 -TvIp <ip>` → `adb uninstall com.rerun.tv`.
- README:
  - Fire TV dev options (ADB debugging on)
  - build requirements (JDK 17, Android SDK 34)
  - first-run steps
  - known limits

On-TV checklist:
1. Tuning lands within ±2s of the guide.
2. Channel change under 2s, with static.
3. A program rolls over to the next with no button press.
4. OK shows the banner.
5. Plex link-code sign-in works.
6. Dormant networks are hidden; adding content + re-sync activates them.
7. A custom channel with a duplicate number is refused.
8. Unplug the Plex server → difficulties card → plug in → recovers.
9. The CRT look is on by default (SUBTLE) on every screen, and its level is remembered across restarts.

- [ ] **Step 1:** Write the scripts and README.
- [ ] **Step 2:** Run the checklist on the TV with the owner.
- [ ] **Step 3:** Commit `chore: install scripts and readme`.

---

Phases 2–5 (HDHomeRun live TV, commercials, streaming channels, idle timer + captions) each get their own plan after Phase 1 is on the TV.
