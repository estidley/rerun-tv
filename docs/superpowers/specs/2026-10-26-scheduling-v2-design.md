# Scheduling v2 design (approved decisions)

Status: **approved by Ethan on 2026-10-26, not implemented.** This is the source of truth for the
scheduling rewrite. The build order, tests and acceptance criteria are in the
[implementation plan](../plans/2026-10-26-scheduling-v2.md). Current behavior is described in the
[design reference](2026-10-23-rerun-design.md#scheduling-and-ads) until v2 ships; update that
reference (and README, AGENTS.md, `docs/channel-lineup.md`) as each step lands.

Items marked **(default)** are defaults chosen during review; Ethan didn't pick them explicitly.
Keep them configurable or easy to change. Everything else is an explicit decision.

## 1. Decisions

### 1.1 Grid: local quarter hour for every channel

- Every scheduled channel uses a **quarter-hour grid**. Each program starts at the first local
  `:00`, `:15`, `:30` or `:45` mark at or after the moment the previous program plus its ad break
  ends. If the previous program ends exactly on a mark, the next one starts there.
- Marks are computed in the **schedule time zone's local wall-clock time** (see 1.10), not UTC.
  That fixes zones with 30- and 45-minute offsets (India +5:30, Nepal +5:45, Chatham +12:45) and
  keeps marks correct across daylight-saving changes.
  - Spring forward: the skipped wall-clock hour simply has no marks.
  - Fall back: the repeated hour has marks in both offsets.
- This includes **Marvelous Timeline and other chronological (fixed-order) channels** (1.6).
- **Exceptions (unchanged):** music stations and Buy Me Maybe keep playing back to back with no
  grid and no ad breaks (1.11).
- Gaps between a program's end and the next mark are therefore always under 15 minutes.

### 1.2 Ad breaks fill the gap to the next quarter hour

- After every program on a gridded channel, the time up to the next quarter-hour mark is
  filled. The goal is that the next program starts exactly on the mark.
- Preferred ad break length is **3 to 8 minutes**.
- **Gap over 8 minutes:** first try a **gap filler** (1.3). If one fits, place it, then fill
  the remaining time to the next mark the same way. With no filler, fill the whole gap with ads.
- **Hard cap:** a single ad break never exceeds **about 23 minutes**. On a 15-minute grid this
  is effectively never reached, but keep the guard.
- **Ads can't fill the gap** (no ad catalog, ads off for the channel, or no combination of clips
  fits): the remaining time becomes a wait that shows the existing **Up Next card**. "If it
  doesn't work, it doesn't work." Never delay the next program to make ads fit.
- **Saved at build time:** ad clips are chosen when the schedule is built and **saved as schedule
  entries**, not computed at tune time. The same build inputs give the same clips.
- **Tune-in mid-break:** tuning in during a break plays the saved clip at the right offset.
- **Per-channel ads setting:** **Normal** (fill as above), **Off** (no ads; the gap becomes an Up
  Next wait, though fillers may still be used), or **Fixed N** (exactly N clips per break where
  possible, any remainder becomes an Up Next wait).
- Ads still come only from the separately synced ad catalog (Settings > Ad library).

### 1.3 Gap fillers

- A gap filler is one episode (or other short item) that **matches the channel's rules**, the
  active time block's rules (1.7) included, and **fits in the remaining gap** before the next
  quarter-hour mark.
- Fillers are picked at random. **Any episode is allowed, including finales.**
- Fillers **never advance a show's saved episode position** and never touch the movie deck.
- Fillers **exclude shows that have an active episode run** on that channel (1.5).
- Fillers are allowed only when the gap is **over 8 minutes**. In practice that means items of
  8 to 15 minutes such as shorts, cartoons and interstitials.
- Fixed-order channels (chronological and In order) don't use fillers; their gaps are ad-filled
  only (keeps the order strict).

### 1.4 Shuffled TV channels: normal picks

- Outside episode runs, shuffled channels keep today's picking: a random show weighted by
  episode count, playing that show's **next episode in order**. This **does** advance the show's
  position.
- Mixed movie and TV channels keep **one movie about every 6 hours** (`movieEveryMs`).
- **Movie-only channels** use the same quarter-hour grid and ad fill as everything else
  **(default; adjustable)**.

