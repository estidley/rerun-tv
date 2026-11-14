# Rerun

Old-school cable TV, built from your own Plex library. For Fire TV and Android TV.

Turn it on and something's already playing. Flip channels and you land in the middle of whatever's on. There's a 90s cable guide, a CRT tube look, static between channels, and "technical difficulties" when your server naps.

The current Android app version is 1.1.0 (versionCode 7); Settings > About displays the installed app version.

- **Network channels:** Dinky Channel, Dinky XD, Slimelodeon, Cartoon Notwork, Kaboomerang, Toonuki, PBJ Kids, Sigh-Fi Channel, USB Network, HMO, and more. The names on screen are parodies; each channel keeps its real network name behind the scenes. With TMDB lists (below), a show lands on every network it really aired on. Without them, shows land by the network and studio stored in Plex.
- **Streaming originals follow the studio and the audience:**
  - Disney+ kids shows go to Dinky Channel.
  - Marvel and live-action Star Wars go to **The Backlot**.
  - Paramount+ kids shows go to Slimelodeon.
  - Max originals go to HMO.
  - Hulu originals go to SFX.
  - Netflix, Prime Video and Apple TV+ originals are spread by genre.
- **Toonuki (164):** all movies and episodes tagged with the Anime genre in your included Plex libraries, regardless of network, studio, title or age rating. Animation alone does not qualify. Episodes inherit genres from their show; add Anime to the show or movie in Plex and sync Rerun if needed. The selected activation minimum applies (12 hours with the Medium default). Stock Toonuki rules upgrade on refresh; edited content rules are preserved. Old non-anime future entries are rebuilt while the current airing stays locked.
- **Comic channels:** Marvelous TV (380), Marvelous Movies (381), Marvelous Timeline (382), Detective Club TV (390), and Detective Pictures (391). Movies and shows stay in their respective channels; Marvelous Timeline combines both. Each channel uses your included Plex libraries and the selected activation minimum. Occupied numbers move to a free slot. Known adaptations, explicit Marvel/DC production metadata, and Marvel/DC labels or collections identify content; generic Disney/Fox/Sony/Warner ownership or Action/Animation genres alone do not. Label otherwise-unrecognized titles **Marvel** or **DC** in Plex, then sync. DC has separate TV/movie channels without an automatic chronology channel.
- **Marvelous Timeline:** plays the MCU group using the [published Marvel timeline](https://www.marvel.com/articles/movies/mcu-timeline-order-disney-plus), including season-level placements, then other continuities as separate groups (X-Men, the separate Spider-Man series, Sony's universe, Spider-Verse, and other adaptations). It skips missing files and loops after the available sequence finishes. Movies and episodes share one sequence; the normal six-hour movie rotation does not apply. Other-continuity placements are an editorial whole-title watch order, not one official multiverse chronology. Unmapped seasons, stand-alone shows and unclassified Marvel films remain included, grouped by show or in a release-year fallback. Unknown movie years cannot reliably distinguish reboots. Future additions join the next sequence cycle; already-locked programs stay scheduled. Ordinary Marvelous TV/Movies remain shuffled under the usual episode-order policy. Channel editing includes Comic universes and Universe timeline options.
- **Theme channels:** Fright Night, The Void, Laugh Track, Adrenaline, living room, Neon Decade, Dial-Up Cinema, Cereal Hour. Laugh Track uses the Comedy genre; TBS metadata belongs on **Ted's TV**, not a comedy-only station. **Remote Control** is the MTV-inspired network station, including its mixed programming. More themes show up only when that genre meets the selected activation minimum: True-ish (documentaries), Canned Laughter (sitcoms), Sob Story (dramedies), Cold Read (psychic and paranormal), This Just In (news), and others such as The Matinee and Case File. See the [complete cable dial](docs/channel-lineup.md) for every predefined station's name, inspiration, matching rules and minimum.
- **Movie channels:** The Big Picture uses the selected ordinary minimum. Second Feature requires at least 24 hours and Late Seating at least 48 hours; a higher preset applies to them too. They share your movies and shuffle independently.
- **Broadcast and expanded cable:** Alphabet (101/ABC), The Eye (102/CBS), Peacock Place (103/NBC), The Den (104/FOX), True North (105/CBC), PBJ (106/PBS), Auntie (107/BBC), The One (108/BBC One), The Dish (110/Sky), and Four Sure (111/Channel 4) match broad network/alias metadata without a genre restriction. PBJ also carries curated public-broadcast programming such as Austin City Limits, Masterpiece, Poirot and Sherlock, while PBJ Kids (165) keeps its own curated children's pool. When no direct network match exists, television-studio fallbacks route episodes from CBS/Paramount, ABC, Universal and Fox television to their respective broadcast stations without rerouting movies or streaming originals. The Beeb (109) separately matches BBC America. The Film Vault (363) shows pre-1990 movies for a Turner Classic Movies-style library. The Atlas (142), The Wild (143), The Hearth (133), and The Game Room (131) use National Geographic, Animal Planet, Hallmark, and Game Show Network metadata. Tokyo Tower (112) separately matches Tokyo Broadcasting System/TBS Japan, while Ted's TV (122) retains U.S. TBS. The Learning Lounge (144) and The Salon (132) add TLC and Bravo. All have bundled icons and guide wordmarks and use the selected activation minimum.
- **Channel order and criteria:** stock stations are grouped into broadcast (101–112), entertainment (120–134), factual/lifestyle (140–146), and kids/animation (160–169). Untouched stock numbers migrate into these groups without changing stable IDs or schedules; renamed, rule-edited, manually renumbered and custom stations retain their configuration, with collisions moved to free numbers. Channels and Edit > What plays show plain-language matching criteria derived from the actual channel rules; the [complete cable dial](docs/channel-lineup.md) lists exact aliases and filters.
- **Language and catchall stations:** normalized Plex original/primary-audio language metadata can generate Mundo (Spanish), Seoul (Korean), Tokyo (Japanese), Bombay (Hindi), Berlin (German), and localized stations for other languages when they meet the selected minimum. Missing markers such as `und`, `Unknown`, and `N/A` fall through to valid audio/show metadata. The Rest (1999, before music genres) contains only otherwise-unmatched video, excluding stock/generated pools. When a refresh changes that pool, invalid future bookings are rebuilt while the currently airing entry and unchanged schedules stay locked. Existing items keep working through an explicit Room migration and gain language assignments on the next sync.
- **Decade stations:** your included libraries automatically add TV decade channels with at least **24 hours of episodes** and, when music libraries are included, music decade channels with at least **12 hours of tracks**. A higher selected preset raises these thresholds. For example, "1980s TV" and "1990s Music" appear alongside genre stations. TV decade numbers start at 1100; music decades at 3000, with collisions moved to a free number. Years come from Plex episode/track metadata (falling back to show/album year); missing years and clips shorter than a minute do not count. Supported decades are 1900s through 2090s. Movies keep their existing decade/theme channels. Sync, setting changes and the first launch after an app install or update (including a sideloaded reinstall) reconcile eligibility (ordinary launches skip the rebuild; an interrupted setting change forces one on the next launch); unedited stations disappear when their pool falls below the threshold, while edited stations retain their settings and minimum unless explicitly overridden. Music decades follow music-library selection.
- **Custom channels:** pick genres, shows, collections, decades and ratings right on the TV. In Channels, hold OK on an ordinary station to disable it without deleting its schedule; hold OK again on its Disabled entry to restore it. From the Top and Buy Me Maybe retain their dedicated controls.
- **Off air:** a time with nothing scheduled says Off Air in the guide and on the screen, then the next program starts. A short break already on the schedule still says Up Next.
- **Music:** include your Plex music libraries during setup or in Manage libraries; they sync automatically without a separate Music setting. Deselect them to remove stock music stations. Clearly spoken-word/non-music genres such as Podcast, Audiobook, Lecture, Interview, News and Spoken Word do not create stock genre or music-decade stations; explicitly edited stations are preserved. The scan continues when you return to the guide; its progress and elapsed time stay visible until the lineup is ready. Re-syncs also survive navigation between screens. Rerun imports audio tracks, not music videos, and adds a station for each genre that meets the selected activation minimum, with a generated name-specific icon and guide wordmark. Genres inherit from albums/artists; untagged tracks go to Unclassified. The guide groups shuffled songs into hourly station blocks such as "Blues Music"; Off Air gaps remain visible. The music screen shows artist, song, album, square album art, elapsed time and song length, without decorative tick marks underneath. Artwork and text travel slowly across a black background to reduce fixed-image burn-in risk (not a guarantee against burn-in).
- **Subtitles:** off until you turn them on in Settings. **Current program subtitle tracks** opens Off, Automatic and every track Plex reports, with language, title, format, embedded/external, forced and SDH/hearing-impaired labels. Automatic uses Plex's selected track or the first available; a chosen global language stays off when unavailable. Off, Automatic and explicit track choices apply only to the current airing and reset on the next program without changing global preferences, Plex track selections or watch history. **Refresh from Plex** checks for newly downloaded subtitles without a library re-sync and preserves the current airing's Off/Automatic/track choice; unrelated audio/HDR/loudness preference changes also preserve it. Downloading itself still uses Plex's subtitle search or local subtitle files. If Plex reports no tracks, the picker says so. Text size cycles through Small, Medium (default), and Large; color cycles through White (default), Yellow, Cyan, and Green. Text uses a semi-transparent black background. Appearance choices are saved and apply without retuning, overriding embedded text styling; image-based or Plex-burned subtitles cannot be restyled locally. Embedded and external text subtitles play directly; other formats are burned in by Plex.
- **Audio tracks:** choose a preferred audio language in Settings. Automatic uses Plex's selected track; an unavailable language falls back to the default. Hold Right for 2 seconds while watching, or choose Current program audio tracks in Settings, to select a track for this airing only. Multi-track video uses a Plex playback session with an explicit audio stream ID; it does not change saved Plex track selections or watch history.
- **Normalize loudness:** optional and off by default in Settings > Audio. Requests Plex audio normalization for both programs and ads whose selected audio stream supports it, using audio transcoding while allowing video copy. Requires a recent Plex server (1.43.4+), Plex Pass, and **Analyze audio tracks for loudness** under Plex Server > Library, including the commercials library. Music stations are unchanged. Unanalyzed tracks, Dolby Vision, video conversion (including HDR-to-SDR), and selected subtitles (including text) currently skip normalization and show a warning; volume consistency is not guaranteed when either source is ineligible. This third-party integration still needs live Plex/TV validation; it does not change Plex's saved track selections or initiate analysis.
- **HDR compatibility:** Auto HDR checks the connected display and HDR decoder before direct playback. Select Convert HDR to SDR in Settings if your TV flashes or shows incorrect colors even when it reports HDR support; Prefer native HDR opts out of capability-based conversion. Conversion requests H.264 8-bit video with video remuxing disabled, and requires **Enable HDR tone mapping** in Plex Server > Transcoder and the server owner's Plex Pass. Server hardware must keep up; some Dolby Vision formats require an SDR version. Detected HDR playback errors retry once using SDR conversion. These settings apply to episodes, movies, and ad clips.
- **Adaptive guide:** opening the guide selects the current station and its currently airing program after loading. Later row refreshes preserve the channel you selected rather than snapping back to the playing station. Hold Up/Down to page through channels and Left/Right to jump three hours; the **NOW** header button returns to the current time without changing stations. Left and Right still step by 15 minutes. The time ruler labels each whole half hour, so a 12-hour time such as 10:00 AM stays on one line. Whole rows fill the available guide area when there are enough channels. Larger displays can show more rows; shorter lineups keep readable row heights. Each channel cell shows a vertical channel number and the channel's illustrated wordmark, without duplicate name text. The focused channel's current name appears in the program panel, and alternating row colors make the grid easier to follow.
- **Locale formats:** every time and date follows the device locale (12/24-hour clock, date order). The guide shows the date by the clock and in the header. The playback info banner shows Up Next right-aligned with its start time and a countdown.
- **Theme colors:** Settings offers Default (classic cable blue), Lucy (1950s cherry and mint), Partridge (1970s harvest gold and avocado), Headroom (1980s neon and midnight), and Gak (1990s slime and grape), arranged in two readable rows. The choice applies immediately to the guide, menus, and overlays and is saved between sessions; existing saved theme choices keep working.
- **Retro typography:** VT323 stays on display headings and channel-number overlays; Barlow Semi Condensed gives menus, guide listings, descriptions and help text a clearer broadcast-style companion.
- **Guide layout:** choose Compact, Default (the default), or Large in Settings. All fill the guide area when enough channels exist; Large uses a taller program panel, bigger text and fewer rows. Channel cells show a vertical number and an illustrated wordmark; the program panel shows the date, full scheduled time range (even when the cell is clipped by the visible window) and the Plex description (fetched for the focused program only; metadata GET, no watch-history updates). Movies in the grid show their year, e.g. "WarGames (1983)". Earlier Comfortable/Large choices migrate to Compact/Default.
- **Manage libraries:** change your included libraries from Settings without signing out. Library pickers show `+` for included libraries and `x` for excluded libraries, with text labels in every theme. Save and sync continues if you leave the screen, updates the selection only after a successful fetch, and rebuilds the guide. Playback is reconciled with the new selection without closing menus; background completion waits until the app resumes before starting playback.
- **Ad library:** setup asks you to select a Plex Other Videos or Movies library or Disable ads before choosing regular libraries. Selecting a source syncs its clips to a separate, persistent ad catalog; Settings also offers Force ad sync to refresh additions/removals. Failed syncs preserve the previous source and catalog. Off clears the catalog. The source is saved per server and excluded from regular library pickers/imports; re-sync regular libraries after selecting a previously imported library. Ads fill scheduled breaks without delaying programs; gaps too short for a clip show Up Next.
- **Buy Me Maybe:** ads enabled with at least one synced clip adds a commercials-only station, starting at channel 998 (or the next free number, skipping reserved numbers). Clips shuffle back-to-back with no padding or ordinary activation minimum. The guide shows hour-long "Buy Me Maybe" blocks, not individual ads. It uses only the ad catalog and disappears when ads are disabled or the catalog becomes empty. Choosing Edit for this station opens Ad library settings. Ad sync continues if you leave Settings and reconciles playback if the station is removed.
- **Guide updates:** startup loading shows the RERUN wordmark with its red beret and an animated spinner. Rebuilding the guide shows placeholder rows behind a fixed-size retro window with an **Updating Guide** title bar, progress details and elapsed time; changing messages do not resize the window. The same window appears while an empty lineup loads. Only the visible and adjacent channel rows are topped up and built, and the guide notices clock or time-zone changes on its next tick; only the header actions (Channels, Settings and NOW) are available during the update. The guide refreshes after syncs and as its time window advances, without reloading the schedule on every clock tick. Playback stays muted in the guide and other menus, even when a new program starts.
- **Clip transitions:** programs and ads on the same station fade through black instead of showing static or picture roll. Actual station changes retain the configured tuning effects. The next clip still seeks to its scheduled wall-clock position.
- **Playback descriptions:** the information banner shows a two-line Plex program description when available, using the guide's bounded credential/revision-scoped cache and existing timeout. Hidden banners make no description request, and music tracks stay unchanged.
- **Start over:** hold Left for 2 seconds during an episode or movie (a message appears if the current program cannot be restarted). Movies ask you to confirm starting from the beginning, with **Start movie** and **Cancel** choices; they never offer episode queues. Episodes offer just this episode or all remaining available episodes of the same show, in season/episode order across seasons. Playback starts at zero on the device-local **From the Top** station; the original station keeps its schedule. Changing stations prompts **Pause until later**, **Remove station**, or **Cancel**; playback stays held through loading and retries until you decide. Opening the guide or menus does not prompt. Paused playback and its queue stay in the guide/channel list across app restarts, resuming from the saved media position. Retuning the same saved episode preserves its audio choice and HDR fallback. Progress is also checkpointed every five seconds; an abrupt process loss may replay those last few seconds. The station disappears when its queue finishes and returns to the original station if still active (or an active fallback). Starting another program warns before replacing a saved station. A short Left still jumps to the last channel. From the Top stays out of the channel editor and last-channel history; it uses channel 999 or the next free number.
- **Minimum programming:** Settings > Lineup offers Low (4 hours), Medium (12 hours, the default), and High (24 hours). Special movie-overflow and decade thresholds remain when higher. Changing the preset rebuilds eligibility from the saved library without a Plex rescan. Renaming or editing a station does not waive its minimum. Channels > Edit > Minimum programming lets you explicitly choose a lower/higher minimum or restore the default; that override takes priority over the preset. Even "No minimum" needs playable content; music stations still need included music tracks. Empty/dormant channels stay out of the guide, and stock extra channels are not created until they qualify. Known old stock names update automatically; personal renames, rules and locked schedules remain intact.
- **Local 15-minute scheduling.** Movies and episodes start at :00, :15, :30 or :45 in your device's local time zone. Music and Buy Me Maybe continue back to back. Editing a channel keeps what is on now; if that entry ends off a quarter-hour mark, filler runs until the next mark and the following movie or episode starts there. Schedules are stored on the device; the schedule-sharing pilot has been removed. Existing saved schedules stay intact after upgrading, and routine top-ups append without reshuffling them for up to 48 hours ahead. Changing included libraries intentionally rebuilds schedules; removing scheduled media or channels can also replace playback.
- **The Rest finds otherwise-unmatched video.** Only optional genre, movie-multiplex, decade and language stations that meet their minimum remove items from this catchall, so below-threshold collections still have somewhere to play.
- **Read-only.** Nothing you watch is reported back to Plex. Your Continue Watching stays clean.
- **Updates and sign-out:** database upgrades preserve server/library choices using explicit migrations, never a silent database reset. The first launch after an app update rebuilds saved channels (other launches skip it); startup shows loading meanwhile, not premature no-channel instructions; failures remain distinct from an empty lineup. Missing server configuration returns to setup. Sign out clears Plex credentials and the local library/lineup while keeping display preferences.

## Install on a Fire TV

The guide and top-right channel display use bundled illustrated wordmarks; the channel manager and bottom playback info retain square icons. The top-right wordmark is larger than the previous icon, with the complete artwork fitted in its original proportions above the channel number. The library covers 69 named channels and all 40 TV/music decade templates, with fallbacks for custom channels and arbitrary music genres. Editable SVGs and wordmarks, a preview, stable-ID mapping and regeneration instructions are in [`design/channel-icons`](design/channel-icons). Lossless WebP renders of both are committed for Android, so ordinary builds do not need Node or an SVG decoder.

Requirements:
- JDK 17 or newer (Gradle 9 will not run on JDK 11)
- Android SDK (platform 37)
- `adb` on PATH
- A TV running Android 8.0/API 26 or newer; Android 7-based Fire OS devices are not supported.

1. On the TV, unlock Developer Options: Settings → My Fire TV → About → click the device name 7 times.
2. Turn on **ADB Debugging** in Developer Options.
3. Note the TV's IP address, under About → Network.
4. On your PC:

```powershell
.\scripts\install.ps1 -TvIp 192.168.1.50
```

The first build creates a private release key (`rerun-release.jks` and `keystore.properties`). Both stay on this machine and out of git. The script asks for a password, or generates a random one when it cannot prompt. Back those two files up. An install signed with a different key will not update the app, and saved Plex tokens will not carry over.

5. On the TV, the app shows a code. Enter it at **plex.tv/link**, pick your server, select the libraries to include, and continue to the first sync. Your choices are saved for future syncs; newly added libraries are not included automatically. Included music libraries sync automatically, with no separate enable step. Existing installations without a saved selection continue syncing all supported libraries, including music.

To remove it: `.\scripts\restore.ps1 -TvIp 192.168.1.50`

## Shared network lists from TMDB

Plex stores one network per show, and nothing at all about which network aired a movie. To fill channels with what each network really aired, the build can download lists from [TMDB](https://www.themoviedb.org/): every show each network aired, plus made-for-TV movies from that network's own studios (Disney Channel Original Movies, HBO Films, and so on). Shows that aired on several networks appear on each of those channels.

The repository includes a shared snapshot at [`data/tmdb/rerun/tmdb-networks.json`](data/tmdb/rerun/tmdb-networks.json). A normal build bundles it into the APK automatically, with no TMDB account or token required. The TV reads these lists locally; it never calls TMDB. A fresh local refresh overrides the shared snapshot on that computer.

Only maintainers refreshing the data need their own free TMDB token. It is used for the download and never saved in the JSON, repository or app.

1. Create a free account at [themoviedb.org](https://www.themoviedb.org/signup) and verify your email.
2. Go to **Settings > API** ([themoviedb.org/settings/api](https://www.themoviedb.org/settings/api)) and request an API key. Choose **Developer**, accept the terms, and fill in the short form (personal or non-commercial use is fine).
3. That page then shows two values. Copy the **API Read Access Token**, the long one that starts with `eyJ`. Rerun doesn't use the shorter **API Key**.
4. Build and install with the token:

   ```powershell
   .\scripts\install.ps1 -TvIp 192.168.1.50 -TmdbToken 'eyJhbGciOi...'
   ```

   Or keep it out of your command history by setting it for the current shell only:

   ```powershell
   $env:TMDB_TOKEN = 'eyJhbGciOi...'
   .\scripts\install.ps1 -TvIp 192.168.1.50
   ```

The refresh writes `local\tmdb\rerun\tmdb-networks.json`. Git ignores that local file, and later builds on your computer reuse it until you refresh again. Failed, malformed or incomplete discovery responses stop the refresh and preserve the previous lists. To refresh without installing, set `TMDB_TOKEN` and run `.\gradlew.bat :tmdb:run`. The refresh prints each channel's real network, the TMDB networks it matched, and how many shows and movies it found. It also warns about any network it couldn't find.

To share a successful refresh with everyone:

```powershell
# Run after :tmdb:run; publishing itself does not need a token or internet access.
.\gradlew.bat :tmdb:publishSnapshot :tmdb:check
git diff -- data/tmdb/rerun/tmdb-networks.json
```

Commit the reviewed `data/tmdb/rerun/tmdb-networks.json` through a pull request. Publishing rejects empty, unreadable, incomplete or duplicate-network snapshots and serializes only the public reference fields. CI validates this shared snapshot and runs the publisher tests without credentials. Once the update is merged, other users pull the repo and rebuild/install normally to receive the newer lists. Already-installed TVs keep their existing snapshot until the app is updated.

TMDB doesn't track which theatrical movies a channel showed. Movies outside a network's own studios still use the studio-owner rules.

This product uses the TMDB API but is not endorsed or certified by TMDB.

## Remote

| Button | Watching | Guide |
|---|---|---|
| ▲ / ▼ | Channel up / down | Move between channels |
| Hold ▲ / ▼ | — | Page up / down |
| ◀ | Last channel | Earlier |
| Hold ◀ 2s | Start current movie or episode over | — |
| Hold ◀ | — | Jump 3 hours earlier |
| ▶ / OK | Info banner | Later / tune in |
| Hold OK 2s | Cycle CRT look (Off / Subtle / Full) | — |
| Hold ▶ 2s | Select current program audio track | — |
| Hold ▶ | — | Jump 3 hours later |
| ☰ | Guide | Edit the focused channel |
| Back | Guide | Back to TV. Press Back again right away to exit. |

Channels, Settings and NOW sit at the top of the guide: press ▲ from the first row, then ◀ or ▶ to choose. NOW returns to the current time without changing stations.

## Touch controls (phones and tablets)

Rerun also runs on Android phones and tablets, full screen in landscape. Touch controls turn on only when the device has a touchscreen and is not running as a TV, so TV remotes work exactly as above. With TalkBack, double-tap the video for the banner and touch buttons, or use the actions menu for Channel up, Channel down, Last channel and Open guide.

| Touch | Watching | Guide |
|---|---|---|
| Swipe up / down | Channel up / down | Drag to move between channels |
| Swipe left | Last channel | Drag to move through time |
| Swipe right | Info banner | — |
| Tap | Show / hide the info banner and touch buttons | Select a program; tap it again to tune in |
| Hold | Guide | Edit that channel |
| Back gesture | Guide | Back to TV. Go back again right away to exit. |

While the banner is up, touch buttons cover the remote's hold actions: Guide, CH ▲, CH ▼, Last channel, Start over, Audio and CRT. In the guide, tap CHANNELS or SETTINGS at the top. Settings and other menus respond to taps.
## Commercials (optional)

`tools/commercials/fetch_archive_item.py` downloads every video in an Internet Archive item and queues each one for a local upscaler. Upscaled clips go into a Plex library named **Commercials**. Choose that library as the ad source during setup or in Settings. Rerun plays clips that fit between shows, seeking to the current ad position when you tune into a break. Clip choices are stable for a given channel, break, and catalog; consecutive repeats are avoided when another clip fits. Ads never appear as guide entries: each program's displayed time includes its full following break, including consecutive filler entries beyond the visible query window, ending when the next program starts or a schedule gap begins. Disabled ads, empty catalogs, and remaining gaps too short for a clip show an "Up Next" card. Times with no schedule say Off Air.

Commercial transitions do not redisplay the upper-right channel name/number; changing
channels or opening the info banner still shows it.

Vintage commercials are generally still under copyright. Keep downloaded copies for personal viewing, and never commit media to this repository.

## Music playback troubleshooting

Common audio formats play directly, including Plex's `mpeg` container label for MP3.
Other or unknown formats, including non-DRM WMA, request audio-only MP3 transcoding from Plex instead of silently
counting down through unplayed songs. A failed direct audio stream retries once through that
audio transcoder; if conversion also fails, the player displays a connection/transcoder error.
The music HTTP endpoint returns a progressive audio stream even though its URL ends in `.m3u8`;
Rerun handles it as MP3 rather than an HLS playlist. Errors show a player error code and HTTP
status when available, without request URLs or credentials.
The song remains aligned to its scheduled position, and menus stay muted.
Plex must be able to read and transcode the source file; this fallback still needs live-server validation.

## Development

- `core/` is pure Kotlin: catalog, routing, scheduler, tune math, and Plex parsing.
- `app/` is the Android shell: Compose UI, Media3 player, and Room. JVM regression tests use Robolectric. A debug build needs no signing key. A release build needs the key created by `scripts/keystore.ps1`.
- `tools/ktest/` runs the core tests with only a Kotlin compiler jar, for machines without Maven access.
- Large-library scheduling and lineup workloads: `.\gradlew.bat :core:test --tests com.rerun.core.PerformanceWorkloadTest --rerun-tasks`. Timings and baseline-output fingerprints are in `core/build/test-results/test/TEST-com.rerun.core.PerformanceWorkloadTest.xml`; these are local JVM measurements, not TV frame-rate benchmarks.
- Playback timers are local to visible overlays/music progress instead of polling the whole player UI. Library readers share a list/map snapshot loaded off the UI thread; concurrent cold loads are coalesced and invalidated loads cannot republish stale data. Playback metadata parsing also runs off Main. Plex section downloads overlap at most two at a time and assemble in original section order; failed fetches do not write a partial library. Lineup planning runs outside the Room transaction; queue-repair checks retain their original order inside the shorter atomic commit under the repository mutex.
- Playback metadata is cached only in memory, up to 16 items for 60 seconds, scoped to the Plex server, credentials, and guide revision. Syncs and sign-out invalidate it; remote Plex track-selection changes may take up to a minute to appear on a subsequent tune. After playback starts, the next video program's metadata is prefetched 45 seconds before its scheduled start so it stays within this TTL, including across filler breaks; empty breaks re-arm prefetch, and retune, stop and clear cancel it. Display/decoder HDR support is still checked on each tune.
- `RerunTiming` logs report tune, metadata, sync and schedule stage durations without credentials, URLs or media titles. Use them with physical-TV profiling to evaluate real improvements; unit tests and local JVM workloads do not establish TV frame rate or channel-change latency.
- Decade qualification adds one linear pass through library metadata, storing only duration totals, not copies of every channel pool. Renames reuse those totals; custom rules use the existing resolver. Higher decade thresholds reduce repetitive stations, and no genre-by-decade combinations are generated. At most 40 stock decade stations can qualify; each active station still has schedule/persistence costs.
- Each channel has one schedule. East/West feeds and their setting have been removed. Database upgrades delete saved West feeds and their derived schedules, retain original channels and locked entries, and restore the last-tuned feed to its original channel.

From the repository root on Windows:

```powershell
. .\scripts\jdk.ps1
Set-RerunJdk
.\gradlew.bat :core:test :app:testDebugUnitTest :tmdb:assemble :app:assembleDebug :app:lintDebug --console=plain --quiet
if ($LASTEXITCODE -ne 0) { throw 'Validation failed' }
```

Contributor guidance: [AGENTS.md](AGENTS.md). Current architecture: [design reference](docs/superpowers/specs/2026-10-23-rerun-design.md). Delivered scope and outstanding device checks: [PR readiness](docs/superpowers/plans/2026-10-23-rerun-phase1-core-tv.md). Scheduling v2 (local 15-minute grid shipped; remaining work planned): [design](docs/superpowers/specs/2026-10-26-scheduling-v2-design.md) and [plan/handoff](docs/superpowers/plans/2026-10-26-scheduling-v2.md).

### Continuous integration

[CI](.github/workflows/ci.yml) runs on pull requests to `main`, pushes to `main`, and manual dispatch.
Windows runners execute the Gradle checks above using JDK 21 and Android platform 37.
A parallel job runs PSScriptAnalyzer (errors fail; warnings remain visible) and the isolated
keystore property-encoding regression. The final `CI` check requires both jobs to pass.
CI also validates Android versions against release tags and the PR base: unchanged versions
are allowed for feature PRs; a bump must increase both `versionName` and `versionCode`.
Pushing a `v*` tag runs CI and requires the tag to match the Android version. A failed check
does not delete a tag or prevent someone from manually publishing; check CI before publishing.
Test/lint reports and successful Android verification debug APKs are available as workflow artifacts for 14 days.
Debug APKs use debug signing and cannot update installations signed with your private release key.
CI does not require release credentials or replace live TV/Plex testing.

### Preparing a release

Run **Actions > Prepare release > Run workflow** on `main`, choosing patch, minor or major.
It opens a PR changing only the Android version and explicitly dispatches CI for that branch
(PRs made with `GITHUB_TOKEN` do not automatically trigger PR workflows). Repository
**Settings > Actions > General > Allow GitHub Actions to create and approve pull requests**
must permit PR creation; no personal access token or release-signing credentials are needed.
The workflow never force-pushes, merges, signs, tags or publishes. An existing branch for the
same version causes a visible failure rather than overwriting work.

Review the PR and its CI result, merge it, create the release tag and GitHub release, and wait
for the tag's CI run to pass. The workflow's PR is a version preparation, not evidence that a
release has shipped. Feature merges after a tag may legitimately retain the last version until
the next release-preparation PR.

#### Publishing with GitHub Actions

The manually dispatched **Publish release** workflow builds and signs the tagged source, verifies
the APK version and signing certificate, attaches the APK and R8 mapping archive, and replaces
the release notes with a short installation guide, supplied highlights, checksums and a changelog
link. It does not create or move tags and cannot run on pull requests.

For one-time setup, create a protected GitHub environment named `release`, restricted to `main`
and optionally requiring a reviewer. Add these environment secrets using the original release
signing key; never generate a replacement key for an existing app:

- `RERUN_KEYSTORE_BASE64`: Base64-encoded contents of `rerun-release.jks`.
- `RERUN_KEYSTORE_PASSWORD` and `RERUN_KEY_PASSWORD`: the corresponding passwords.
- `RERUN_KEY_ALIAS`: the key alias, normally `rerun`.
- `RERUN_SIGNING_CERT_SHA256`: the certificate digest from a previously published APK, used to
  prevent an incompatible signing key from being released. Do not copy this from the new build.

To copy the keystore's Base64 value on Windows without printing it to the terminal:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes((Resolve-Path '.\rerun-release.jks'))) | Set-Clipboard
```

Paste it into the GitHub secret, then clear the clipboard. To publish, open **Actions > Publish
release > Run workflow** on `main`, enter the existing tag, and write user-facing highlights as
one Markdown bullet per line (for example, `- Restart episodes and resume them later`). The
workflow fails before signing or uploading if the tag CI has not passed, the tag is not reachable
from `main`, the release is missing, or the signing certificate differs from the established one.

The existing manual build/sign process remains available as a fallback. Signing credentials are
never available to CI or the Prepare release workflow.

HDHomeRun/live TV, TMDb enrichment, automatic commercial splitting, ad content/age filtering, and an idle timeout are not implemented. Review all clips in your chosen ad library yourself; Rerun does not screen ads for children. No device CPU/RAM/frame-rate gains or live Plex loudness measurements are claimed by the JVM benchmarks.

## Credits

VT323 font © The VT323 Project Authors, SIL Open Font License 1.1 (`licenses/VT323-OFL.txt`). Not affiliated with Plex or any studio named here. The names on the guide are parodies.

Barlow Semi Condensed © 2017 The Barlow Project Authors, SIL Open Font License 1.1 (`licenses/Barlow-OFL.txt`). Regular and Medium are bundled locally; no font downloads are needed at runtime.
