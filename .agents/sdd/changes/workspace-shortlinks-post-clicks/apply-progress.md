# Apply Progress: Workspace Shortlinks for Publications and Click Metrics

## Delivery Boundary

- Strategy: `github-stacked-prs`
- Trunk: `main` (`origin/main` = `7057f9b558fe180504862028d6f302869caa7367`)
- Parent branch: `main`
- Base: `main`
- Branch: `workspace-shortlinks-click-capture`
- Position: 1 (bottom layer)
- Worktree: clean at start; no branch change, commit, push, or PR performed.

## Phase 1 Progress

- [x] 1.1 RED: Extended redirect controller tests for successful active redirect recording, no recording after resolution-not-found, and redirect preservation when recording throws.
  - RED evidence: focused test compilation failed before implementation with unresolved `RedirectClickRecorder` and constructor mismatch.
- [x] 1.2 Added `link_clicks` migration with link foreign key and `recorded_at` timestamp only; included in master changelog.
- [x] 1.3 Added application port and R2DBC adapter. Redirect processing awaits recording after successful resolution, swallows runtime tracking failures, and propagates coroutine cancellation.
- [x] 1.4 Focused controller and PostgreSQL persistence tests passed, including Liquibase startup and duplicate redirect row recording.

## Latency Evidence

The local controller test task completed in 10 seconds (Gradle task wall time), but this includes build overhead and does not isolate redirect-recording latency. PostgreSQL test execution earlier completed in 24 seconds including application startup/container setup; this is not a valid per-insert latency measurement. No defensible request or database-write latency measurement has yet been captured. The recording is synchronous/awaited and adds database round-trip latency to redirects.

## Files Changed

- `RedirectController.kt`: after resolution, best-effort awaited recorder call.
- `RedirectClickRecorder.kt`: application port accepts domain `LinkId`.
- `R2dbcRedirectClickRecorder.kt`: inserts UUID and link ID into `link_clicks`.
- `003-create-link-clicks.yaml` and `db.changelog-master.yaml`: persistence schema and registration.
- `RedirectControllerTest.kt`: active / failed / missing behavior.
- `R2dbcShortLinksPostgresIntegrationTest.kt`: duplicate redirect rows check.
- `tasks.md`: completed items 1.1–1.3.

## Verification

- Passed: `./gradlew :server:smp:test --tests 'com.profiletailors.smp.shortlinks.infrastructure.http.RedirectControllerTest' --no-daemon`
- Passed: `./gradlew :server:smp:test --tests 'com.profiletailors.smp.shortlinks.infrastructure.http.RedirectControllerTest' --no-daemon`
- Passed: `./gradlew :server:smp:test --tests 'com.profiletailors.smp.shortlinks.infrastructure.http.RedirectControllerTest' :server:smp:detekt --no-daemon`
- Passed: final combined command `./gradlew :server:smp:test --tests 'com.profiletailors.smp.shortlinks.infrastructure.http.RedirectControllerTest' --tests 'com.profiletailors.smp.shortlinks.infrastructure.persistence.R2dbcShortLinksPostgresIntegrationTest.records each resolved redirect against its link' :server:smp:detekt --no-daemon` (BUILD SUCCESSFUL; PostgreSQL container-backed test and Detekt included).
- Failed intermediate invocations exposed test setup, package placement, import, and UUID/LinkId boundary issues; corrected before the passing run. No failures were bypassed.
- Not run: full `just backend-check`, BDD (this layer does not add API routes), full PostgreSQL suite.
