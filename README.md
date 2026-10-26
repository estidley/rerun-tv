# Rerun

Old-school cable TV, built from your own Plex library. For Fire TV and Android TV.

Turn it on and something's already playing. Flip channels and you land in the middle of whatever's on. There's a 90s cable guide, a CRT tube look, static between channels, and "technical difficulties" when your server naps.

- **Network channels:** Dinky Channel, Dinky XD, Slimelodeon, Cartoon Notwork, Kaboomerang, Toonuki, PBJ Kids, Sigh-Fi Channel, USB Network, HMO, and more. The names on screen are parodies; each channel keeps its real network name behind the scenes. With TMDB lists (below), a show lands on every network it really aired on. Without them, shows land by the network and studio stored in Plex.
- **Streaming originals follow the studio and the audience:**
  - Disney+ kids shows go to Dinky Channel.
  - Marvel and live-action Star Wars go to **The Backlot**.
  - Paramount+ kids shows go to Slimelodeon.
  - Max originals go to HMO.
  - Hulu originals go to SFX.
  - Netflix, Prime Video and Apple TV+ originals are spread by genre.
- **Toonuki (105):** all movies and episodes tagged with the Anime genre in your included Plex libraries, regardless of network, studio, title or age rating. Animation alone does not qualify. Episodes inherit genres from their show; add Anime to the show or movie in Plex and sync Rerun if needed. The normal four-hour activation minimum applies. Stock Toonuki rules upgrade on refresh; edited content rules are preserved. Old non-anime future entries are rebuilt while the current airing stays locked.
- **Comic channels:** Marvelous TV (380), Marvelous Movies (381), Marvelous Timeline (382), Detective Club TV (390), and Detective Pictures (391). Movies and shows stay in their respective channels; Marvelous Timeline combines both. Each channel uses your included Plex libraries and the normal four-hour activation minimum. Occupied numbers move to a free slot. Known adaptations, explicit Marvel/DC production metadata, and Marvel/DC labels or collections identify content; generic Disney/Fox/Sony/Warner ownership or Action/Animation genres alone do not. Label otherwise-unrecognized titles **Marvel** or **DC** in Plex, then sync. DC has separate TV/movie channels without an automatic chronology channel.
- **Marvelous Timeline:** plays the MCU group using the [published Marvel timeline](https://www.marvel.com/articles/movies/mcu-timeline-order-disney-plus), including season-level placements, then other continuities as separate groups (X-Men, the separate Spider-Man series, Sony's universe, Spider-Verse, and other adaptations). It skips missing files and loops after the available sequence finishes. Movies and episodes share one sequence; the normal six-hour movie rotation does not apply. Other-continuity placements are an editorial whole-title watch order, not one official multiverse chronology. Unmapped seasons, stand-alone shows and unclassified Marvel films remain included, grouped by show or in a release-year fallback. Unknown movie years cannot reliably distinguish reboots. Future additions join the next sequence cycle; already-locked programs stay scheduled. Ordinary Marvelous TV/Movies remain shuffled under the usual episode-order policy. Channel editing includes Comic universes and Universe timeline options.
- **Theme channels:** Fright Night, The Void, Laugh Track, Adrenaline, The Living Room, Neon Decade, Dial-Up Cinema, Cereal Hour. Laugh Track uses the Comedy genre; TBS metadata belongs on **Ted's TV**, not a comedy-only station. **Remote Control** is the MTV-inspired network station, including its mixed programming. More themes show up only when that genre has at least 4 hours: True-ish (documentaries), Canned Laughter (sitcoms), Sob Story (dramedies), Cold Read (psychic and paranormal), This Just In (news), and others such as The Matinee and Case File. See the [complete cable dial](docs/channel-lineup.md) for every predefined station's name, inspiration, matching rules and minimum.
- **Movie channels:** 4 hours of movies adds The Big Picture. 24 hours adds Second Feature. 48 hours adds Late Seating. They share your movies and shuffle independently.
- **Decade stations:** your included libraries automatically add TV decade channels with at least **24 hours of episodes** and, when Music is enabled, music decade channels with at least **12 hours of tracks**. For example, "1980s TV" and "1990s Music" appear alongside genre stations. TV decade numbers start at 1100; music decades at 3000, with collisions moved to a free number. Years come from Plex episode/track metadata (falling back to show/album year); missing years and clips shorter than a minute do not count. Supported decades are 1900s through 2090s. Movies keep their existing decade/theme channels. Sync, switch changes and the first launch after an app install or update (including a sideloaded reinstall) reconcile eligibility (ordinary launches skip the rebuild; an interrupted switch change forces one on the next launch); unedited stations disappear when their pool falls below the threshold, while edited stations retain their settings and minimum unless explicitly overridden. Music decades follow the Music switch.
- **Custom channels:** pick genres, shows, collections, decades and ratings right on the TV.
- **Off air:** a time with nothing scheduled says Off Air in the guide and on the screen, then the next program starts. A short break already on the schedule still says Up Next.
- **Music:** enable Music in Settings and include your Plex music libraries in Manage libraries. The scan continues when you return to the guide; its progress and elapsed time stay visible until the lineup is ready. Re-syncs also survive navigation between screens. Rerun imports audio tracks, not music videos, and adds a station for each genre with at least 4 hours. Genres inherit from albums/artists; untagged tracks go to Unclassified. The guide groups shuffled songs into hourly station blocks such as "Blues Music"; Off Air gaps remain visible. The music screen shows artist, song, album, square album art, elapsed time and song length, without decorative tick marks underneath. Artwork and text travel slowly across a black background to reduce fixed-image burn-in risk (not a guarantee against burn-in).
- **Subtitles:** off until you turn them on in Settings. Choose a preferred language or Automatic (Plex's selected track). If your language is unavailable, subtitles stay off for that program. Embedded and external text subtitles play directly; other formats are burned in by Plex.
- **Audio tracks:** choose a preferred audio language in Settings. Automatic uses Plex's selected track; an unavailable language falls back to the default. Hold Right for 2 seconds while watching, or choose Current program audio tracks in Settings, to select a track for this airing only. Multi-track video uses a Plex playback session with an explicit audio stream ID; it does not change saved Plex track selections or watch history.
- **Normalize loudness:** optional and off by default in Settings > Audio. Requests Plex audio normalization for both programs and ads whose selected audio stream supports it, using audio transcoding while allowing video copy. Requires a recent Plex server (1.43.4+), Plex Pass, and **Analyze audio tracks for loudness** under Plex Server > Library, including the commercials library. Music stations are unchanged. Unanalyzed tracks, Dolby Vision, video conversion (including HDR-to-SDR), and selected subtitles (including text) currently skip normalization and show a warning; volume consistency is not guaranteed when either source is ineligible. This third-party integration still needs live Plex/TV validation; it does not change Plex's saved track selections or initiate analysis.
- **HDR compatibility:** Auto HDR checks the connected display and HDR decoder before direct playback. Select Convert HDR to SDR in Settings if your TV flashes or shows incorrect colors even when it reports HDR support; Prefer native HDR opts out of capability-based conversion. Conversion requests H.264 8-bit video with video remuxing disabled, and requires **Enable HDR tone mapping** in Plex Server > Transcoder and the server owner's Plex Pass. Server hardware must keep up; some Dolby Vision formats require an SDR version. Detected HDR playback errors retry once using SDR conversion. These settings apply to episodes, movies, and ad clips.
- **Adaptive guide:** opening the guide selects the current station and its currently airing program after loading. Whole rows fill the available guide area when there are enough channels. Larger displays can show more rows; shorter lineups keep readable row heights. Channel names wrap when the row is tall enough, and alternating row colors make the grid easier to follow.
- **Theme colors:** Settings offers Standard blue, 70s earth tones, and 50s diner colors. The choice applies immediately to the guide, menus, and overlays and is saved between sessions.
- **Retro typography:** VT323 stays on display headings and channel-number overlays; Barlow Semi Condensed gives menus, guide listings, descriptions and help text a clearer broadcast-style companion.
- **Guide density:** choose Compact, Comfortable, or Large rows in Settings. All three fill the guide area when enough channels exist; taller rows allow more channel-name lines.
- **Manage libraries:** change your included libraries from Settings without signing out. Library pickers show `+` for included libraries and `x` for excluded libraries, with text labels in every theme. Save and sync continues if you leave the screen, updates the selection only after a successful fetch, and rebuilds the guide. Playback is reconciled with the new selection without closing menus; background completion waits until the app resumes before starting playback.
- **Ad library:** setup asks you to select a Plex Other Videos or Movies library or Disable ads before choosing regular libraries. Selecting a source syncs its clips to a separate, persistent ad catalog; Settings also offers Force ad sync to refresh additions/removals. Failed syncs preserve the previous source and catalog. Off clears the catalog. The source is saved per server and excluded from regular library pickers/imports; re-sync regular libraries after selecting a previously imported library. Ads fill scheduled breaks without delaying programs; gaps too short for a clip show Up Next.
- **Buy Me Maybe:** ads enabled with at least one synced clip adds a commercials-only station, starting at channel 998 (or the next free number, skipping reserved numbers). Clips shuffle back-to-back with no padding or four-hour minimum. The guide shows hour-long "Buy Me Maybe" blocks, not individual ads. It uses only the ad catalog and disappears when ads are disabled or the catalog becomes empty. Choosing Edit for this station opens Ad library settings. Ad sync continues if you leave Settings and reconciles playback if the station is removed.
- **Guide updates:** startup loading shows the RERUN wordmark with its red beret and an animated spinner. Rebuilding the guide shows an updating message and elapsed time instead of an empty grid; only Channels and Settings are selectable during the update. The guide refreshes after syncs and as its time window advances, without reloading the schedule on every clock tick. Playback stays muted in the guide and other menus, even when a new program starts.
- **Clip transitions:** programs and ads on the same station fade through black instead of showing static or picture roll. Actual station changes retain the configured tuning effects. The next clip still seeks to its scheduled wall-clock position.
- **Start over:** hold Left for 2 seconds during an episode. It starts from the beginning on its own channel, and the TV returns when it ends. A short Left still jumps to the last channel. The temporary channel stays out of the channel editor and last-channel history; channel 999 is reserved for it.
- **Minimum programming:** ordinary channels need 4 hours; movie overflow and decade stations use their higher thresholds. Renaming or editing a station does not waive its minimum. Channels > Edit > Minimum programming lets you explicitly choose a lower/higher minimum or restore the default. Even "No minimum" needs playable content; Music must still be enabled. Empty/dormant channels stay out of the guide, and stock extra channels are not created until they qualify. Known old stock names update automatically; personal renames, rules and locked schedules remain intact.
- **Locked 48-hour schedule.** Routine top-ups append without reshuffling stored entries. Changing included libraries intentionally rebuilds schedules; removing scheduled media or channels can also replace playback.
- **Read-only.** Nothing you watch is reported back to Plex. Your Continue Watching stays clean.
- **Updates and sign-out:** database upgrades preserve server/library choices using explicit migrations, never a silent database reset. The first launch after an app update rebuilds saved channels (other launches skip it); startup shows loading meanwhile, not premature no-channel instructions; failures remain distinct from an empty lineup. Missing server configuration returns to setup. Sign out clears Plex credentials and the local library/lineup while keeping display preferences.

## Install on a Fire TV

The guide, channel manager and playback info display the bundled 1990s channel artwork. The library covers 60 named channels and all 40 TV/music decade templates, with fallbacks for custom channels and arbitrary music genres. Editable SVGs and wordmarks, a preview, stable-ID mapping and regeneration instructions are in [`design/channel-icons`](design/channel-icons). Lossless WebP renders are committed for Android, so ordinary builds do not need Node or an SVG decoder.

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

5. On the TV, the app shows a code. Enter it at **plex.tv/link**, pick your server, select the libraries to include, and continue to the first sync. Your choices are saved for future syncs; newly added libraries are not included automatically. Music libraries are only synced when Music is enabled in Settings. Existing installations without a saved selection continue syncing all supported libraries.

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
| ◀ | Last channel | Earlier |
| Hold ◀ 2s | Start current episode over | — |
| ▶ / OK | Info banner | Later / tune in |
| Hold OK 2s | Cycle CRT look (Off / Subtle / Full) | — |
| Hold ▶ 2s | Select current program audio track | — |
| ☰ | Guide | Edit the focused channel |
| Back | Guide | Back to TV. Press Back again right away to exit. |

Channels and Settings sit at the top of the guide: press ▲ from the first row.

## Commercials (optional)

`tools/commercials/fetch_archive_item.py` downloads every video in an Internet Archive item and queues each one for a local upscaler. Upscaled clips go into a Plex library named **Commercials**. Choose that library as the ad source during setup or in Settings. Rerun plays clips that fit between shows, seeking to the current ad position when you tune into a break. Clip choices are stable for a given channel, break, and catalog; consecutive repeats are avoided when another clip fits. Ads never appear as guide entries: each program's displayed time includes its following break, ending when the next program starts. Disabled ads, empty catalogs, and remaining gaps too short for a clip show an "Up Next" card. Times with no schedule say Off Air.

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
- Playback timers are local to visible overlays/music progress instead of polling the whole player UI. Library readers share a list/map snapshot loaded off the UI thread; concurrent cold loads are coalesced and invalidated loads cannot republish stale data.
- Playback metadata is cached only in memory, up to 16 items for 60 seconds, scoped to the Plex server, credentials, and guide revision. Syncs and sign-out invalidate it; remote Plex track-selection changes may take up to a minute to appear on a subsequent tune. Display/decoder HDR support is still checked on each tune.
- Decade qualification adds one linear pass through library metadata, storing only duration totals, not copies of every channel pool. Renames reuse those totals; custom rules use the existing resolver. Higher decade thresholds reduce repetitive stations, and no genre-by-decade combinations are generated. At most 40 stock decade stations can qualify; each active station still has schedule/persistence costs.
- Each channel has one schedule. East/West feeds and their setting have been removed. Database upgrades delete saved West feeds and their derived schedules, retain original channels and locked entries, and restore the last-tuned feed to its original channel.

From the repository root on Windows:

```powershell
. .\scripts\jdk.ps1
Set-RerunJdk
.\gradlew.bat :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --console=plain --quiet
if ($LASTEXITCODE -ne 0) { throw 'Validation failed' }
```

Contributor guidance: [AGENTS.md](AGENTS.md). Current architecture: [design reference](docs/superpowers/specs/2026-10-23-rerun-design.md). Delivered scope and outstanding device checks: [PR readiness](docs/superpowers/plans/2026-10-23-rerun-phase1-core-tv.md).

### Continuous integration

[CI](.github/workflows/ci.yml) runs on pull requests to `main`, pushes to `main`, and manual dispatch.
Windows runners execute the Gradle checks above using JDK 21 and Android platform 37.
A parallel job runs PSScriptAnalyzer (errors fail; warnings remain visible) and the isolated
keystore property-encoding regression. The final `CI` check requires both jobs to pass.
Test/lint reports and successful Android verification debug APKs are available as workflow artifacts for 14 days.
Debug APKs use debug signing and cannot update installations signed with your private release key.
CI does not require release credentials or replace live TV/Plex testing.

HDHomeRun/live TV, TMDb enrichment, automatic commercial splitting, ad content/age filtering, and an idle timeout are not implemented. Review all clips in your chosen ad library yourself; Rerun does not screen ads for children. No device CPU/RAM/frame-rate gains or live Plex loudness measurements are claimed by the JVM benchmarks.

## Credits

VT323 font © The VT323 Project Authors, SIL Open Font License 1.1 (`licenses/VT323-OFL.txt`). Not affiliated with Plex or any studio named here. The names on the guide are parodies.

Barlow Semi Condensed © 2017 The Barlow Project Authors, SIL Open Font License 1.1 (`licenses/Barlow-OFL.txt`). Regular and Medium are bundled locally; no font downloads are needed at runtime.
