# Rerun — design spec

*Working name. Old-school cable TV, built from your own Plex library, running on a Fire TV.*

Date: 2026-10-23 · Status: draft for review

---

## 1. What we're building

A sideloaded Android app on the Fire TV that makes a personal Plex library behave like 1990s cable. There are always channels on, always something playing, and he watches whatever's on.

### Requirements (stated)

- Runs on the TV itself. Target: a **Fire TV Edition TV (Fire OS)**. No extra box.
- Pulls from his **Plex** server.
- Channels mix **movies and TV shows**; shows play their episodes **in order**.
- **Network channels**: Disney Channel, PBS, SYFY, Nickelodeon, and so on, built from what he owns. A network with no content stays **hidden but kept in the background** and turns on by itself when content shows up.
- **Streaming content lives on the classic networks**, not on app-named channels. Shows and movies are routed by **owner family + audience**. Disney+ kids shows go to Disney Channel, preschool to Disney Junior, animated action to **Disney XD** (its own channel), and Disney's grown-up material (Marvel, live-action Star Wars) to a new **Touchstone** channel. Paramount+ kids shows (the new *Avatar* series) go to **Nickelodeon**. Max originals go to HBO, Hulu originals to FX, Peacock originals to SYFY/USA. Netflix, Prime Video and Apple TV+ have no cable twin, so their originals are spread **by genre** into existing networks (sci-fi → SYFY, comedy → Comedy Central, kids animation → Cartoon Network, documentary → Discovery/History, other drama → USA). Nothing launches the real apps.
- **Custom channels** he builds himself ("Fright Night").
- **Real live TV** from his **HDHomeRun** tuner, mixed into the same lineup. **No DVR**; Plex already handles recording.
- **Always-on schedule**: tune in mid-program at the exact spot. No restart, no pause.
- **Old commercials** fill the gaps between programs, pulled from a website or API if one exists.
- No ripping or downloading of outside content.

### Assumptions

- Single TV, single household user.
- No pause, rewind or skip. Live is live.
- Plex server and HDHomeRun are on the same home network as the TV.
- Programs start on :00 and :30 boundaries.
- Watching here should **not** change Plex watched status or Continue Watching.

---

## 2. Inspiration (ideas only, no code copied)

| Project | Idea we're borrowing |
|---|---|
| Tunarr / dizqueTV | Lineup loops from a channel start time; *flex* padding to round times; *cyclic shuffle* (random show order, episodes in sequence); filler is chosen at play time, not schedule time |
| pseudo-channel | Saturday-morning cartoon blocks; vintage commercials in the gaps |
| PseudoTV Live | Channels auto-built from genre, network and studio; info strip on channel change |

**Where we differ.** All of those are **servers** that pretend to be a tuner, and watching them through Plex needs Plex Pass. Rerun is a **native player on the TV**, so it needs no server, no Plex Pass, and no PC left running. It also avoids the "player breaks when the next episode's format differs" problem dizqueTV documents, because we own the player.

---

## 3. Architecture

One Android app, Kotlin + Jetpack Compose (same stack as Kyber), Media3/ExoPlayer for playback, Room for local storage. Fire OS 7/8 (Android 9/11), minSdk 25.

```
 ┌───────────── Sources ─────────────┐   ┌──────── Brain ────────┐   ┌──── Screen ────┐
 │ Plex server  (library, streams)   │──▶│ Library cache (Room)  │   │ Player + OSD   │
 │ HDHomeRun    (live TS, lineup)    │──▶│ Channel catalog       │──▶│ Guide grid     │
 │ TMDb         (studio metadata)    │──▶│ Scheduler (48h ahead) │   │ Channel mgr    │
 │ Commercials  (Plex folder + IA)   │──▶│ Tuner engine          │   │ Settings       │
 └───────────────────────────────────┘   └───────────────────────┘   └────────────────┘
```

### 3.1 Units (each one testable alone)

