# GitHub Actions CI implementation plan

## Goal and scope

Run lint, verification, JVM tests, and a debug APK build for pull requests and main.
Use the existing Gradle wrapper and checks; do not introduce a Kotlin formatter or a
release-signing dependency. Implemented in `.github/workflows/ci.yml`; GitHub-hosted
acceptance and branch-protection configuration remain rollout steps.

The repository includes both
Gradle launchers and the wrapper JAR. Android requires SDK platform 37; JVM targets are 17.
Compose rendering tests use Robolectric native graphics, not an Android emulator.

## Proposed workflow

Create `.github/workflows/ci.yml` with:

- `pull_request` targeting `main`, `push` to `main`, and `workflow_dispatch`.
- Read-only `contents` permission. Use `pull_request`, never `pull_request_target`, to
  execute contributor code without exposing repository secrets.
- Concurrency grouped by workflow and PR number or branch. Cancel superseded PR runs.
- A stable final job named `CI`, suitable for a required branch-protection check.
- No workflow-wide path filters: documentation-only PRs must still receive the required check.
- Explicit job timeouts and action dependencies pinned to verified full commit SHAs.

### Job: Android verification

Start on `windows-latest` to match the validated local environment, including native
Robolectric rendering. Set up Temurin JDK 21 explicitly; compile targets remain Java 17.
Use Android SDK setup and install platform 37 explicitly. The Android Gradle plugin
installs its required build-tools revision using the licensed SDK.

Use the Gradle setup action for wrapper validation and dependency caching. Fork PRs must
not write shared caches. Do not cache the complete checkout, signing material, or local
SDK configuration. Do not enable a configuration cache until repository compatibility
has been demonstrated.

Run these steps separately so failures are easy to identify:

| Step | Gradle tasks or check |
|---|---|
| Patch hygiene | `git diff --check` against the PR merge base; on main/manual runs, check the triggering commit |
| JVM tests | `:core:test :app:testDebugUnitTest` |
| Android lint | `:app:lintDebug` |
| Debug build | `:app:assembleDebug` |

Use `.\gradlew.bat`, `--console=plain`, and `--stacktrace`. Check `$LASTEXITCODE`
after every native command. Run lint and build after test failure when setup succeeded,
but preserve the failed job outcome. Do not use `continue-on-error` to turn failures green.
Fetch enough Git history for the hygiene comparison.

These explicit tasks are the established repository checks. Do not add an unqualified
root `check`/`build` invocation that may select release variants and require a keystore.

### Job: PowerShell analysis

Run on Windows in parallel with Android verification. Install a pinned, verified
PSScriptAnalyzer version from PSGallery and analyze tracked scripts under `scripts`.
Fail on analyzer errors; publish warnings visibly. Baseline existing warnings before
making them fatal, without suppressing new errors or adding unrelated script rewrites.

The existing `scripts/test-keystore.ps1` parses the keystore script and loads only its
property encoder; it does not run key setup or write signing files. Run this isolated
regression check. Never invoke installation or device-reset scripts.

### Job: CI

Depend on both verification jobs and run even when either fails. Succeed only when
both jobs succeed; failure, cancellation, or an unexpected skip must not pass the gate.
Configure branch protection to require this check after the first successful run.

## Reports and artifacts

Upload available diagnostic reports even when verification fails:

- Core and Android JUnit XML plus HTML test reports.
- Android lint HTML and SARIF reports as downloadable artifacts.
- Gradle diagnostic reports when produced.

Upload the debug APK and its output metadata only after successful verification.
Use a short retention period, such as 14 days, and include the commit SHA in artifact names.
The debug APK is not a release artifact and cannot replace an installation signed with
the private release key. Do not upload keystores, properties files, credentials, full
Gradle caches, or device logs.

SARIF upload to code scanning is optional follow-up work: downloadable reports require
no additional permissions and work for fork PRs.

## Implementation sequence

1. Add the workflow with setup, verification, reporting, and the aggregate gate.
2. Add the optional isolated keystore check only after inspecting its safety.
3. Update README and AGENTS with the workflow, local equivalent, artifact location, and
   required-check name. Add a CI badge once the workflow exists on the default branch.
4. Open a PR and exercise a normal run, a deliberate failing test, a lint failure, and
   a documentation-only update. Confirm reports survive failures and the gate stays red.
5. Confirm a fork PR works without secrets or cache-write permissions. Confirm a newer
   PR update cancels the obsolete run and the debug artifact identifies the tested version.
6. Enable required checks after the workflow has passed on GitHub-hosted runners.

## Deferred

- Signed release builds and publishing: separate protected workflow with explicit signing policy.
- Emulator/device instrumentation: not needed for the current JVM suite.
- Physical TV/Plex acceptance: CI cannot verify HDR rendering, real codec negotiation,
  WMA playback against a live server, loudness consistency, or measured device performance.
- Linux migration and job sharding: evaluate after collecting CI timings and proving
  native rendering tests behave identically. Avoid duplicate builds and cache complexity
  before measurements justify them.
