# Marathon channels (Naruto, Shippuden) — design

*2026-11-20. Status: draft for owner approval.*

## Intent

Ethan wants channels that play one series start to finish, in order, then loop. The first two are
**Naruto** and **Naruto Shippuden**. When a show is named oddly in Plex, he should be able to fix the
channel from the TV menu: type a word, see everything in the library related to it, and tick what
belongs. Each stock station gets an icon.

## Goals

- A **Marathon** play order that any channel can use: every ticked item airs once per loop, in
  series order, then the loop restarts at episode 1.
- Two stock stations, **Naruto** and **Shippuden**, that appear on their own when the shows are in
  the library and stay dormant otherwise (public-repo users without them see nothing).
- An editor **Find related** picker: a word in, every show, movie and collection whose title
  contains it out, as checkboxes.
- The channel keeps its place through library changes, resyncs and edits.
- Original icon + wordmark for each stock station, in the existing 1990s icon style.

## Non-goals

- Canon-only / filler skipping (possible later; filler airs like any episode).
- Interleaving movies or specials by air date inside the episode run.
- New network routing, TMDB refresh changes, or a Boruto station.

## Anti-goals (failure even if it "works")

- An episode airing twice per loop, or two copies alternating.
- Specials or OVAs airing before episode 1.
- The channel jumping back to episode 1, or skipping a day's worth of episodes, after a resync,
  an edit or a library-selection change.
- Icons that reproduce the franchise's logo, characters, leaf symbol, spiral, headband plate,
  red clouds, fox or orange-jumpsuit palette. The icons must be original.

## Constraints (from AGENTS.md and the code)

- `Rules` include sources are OR'd; `titles` match by `titleKey()` on `showTitle`; episodes inherit
  the show's `tmdbId` (`PlexParse`), so `tmdbIds` is a stable show identity.
- `titleKey()` also feeds `bucketOf()` (MOVIES 1/2 slices). It must not change, or slices reshuffle.
- Channel defs/state persist as JSON via `core/codec`; missing fields must decode to today's
  behavior. No destructive Room fallback.
- Routine scheduling appends locked entries; channel edits keep the airing entry and rebuild the
  future; library-inclusion changes clear `entries` and `states` (`ScheduleRepo` ~L156).
- The approved icon library is preserved; new icons need owner approval of a sample first, then go
  through `tools/channel-icons` (`npm run build`) so drawables and Kotlin lookups regenerate.
- Never write to Plex watch state.

## Approaches considered

1. **Two hard-coded stations** with fixed rules. Fastest, but the next series (Dragon Ball, Avatar)
   is code again, and none of the fixes help custom channels.
2. **General Marathon play order + Find related picker, with Naruto/Shippuden as stock
   instances.** *Chosen* (owner went with the recommendation). Every series channel, stock or
   custom, gets the same ordering, dedupe and place-keeping.
3. **Reuse `IN_ORDER` as-is** and only add stock rows. Rejected: `IN_ORDER` round-robins between
   shows, so duplicate copies alternate and every episode airs twice; season 0 sorts first; the
   pointer is a list index, so an inserted episode shifts the run.

## Design

### 1. Play order (`core`)

- Add `PlayOrder.MARATHON` (editor label **Marathon**). Existing orders are untouched.
- `MarathonOrder.ordered(pool): List<MediaItem>` (pure, tested) builds one loop:
  1. **Series grouping.** Episodes group into a *series* by `tmdbId` when present, else by
     `matchKey(showTitle)`. Several Plex shows with the same identity (dub + sub libraries, a
     re-added show) are one series.
  2. **Dedupe.** Within a series, one item per `(season, episode)`. Prefer the copy whose Plex show
     has the most episodes in the pool; tie → lowest item key. Deterministic.
  3. **Series order.** By earliest episode year, then `matchKey`, then series id.
  4. **Inside a series.** Seasons ≥ 1 by `(season, episode, key)`, then season 0 (specials) by
     `(episode, key)` **at the end** (owner: end or beginning is fine; end means a loop opens on
     episode 1).
  5. **Movies** (only if ticked) after all series, by `(year, matchKey, key)`.
- **Cursor, not index.** State stores the *sort position* of the last item placed, as ints in
  `ChannelState.episodePointers` under reserved keys (`__mara_group`, `__mara_series`,
  `__mara_season`, `__mara_episode`, `__mara_tiebreak`). The next pick is the first item strictly
  after that position; none left → wrap to the first item. An episode added before the cursor
  waits for the next loop; one added after it airs on time; a removed episode is skipped. No
  pointer shift.
- `Scheduler.pick` routes `MARATHON` to this path (no movie spacing, no RNG calls), so other
  orders' determinism fingerprints don't change.

### 2. Keeping its place across rebuilds

