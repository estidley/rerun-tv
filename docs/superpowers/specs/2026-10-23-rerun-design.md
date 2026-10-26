# Rerun architecture and behavior

Current implementation reference, updated 2026-10-25. This replaces the original speculative design.
Installation, features, remote controls, and Plex prerequisites live in [README](../../../README.md);
contribution rules and validation commands live in [AGENTS.md](../../../AGENTS.md).

## Scope

Channel identities use stable IDs, independent of user-renamed display text. Guide rows use `ChannelWordmark` with 400 x 270 lossless renders of the wordmark SVGs, preserving aspect ratio beside the vertical number without duplicate name text. The selected channel's current name remains in the program panel. `ChannelIcon` retains square artwork in channel management and playback OSD/info. The 102 SVG icon/wordmark pairs live under `design/channel-icons`; a pinned Sharp generator produces both sets of transparent Android drawables and static resource maps. Unknown music/custom channels use explicit fallback art.

The tracked `data/tmdb/rerun/tmdb-networks.json` provides network/company membership to builds without credentials. A gitignored local refresh takes precedence. Maintainers run `:tmdb:publishSnapshot` after refreshing to validate and publish only the public reference schema; `:tmdb:check` verifies the shared file and publisher behavior in CI. Tokens never enter either JSON or the APK.

Rerun is a native Android/Fire TV player for a personal Plex library, not a virtual tuner server.
The minimum Android API is 26; compile/target SDKs and dependency versions are defined by the build files.
Basic playback does not itself require Plex Pass; optional Plex HDR tone mapping and loudness normalization do.
There is no separate Rerun server and no watch-history reporting.

Implemented sources are Plex movies, episodes, music tracks, and a separately selected ad library.
HDHomeRun/live TV, general TMDb metadata enrichment, commercial content filtering, archive streaming, and idle timeout
are not implemented. The local commercial helper downloads files and queues upscaling; it does not
automatically split compilations or classify ads. Users must review their own ad content.

## Ownership and data flow

| Component | Responsibility |
|---|---|
| `core` | Models, catalog/routing/resolution, deterministic scheduler, guide/tune math, ad planning, Plex parsing and playback plans |
| `RerunApp` | Process-level Room, settings, HTTP, repository and sync wiring; awaited startup lineup refresh |
| `LibrarySync` | Serialized paged imports, persistent library selections, ad catalog updates and lineup rebuilds |
| `ScheduleRepo` | Transactional channel/library changes, cached library snapshot, schedule top-ups, West mirroring and guide revision |
| `TvController` | Activity-owned navigation, foreground eligibility, startup state, scans, progress/errors and playback reconciliation |
| `TunerEngine` | Single Media3 player, metadata cache, wall-clock seeks/rollovers, tracks, compatibility policy and playback errors |
| Compose screens | Render state and remote focus; local overlay/music timers, not whole-player clock polling |

Shared typography pairs VT323 for display headings/OSD/channel numbers with Barlow Semi Condensed
for body, caption and guide-cell text. Both are bundled OFL fonts; readable text has no pixel-offset shadow.

Room stores media, channels, schedules, scheduler state, ad clips and settings.
Explicit migrations preserve prior data. Credentials are kept out of play URLs and redacted from
diagnostics; HTTP token attachment follows the trusted Plex host policy.

Toonuki (channel 105, stable ID `toonami`) uses only the Anime genre for episodes and movies,
without network/title or rating restrictions. Animation alone does not match. Stock legacy rules
upgrade on lineup refresh while preserving channel names/numbers/order and explicitly edited pools.
Rule upgrades and stale non-anime Toonuki queues discard future entries after the current airing;
West is remirrored from East. Ordinary refreshes preserve valid locked schedules.

## Network reference lists

Built-in network channels use parody display names while retaining real network aliases and stable IDs.
Refresh replaces retired built-in names and preserves names edited by the user.
The optional `:tmdb:run` build-time tool downloads network/company references using `TMDB_TOKEN`.
It writes gitignored `local/tmdb/rerun/tmdb-networks.json`; subsequent builds bundle that resource.
The app receives the lists, never the token, and makes no TMDB requests.
Episodes match their show TMDB ID; movies match the listed production companies, not broadcast history.
Matching combines reference memberships with the network Plex reports, then falls back to owner-family
routing only if neither matches. Missing or malformed bundled lists retain Plex-based routing. Invalid, missing or inconsistent TMDB discovery pages abort refresh before replacing the previous lists. Tests disable local lists.
Live refresh coverage and installed-TV behavior still need validation.

