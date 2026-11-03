# Shared schedules: deterministic scheduling code

Status: **opt-in pilot implemented locally**, authorized on 2026-10-28.
Required JVM tests, debug assembly and Android lint pass; live acceptance remains open.
No hosting, coordinator, shared credentials or Plex watch-history writes.
See the [delivery plan](../plans/2026-10-27-shared-schedules.md).

## Recipe and scope

The manually enterable code identifies a versioned UTC recipe, random seed and anchor
day. Version 1 fixes the approved preferences rather than offering an unimplemented
mixed-show option. Future presets require an explicit recipe version. Core decode
accepts case-insensitive Crockford Base32, O/I/L aliases, hyphens and surrounding
spaces, and requires exactly 24 symbols after hyphens are removed. The app validator
and the code field bound raw input to 128 characters before decode, so grouped codes
are accepted. Join opens a dedicated entry form with vertically spaced confirmation
actions. The pending entered code and refresh status stay visible after confirmation,
without claiming it is active before saving.
QR and platform share-link integration are not required to generate programming.
The code is a scheduling recipe, not encryption, authentication or Plex access.

The v1 payload is 24 symbols: an 8-bit version, signed 64-bit seed, signed 32-bit
UTC epoch day, and 16 checksum bits derived from domain-separated SHA-256. UTC and
the approved block policy are fixed by version 1 rather than encoding a zone string.
Core maps O to 0 and I/L to 1. Generated codes use digit 0, not letter O; the
display uses a larger monospace font with the slashed-zero feature requested.

Only stock predefined video channels with configured numbers 100-199 participate.
The template number is not checked: a stock theme renumbered into that range
participates when its other programming fields still match. User-created channels
never participate. Stable catalog identity determines whether a channel is predefined,
not `ChannelKind.CUSTOM` or the database `builtIn` flag. Renaming is presentation
only; an in-range renumber still changes the salt and therefore the sequence.
Rules/order/time-block edits are conservatively excluded rather than silently ignored.
Stock time-block/chronological policies that the pilot does not implement stay local
and must be identified as limitations. Music, Buy Me Maybe and start-over stay local.
The ordinary scheduler remains the default when no recipe is enabled.

## Programming rules

- Fixed 3-hour windows start at 00/03/06/09/12/15/18/21 UTC. DST and device zone
  affect display labels only. The anchor is midnight UTC. A request whose start is
  before that midnight omits the pre-anchor portion but returns whole entries
  intersecting the portion at or after the anchor. Requests entirely before the
  anchor return no entries.
- One show owns each TV window. Eligible episodes sort by season, then episode, then
  UTF-8 key bytes. A missing season or episode sorts last, not as 0 the way
  `Scheduler` does. Continue from the first unplayed eligible episode at the show's
  next opportunity. Wrap only after exhausting the show's eligible sequence. Do not
  skip an episode merely because it does not fit the remainder of a window.
- Start episodes on UTC quarter-hour marks; pad to the next mark using local
  commercials/Up Next. Episodes longer than 3 hours are excluded visibly; an episode
  exactly 3 hours long is eligible. Empty eligible shows are not selected.
- Equal-exposure seeded show rounds give each eligible show one window before its
  second. Prevent consecutive windows of the same show when alternatives exist.
  The permutation algorithm may constrain round endpoints to achieve this without
  recursively generating preceding rounds. Those fixed endpoints explain no-repeat
  at round boundaries only. A deck of two or fewer items is one fixed permutation.
  They are not why a UTC instant maps to an entry, and a two-item test is not the
  50,000-item age gate. Time position is the window index plus each show's
  quarter-unit cycle.
- Mixed channels alternate TV/movie windows with a stable channel-specific phase.
  Phase derives from the code and channel identity/number, not catalog iteration order.
  When only one content type qualifies, use it in every window; neither means no output.
- One movie per movie window. Use a separate deterministic repeat-resistant deck.
  Exhaust movies before repeating, preventing immediate repeats when alternatives exist.
  Source duration must be **strictly less than 3:02:00**. Source runtimes over 3:00:00
  stop at exactly the window boundary; warn that omitted content may not be credits.
  Keep source runtime in input identity rather than pretending it is the capped runtime.
  Other scheduling modes retain their existing handling of long movies.
- Regular video eligibility retains the existing minimum duration of 60 seconds.

## Seeking without historical replay

The shipped `Scheduler.extend` is not used as the shared generator. It is stateful
and cannot jump directly from a code to today's show positions.

Use the absolute opportunity index to locate the show round and movie deck. For each
show, the next-window episode index is a finite transition on its current episode
index: pack quarter-hour-rounded episode lengths into 12 quarter-hour units.
Build that transition from the current catalog, then use its finite cycles/jumps to
find the state after the required number of opportunities. Work is bounded by the
catalog and requested horizon, not the number of days since the anchor. An unchanged
catalog may reuse indexes; a cache is never required to recover a code on a new device.
Do not claim constant-time startup: sorting and indexing 50,000 videos still cost work.