### 1.5 Episode runs (3 in a row) and daily run slots

- **Applies to:** shuffled channels that have TV episodes.
- **Run slots:** each such channel has **2 run slots per day at 4:00 PM and 8:00 PM local time**
  **(default)**.
- **What a run is:** at a run slot, the channel plays a **run of 3 consecutive episodes** of one
  show, from that show's saved position (E1, E2, E3).
- **Never split:** the three episodes are never separated. Between them, each episode's ad break
  fills to the next quarter-hour mark (1.2), with **no gap fillers inside a run**.
- **Next day:** the same slot continues the **same show** with the next three episodes (E4, E5,
  E6), and so on.
- **End of season:** the run **continues into the next season** **(default)**. When the show runs
  out of episodes, the slot picks a new random show.
- **Run slots are fixed start times:** whatever plays before a run slot must **end (with its ad
  break) by the slot start**. Pick a shorter item that fits, or ad-fill or wait if nothing fits.
- **Movies:** a mixed channel's due movie waits until after the run.
- **Time blocks:** if a run slot falls inside a time block whose rules exclude the run's show,
  that day's run is skipped. The run continues on the next day it's allowed, and its position is
  kept.
- **State:** each slot's show and next-episode position persist in the channel's saved scheduler
  state.
- **Show positions:** runs advance the show's position like normal picks, so a show never
  repeats episodes across runs and normal picks.

### 1.6 Fixed-order channels

