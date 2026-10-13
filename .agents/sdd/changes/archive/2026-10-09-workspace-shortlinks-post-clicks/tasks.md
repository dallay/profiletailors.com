# Tasks: Workspace Shortlinks for Publications and Click Metrics

## Review Workload Forecast

| Field | Value |
|---|---|
| Estimated changed lines | 600–900 |
| 400-line budget risk | High |
| Chained PRs recommended | No — explicitly approved size exception |
| Suggested split | Internally verified slices; one final PR to `main` |
| Delivery strategy | single PR final to `main` (`size-exception`, expressly approved by the user) |

Decision needed before apply: No
Chained PRs recommended: No
Chain strategy: size-exception
400-line budget risk: High

Size exception: The user expressly approved one final PR to `main` despite the 400-line review budget. The related phases must integrate to complete the end-to-end flow. Mitigations: verify each phase internally by slice; include the slice breakdown and verification evidence in the PR description; keep the reviewer-budget warning visible.

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|---|---|---|---|
| 1 | Click capture foundation | Final single PR | Phase 1 passed; preserve completed work. |
| 2 | Link-specific metrics API | Final single PR | Phase 2 passed; preserve completed work. |
| 3 | Composite-cursor workspace collection API | Final single PR | Phase 3 first slice; complete and verify before frontend integration. |
| 4 | Composer and metrics UI | Final single PR | Depends on the verified collection API contract. |

## Phase 1: Click Capture Foundation — passed

- [x] 1.1 Add click persistence migration, record successful active-link redirects, and verify failure does not disrupt redirect.
- [x] 1.2 Verify redirect and persistence behavior; independent Phase 1 verification passed.

## Phase 2: Link-Specific Metrics — passed

- [x] 2.1 Implement authenticated workspace-scoped query for a link's all-stored-click count; foreign and unknown IDs share not-found behavior.
- [x] 2.2 Add handler, HTTP, PostgreSQL, and BDD coverage; independent Phase 2 verification passed.

## Phase 3: Workspace Collection API, Composer, and Metrics UI — Slice 1 passed with warnings; Slice 2 passed with warnings

### Slice 1: Backend collection API

- [x] 3.1 RED: Add failing controller/BDD scenarios for workspace isolation, zero-click counts, bounded `limit`, opaque composite `(created_at,id)` cursor continuation, exclusive seek, and `created_at DESC, id DESC` ordering, including timestamp ties. Controller test initially failed compilation because collection API types were absent; API added next. Cucumber regression reached collection cursor continuation, failed on UUID decoding, then implementation/test were corrected.
- [x] 3.2 Implement collection pagination in `server/smp/src/main/kotlin/com/profiletailors/smp/shortlinks/{domain,application,infrastructure}`: accept `limit` and opaque cursor; seek strictly before `(created_at,id)`; fetch `limit + 1`; return next cursor from the last included row. Do not promise a stable snapshot.
- [x] 3.3 Complete API contract, Cucumber, and persistence coverage in `server/smp/src/test/kotlin/com/profiletailors/smp/shortlinks/`, `server/smp/src/test/kotlin/com/profiletailors/smp/bdd/glue/ShortLinksBddSteps.kt`, and `server/smp/src/test/resources/features/shortlinks.feature`; verify PostgreSQL tie ordering and exclusive continuation. Final serial reruns passed: the shortlinks BDD collection scenario asserts workspace isolation and 1/0 counts, and the PostgreSQL persistence class covers 10 tests; see the verification report for XML timestamp caveat.
- [x] 3.4 Document endpoint, auth/workspace scope, versioned media type, response/errors, `limit`, opaque composite cursor, DESC ordering, exclusive continuation, and no-snapshot semantics in SpringDoc/OpenAPI annotations and `docs/api-versioning*.md`. Contract documented in SpringDoc and `docs/api-versioning.md`.

### Slice 1 verification — passed with warnings

- [x] 3.5 Re-run verification serially after initial EOF/timeout failures. Final `just backend-check` and `just backend-test-postgres` runs completed BUILD SUCCESSFUL; corrected `just backend-bdd-fast` completed BUILD SUCCESSFUL with `EXIT_CODE=0`, and its collection scenario asserts workspace A's two IDs with counts 1/0 and excludes workspace B. See `verify-report-phase3-slice1.md`; proceed to Slice 2.

### Slice 2: Frontend integration — **superseded by Phase 4**

The previous Slice 2 implemented an explicit opt-in (`Use shortlink` / `Keep original URL`) per user decision during QA. The product owner subsequently requested an automatic replacement flow: every distinct URL in the post content must be shortened at submit time without user opt-in. Slice 2 is therefore superseded by Phase 4. The previously passing tasks 3.7 (shortlinks list view) and 3.8 (app lint/type-check/tests/build) remain valid for their scope and are not affected by Phase 4.

- [x] 3.6 Wire the verified collection API into the publishing composer; implement explicit shortening choice, preview, failure fallback, and unchanged non-link publication behavior in `apps/web/app/src/modules/publishing/`. Fresh focused composer suite passed: 1 file / 67 tests. **Superseded by Phase 4 task 4.4; the explicit opt-in flow is removed and replaced with automatic replacement.**
- [x] 3.7 Add workspace link/count presentation in `apps/web/app/src/modules/shortlinks/` (or the established feature owner), including zero counts, cursor pagination, loading, empty, and error states. Focused view suite passed: 1 file / 6 tests, including previous-cursor recovery after an error. Remains valid.
- [x] 3.8 Obtain passing focused app tests for composer payload/fallback and list rendering/pagination; run app lint, type-check, tests, and build. Focused suites pass; complete app test suite passed (169 files / 1922 tests), type-check and build passed. Lint exited successfully with two warnings in untouched existing files. `git diff --check` passed. Backend checks were not repeated because this frontend slice does not alter backend behavior. Remains valid for affected files; Phase 4 may require re-running the focused composer suite.
- [x] 3.9 Complete independent verification and record `verify-report-phase3-slice2.md`; verdict PASS WITH WARNINGS. Product/API docs were inspected; retain one final PR to `main`. Do not commit, push, or open a PR without authorization. **Will be superseded by `verify-report-phase4.md` after Phase 4 lands.**

