# Scheduling v2 implementation plan and handoff

Status: **not started**. The design is approved in the
[scheduling v2 design spec](../specs/2026-10-26-scheduling-v2-design.md); read it first, since
section numbers below (§) refer to it. Base: `main` at `6078bcd` (v1.0.1 plus PR #11, merged 2026-10-26). No implementation code
exists yet: no `feat/scheduling-v2` branch was created and there's no WIP stash.

## Handoff summary

- **Goal:** replace the 30-minute UTC grid and tune-time ad fill with a local quarter-hour grid,
  ad breaks saved at build time, gap fillers, the block-overrun rule, 3-episode runs in daily
  slots, strict timeline mode, background sync before top-up, safer item removal, a time-zone
  setting with a reset prompt, and a one-time upgrade rebuild.
- **Ship as 8 small PRs to `main`, in order.** Each must pass validation on its own and leave the
  app working.
- **Defaults Ethan didn't explicitly pick:** run slot times (4 PM and 8 PM), next-season
  continuation, and movie-only channel handling. Keep them configurable and call them out in PR
  descriptions.
- **Marvelous Timeline (decided):** same quarter-hour grid and ad fill as every channel, with
  strict order and no fillers (§1.6). The earlier fixed-2-ads rule is superseded.
- **Implemented separately, not part of these eight PRs:** the opt-in
  [sharing-code pilot](../specs/2026-10-26-shared-schedules-design.md).
  Preserve its stable identities and explicit algorithm/state versions during the v2 rewrite.
  Different Plex users can participate with matching effective access. It uses no-service
  seekable video blocks; commercials and music stay local.

## Repository rules (from AGENTS.md and owner instructions)

- Branch from `main`, open PRs to `main`, small commits.
- **Never force-push.** Don't merge without Ethan's approval. Joel (`joelst`) also commits; merge
  `main` into the branch (no rebase) if it moves.
- **Don't install to or uninstall from Ethan's Fire TV Cube.** Debug builds can't update the
  release-signed install, and uninstalling wipes the saved Plex sign-in.
- **Release flow** (only when asked):
  - Run the **Prepare release** workflow on `main` (patch/minor/major) and review its version-bump
    PR. It increments both values in `app/build.gradle.kts`; feature PRs need no bump.
    The workflow derives the next version from `main`; a patch increments the patch component
    and `versionCode` by one. Settings > About reads
    `BuildConfig.VERSION_NAME`, so no other file holds the version.
  - Run `gradlew assembleRelease bundleRelease`, signed by the local gitignored
    `rerun-release.jks` and `keystore.properties`. Never commit or print them.
  - Run `apksigner verify --print-certs`. The certificate SHA-256 must stay
    `51fb8f9a1e46471e50545179eb9720cf8aaaf4b0342e278eac393ed3ce6d860e`.
  - Tag `v<versionName>`, then publish a GitHub Release with `rerun-tv-v<version>.apk`, the
    mapping zip and SHA-256 values.
- Preserve the AGENTS.md invariants, updating the ones v2 intentionally changes:
  - "Routine scheduling appends locked entries."
  - "Ads … fit inside scheduled filler; never delay the next program." Ads are now saved
    entries.
  - Buy Me Maybe rules.
  - Wall-clock playback.
  - Explicit Room migrations with no destructive fallback.

## Validation (run before every push)

```powershell
. .\scripts\jdk.ps1
Set-RerunJdk
.\gradlew.bat :core:test :app:testDebugUnitTest :tmdb:assemble :app:assembleDebug :app:lintDebug --console=plain --quiet
if ($LASTEXITCODE -ne 0) { throw 'Validation failed' }
```

CI (`.github/workflows/ci.yml`) runs on every PR. Cursor Bugbot reviews PRs; address its findings.

## Build steps

### Step 1: Time zone setting and local quarter-hour grid

- **Files:**
  - `core/.../schedule/Schedule.kt`: zone-aware `quarterCeil(ms, zone)` and `quarterFloor(ms, zone)` based on local wall-clock time. Keep the old functions only where still needed.
  - `core/.../schedule/Scheduler.kt`: take the zone per `extend` call (not in the constructor), or pass a provider. Align to quarter-hour marks; the gap stays a FILLER for now.
  - `app/.../RerunApp.kt`, `app/.../data/ScheduleRepo.kt`: resolve the effective zone per build.
  - `app/.../data/Settings.kt`: `TIME_ZONE` ("auto" or a zone ID) and `LAST_SCHEDULE_ZONE`.
  - `app/.../ui/settings/SettingsScreen.kt`: Time zone row (Automatic / pick list).
  - `app/.../ui/TvController.kt`, `app/.../ui/MainActivity.kt`: detect changes at start and resume, and via the `ACTION_TIMEZONE_CHANGED` receiver; show the "Reset your schedule?" Yes/No dialog.
  - `ScheduleRepo.resetAllFuture(nowMs)`: keep each channel's current entry, delete after it, top up.
  - `app/.../ui/guide/GuideScreen.kt`: window flooring and clock in the effective zone. Reuse
    `app/.../ui/ClockWatch.kt` (PR #11): it already detects clock jumps and zone changes on the
    guide's 30-second tick and bumps `clockEpoch`. Hook the "Reset your schedule?" check into the
    same signal rather than adding a second poller. Note `gridFloor` there is still the UTC
    30-minute grid.
  - Formatting already follows the device locale, 12/24-hour setting and zone (`clockTime`,
    `clockDate`, `clockFormatSettings` in `Overlays.kt`); a manual zone setting must feed them too.
- **Tests:**
  - Core: quarter-hour marks in America/Chicago, Asia/Kolkata (+5:30), Asia/Kathmandu (+5:45), Pacific/Chatham, and DST spring-forward and fall-back days.
  - Scheduler: programs start on local quarter marks.
  - App: Settings persistence, the change-detection flow, Yes keeps now-playing, No keeps entries.
- **Acceptance:**
  - Every non-music, non-Buy Me Maybe program starts on a local quarter-hour mark in the effective zone.
  - Gaps are under 15 minutes.
  - The prompt appears once per change, and its Yes/No behavior matches §1.10.

### Step 2: Ad breaks as saved entries with gap fill

- **Files:**
  - `core/.../schedule/Schedule.kt`: add `EntryKind.AD` (the clip key in `itemKey`) and `EntryKind.WAIT` (Up Next). Keep `FILLER` for legacy entries.
  - `core/.../schedule/Scheduler.kt`: an ad planner that fills each gap to the next mark (preferred 3–8 min, 23 min cap). The ad catalog is passed into `extend`, picked deterministically from the channel's saved RNG, with no immediate repeats. Whatever can't be filled becomes WAIT.
  - `core/.../model/Channels.kt` + `core/.../codec/Codecs.kt`: add `AdsPolicy` (Normal / Off / Fixed N) to `ChannelDef`; the codec reads a missing value as Normal.
  - `app/.../ui/channels/ChannelEditor.kt`: Ads control.
  - `core/.../schedule/GuideBlocks.kt`: fold AD and WAIT into the previous program.
  - `app/.../player/TunerEngine.kt`: play AD entries at the offset and show the Up Next card for WAIT. Keep `adFiller` only for legacy FILLER entries.
  - `ScheduleRepo.topUp`: pass the ad catalog.
  - `app/.../data/Db.kt`: no schema change (kind is a string), but make `EntryRow.toEntry` tolerate unknown kinds for downgrade safety.
- **Tests:**
  - Planner: same inputs give the same output; the break ends exactly on the mark when clips fit; the cap holds; an empty catalog gives WAIT; Off and Fixed N.
  - GuideBlocks folding.
  - TunerEngineTest: tune in mid-AD seeks within the clip; WAIT shows Up Next.
  - Codec round-trip with and without the policy.
- **Acceptance:** no tune-time ad selection for new entries; the guide shows no individual ads; tuning in mid-break plays the right clip at the right offset.

### Step 3: Gap fillers

- **Files:** `Scheduler.kt`. When the remaining gap is over 8 minutes, pick a random matching item that fits, honoring the active block's rules. It must not touch episode pointers, the movie deck or `recent`, and must exclude active-run shows. Then fill the rest per Step 2. Skip fillers for In order and chronological channels.
- **Tests:** fillers fit and match the rules; pointers don't change; finales are allowed; active-run shows are excluded; no fillers on fixed-order channels.
- **Acceptance:** a gap over 8 minutes with a fitting item gets that item, then ads; show positions are identical with and without fillers.

### Step 4: Time-block overrun rule

- **Files:** `Scheduler.kt` (find the next block boundary in local time; shorter-pick search; keep skipped deck items), `Channels.kt` and `Codecs.kt` (per-channel tolerance, default 20 min), and the editor if exposed.
- **Tests:**
  - Cartoon Network weekday 4–7 PM block: no program overruns 4 PM or 7 PM by more than 20 minutes.
  - The fallback when nothing fits.
  - In order and chronological channels are exempt.
  - Existing DST block tests still pass.
- **Acceptance:** §1.7.

### Step 5: Episode runs and daily run slots

- **Files:**
  - `Scheduler.kt` and `Schedule.kt`: `ChannelState` gains run slots (slot local time, show key, next index); `StateCodec` stays backward compatible.
  - Slot anchors at 4 PM and 8 PM local by default; zero-tolerance end before a slot; 3 consecutive episodes with ad fill between them; next-season continuation; a new random show when the show is exhausted; skip a slot when a block excludes the show; a due movie waits until after the run.
  - `Channels.kt` and `Codecs.kt`: optional per-channel run settings (slots, episodes per run).
- **Tests:**
  - Day 1 plays S1E1–3 and day 2 plays E4–6 at the same slot.
  - The run is never split.
  - The preceding program ends by the slot.
  - Season rollover; exhausted show.
  - A blocked day keeps its position.
  - A movie that's due is deferred.
  - Same inputs give the same output.
  - In order channels never get runs.
- **Acceptance:** §1.5.

### Step 6: Timeline mode (chronological)

- **Files:** `Scheduler.kt`: chronological channels use strict deck order, ignore blocks, start on the next quarter mark, fill each gap with the same quarter-hour ad fill as other channels (Normal by default). There are no fillers, so a gap over 8 minutes is filled with ads up to the cap, else an Up Next wait. Disable or ignore blocks for this order in `ChannelEditor.kt`.
- **Tests:** exact order across top-ups and blocks; no skips or shortening; every item starts on a local quarter mark; gaps are ad-filled with no fillers (including gaps over 8 min); same inputs give the same output.
- **Acceptance:** §1.6.

### Step 7: Sync before build, and safe item removal

- **Files:**
  - `app/.../data/LibrarySync.kt`: `syncIfStale(24h)`, serialized, fail-soft.
  - `app/.../ui/TvController.kt` and `ScheduleRepo.kt`: before a top-up, if the last sync is stale, start the background sync. Never block tuning; a cold start builds from the saved library.
  - `ScheduleRepo.refreshChannels`: PR #11 already keeps the airing entry and later entries up to the first missing item (`EntryDao.firstMissingStart` + `deleteFrom`) and keeps the channel's state. **This step only adds dropping the missing keys from the saved deck, `recent`, episode pointers and run slots**, so the next top-up never picks a removed item.
  - Top-up triggers: tuning (`ensure(channelId)`), sync (`ensure(now)`) and the guide (`ensure(ids, now)` for visible rows, repeated every 10 minutes while open, PR #11). Check sync staleness once per trigger batch, not per guide row, and never from inside the 10-minute guide refresh loop while a sync is already running.
- **Tests:**
  - LibrarySyncTest: stale vs. fresh; failure falls back.
  - ScheduleRepoTest: an item removed mid-program keeps now-playing and positions; a library-selection change still rebuilds.
- **Acceptance:** §1.8.

### Step 8: Upgrade rebuild and docs

- **Files:**
  - `ScheduleRepo` and `RerunApp`/`TvController` startup: a one-time `resetAllFuture` behind a settings flag (`SCHEDULING_V2_MIGRATED`).
  - README: scheduling, Settings > Time zone, ads behavior.
  - `docs/superpowers/specs/2026-10-23-rerun-design.md`: Scheduling and ads section.
  - `docs/channel-lineup.md`: where scheduling is mentioned.
  - AGENTS.md: update the invariants listed above.
  - Mark this plan done.
- **Tests:** the migration runs once and keeps now-playing; a second launch is a no-op.
- **Acceptance:** fresh installs and upgrades both produce v2 schedules; docs match the behavior.

## Open risks

- **Downgrade:** old APKs decode `kind` with `EntryKind.valueOf` and would crash on AD or WAIT
  entries. Sideloading an older version after v2 would need app data cleared. Mention it in
  release notes.
- **Random seeds:** since PR #11 a channel's first seed is random and saved. Write determinism
  tests from an explicit saved `ChannelState` (spec §3 #20), never from the channel ID.
- **Determinism:** the deterministic fingerprints in `PerformanceWorkloadTest` and the existing
  scheduler tests change by design. Update them in the step that changes them.
- **Build cost:** quarter-hour ad planning adds entries (about 4–8 per program). Watch
  `ScheduleRepo.ensure` time on the Fire TV CPU (JVM timings aren't device evidence).
- **Narrow guide cells:** fillers and short gaps make narrow cells; check readability on a TV.
  The wordmark channel column from PR #11 grows with the layout (about 125 to almost 300 dp), so cells
  are narrowest in Large.
- **Background sync:** on large libraries it takes minutes. It must not hold
  `ScheduleRepo`'s mutex for long or block tuning.
- **Time-zone dialog:** must not appear during onboarding or on top of other dialogs.
- **Device checks:** run-slot behavior around DST days and real-TV guide readability need live
  checks; there's no emulator on AETERNUM.