## Comic franchise channels

`ComicCatalog` provides Marvelous TV/Movies/Timeline and Detective Club TV/Detective Pictures.
Franchise inclusion is separate from network ownership: exact production metadata, explicit Plex labels/collections,
and a curated title/kind/year registry qualify items. Generic distributors and genres alone do not.
`Rules.franchises` is an additional OR inclusion source, with the existing kind/rating filters still applied.
Channel JSON missing this field decodes to an empty set; no Room schema migration is needed.
New station numbers are preferred values and move around existing custom channels. User edits remain preserved.

Marvelous Timeline uses `CHRONOLOGICAL` order: a single persisted deck contains both movies and episodes.
The MCU group follows the Marvel source verified 2026-10-25 with independent TV-season positions.
Legacy universes follow as separate groups; time-travel placements are an editorial viewing order.
Unmapped seasons/items use the documented grouped fallback, and missing library files are skipped.
A state marker prevents a prior shuffle deck from being reused as chronology. Normal movie/episode and music
schedulers retain their existing algorithms. The guide, filler and locked schedule rules still apply.
The full icon set is bundled in `app/src/main/res/drawable-nodpi`; only physical TV legibility remains to be checked on a device.

## Scheduling and ads

- Ordinary active pools require at least four hours of content. Channels can be built-in, generated, custom, or music.
- Additional stock decade stations require 24 hours of TV episodes or 12 hours of music tracks
  in the decade, alongside existing genre stations. Music decades honor the Music switch.
  Decades span 1900s-2090s; unknown years and items under one minute do not qualify.
  Episode/track year uses the existing Plex show/album fallback. Movies do not count toward TV decades.
  IDs and preferred numbers are stable (`decade:tv:<year>`, 1100+; `decade:music:<year>`, 3000+).
  Ordinary collision resolution, editing and schedule preservation apply.
  Unedited stations below their threshold are removed; edited definitions retain the existing
  default station minimum, or an explicit persisted `minimumContentHours` override (0–168).
  Renames/rule edits do not implicitly lower the minimum. Empty pools stay dormant and Music
  remains capability/settings gated. Save, refresh and editor previews share the same minimum policy;
  nullable overrides live in the existing channel JSON without a Room schema change.
- The [complete cable dial](../../channel-lineup.md) documents all predefined names and rules.
  Laugh Track is the Comedy-genre station; the former TBS station is Ted's TV, still matching
  network/studio TBS. Remote Control replaces The Video Channel for mixed MTV programming.
  Recognized old names migrate by channel ID without changing rules, numbers or locked entries.
- Schedules extend approximately 48 hours and top up below 24 hours. Epoch times are UTC; time blocks use the device zone.
- Movies use a shuffled deck; shows preserve episode pointers. Music plays back to back.
- Video padding ends at the next half-hour boundary and becomes a filler entry.
- Routine top-ups do not reshuffle existing entries. Changing included libraries rebuilds schedules;
  missing items and removed channels invalidate affected playback. Channel edits rebuild the future after the current entry.
- Each channel owns one schedule; there are no regional delayed feeds. Room migration 2-to-3
  removes legacy West channels/entries/state and their setting, maps the last-tuned feed to its
  original channel, and normalizes old feed-name suffixes without rebuilding original schedules.
- Ads are chosen deterministically at playback time from the persisted catalog, seek to the current
  break position, and never extend the program deadline. Short/unfillable remainders show Up Next.
- A nonempty enabled ad catalog also creates **Buy Me Maybe**, a generated commercials-only station.
  Its preferred number is 998; collisions advance to a free number, skipping reserved numbers.
  It shuffles clips back-to-back without the ordinary minimum-duration/four-hour threshold, tail
  skipping or padding. Items stay in the ad table, not the main library. Catalog
  changes invalidate only its schedule; disabling ads or emptying the catalog removes it.
- Tuning in the last five seconds of a program skips its tail. A future filler entry waits until its
  start before retrying ad selection, rather than sleeping through the break.
- The guide merges program and following filler into one cell. Music and Buy Me Maybe use hourly station blocks rather than individual songs or ads; Off Air gaps remain visible.
- Each guide opening selects the current station and wall-clock program after loading; subsequent browsing is not reset by schedule reloads.

