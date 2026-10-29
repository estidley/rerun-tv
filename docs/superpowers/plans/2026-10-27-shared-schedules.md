# Shared schedules delivery plan

Status: **local implementation complete**. Core/app tests, TMDB assembly, debug APK
assembly and Android lint pass. Initial single-device join feedback is incorporated;
two-device matching and physical TV performance acceptance remain open.
QR/system share entry remains deferred; grouped manual entry and clipboard are implemented.

The user authorized end-to-end opt-in pilot delivery on 2026-10-28 after choosing
fixed 3-hour UTC windows, quarter-hour episode spacing, ordered continuation,
equal show rounds, repeat avoidance and one movie per alternating movie window.
The [current design](../specs/2026-10-26-shared-schedules-design.md) is authoritative.
Earlier publisher/service plans and decision-only implementation gates are superseded.

## Delivery slices

1. **Core recipe and seeking.** Compact Base32/checksum code, UTC seed/anchor recipe,
   stable SHA-256 choices, stock-channel capability check, direct show/movie opportunity
   addressing and finite-state episode packing. No historical calendar replay.
   Verify continuation against an independent sequential packing reference and range
   seams. Exercise 50,000-item libraries and old anchors. Record actual prototype
   limitations, especially round randomness and unsupported stock time-block channels.
2. **Opt-in Android integration.** Generate, display, copy, enter and disable the code.
   Persist in existing settings rows where possible, validate before mutation, and own
   updates in the controller/repository rather than navigation-scoped work.
   Preserve current programming until next 3-hour UTC cutover and show pending status.
   Leave custom, edited, unsupported, music and out-of-range channels unchanged.
3. **Local ad booking.** Fresh local entropy, persistent selected plans, bounded retention,
   process-death reuse, missing clips waiting in place, no changes to shared program
   start times. Do not change ordinary local ad behavior outside the pilot.
4. **Refresh, identity and lifecycle.** Use immutable inputs off Main, reject invalidated
   builds, expose manually comparable input hints and warn about unequal catalogs.
   Preserve mute/foreground/sign-out semantics and persist clear cutover ownership.
5. **Integrated validation.** Required Gradle checks in AGENTS.md plus targeted pilot
   tests. Update README/design/readiness to match actual integration. The owner's full
   review/commit/PR request includes the separately reviewed version/CI work.

## Conservative implementation defaults

- Missing TV/movie pool: fill every window with the available type.
- Version 1 is the approved UTC same-show preset; additional recipe settings are
  versioned rather than silently inserted into a longer payload.
- Edited effective stock rules and stock policies not implemented by the seekable
  algorithm stay on the ordinary scheduler, with a visible limitation.
- Next UTC 3-hour boundary is the application boundary for existing programming;
  no automatic interruption to make an old booked window match a new cold install.
- Channel display names do not affect generation; configured numbers do.

## Verified API contracts

`SharedScheduleCode(seed, anchorDay)` and `SharedPilotScheduler.participates`,
`generate` and `fingerprint` exist with the following contracts:

- Requests that straddle the anchor omit the pre-anchor portion and return whole
  intersecting entries at or after the anchor. Tests cover straddling ranges and seams.
- Fixed deck endpoints give direct no-repeat at round boundaries. Size <= 2 is a
  fixed permutation. They do not prove time seekability. Measure 50,000 items and
  an old anchor; the two-item deck is not that measurement.
- "Pool already routed, matching the scheduler" is false. Match `Resolver.pool` at
  the caller if that is the contract. Say the core does not re-apply rules and does
  not use `Scheduler.extend`'s empty-filter fallback. Name the durations the index drops.
- Grouped manual and clipboard entry are implemented. The validator and input field
  allow up to 128 raw characters before decoding the normalized 24-symbol code;
  app and UI tests cover the 29-character grouped form.
- `participates` checks the configured number, not the template number. A stock
  theme moved to 150 participates. Do not "fix" that to a both-numbers gate without
  an owner decision. `cn` stays out because the template has blocks.

## Live acceptance still required

Use two independently signed-in devices on the same Plex server. Compare generated
program IDs/start/end (not folded guide cells or ads), test old code/data reset,
new episodes, different ad catalogs, disabled ads and independent music. Check the
3:00:00, 3:01:11, 3:01:59.999 and 3:02:00 movie cases. Observe midnight and UTC
window boundaries, menus remaining muted, process-death ad reuse and pending cutovers.
Measure cold indexing and tune responsiveness on a TV before performance claims.

## Preserved prior work

The CI/release-preparation files are a separate change included in the owner's
full-worktree review/commit/PR request.
The earlier experimental SharedManifest core model/codec/test was removed rather
than leaving an unused publisher protocol beside the deterministic pilot.