| Unit | Job | Depends on |
|---|---|---|
| `plex/PlexAuth` | 4-character PIN sign-in via plex.tv/link; stores token | network |
| `plex/PlexServers` | Lists the account's servers, picks the best connection (local first, then remote, never relay unless forced) | PlexAuth |
| `plex/PlexLibrary` | Pages through movie and show sections with `includeGuids=1`; maps to `MediaItem` | PlexServers |
| `plex/PlexStreams` | Builds the play URL for an item and start offset: direct play if codecs are safe, else Plex universal transcode (HLS) | PlexServers |
| `hdhr/HdhrClient` | Finds the HDHomeRun (Plex's tuner list → UDP discovery → manual IP); reads `lineup.json`; builds `http://<ip>:5004/auto/v<ch>` | network |
| `hdhr/HdhrGuide` | Now/next guide for live channels from the HDHomeRun guide service (uses `DeviceAuth` from `discover.json`) | HdhrClient |
| `meta/Tmdb` | Phase 4, optional: for items whose Plex studio is only a production company, look up TMDb networks/production companies (via the item's TMDb GUID) to find the owner family | user's free TMDb key |
| `library/` | Room DB of items, shows, episodes, network aliases, TMDb facts | — |
| `channels/Catalog` | Built-in channel definitions (networks, curated, themes) and the owner-family routing table with name aliases and default numbers | — |
| `channels/Resolver` | Rules → content pool; decides active vs **dormant** | library |
| `schedule/Scheduler` | Writes the **locked** schedule 48h ahead per channel | Resolver |
| `filler/` | Fills gaps at play time: commercials → "Up Next" card | Plex, Internet Archive |
| `tuner/TunerEngine` | "Tune to channel X now": find entry, compute offset, drive ExoPlayer, cover the load with static | everything |
| `ui/` | Player overlays, guide, channel manager and editor, settings, onboarding | TunerEngine, Scheduler |

### 3.2 Channels

Every channel is a **definition** (rules) plus a **lineup number**.

| Kind | Examples | How content is found | Numbers |
|---|---|---|---|
| Live | 4.1 KDFW, 8.1 WFAA | HDHomeRun lineup | Real numbers (4.1, 5.1…) |
| Network | Disney Channel, Nickelodeon, PBS, SYFY, Cartoon Network, MTV, USA, TNT, Comedy Central, ABC Family… | Show's network (Plex `studio` on shows) matched through an **alias table** (e.g. "Disney Channel", "Disney XD", "Toon Disney" → Disney Channel). Movies: TMDb production company or a Plex label/collection | 100–199 |
| Curated | **Boomerang**, **Toonami** | Shows that *aired on* the channel but whose Plex network points elsewhere (e.g. *Dragon Ball Z* → Fuji TV, *Scooby-Doo* → CBS). Ships with a built-in title list matched by TMDb ID, then title + year. **Boomerang**: classic Hanna-Barbera, Looney Tunes, MGM and other classic cartoons, plus anything tagged with the Boomerang network. **Toonami**: the Cartoon Network action/anime lineup (DBZ, Sailor Moon, Gundam Wing, Tenchi, Outlaw Star, Yu Yu Hakusho, Rurouni Kenshin, ThunderCats, Voltron, Reboot…). Also available as a **block on Cartoon Network** (weekdays 4–7 PM, Saturday late night) | 100–199 |
| *(routing, not a channel kind)* | Owner families: Disney, Paramount, Warner, NBCU, Hulu, Netflix/Prime/Apple | `Router`: item's network or studio (e.g. "Disney+", "Marvel Studios", "Lucasfilm", "Paramount+", "Max", "Hulu", "Netflix") picks the family; content rating and genre pick the channel inside it (table in §3.2a) | — |
| Theme | Horror, Sci-Fi, Family, Comedy, Action, 80s Movies, Saturday Morning | Genre, decade, rating | 300–399 |
| Custom | "Fright Night" | Editor on the TV: genres, shows, collections, labels, decades, ratings, movies/shows/both, order, time blocks | 400+ |

### 3.2a Owner-family routing

Used when an item's network/studio isn't itself a classic network name. Ratings: *preschool* = TV-Y; *kids* = TV-Y7, TV-Y7-FV, TV-G, G; *family* = kids + TV-PG, PG; *grown-up* = everything else (PG-13, TV-14, R, TV-MA, unrated).

| Family (network/studio strings) | → Channel |
|---|---|
| **Disney** (Disney+, Walt Disney Pictures/Animation/Television, Pixar, Marvel Studios/Animation/Television, Lucasfilm, Disney Television Animation) | preschool → Disney Junior · animation that is action/sci-fi/adventure or from Marvel/Lucasfilm, family-rated → **Disney XD** · other family → Disney Channel · grown-up → **Touchstone** |
| **Paramount** (Paramount+, Nickelodeon Animation Studio, Nickelodeon Productions, Nickelodeon Movies) | preschool → Nick Jr. · family → Nickelodeon · grown-up comedy → Comedy Central · other grown-up → Paramount Network |
| **Warner** (HBO, Max, HBO Max, Warner Bros. Animation, Cartoon Network Studios, Williams Street) | family → Cartoon Network · grown-up animation → Adult Swim · other grown-up → HBO |
| **NBCU** (Peacock) | sci-fi/fantasy/horror → SYFY · other → USA |
| **Hulu** | → FX |
| **Netflix / Prime Video / Amazon Studios / Apple TV+** | preschool → PBS Kids · family animation → Cartoon Network · sci-fi/fantasy/horror → SYFY · comedy → Comedy Central · documentary → Discovery (history topics → History) · reality → TLC-style lands on USA · other → USA |

An item routed nowhere still appears in theme and custom channels.

**Dormant rule.** A channel stays hidden when its pool holds **less than 4 hours** of content. That keeps three movies from looping all weekend. Dormant channels are still listed (grayed out) in the channel manager, can be renumbered or used as a source inside custom channels, and switch on by themselves after a sync.

### 3.3 Scheduling

- **Locked 48-hour schedule.** Each channel's schedule is written to the database 48h ahead and topped up when less than 24h remains. Once written, it doesn't change, so a library sync can't yank you out of the middle of a movie and the guide always matches what plays.
- **Movies: "deck" shuffle.** Shuffle the whole pool and deal it out. No repeats until the deck runs out, and no title repeats within the last ~20% of the deck when a new deck starts.
- **Shows: cyclic shuffle.** The next show is picked at random, weighted by episode count; each show keeps a **saved episode pointer**, so episodes always go in order and wrap at the end.
- **Mixed channels** (e.g. Disney Channel with DCOMs): shows and movies interleave at a channel-set ratio. Default: one movie per ~6 hours, preferring evening slots.
- **Time blocks (optional per channel):** "Fri–Sat 21:00–03:00 → R-rated only", "Sat 07:00–12:00 → cartoons". A block narrows the pool while it's active.
- **Padding:** every program's slot rounds up to the next :00 or :30. The leftover time is a **filler block** with only a length. *What* fills it is chosen at play time (Tunarr's "flex" idea), so commercials can change without rescheduling.
- **Seeded randomness** per channel, so a schedule can be regenerated identically for debugging.

### 3.4 Tuning (the "it's already on" feeling)

1. Find the schedule entry covering *now*.
2. Compute `offset = now − entry.start`.
3. Play with ExoPlayer starting at `offset`.
4. Show static noise and the green channel number until the first frame renders.
5. If loading took *n* seconds, start at `offset + n` so we still land on schedule.
6. Pop the info banner (title, year, rating, time left, up next) for 5 seconds.

While a channel plays, the engine queues the next entry so programs roll over seamlessly.

**Playback choice.**
- **Direct play** for H.264/HEVC video with AAC/AC-3/E-AC-3 audio in MKV/MP4.
- Anything else (DTS, TrueHD, odd containers) goes through **Plex's transcoder**, often audio only, which is cheap for the server.

**Live channels.** Play the HDHomeRun MPEG-TS stream directly. Closing the stream frees the tuner.

### 3.5 Filler and commercials

The source of each gap is decided in this order:

1. **Your own commercial library.** A Plex library or collection named **"Commercials"**. Optional subfolders or labels by decade (`1990s`) or channel (`Nickelodeon`), so Nick gets Nick bumpers.
2. **Prepared commercial packs (owner's PC, one-time).** A prep tool in `tools/commercials/` downloads a chosen archive.org item (first target: `2000VariousCommercials`), splits compilation reels into single spots by black-frame detection, **upscales** them (Real-ESRGAN via Video2X if a GPU is present, else ffmpeg denoise + Lanczos), and writes them into the Plex "Commercials" library folder with decade labels. The files stay on the owner's machine; the public repo ships only the tool, never the videos.
3. **Internet Archive: AdViews.** Duke University's collection of thousands of digitized vintage commercials (1950s–1980s) on archive.org. Found with the archive.org search API and **streamed** from archive.org, not downloaded or stored.
4. **"Up Next" card.** Channel logo, next program's poster and a countdown. Always works, offline too.

Rules:
- Fill the gap with as many commercials as fit, and finish with the Up Next card for any remainder under ~30s.
- **Kids channels** exclude commercials whose title or subject matches tobacco, alcohol, firearms or medication. This matters a lot: cigarette ads ran on TV until 1971 and are in the 50s–60s material.
- Per-channel switch: commercials on / off / Up Next only.
- Commercials only play **between** programs, never mid-show.

### 3.6 Screens and remote

Fire TV remote mapping:

| Button | In player | In guide |
|---|---|---|
| ▲ / ▼ | Channel up / down (static + number) | Move between channels |
| ◀ | Previous channel ("last") | Earlier in time |
| ▶ | Info banner | Later in time |
| OK | Info banner (press again: details) | Tune to channel |
| ☰ Menu | Open guide | Channel options (hide, renumber, edit) |
| Back | Open guide | Back to player; again exits |
| ⏯ | Toggles captions ("CC") | — |

**Screens:**
- **Onboarding:** Plex link code, server choice, HDHomeRun found or not.
- **Player:** full screen, with these overlays:
  - static noise
  - channel number (VCR-style green)
  - info banner
  - "Technical Difficulties" card on errors
  - optional CRT look (scanlines, vignette)
- **Guide:** grid with channels down the side and 30-minute columns, a now-line, and a preview of the focused cell.
- **Channel manager:** active and dormant channels, plus a "New channel" entry that opens the editor.
- **Settings:** sources, commercials, CRT look, idle timeout, re-sync, sign out.

---

## 4. Red team: what could go wrong, and what we do about it

| # | Risk | Severity | Mitigation |
|---|---|---|---|
| R1 | **Plex network names are messy** ("Disney Channel" vs "Disney XD" vs "Disney Junior"; "Syfy" vs "Sci Fi Channel") | High | Alias table per network, matched case-insensitively. Unmatched networks are listed in the manager so the owner can map them by hand |
| R1b | **Block-era channels (Toonami, Boomerang) can't be found from network data** at all | High | Curated title lists shipped in the app (§3.2), editable on the TV. the owner's Plex label "Toonami"/"Boomerang" also counts. Toonami bumpers can live in his Commercials folder labeled "Toonami" |
| R2 | **Movies and some originals list only a production company** in Plex (not "Disney+" or "Netflix") | High | Routing table includes the big production studios (Marvel Studios, Lucasfilm, Pixar, Nickelodeon Movies…). Plex labels/collections as a manual override ("Netflix", "DCOM"). Phase 4 adds TMDb lookups for the rest |
| R3 | **Routing misfiles a show** (a PG-13 Disney+ show that *feels* like Disney Channel lands on Touchstone) | Medium | Rating-based rules are predictable; the channel manager can pin any show to a channel, and pins win over routing |
| R4 | **Library changes break an in-progress schedule** | High | 48h locked schedule (§3.3). A deleted item still in the schedule falls back to the Up Next card for its slot |
| R5 | **Tiny pools loop constantly** (2 SYFY shows) | Medium | 4-hour dormant threshold; repeat-distance rules in the deck shuffle |
| R6 | **Tune-in lag** (seeking into a big remote file takes 1–3s; transcode start 3–6s) | Medium | Static covers it; offset adds the lag. Prefer direct play. Pre-resolve URLs for ±1 channel |
| R7 | **Codec gaps on Fire TV** (DTS/TrueHD audio, PGS subtitles) | Medium | Codec check per item at sync; audio-only transcode via Plex. Subtitles off by default; CC toggles text subs (SRT) only in v1 |
| R8 | **Plex watched status / Continue Watching polluted** | Medium | Never send playback timeline or scrobble reports to Plex |
| R9 | **HDHomeRun tuners busy** (Plex DVR recording) or **ATSC 3.0 / DRM channels** (HEVC + AC-4 audio, encryption) won't play | Medium | Handle 503 ("all tuners in use") with a "No Signal" card. Skip lineup entries tagged `drm`. Mark channels that fail AC-4 as unsupported |
| R10 | **Live guide data**: Plex's EPG endpoints are undocumented | Medium | Use the HDHomeRun guide service instead. Fallback: channel name + "Live" |
| R11 | **Commercial content**: tobacco/alcohol ads on a kids channel | High | Keyword/subject filter on kids channels (§3.5). Per-channel off switch |
| R12 | **Commercial rights**: vintage ads are generally still copyrighted | Medium | Private home viewing only. Downloaded packs live only in the owner's own Plex library; the repo contains the prep tool, never media |
| R13 | **AdViews skews 50s–80s**; 90s ads are sparse there | Low | Decade filter per channel. the owner's Plex "Commercials" folder covers the 90s. Up Next card as fallback |
| R14 | **archive.org slow or down** | Low | Short timeout (3s) → skip to next commercial → Up Next card. Never stalls the channel |
| R15 | **Left running all night**: Plex transcodes and an HDHomeRun tuner stay busy for hours | Medium | Idle timer (default 4h without a button press) → "Are you still watching?" → stop streams |
| R16 | **Fire TV memory** (TVs have little RAM; two players at once is risky) | Medium | One ExoPlayer instance; pre-resolve URLs instead of pre-buffering video |
| R17 | **Plex server asleep or unreachable** | Medium | Retro "We're experiencing technical difficulties" card with auto-retry. Live channels keep working if only Plex is down |
| R18 | **Plex connection picking** (local vs remote vs relay; certificates) | Low | Prefer local `plex.direct` HTTPS connections; test each, cache the winner, re-test on failure |
| R19 | **Big libraries** make the first sync slow | Low | Paged sync (`X-Plex-Container-Start/Size`), progress bar, incremental resync by `updatedAt` |
| R20 | **Can't compile in this workspace** (no Google/Maven access) | Medium | Pure-Kotlin core (scheduler, resolver) gets JVM unit tests. First full build runs on the developer's PC, like Kyber, ideally driven remotely through his PC with his OK |
| R20b | **No GPU shaders on Fire OS** (Android's AGSL shaders need Android 13; Fire OS 7/8 is Android 9/11), so true screen curvature isn't possible | Low | Build the CRT look from cheap overlays drawn once: scanline bitmap, radial vignette, soft glow, rounded-corner tube mask. No per-frame cost on the video |
| R21 | **Scope is big** | High | Phased build (§5). Each phase ships something watchable |

**Defaults chosen** (flag any to change):
- Name **Rerun**.
- Channel number ranges in §3.2.
- 4-hour dormant threshold.
- 48-hour locked schedule.
- No mid-show commercial breaks.
- Captions off by default.
- 4-hour idle timeout.
- Disney's grown-up channel is named **Touchstone**.
- Netflix / Prime Video / Apple TV+ originals are spread **by genre** into existing networks (no app-named channels).

---

## 5. Phases

| Phase | Ships | Done when |
|---|---|---|
| **1 — Core TV + look & feel** | Plex sign-in, library sync, network + curated + theme + custom channels, scheduler (incl. time blocks), tune-in-mid-show, info banner, guide, channel manager/editor, Up Next filler, **and the full retro look**: CRT overlay (scanlines, vignette, glow, rounded-tube mask; Off/Subtle/Full), channel-change static with vertical roll, power-on/off line collapse, VCR-green OSD, 90s cable-guide styling | the owner can flip between auto-built channels, land mid-program at the right spot, and it already *looks* like a 90s TV for testing |
| **2 — Live TV** | HDHomeRun discovery, live channels in lineup and guide | Flip from Disney Channel to 8.1 and back |
| **3 — Commercials** | Commercial prep tool (download → split → upscale → Plex folder), Plex "Commercials" library, AdViews streaming, kids filter | Gaps play clean, upscaled vintage ads; kids channels stay clean |
| **4 — Smarter routing** | TMDb lookups for items whose Plex studio is only a production company; per-show channel pins UI | Netflix/Disney+ originals with odd studio names land on the right network |
| **5 — Polish** | Idle timer, captions | Overnight idle stops streams; CC works |

---

## 6. Testing

- **Unit (JVM):**
  - scheduler:
    - padding math
    - deck shuffle never repeats within a deck
    - episode order preserved
    - 48h lock survives a library change
    - time blocks
  - resolver: aliases, dormant threshold
  - filler planner: fits commercials into the gap
  - kids filter
- **Parser fixtures:** recorded Plex, HDHomeRun, TMDb and archive.org JSON.
- **On-TV checklist:**
  - tune lands within ±2s of the guide
  - channel up/down under 2s
  - live channel plays and frees the tuner on exit
  - Plex unplugged → difficulties card → auto-recovers
  - overnight idle stops streams
