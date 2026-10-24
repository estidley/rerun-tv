# Core TV delivery and PR readiness

Updated 2026-10-24. The original implementation scaffold and unchecked task recipes have been
retired; this file tracks delivered scope and real acceptance gaps, not instructions to recreate the app.
See [README](../../../README.md), [AGENTS.md](../../../AGENTS.md), and the
[current design reference](../specs/2026-10-23-rerun-design.md).

## Delivered

- Plex sign-in/server choice, persistent selected libraries and explicit Room migrations.
- Network/curated/theme/movie/custom channels, deterministic scheduling and channel editing.
- East/West feeds, genre music stations, Off Air and Up Next states, hidden episode start-over.
- Persistent ad library, deterministic gap-filling playback, and program guide cells that include breaks.
- TV remote navigation, adaptive guide, display themes/density, CRT/effects and margined Settings.
- Navigation-safe scans, guide progress/elapsed time and restricted update controls.
- Startup loading/error/confirmed-empty states; no premature Settings/sync prompt during restore.
- Foreground-only playback reconciliation, including library changes completed after screen navigation.
- Audio language/per-airing selection, subtitles, HDR compatibility controls and optional Plex normalization.
- Same-station fade-through-black, retaining static/roll only for station changes.
- Scheduler/lineup indexes, coalesced library snapshot, bounded metadata cache and isolated UI timers.

## Automated acceptance

Run the full command in AGENTS.md, covering core tests, Android JVM tests, debug APK assembly and lint.
The suite includes regression coverage for:

- Fixed scheduling output fingerprints, large pools, episode order, time blocks and filler boundaries.
- Room persistence/migrations, transactional library selections and stale snapshot invalidation.
- Startup restoration, genuinely empty lineups, failure/cancellation and sign-out.
- Scans surviving navigation, failure rollback, guide controls and background completion.
- Audio/video request policy, normalization eligibility, subtitle and HDR paths.
- Same-station fades, cancellation during fade, menu mute and wall-clock offsets.

Local JVM benchmark medians from the first optimization pass:

| Workload | Before | After |
|---|---:|---:|
| 5,000 music tracks | 121.0 ms | 5.8 ms |
| 5,500 mixed items | 10.5 ms | 0.9 ms |
| 2,500-item lineup | 134.8 ms | 33.5 ms |

These are synthetic local measurements with preserved output fingerprints, not TV performance figures.
Use `PerformanceWorkloadTest` and its XML output to reproduce the workload; timings vary by machine.

## Physical TV and live Plex acceptance still open

- [ ] Update an installed signed build with saved channels: loading/updating appears until restore settles; genuinely empty and failed states remain distinct.
- [ ] Enable Music or save library changes, leave the screen, and confirm final lineup and muted menu playback. Press Home mid-scan; no playback starts until resume.
- [ ] Tune near program end, observe ads at the break, and confirm the next program starts on schedule.
- [ ] Confirm fades composite correctly over the real video surface, including program/ad transitions and station changes during fades.
- [ ] Check audio and subtitle selection with multi-track 4K/HDR media on a live server; audio-only changes should not impose video downscaling limits.
- [ ] Verify HDR color rendering, tone mapping, fallback errors and Dolby Vision limitations on affected TVs.
- [ ] With Plex Pass, server support and analyzed program/ad audio, verify normalization eligibility, output loudness consistency and server transcode load.
- [ ] Measure target-TV frame rate, CPU, memory, channel-change latency and long-running playback.

Unimplemented roadmap ideas (HDHomeRun/live TV, TMDb enrichment, automatic clip splitting,
ad age/content filtering and idle timeout) are not acceptance requirements for this PR.