Standard SHA-256 with domain-separated, unambiguous fields defines deterministic choices.
Do not use platform-default random generators, hash iteration order, display titles or
installation identity for shared programming. A reference simulation verifies episode
continuation and range seams. Golden recipe vectors pin the portable encoding.

## Catalog changes and application

The same code, server-scoped media IDs, durations, grouping and effective channel inputs
produce the same program IDs and UTC times for an interval. Different Plex users are
supported only with equal effective access on the same server. No cross-server title
matching is implied.

Use current regular-video content, not historical snapshots. Adding a missing earlier
episode can shift progression and replay an episode; this is intentional. Refresh before
building when possible, keep a fixed snapshot during a build, and report refresh failures
without treating them as empty content. A refresh does not prove simultaneous catalog
visibility on another device.

Joining refreshes Plex before applying the recipe. A tuned/visible channel needing
extension requests a controller-owned refresh when the saved scan is at least 24 hours
old. Playback and initial extension use the saved snapshot while that scan runs.
Failed attempts keep the snapshot, report the failure and are throttled for five minutes.
Unchanged channel definitions, recipe and library generation reuse a validated booked
horizon until it needs topping up. Horizontal guide window changes read those bookings
without ensuring the same rows again; newly visible rows and periodic refreshes still
ensure schedules. Neither optimization changes deterministic program fingerprints.
Canonical fingerprints are shared by recipe validation, status reads and top-ups in a
128-entry LRU cache keyed by library generation, complete channel definition and recipe.
Library invalidation/sign-out clears it; invalidated in-flight calculations are retried.
Disabled status does not resolve pools or load the library. The guide caches at most
nine hours per nearby row (the three-hour view plus three hours on either side), renders
only the current view, and refills before reaching a cached edge. It evicts distant rows
and their metadata, and refreshes on revisions, clock jumps, retries and ten-minute top-ups.

Use the next UTC 3-hour boundary as the conservative application boundary when changing
an existing schedule; do not interrupt the airing program. Cutovers recheck elapsed operation time before
deleting entries and move to a still-future boundary if a block has started meanwhile.
Previously booked entries can temporarily differ until cutover, so show pending/applied status. Cold devices with no
booked history may generate the current window directly. Input fingerprints are manually
compared hints, not peer verification. Exclude ads, music, display names, audio/subtitle
preferences and local revision counters. Reject stale in-flight local results.

## Device-local commercials

Local ads never alter shared program boundaries, including when disabled or unavailable.
Draw fresh local randomness only when a break is first booked. Persist the selected clip
plan atomically; retune, restart or catalog refresh must not redraw a surviving booking.
Tune to the clip's wall-clock offset. Missing booked clips become Up Next for their
remaining time, not an unsynchronized extension. Bound/prune saved plans.
Do not call the ordinary deterministic `adFiller` unchanged for shared breaks.

## Contract limits

These are facts about the current functions. They are not extra product choices.

- After the anchor, `generate` returns whole entries that intersect the request.
  Those entries may end after `toMs`. It does not clip them to the request. It does
  cap a movie's scheduled end at the window while leaving source duration unchanged.
  Episodes longer than the window are omitted instead. No-delay holds only if
  playback stops at schedule `endMs`, not at media EOF. Live playback truncation
  remains required.
- The numeric bounds are overflow guards: anchor day and request instants within
  +/-100,000,000 UTC epoch days, and a request span of at most 366 days. A span of
  exactly 366 days is allowed. They do not approve a longer recipe or a UI horizon.
- The pool is not re-filtered with `Resolver.inBase`, and an empty rule match does
  not fall back to the raw pool. That is not `Scheduler.extend`. The caller must
  pass an already routed pool. The index then drops tracks, sub-minute items,
  episodes over 3 hours, and movies at or above 3:02:00. Duplicate keys with
  different identity records fail the build; they are not silently collapsed.
- `movieEveryMs` must match the template or the channel does not participate. It
  is not the movie cadence. Version 1 alternates windows.
- The anchor day is inside the deck salt. A different anchor is a different
  sequence, not the same sequence shifted by a day.
- `ScheduleRepo` drops generated entries whose `startMs` is before the requested
  start. That preserves seams only when that prefix is already stored and the
  requested start is a stored entry boundary. It is not the core seam contract.
  A fingerprint change can come from metadata the generator does not read, such as
  genre, studio, labels or an over-long item. Cutover then is not proof the
  program sequence changed.
- The app rejects an anchor after the device's current UTC day. Core decode does
  not. Clock skew can reject a code another device just generated.

## Validation and limits

Test direct/sequential progression, shuffled inputs, independent subranges, small pools,
round seams, long-film thresholds, data reset and old anchors. Scale probes must separate
indexing from generation and report work, not only noisy timings. Stock time-block
channels and edited channels remain independent until explicitly supported.

Real Plex access, TV input/clipboard behavior, playback rollover/truncation, local ad
stability after process death and two-device agreement require live validation. Local
JVM checks are not proof of device performance or synchronized clocks.

The earlier unwired experimental `SharedManifest` publisher model was removed during
pilot delivery. Its AD compatibility/revision semantics are not this feature's protocol.
Do not integrate a service or claim that all old design proposals have shipped.