## Phase 4: Automatic Shortlink Integration at Publication Submit — in progress

This phase replaces the explicit opt-in flow with an automatic replacement flow at submit time. The composer extracts every distinct URL in the post content, shortens each one via `POST /api/v1/links` (best-effort, deduped by URL), and submits `POST /api/publishing/publications` with the shortened content. The existing composer preview renders current text; the transformed text is produced at submit and sent without a choice step. Edit mode does not invoke the shortlink service.

- [x] 4.1 Update `specs/publishing-shortlinks/spec.md` so the requirement is "automatic shortlink replacement at publication creation" with the seven scenarios captured in the latest revision: single URL, repeated URL dedup, multiple distinct URLs, no URL, best-effort failure, live preview reflects submit content, edit mode does not shorten. The current spec contains this requirement and all seven scenarios; it supersedes the prior opt-in behavior change recorded in Phase 3 Slice 2 without erasing that history.
- [x] 4.2 Add a focused regression test for `shortlinks/domain/shortlink-post-content.ts` covering URL extraction, deduplication of identical URLs, replacement of every occurrence, and no-URL identity. Focused domain tests pass.
- [x] 4.3 Refactor `CreatePostModal.vue` to remove the opt-in checkbox, manual preview, Use/Keep controls, and obsolete decision state. The existing post preview continues to render the current composer text.
- [x] 4.4 Before `publishingStore.schedulePost`, automatically extract and deduplicate URLs, request shortlinks in parallel, replace all matching occurrences for successful results, and submit despite failures with an inline non-blocking warning. Focused composer tests pass for successful distinct/repeated URLs, failure fallback, no URL, and obsolete controls removal.
- [x] 4.5 Edit-mode submit bypasses the replacement step. Focused composer test verifies unchanged content is passed to updatePost and the shortlink service is not called.
- [x] 4.6 Focused composer tests assert no obsolete opt-in/Use/Keep controls, auto-shortening of distinct/repeated URLs, failure fallback while still submitting, no-URL no-call pass-through, and edit-mode bypass. No lint/type suppressions introduced.
- [x] 4.7 Run `pnpm --filter app test:run` and the focused composer suite. All tests MUST pass without lint/type suppression. Re-run `pnpm --filter app type-check` and `pnpm --filter app lint`. App suite passed (169 files / 1,920 tests); focused composer suite passed (1 file / 67 tests); type-check passed; affected-file Biome check passed; lint exited 0 with one warning in untouched `src/modules/publishing/presentation/components/mobile/SchedulerTimelineBody.vue:23` (`viewport` unused). Exact scoped diff check was run; repo-wide diff-check is not claimed clean because unrelated worktree modifications exist.
- [x] 4.8 Record the verification report `verify-report-phase4.md` describing the change, the new composer test coverage, the focused suite result, the type-check and lint outcome, and any product/API/doc updates. Verdict: PASS WITH WARNINGS. Deterministic runner unavailable; see report for fallback evidence and limitations. Do not commit, push, archive, or open a PR without explicit user authorization.
- [x] 4.9 Re-run the acceptance QA `qa-report.md` end-to-end with the browser. Capture: auto-shortening of a single URL, dedup of repeated URL, multiple distinct URLs, no-URL text unchanged, best-effort failure does not block submit, edit mode does not shorten, no opt-in checkbox in the DOM. Update the matrix and verdict. Verdict: **PASS WITH WARNINGS** (Phase 4 rerun 2026-10-09 ~08:39 UTC evidences the two previously NOT TESTED scenarios). Browser-UI end-to-end: DOM confirmed without opt-in/Use/Keep/preview controls; submit triggered 2x `POST /api/v1/links 201` (dedup of 3 occurrences to 2 distinct destinations) followed by `POST /api/publishing/publications 200` with `bodyText` that only contains shortcodes (`EktNZ6C4pF`, `tBnEGjZ89o`) and reuses the same shortlink for the repeated URL; `scheduleMode=SCHEDULED_AT` with `dueAt=2030-01-15T09:37:00Z` keeps the publish off the worker. Member reread in workspace `...0006`: member JWT HS256 seeded `lgrvRHnLVZ` → `example.com`, two authenticated rereads showed the link and the `recordedRedirects` increment 0→1 after an anonymous 302 with the right Location; cross-workspace isolation confirmed (owner `...0002` does NOT see `lgrvRHnLVZ`). No publish, no Schedule Now, no OAuth exchange, no provider refresh, no destructive delete, no source/`env`/backup/process mutation, no commit/push/archive. Safety gate (publisher mock-only and provider refresh authorization safety) closed by runtime evidence (`POST /api/publishing/linkedin/connections/initiate` returned `authorizationUrl=http://localhost:33185/oauth/v2/authorization?...` with a HMAC-signed state; `tokenBaseUrl` and `authorizationBaseUrl` resolve to `localhost:33185`; WireMock journal empty for product paths during the run). Focused composer suite (60 tests) covers all seven Phase 4 scenarios; `shortlink-post-content.test.ts` (4 tests) covers URL extraction, dedup, and replacement. See `qa-report.md` for the matrix, evidence, and handoff.
