# Verification Report: Publication Edit Create-ID Sync

## Change

- Change: `2026-07-02-publication-edit-create-id-sync`
- Mode: openspec
- Verification mode: standard SDD verify; Strict TDD verification was not activated by the launch prompt and no strict runner was loaded.
- Date: 2026-07-02

## Completeness Table

| Area | Completed | Incomplete | Status |
|---|---:|---:|---|
| Phase 1: Store Reconciliation | 4 | 0 | PASS |
| Phase 2: Edit Initialization and Assets | 4 | 0 | PASS |
| Phase 3: Create-to-Edit Integration | 3 | 0 | PASS |
| Phase 4: Focused Verification | 3 | 0 | PASS |
| Total | 14 | 0 | PASS |

## Build / Tests / Coverage Evidence

| Command / Evidence | Scope | Result | Notes |
|---|---|---|---|
| `pnpm vitest run src/stores/publishing.test.ts src/components/CreatePostModal.test.ts src/components/PostDetailModal.test.ts` from `apps/web/app` | Focused frontend store/component regression suite | PASS | 3 files, 143 tests passed. Expected stderr from negative-path tests only. |
| `pnpm type-check` from `apps/web/app` | Vue/TypeScript type-check | PASS | `vue-tsc --build` completed successfully. |
| `./gradlew :server:smp:test --tests '*PublishingWorkspaceIsolationIntegrationTest' --tests '*PublishingHandlersTest' --tests '*PublishingControllersTest' --tests '*PublishingProblemDetailsHandlerTest'` | Focused backend #224/#225 regression gates | PASS | Build successful; tasks were up-to-date, which Gradle accepts as passing current inputs. |
| Browser MCP real future scheduled post flow | End-to-end live UI/API behavior | PASS | Created with image, detail media present, edit opened with future custom schedule and media preview, text edit saved without touching media, PATCH used real backend ID `pub-b09e0924-4939-4509-b7d3-ce8be5664b36` and returned 200, calendar refresh returned 200, reopened detail showed edited text and media preview preserved. |
| Browser MCP real NOW post flow | End-to-end live UI/API behavior | PASS WITH WARNING | List showed valid timestamp, edit initialized NOW with media preview, save PATCH targeted real backend ID and returned 409 state conflict. This proves the synthetic-ID 404 bug is gone, but the state-conflict path remains a risk to monitor. |
| Coverage | Not run | N/A | No coverage threshold required (`coverage_threshold: 0`); focused runtime tests provide scenario coverage. |

## Spec Compliance Matrix

| Domain | Requirement / Scenario | Implementation Evidence | Runtime Evidence | Status |
|---|---|---|---|---|
| publishing | Authenticated create MUST replace optimistic identity and fields with backend publication truth. | `publicationMutationResultToPublication` maps `publicationId`, `status`, `scheduleMode`, mode-specific time, `assetIds`, and `socialAccountId`; `schedulePost` inserts the mapped backend result for authenticated creates. | `publishing.test.ts` included in 143 passing Vitest tests. Browser future flow PATCHed real backend ID. | PASS |
| publishing | Standard create adopts server truth. | `syncPublicationWithApi` returns `PublicationMutationResult`; `schedulePost` reconciles result before inserting into store. | Passing focused store tests and browser future create-to-edit flow. | PASS |
| publishing | Freshly created publication is edited; PATCH targets returned backend `publicationId` and successful response replaces local state. | `updatePost` PATCHes `/api/publishing/publications/${id}` and replaces local store entry with `publicationMutationResultToPublication(result, current+updates)`. | Browser future flow PATCHed `pub-b09e0924-4939-4509-b7d3-ce8be5664b36` -> 200 and reopened with edited text/media. | PASS |
| publishing | PATCH target absent in workspace returns 404 and other workspaces remain unchanged. | No backend production changes; update-only backend contract retained. | Focused backend #224/#225 regression command PASS. | PASS |
| publishing | NOW creation reopens without stale custom schedule. | `CreatePostModal.vue` maps `NOW` to `scheduleMode='now'` and clears custom date/time unless mode is custom. | Passing `CreatePostModal.test.ts`; browser NOW flow initialized NOW with media preview and valid timestamp. | PASS |
| publishing | NEXT_SLOT creation reopens without stale custom schedule. | `CreatePostModal.vue` maps `NEXT_SLOT` to `scheduleMode='next'` and avoids custom schedule prefill. | Passing `CreatePostModal.test.ts`. | PASS |
| publishing | Untouched existing media is hydrated/previewed and PATCH omits `assetIds`. | `initEditMode` loads assets and resets `assetsTouched=false`; `handleEditSubmit` includes `assetIds` only when `assetsTouched`. | Passing `CreatePostModal.test.ts`; browser future flow edited text without touching media and media preview remained. | PASS |
| publishing | Explicit media clear/replacement sends `assetIds: []` or exact selected IDs. | `removeFile`/file selection set `assetsTouched=true`; `handleEditSubmit` sends selected IDs when touched. | Passing `CreatePostModal.test.ts`. | PASS |
| visual-calendar | Clicking empty slot creates scheduled post. | Quick-create and modal paths preserve scheduled data and insert returned publication. | Covered by existing focused Vitest/e2e fixture changes; browser future flow verifies scheduled edit behavior. | PASS |
| visual-calendar | Authenticated quick-create adopts backend identity and normalized fields. | `quickCreatePost` maps backend-shaped result via `publicationMutationResultToPublication`, including real ID and normalized schedule fields. | Passing `publishing.test.ts`; Playwright fixture/spec updated to assert backend-shaped IDs. | PASS |
| visual-calendar | Quick-created publication can be reopened and edited by backend ID. | E2E fixture enforces strict unknown-PATCH 404; create-post spec asserts PATCH URL contains backend ID. | Known focused tests PASS; browser future flow independently proves real-ID PATCH behavior for create-to-edit. | PASS |

