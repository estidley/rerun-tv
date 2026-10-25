# Contributor and agent guidance

## Layout and commands

- `core`: pure Kotlin/JVM models, Plex parsing and playback policy, catalog/routing, scheduling, and filler planning.
- `app`: Android Compose UI, Room persistence, HTTP, and one Media3 player per activity.
- `tools/tmdb`: build-time TMDB refresh (`:tmdb:run`) writes gitignored `local/tmdb/rerun/tmdb-networks.json`; `:tmdb:publishSnapshot` validates and promotes it to tracked `data/tmdb/rerun/tmdb-networks.json`. Core bundles local lists when present, otherwise the shared snapshot. `:tmdb:check` tests publishing and validates the tracked data without credentials.
- `scripts`: Windows PowerShell build/install/key helpers. `tools/commercials` is a separate local media-preparation utility.
- Use the Gradle wrapper and versions in `gradle/libs.versions.toml`; Android SDK settings are in `app/build.gradle.kts`.
- Windows requires JDK 17+ and Android SDK platform 37. Initialize the JDK in each fresh shell:

```powershell
. .\scripts\jdk.ps1
Set-RerunJdk
.\gradlew.bat :core:test :app:testDebugUnitTest :tmdb:assemble :app:assembleDebug :app:lintDebug --console=plain --quiet
if ($LASTEXITCODE -ne 0) { throw 'Validation failed' }
```

For a narrow app test, use `:app:testDebugUnitTest --tests 'fully.qualified.TestClass'`.
Run the full checks above before marking a behavior change ready. Debug builds do not need release signing.
Analyze changed PowerShell scripts with PSScriptAnalyzer; check `$LASTEXITCODE` after native commands.

GitHub Actions runs these explicit Gradle tasks on Windows with JDK 21 and Android platform 37,
plus PSScriptAnalyzer 1.24.0 and the isolated `scripts/test-keystore.ps1` check. The aggregate
`CI` status requires both verification jobs. Reports/debug APKs are retained for 14 days.
Keep PR workflows read-only and secret-free; release signing is not part of CI.

## Behavioral invariants

- Comic channels use specific franchise metadata, curated title/year matches or explicit Marvel/DC labels/collections, never generic studio ownership. Marvelous Timeline combines movies/episodes in persisted universe-group order and season positions; normal channels retain their shuffle behavior. New station numbers must avoid custom-channel collisions. Missing franchise JSON fields decode to empty sets.
- Generate/integrate the full SVG icon library only after the user approves the five sample designs.

- Toonuki uses only the Anime genre for movies/episodes; it has no network/title or age-rating restriction. Upgrade only its legacy stock rules and preserve user-edited pools.
- Network channels match on what really aired: TMDB reference lists first (a show may belong to several networks), then the network Plex reports, and owner-family routing only when neither matches. Each `NetworkDef` keeps its `realName` and real `aliases`; parody names are display-only.
- The TMDB token is build-time only: read from `TMDB_TOKEN` (or `install.ps1 -TmdbToken`), sent only in the Authorization header, never written to disk, the repo, the APK, logs or URLs. Local refreshes stay in gitignored `local/`; only validated public network/company metadata and TMDB IDs are published to `data/tmdb/`. Tests run with `rerun.tmdb=off`.
- Never send Plex scrobble/timeline/watch-history updates. Never log or embed real credentials, signing keys, or tokens in URLs, tests, docs, or commits.
- Preserve explicit Room migrations and existing preferences; do not add destructive database fallback. Keep keystores, `keystore.properties`, and local SDK configuration out of version control.
- Routine scheduling appends locked entries. Library inclusion changes intentionally invalidate schedules; channel edits preserve the currently airing entry and rebuild the future.
- Stock decade stations require 24 hours of episodes or 12 hours of music; keep qualification a single metadata aggregation pass, preserve edited stations, and honor Music/East-West settings. Do not generate genre-by-decade combinations.
- Linear playback follows wall-clock time even after buffering and fades. Start-over is the explicit exception, on a hidden one-shot channel.
- Ads come from a separately persisted catalog and fit inside scheduled filler; they must never delay the next program. Guide cells include the following break without listing individual ads.
- Buy Me Maybe is generated from a nonempty enabled ad catalog only. Keep its clips separate from regular library pools, back-to-back without padding/tail skipping, and omit West feeds. Show hourly station blocks in the guide without changing clip-level playback schedules.
- Keep audio muted in menus across retunes and rollovers. Activity stop cancels playback, not scans; scan completion must not start background playback.
- Own navigation-safe library/lineup updates in `TvController`, not a composable coroutine. Sign-out cancels and joins controller jobs before clearing connection data.
- Do not infer an empty lineup from a flow's initial empty value. Await startup refresh and a successful channel query; keep loading, failure, and confirmed empty states distinct.
- Keep expensive parsing/snapshot loads and pool calculations off Main. The shared library snapshot must reject invalidated in-flight loads; metadata caching stays bounded and credential/revision scoped.
- Apply audio/subtitle selections after cached metadata lookup. Recheck display/decoder HDR capability for each tune. Audio-only conversion must not add video downscaling caps.
- Normalization is opt-in and capability-gated, not client volume control. Explain skipped normalization and errors; do not claim live-server support or measured output without device evidence.

## Tests and documentation

- Core tests use `kotlin.test`; app tests use JUnit, Mockito, Robolectric, and Compose test APIs.
- Stub newly read suspend settings values explicitly in Mockito fixtures, especially Boolean returns.
- Compose tests with a manual clock need recomposition and animation frames before asserting output. Keep rollover time and wall-clock time controlled independently.
- Keep deterministic scheduling fingerprints unchanged for pure performance edits. `PerformanceWorkloadTest` reports local JVM timings, not TV CPU, memory, or frame rate.
- Update README and the current design/readiness references for behavioral changes. Remove obsolete scaffolding instructions rather than presenting planned features as shipped.
- Physical TV rendering, HDR/tone mapping, Plex track negotiation/normalization, and resource measurements require live validation; automated checks are not substitutes.
