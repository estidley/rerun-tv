# RERUN-TV handoff

Updated Friday, Nov 13, 2026, 2:20 PM CST (America/Chicago). This is the operational handoff for the Android TV / Fire TV app. A longer scheduling plan already exists and should not be re-researched: [docs/superpowers/plans/2026-10-26-scheduling-v2.md](superpowers/plans/2026-10-26-scheduling-v2.md), with the approved design in [docs/superpowers/specs/2026-10-26-scheduling-v2-design.md](superpowers/specs/2026-10-26-scheduling-v2-design.md). Contributor rules and behavioral invariants are in [AGENTS.md](../AGENTS.md). Channel identity rules are in [docs/channel-lineup.md](channel-lineup.md).

Rerun turns a personal Plex library into old-school cable TV for Fire TV and Android TV: channels are already playing, the guide looks like 90s cable, and playback follows the wall clock. `main` is at `0b61619` after [PR #25](https://github.com/estidley/rerun-tv/pull/25) merged on Thursday, Nov 12, 2026 (7:49 PM CST): playback, subtitle, and channel-routing improvements. The current released Android app on `main` is **1.0.4 (versionCode 6)**, GitHub release [v1.0.4](https://github.com/estidley/rerun-tv/releases/tag/v1.0.4). Joel Stidley (`joelst`), a collaborator, is the author of recent mainline work and of the only open PR, [#26](https://github.com/estidley/rerun-tv/pull/26) (“Simplify scheduling and establish 15-minute local baseline”), opened Thursday, Nov 12, 2026 (10:54 PM CST). That PR is a large deletion (−2,471 / +181, 39 files): it removes the opt-in schedule-sharing pilot, keeps existing Room schedules instead of wiping them, moves movies and episodes onto a device-local 15-minute grid, leaves music and Buy Me Maybe back-to-back, and bumps the app to **1.1.0 (versionCode 7)**. It is mergeable and clean, with unaddressed Copilot review comments. This reset-day pass adds this handoff and a read-only review of PR #26; nothing is merged, commented on, or published.

## Ethan's instructions and goal

- Goal: a live, cable-style guide over the user's own Plex library, with parody network channels, theme channels, comic/timeline stations, ads that never delay the next program, and wall-clock playback.
- Branch from `main` and open PRs to `main`. Never force-push. Do not merge without Ethan's approval. If `main` moves, merge `main` into the branch (no rebase).
- Do not install to or uninstall from Ethan's Fire TV Cube. Debug builds cannot update a release-signed install, and uninstalling wipes the saved Plex sign-in.
- Never send Plex scrobble, timeline, or watch-history updates. Never log or embed credentials, signing keys, or tokens. The TMDB token is build-time only and must never land in the repo, APK, logs, or URLs.
- Preserve explicit Room migrations; no destructive database fallback. Keep keystores and local SDK configuration out of version control.
- Feature PRs need no version bump; version changes should move both `versionName` and `versionCode` together when they do change.
- Scheduling v2 is approved in the design spec cited above, to be shipped as small ordered PRs. Defaults Ethan did not explicitly pick (run slot times 4 PM and 8 PM, next-season continuation, movie-only channel handling) stay configurable and should be called out in PR descriptions.
- An older standing note was that work should land on a review branch with an APK and not go to `main` or a release. The current repo rules, official 1.0.x GitHub releases, and the scheduling-v2 plan all say PRs go to `main` and releases follow the Prepare / Publish workflows. Follow the written repo rules unless Ethan restates the older process.
- Merges, releases, and device installs are Ethan's decisions.

## Checklist

### Done

- [x] Fire TV / Android TV app with Plex library channels, 90s-style guide, CRT look, start-over (From the Top), lineup minimums, language and catch-all stations, comic channels, Marvelous Timeline, Toonuki Anime-genre rule, and the 1.0.4 playback/subtitle/routing pass (PR #25, 2026-11-12)
- [x] GitHub releases [v1.0.0](https://github.com/estidley/rerun-tv/releases/tag/v1.0.0), [v1.0.1](https://github.com/estidley/rerun-tv/releases/tag/v1.0.1), and [v1.0.4](https://github.com/estidley/rerun-tv/releases/tag/v1.0.4)
- [x] Approved scheduling-v2 design and ordered plan (not fully implemented; see that plan rather than rewriting it here)
- [x] Read-only review of PR #26 completed 2026-11-13 (summary below). No review comment, approval, or merge was posted on GitHub.
- [x] This operational `docs/HANDOFF.md` drafted 2026-11-13 (this PR; not merged)

### Left — approved (scheduling v2 remaining steps)

Approved in the v2 design/plan. On current `main` these are still unimplemented. PR #26 would complete only the local 15-minute grid piece of Step 1, not the rest.

- [ ] Time-zone setting (Automatic / pick list), change detection, and the “Reset your schedule?” prompt
- [ ] Ad breaks saved at build time, with WAIT/Up Next for leftover gaps; Channel Ads policy
- [ ] Gap fillers when a gap is over 8 minutes (skipped on in-order / chronological channels)
- [ ] Time-block overrun rule (default 20 minutes)
- [ ] 3-episode daily run slots (defaults 4 PM and 8 PM local, unless Ethan picks other times)
- [ ] Timeline mode (strict order, same quarter-hour ad fill, no fillers)
- [ ] Sync-before-top-up and safer removal of missing items from saved decks/pointers
- [ ] One-time upgrade rebuild and docs (Step 8)

### Left — awaiting Ethan

- [ ] Whether to merge [PR #26](https://github.com/estidley/rerun-tv/pull/26) (see review below). Recommendation: do not merge yet.
- [ ] Whether retiring the schedule-sharing pilot is acceptable. The approved v2 plan said to preserve that pilot during the rewrite; #26 deletes the feature and its design docs.
- [ ] The open product question of re-randomizing the schedule hourly (not implemented in #26 or on `main`).
- [ ] Physical TV and live Plex timing / resource validation of whatever scheduling baseline ships, including the three Copilot findings on #26 if that PR is pursued.
- [ ] A 1.1.0 GitHub or device release. Not an agent action without Ethan's OK, and not before the Fire TV install rule above.

## PR #26 read-only review (2026-11-13)

**PR:** [estidley/rerun-tv#26](https://github.com/estidley/rerun-tv/pull/26) — “Simplify scheduling and establish 15-minute local baseline”  
**Author:** `joelst` (GitHub Collaborator; branch is `estidley:feat/local-quarter-hour-schedule`, same repo, not a fork)  
**Against:** `main` at `0b61619`. **Head:** `bc2e4b4`. One commit, 39 files, +181 / −2,471. **Mergeable:** yes, `clean`. No issue comments; three unaddressed Copilot inline comments from Nov 12, 2026, 11:15 PM CST. The author reports `:core:test`, `:app:testDebugUnitTest`, `:tmdb:assemble`, `:tmdb:check`, `:app:assembleDebug`, `:app:lintDebug`, and `scripts/check-version.ps1` passed, and says physical TV / live Plex behavior still needs a device check.

**What it changes.** Removes the schedule-sharing pilot (code generation, cutovers, refresh jobs, shared-ad bookkeeping, `SharedPilot*` sources, settings keys, and the shared-schedule design/plan docs). Keeps existing Room-backed schedules instead of wiping them for the grid change. Replaces the 30-minute UTC grid with a device-local 15-minute grid for movies and episodes, including non-hour offsets and DST; music and Buy Me Maybe stay back-to-back with hourly guide blocks. Bumps `versionCode` 6 → 7 and `versionName` 1.0.4 → 1.1.0. Updates the v2 plan status to “partially implemented” and drops the “preserve the sharing pilot” requirement from that plan.

**Code quality.** The deletion is coherent: pilot types, UI, repo hooks, and tests come out together, and the remaining scheduler/guide code is updated to a zone-aware `gridFloor`. Tests are updated rather than deleted wholesale, except for the pilot-specific suites that the feature removal makes obsolete. This is not the full approved Step 1 (no manual time-zone setting or reset prompt) and it is not the “eight small PRs” sequence in the plan. The version bump is larger than a typical feature PR, but it matches a scheduling baseline change if Ethan wants 1.1.0.

**Security (outside-contributor review).** Low risk for exfiltration or supply-chain issues:

- No `.github` workflow, CI, script, keystore, or signing-config changes. The only `app/build.gradle.kts` edit is the version pair. The existing `signingConfigs` block is untouched.
- No new network calls, URLs, tokens, or HTTP clients in the added lines. The PR removes cross-device sharing rather than adding a service.
- Same-repository collaborator branch, not a fork PR, so CI runs in the already-trusted repo context.

**Conflicts / build.** GitHub reports the PR mergeable and clean. Automated tests are claimed passing by the author; this review did not re-run Gradle. Three Copilot findings are still open and look real:

1. `TunerEngine` / `adFiller`: leftover multi-hour filler from a preserved pilot schedule can make each rollover rebuild a long ad prefix (performance).
2. `GuideScreen.TimeHeader` also uses `GRID_MS`, so the three-hour header goes from six columns to twelve and can wrap or overflow at the supported 1280 dp width.
3. Editing a retained pilot channel mid-program can start the next video off the quarter-hour grid (`lastEndMs` used directly after `resetFuture`).

**Recommendation.** Do not merge yet. Ask Ethan to decide whether the sharing pilot should be retired, then either request a follow-up for the three Copilot findings plus a physical TV pass, or land #26 only after those are addressed. Do not treat this PR as the complete scheduling-v2 delivery.