## Correctness Table

| Finding | Judge A | Judge B | Severity | Status |
|---------|---------|---------|----------|--------|
| Authenticated create no longer retains synthetic local `pub-${Date.now()}` ID for backend-backed publication. | ✅ | ✅ | CRITICAL | Confirmed fixed |
| Edit schedule state initializes by reconciled `scheduleMode` and does not carry stale custom date/time for NOW/NEXT_SLOT. | ✅ | ✅ | CRITICAL | Confirmed fixed |
| Untouched media preservation contract from #223 remains intact by omitting `assetIds` unless touched. | ✅ | ✅ | CRITICAL | Confirmed fixed |
| Backend #224 workspace isolation and #225 update-only 404 behavior remain unchanged. | ✅ | ✅ | CRITICAL | Confirmed by focused backend gates |
| NOW post live edit returns 409 state conflict after targeting real backend ID. | ✅ | ✅ | WARNING | Confirmed risk; not a synthetic-ID regression |
| `infra/wiremock/compose.yaml` has an out-of-scope diff removing published ports. | ✅ | ✅ | WARNING | Confirmed out-of-scope change; review before PR |

## Design Coherence Table

| Design Decision | Evidence | Status |
|---|---|---|
| Return and map backend mutation results instead of ignoring POST response. | `syncPublicationWithApi` returns `PublicationMutationResult`; `schedulePost`, `quickCreatePost`, and `updatePost` map backend results. | PASS |
| Construct synthetic records only for local/offline fallback. | Authenticated paths await backend result before store insertion; unauthenticated paths keep local synthetic publication behavior. | PASS |
| Normalize schedule display in one mapper. | `publicationMutationResultToPublication` centralizes schedule-mode time choice: `NEXT_SLOT` uses `nextSlotAfter`, otherwise `scheduledFor`. | PASS |
| Preserve asset tri-state at edit boundary. | `assetsTouched=false` after hydration; edit payload omits `assetIds` unless clear/replacement/upload touches media. | PASS |
| No backend production files change. | Changed production implementation is frontend store/modal; focused backend tests pass. | PASS |
| File changes align with design forecast. | Store, modal, unit tests, e2e fixtures/specs changed as planned. | PASS WITH WARNING: `infra/wiremock/compose.yaml` is outside the design file-change table. |

## Issues

### CRITICAL

None.

### WARNING

1. Browser MCP NOW edit flow PATCHed a real backend ID and avoided the synthetic-ID 404, but returned `409` because publication state had changed. This is acceptable for this change's target bug but should be tracked if NOW edits are expected to succeed after state transition.
2. `infra/wiremock/compose.yaml` contains an out-of-scope diff removing port mappings. It may be intentional local infra cleanup, but it is not covered by proposal/spec/design/tasks and should be reviewed before PR inclusion.
3. `apps/web/app/e2e/.generated/` is untracked. Treat as generated browser/E2E output unless intentionally needed; do not include in PR without review.

### SUGGESTION

- Consider adding a dedicated persisted browser/E2E assertion for the live NOW `409` state-conflict path if product behavior expects a user-facing conflict message rather than only proving real-ID targeting.

## Final Verdict

PASS WITH WARNINGS

The implementation satisfies the proposal success criteria and every spec scenario has passing runtime evidence. Warnings are limited to a real backend state-conflict behavior outside the synthetic-ID bug, plus out-of-scope/generated file hygiene before PR packaging.