## Startup, updates and lifecycle

Startup starts in loading state for an existing sign-in, awaits the automatic saved-lineup refresh,
then queries active channels. Only a successful settled empty result displays no-channel guidance.
The loading card uses the existing beret wordmark and an indeterminate animated spinner.
Failures and confirmed empty states retain the wordmark but not the spinner. Missing
connection/initial-sync configuration returns to onboarding.

Music, re-sync, ad catalog and included-library updates are controller-owned and survive screen navigation.
Guide rebuilding hides rows behind progress/elapsed time and permits only Channels and Settings controls.
Failed flag updates restore the previous value; failed imports preserve selections until commit.
Playback reconciliation preserves open menus. Completing a scan after activity stop does not tune;
resume starts the appropriate current/replacement station. Sign-out cancels and joins controller work.

## Playback compatibility and transitions

Direct playback is preferred for the supported container/video/audio policy in `Playback`.
Music formats outside the direct-play policy (or missing format metadata) use the Plex audio
music universal transcoder with HTTP/MP3 output, explicitly loaded as progressive audio despite
the endpoint's `.m3u8` suffix. A failed direct music stream retries through audio
conversion once per airing; conversion failures show a player error code and available HTTP status
instead of a silent Up Next loop. Diagnostics omit raw exception messages, URLs and credentials.
Multi-audio selection uses an explicit Plex stream ID. Audio-only conversion allows video copy without
1080p/bitrate caps; required video conversion retains its compatibility limits.
Subtitles can use external or embedded text, otherwise Plex burn-in.
Locally rendered text subtitles use saved Small/Medium/Large size and White/Yellow/Cyan/Green
color choices, defaulting to Medium and White on a 60%-opaque black background. Appearance updates
apply through the PlayerView without retuning and override embedded text styles and font sizes.
Bitmap and server-burned subtitles are not restyled locally.

HDR Auto checks connected display and decoder support. Convert requests SDR tone mapping;
Prefer native bypasses the capability-driven conversion choice. Native HDR errors retry SDR once.
Normalization is off by default and requests Plex audio transcoding only for eligible selected tracks;
HDR conversion, Dolby Vision and selected subtitles currently prevent normalization. Skipped normalization
and playback failures are visible, not silent volume guarantees.

Station changes retain configurable static/roll effects. Same-station clips fade to black before media
replacement, hold black while loading, and fade in on the first frame (or music readiness).
Load/fade time is included in wall-clock seeking; menu muting survives replacement.
Start-over alone begins at zero and returns to the prior station when the one-shot ends.
The separate ad-only station is not eligible for start-over; its clips stay in the ad catalog,
and a rejected request displays the normal notification without interrupting playback.
Pending start-over work validates the tune generation before switching, so rollover or retuning
cannot restart an obsolete program. Action messages remain visible during filler and Off Air.
Rollover callbacks relocate playback against current wall time, including after clock rollback.

Plex HTTP calls are asynchronous and coroutine cancellation cancels their sockets, including stalled
response bodies. Focused program descriptions have a five-second HTTP deadline and one timeout
retry. An empty lineup during startup or sync shows progress until loading finishes.

## Performance contracts and validation limits

The guide checks device locale, 12/24-hour preference and time zone on its 30-second tick.
Time-axis labels invalidate on formatting changes even when the schedule window does not advance.
The focused program panel is also keyed on formatting settings, so its date/time range updates
without requiring a focus or program change.

Scheduler candidate indexes and reused lineup calculations preserve deterministic output.
Stock decade qualification aggregates duration by media kind/decade in one metadata pass and
reuses totals for unchanged rules, including renamed/renumbered stations. Edited rules/time blocks
fall back to normal resolution. It retains no per-decade item lists and generates no genre/decade
cross-product. Forty bounded candidates limit growth; qualifying stations still incur normal
schedule generation/storage costs.
Library list/map readers share a coalesced off-Main snapshot; invalidation prevents stale publication.
Parsed playback metadata is cached in memory for at most 16 items and 60 seconds, scoped to server,
credentials and guide revision. Display capability is checked live; remote track defaults can take
up to one minute to appear on a subsequent tune.

The test suite covers policy, persistence, sync/navigation, lifecycle, remote focus and selected rendering.
See the [readiness checklist](../plans/2026-10-23-rerun-phase1-core-tv.md) for remaining physical-TV checks.