- **Chronological** (`PlayOrder.CHRONOLOGICAL`, e.g. Marvelous Timeline):
  - Strict, exact order. Never skip, reorder or shorten.
  - **Ignores time blocks** (the editor should disable blocks for this order, or they're ignored).
  - **Starts each item on the next quarter-hour mark** after the previous item plus its ad break.
  - **Ads use exactly the same quarter-hour fill as every other channel** (1.2), "so that it's
    not different". The ads setting defaults to Normal; Off and Fixed N work as on other
    channels. The preferred break is 3 to 8 minutes. Fillers would break the strict order, so a
    gap over 8 minutes is **filled with ads up to the cap**. If ads can't fill it, the rest is
    an Up Next wait.
  - Fully deterministic from the saved deck.
  - No runs, no fillers.

  **Decision history:** Ethan's earlier "no grid, start whenever, 2 fixed ads between items" and
  the interim "2 fixed ads, then Up Next wait" rule are **superseded**. His latest decision is that
  the Timeline uses the same quarter-hour grid and ad fill as every channel.
- **In order** (`PlayOrder.IN_ORDER`): keeps its order (shows rotate in order; movies play by
  year). It's **exempt from shortening (1.7) and from runs (1.5)**, uses the quarter-hour grid
  with Normal ad fill, and gets no fillers.

### 1.7 Time-block overrun rule

- Before placing an item, find the next **time-block boundary**: the start or end of any block
  on that channel, in local time.
- If the item would **run more than 20 minutes past that boundary**, don't place it. Pick a
  **shorter matching item** from the same candidate pool that ends within the tolerance.
  - Shuffled episodes: try other shows' next episodes; don't skip within a show.
  - Movies: try the next deck movies that fit, keeping skipped movies in the deck.
- If nothing fits, place the gap as ads or wait up to the boundary, using the same fill rules
  (gap filler, then ads, then Up Next).
- **Fixed-order channels are exempt** (they place their next item regardless).
- The 20-minute tolerance should be a per-channel setting with a 20-minute default.
- The boundary rule is separate from run slots: content before a **run slot** has **zero**
  tolerance (1.5).

### 1.8 Library sync before schedule top-up

- Keep the **top-up model:** when a channel has **under 24 hours** of schedule left, append until
  it's **48 hours ahead**. **Never rewrite saved entries** during routine top-ups.
- Before a top-up, if the **last library sync is over 24 hours old**, run a **background library
  sync first** (`LibrarySync.sync()` with the saved selection).
  - **Plex down or sync fails:** build from the saved library and retry at the next top-up
    trigger. Never block playback on the sync.
  - **Cold start with an empty or expired schedule:** build the current window from the saved
    library immediately so something plays, then sync in the background. New content enters with
    the next appended day.
- **Removing a Plex item** rebuilds only the affected channel's **future**:
  - Keep the entry airing now (and its ad break).
  - Delete entries after it, then top up again.
  - **Keep the channel's saved state** (show positions, decks, run slots); drop missing keys from
    the deck and runs.
  - PR #11 already stopped the whole-channel wipe: `refreshChannels` keeps the airing entry and
    later entries up to the first one whose item is missing, deletes from there and keeps the
    saved state. What's left for v2 is dropping the missing keys from the deck and runs (Step 7).
- Changing **which libraries are included** still intentionally rebuilds schedules (unchanged
  invariant).

### 1.9 Upgrade

- On the first launch of the version that ships v2, run a **one-time future rebuild of every
  channel**, keeping the entry airing now. This lets the new grid, ads and runs take effect
  immediately.
- Store a settings flag so it runs only once.

### 1.10 Time zone

- **Setting:** add a Settings option, **Time zone: Automatic (device)** by default, or a manually
  picked zone ID.
- **Read per build:** the scheduler reads the effective zone **at each build/top-up**. Today it's
  read once at app start (`RerunApp.kt`). The guide clock and grid use the same zone.
- **Change detection:** store the zone used by the last build. Compare at app start, on resume
  and on `ACTION_TIMEZONE_CHANGED` (Automatic only), and when the manual setting changes.
- **On a change**, show **"Reset your schedule?"** with **Yes / No**:
  - **Yes:** keep the entry airing now on each channel, delete everything after it, rebuild in
    the new zone, and re-anchor run slots to the new local 4 PM and 8 PM.
  - **No:** keep all saved entries. They're absolute times, so they play on schedule but may no
    longer sit on local quarter-hour marks. New top-ups use the new zone.
  - Either way, record the new zone so the prompt doesn't repeat.

### 1.11 Unchanged channels

- **Music stations:** back to back, no grid, no ads, hourly guide blocks.
- **Buy Me Maybe:** ad clips back to back, no padding, hourly guide blocks, generated only from a
  nonempty enabled ad catalog.
- **From the Top** (hidden start-over channel): unchanged by v2. Since PR #11 it restarts movies
  as well as episodes (its rules use the restarted item's own kind) and never the Buy Me Maybe
  station.

### 1.12 Guide display

- Each **program** gets its own guide cell from its start to its end.
- **Ad breaks and Up Next waits fold into the preceding program's cell**, as filler does today
  (`guideBlocks`). Individual ads are never listed.
- **Gap fillers** get their **own cells** (they're real programs). They're short (8 to 15
  minutes), so the cell may be narrow; titles ellipsize as today.
- **Off Air** still marks true gaps (no entries).
- Music and Buy Me Maybe keep hourly station blocks.
- Run episodes show as consecutive cells. Optionally add a "Part 1 of 3" label later (not
  required).
- Cell width depends on the channel column, which grows with the guide layout since PR #11, so
  a 15-minute cell (one twelfth of the 3-hour timeline) is narrowest in Large.

### 1.13 Tuning in

- **Program:** seek to the offset, as today. Keep the 5-second end skip.
- **Saved ad entry:** play that clip, seeking to the offset within it.
- **Up Next wait:** show the Up Next card until the next entry.
- Playback continues to follow wall-clock time (existing invariant). Ads never delay the next
  program.

## 2. Current code facts (main at 6078bcd, after PR #11)

| Area | Today | Where |
|---|---|---|
| Grid | 30-minute grid computed in **UTC** epoch ms (`GRID_MS`, `gridFloor`, `gridCeil`); each video program padded to the next `:00/:30` with a `FILLER` entry | `core/src/main/kotlin/com/rerun/core/schedule/Schedule.kt`, `Scheduler.kt` |
| Build model | `Scheduler.extend` appends from the last saved end (or the current grid floor) to 48 h ahead; never rewrites saved entries | `core/.../schedule/Scheduler.kt` |
| Top-up | `ScheduleRepo.ensure` tops up channels with under 24 h left (`needsTopUp`). It's lazy. Tuning tops up one channel (`ensure(channelId)`, `TunerEngine`); a library sync tops up all (`ensure(now)`, `LibrarySync`). Since PR #11 the guide tops up **only the visible rows plus a margin** (`ensure(ids, now)`, `wantRange`), reads them with `entriesFor(ids, …)`, and repeats the top-up **every 10 minutes while open** (`ENSURE_INTERVAL_MS`). It prunes entries that ended over 6 h ago | `app/src/main/java/com/rerun/tv/data/ScheduleRepo.kt`, `app/.../ui/guide/GuideScreen.kt`, `core/.../schedule/Tune.kt` |
| Randomness | Since PR #11 a channel with no saved state gets a **random seed** on its first build (`ChannelState(seed = Random.nextLong())` in `ScheduleRepo.topUp`); before that it was `row.id.hashCode()`. The seed and the rest of the scheduler state (deck, recent, episode pointers, `msSinceMovie`, `rngCalls`) are saved as JSON, so a channel is deterministic **from its saved state** but differs between installs and after a state reset | `ScheduleRepo.kt`, `Scheduler.kt`, `Schedule.kt` (`ChannelState`), `core/.../codec/Codecs.kt` (`StateCodec`) |
| Picks | Shows: weighted random show, next episode in order, pointer advances on every pick. Movies: shuffled deck with a recent hold-back. Mixed: movie when `msSinceMovie >= movieEveryMs` (6 h). In order: round-robin shows; movies by year. Chronological: persisted ordered deck | `Scheduler.kt` |
| Time blocks | `TimeBlock` checked only at each program's start, so programs can run into a block. Only built-in block: Cartoon Network weekdays 4–7 PM, Toonami titles | `core/.../model/Channels.kt`, `core/.../catalog/Catalog.kt` |
| Ads | Chosen **at tune time** by `adFiller` (seeded by channel and break start), filling the whole FILLER back to back; Up Next card if none fits | `core/.../schedule/AdFiller.kt`, `app/.../player/TunerEngine.kt` |
| Entries | Room `entries` table: `kind` stored as a **string** and decoded with `EntryKind.valueOf`; unique `(channelId, startMs)`; DB version 3 | `app/.../data/Db.kt` (`EntryRow`) |
| Guide | 3-hour window; filler folded into the previous program; gaps are Off Air; music and Buy Me Maybe hourly. Since PR #11 the channel column shows a vertical number and a wordmark, and its width follows the row height (`iconDp * 400 / 270 + 54` dp, from about 125 dp in Compact to almost 300 dp in Large), so the timeline and every program cell get narrower. While updating it shows skeleton rows. Touch controls (PR #12) work on the rows and cells | `core/.../schedule/GuideBlocks.kt`, `app/.../ui/guide/GuideScreen.kt`, `GuideLayout.kt` |
| Tune | `locate` finds the entry covering now; 5 s end skip; Off Air waits for the next entry | `core/.../schedule/Tune.kt`, `TunerEngine.kt` |
| Time zone | `Scheduler(ZoneId.systemDefault())` built once at app start. Since PR #11, `ClockWatch` notices a clock jump or a zone change on the guide's 30-second tick (wall vs. elapsed time and `TimeZone.getDefault().id`); the guide then re-floors its window and relabels times. It doesn't prompt or rebuild. `clockTime`/`clockDate` follow the device locale, 12/24-hour setting and zone | `app/.../RerunApp.kt`, `app/.../ui/ClockWatch.kt`, `app/.../ui/player/Overlays.kt` |
| Library sync | **Manual only** (onboarding, Settings resync and libraries). App start only re-plans from the saved library (`refreshChannelsForAppVersion`) | `app/.../data/LibrarySync.kt`, `RerunApp.kt`, `app/.../ui/TvController.kt` |
| Item removal | Since PR #11, `refreshChannels` keeps the airing entry and later entries up to the first entry whose item is missing (`EntryDao.firstMissingStart`), deletes from there (`deleteFrom`) and **keeps the channel's state**; the next top-up refills. The hidden From the Top channel is still deleted outright. The saved deck and pointers can still name missing keys | `ScheduleRepo.refreshChannels`, `app/.../data/Db.kt` |
| Edits | Channel edits keep the current entry and rebuild the future (`resetFuture`) | `ScheduleRepo.kt`, `app/.../ui/channels/ChannelEditor.kt` |

## 3. Red-team findings and resolutions

| # | Risk or conflict | Resolution in this design |
|---|---|---|
| 1 | "Rebuild every 48 h" would rewrite a guide the viewer already saw and delay new content up to about 96 h | Keep the never-rewrite top-up (under 24 h left, 48 h ahead); add a sync before the top-up (1.8) |
| 2 | No automatic library sync exists | Background sync when the last sync is over 24 h old, fail-soft (1.8) |
| 3 | Daily syncs plus today's whole-channel wipe on item removal would reset channels often | Rebuild only the future, keep state (1.8) |
| 4 | Random fillers advancing show positions would skip episodes and spoil runs | Fillers never advance positions; exclude active-run shows (1.3) |
| 5 | On-the-hour alignment leaves up to 59-minute gaps | Quarter-hour grid; gaps under 15 min, filled with ads (1.1, 1.2) |
| 6 | Long ad walls | Preferred 3–8 min; over 8 min try a filler; about 23 min hard cap; otherwise Up Next (1.2) |
| 7 | Runs broken by grid padding or fillers | Runs never split; only ad fill between episodes (1.5) |
| 8 | Run slot vs. preceding long program, due movie, time blocks | Zero-tolerance end before the slot; movie waits; blocked day skipped without losing position (1.5) |
| 9 | Fixed order vs. shortening, blocks and fillers | Chronological and In order exempt from shortening and fillers; chronological ignores blocks (1.6) |
| 10 | Marvelous Timeline "start whenever" vs. "every channel on the quarter hour" | Ethan decided: same quarter-hour grid and ad fill as every channel; ads fill gaps over 8 min (no fillers); earlier fixed-2-ads rule superseded (1.6) |
| 11 | Movie-only channels can't use episode fillers | Grid plus ad fill (gaps under 15 min); Up Next if no ads (1.4) |
| 12 | Nothing fits a gap | Filler, then ads, then Up Next wait; never delay the next program (1.2) |
| 13 | Guide clutter from ads and fillers | Fold ads and waits into the previous cell; fillers get their own cells (1.12) |
| 14 | Tune-in mid-break must be deterministic | Ads saved as entries at build time (1.2, 1.13) |
| 15 | Time zone changes mid-program | Yes keeps now-playing and rebuilds the future; No keeps absolute times (1.10) |
| 16 | 30/45-minute offset zones and DST under a UTC grid | Local wall-clock marks (1.1) |
| 17 | Music and Buy Me Maybe | Explicitly unchanged (1.11) |
| 18 | Upgrade with old 30-minute entries | One-time future rebuild keeping now-playing (1.9) |
| 19 | Deterministic fingerprints in tests will change | Intentional; update fingerprints in the PR that changes them and say so |
| 20 | Seeds are random per install since PR #11, so "same inputs give the same output" can't mean the same schedule on every device | Determinism is defined **from a saved `ChannelState`**: tests build the state with an explicit seed (or save one first) and assert that the same state, pool and time give the same entries. Never assert on a seed derived from the channel ID |
| 21 | The wider wordmark channel column (PR #11) narrows every program cell, and 8–15 minute fillers get very thin | Check filler-cell readability in all three guide layouts on a TV (1.12) |

## 4. Recommended numbers and settings

| Setting | Value | Scope |
|---|---|---|
| Grid | Local quarter hour | All gridded channels |
| Preferred ad break | 3–8 min | Global constant |
| Filler threshold | Gap over 8 min | Global constant |
| Ad break hard cap | 23 min | Global constant |
| Ads policy | Normal / Off / Fixed N | Per channel (default Normal on every channel, Timeline included) |
| Block overrun tolerance | 20 min | Per channel (default 20) |
| Episodes per run | 3 | Per channel (default 3) |
| Run slots | 4:00 PM and 8:00 PM local **(default)** | Per channel |
| Run season end | Continue into next season **(default)** | Global |
| Movie cadence (mixed) | 6 h | Per channel (exists: `movieEveryMs`) |
| Top-up | Under 24 h left, 48 h ahead | Global (unchanged) |
| Sync staleness | 24 h | Global |
| Time zone | Automatic | App setting |