The cursor reflects the end of the locked 48h horizon, not what is on screen. So whenever a
marathon channel's future is rebuilt or wiped, the cursor is reset to **just before the item that
is airing at that moment** (retained entry → the item after it; wiped entry → that same episode
restarts). This applies to:

- channel edits (future rebuild),
- missing-item repair after resync,
- the library-selection change that today clears all `states`. Marathon states are re-seeded from
  the airing entry inside the same transaction instead of being dropped.

If nothing is airing (channel never tuned, or the schedule fully expired), the cursor is kept as
stored.

### 3. Matching and Find related (`core` + editor)

- New `matchKey(s)` = `titleKey(s)` with diacritics folded (NFD, strip marks). Used for **rule
  matching and search only**; `bucketOf()` keeps `titleKey()`. Effect: "Naruto Shippūden",
  "Naruto: Shippuden" and "Naruto Shippuden" all match one rule. Accented curated titles (e.g.
  Pokémon) start matching unaccented Plex titles too, which is intended.
- `Facets.related(word, items)` → three groups, each with item counts and total hours:
  **Shows** (one row per series identity; shows "2 copies" when deduped), **Movies**,
  **Collections**. Match = folded substring of `matchKey(word)` in the folded title. Pure, tested.
- Editor, when order is Marathon (and as an extra section on any channel):
  - **Find related** text field, prefilled from the channel name on first open.
  - Result rows are checkboxes. Ticking a show adds its title to `rules.titles` and its `tmdbId`
    (if any) to `rules.tmdbIds`; unticking removes both. Movies/collections tick into
    `titles`/`collections` the same way.
  - Exact title matches start ticked when a *new* marathon channel is created; nothing is
    auto-ticked on an existing channel.
  - Selecting Marathon sets `kinds` to episodes only unless a movie is ticked.
  - Rows use the existing 20-at-a-time paging and `TvPanel` layout rules.

### 4. Stock stations (`core/catalog`)

New "Marathons" group, numbers **170–179** (free today; collisions resolve like other stock rows).

| id | number | name | rules |
|---|---|---|---|
| `marathon:naruto` | 170 | Naruto Marathon | `tmdbIds={46260}`, `titles={"Naruto"}`, `titleKinds={EPISODE}`, `kinds={EPISODE}` |
| `marathon:shippuden` | 171 | Shippuden Marathon | `tmdbIds={31910}`, `titles={"Naruto Shippuden"}`, `titleKinds={EPISODE}`, `kinds={EPISODE}` |

- Both `order = MARATHON`, kind `CURATED`, default ad/clock settings (≈23-min episodes on the
  15-min grid → a ~7-min break after each, like real TV; Ethan can switch a station to No ads /
  back-to-back per channel).
- Generated like other stock extras (`isGenerated` gains `marathon:`): appear when the pool meets
  the minimum-content preset, dormant otherwise, user edits preserved via `looksStock`.
- `matchKey` keeps "Naruto" from also matching "Naruto Shippuden" (exact key, not substring), so the
  two stations never share episodes. Boruto and the movies are not included unless ticked.
- Toonuki (Anime genre) keeps airing these episodes too; overlap between a genre station and a
  marathon is fine and needs no exclusion.

### 5. Icons

- Two original icons + wordmarks in `design/channel-icons` (128×128, flat ink, cream outline,
  same palette family as the Toonuki tanuki).
  - **Naruto Marathon:** a rolled ninja scroll with a shuriken pinned through it.
  - **Shippuden Marathon:** two crossed kunai over a rising moon.
  - None of the anti-goal elements above; wordmarks use the station's display name in the library's
    existing lettering style, not the show's logo.
- **Checkpoint:** render the two icon samples and get Ethan's OK *before* running the icon build
  and adding them to `manifest.json`.
- Custom marathon channels keep the existing runtime custom-channel art (initials + name).

### 6. Docs

Update AGENTS.md invariants (Marathon order, cursor, `matchKey` vs `titleKey`), README, and
`docs/channel-lineup.md` (Marathons group, 170–179).

## Testing

- `core`: ordering (seasons, specials last, movies last), dedupe of two copies, wrap-around,
  inserted/removed episode keeps the cursor, diacritic matching, Naruto ≠ Shippuden, stock
  generation/dormancy, codec decode of old JSON (no `MARATHON`, no cursor keys), unchanged
  fingerprints for SHUFFLE/IN_ORDER/CHRONOLOGICAL.
- `app`: Find related picker (Robolectric/Compose), cursor re-seed on edit and on
  library-selection change (`ScheduleRepo`).
- Full AGENTS.md validation command before marking ready. On-TV check: tune each station, confirm
  episode 1 → 2 → … and that a resync doesn't move the run.

## Left to the builder

- Exact cursor tuple encoding and tiebreak for movies.
- Find related field placement and copy.
- Where `MarathonOrder` lives (`core/schedule` suggested).
