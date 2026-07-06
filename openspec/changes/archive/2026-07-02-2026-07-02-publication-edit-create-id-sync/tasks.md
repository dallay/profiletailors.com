# Tasks: Publication Edit Create-ID Sync

## Review Workload Forecast

| Field                   | Value             |
|-------------------------|-------------------|
| Estimated changed lines | 280–380           |
| 400-line budget risk    | Medium            |
| Chained PRs recommended | No                |
| Suggested split         | Single grouped PR |
| Delivery strategy       | single-pr         |
| Chain strategy          | pending           |

Decision needed before apply: No
Chained PRs recommended: No
Chain strategy: pending
400-line budget risk: Medium

### Suggested Work Units

| Unit | Goal                                                            | Likely PR | Notes                                                      |
|------|-----------------------------------------------------------------|-----------|------------------------------------------------------------|
| 1    | Reconcile create identity/schedules and preserve edit contracts | PR 1      | Single grouped PR; tests and browser verification included |

## Phase 1: Store Reconciliation (RED → GREEN → REFACTOR)

- [x] 1.1 RED: In `apps/web/app/src/stores/publishing.test.ts`, add failing authenticated
  standard-create and quick-create cases asserting adoption of real `publicationId`, `status`,
  `socialAccountId`, assets, and normalized schedule fields.
- [x] 1.2 RED: Add store cases for NOW, NEXT_SLOT, and CUSTOM/SCHEDULED_AT mapping, authenticated
  failure without placeholder insertion, and synthetic IDs only for local fallback.
- [x] 1.3 GREEN: In `apps/web/app/src/stores/publishing.ts`, return the mutation result, centralize
  backend-result mapping, and reconcile both create actions from server truth.
- [x] 1.4 REFACTOR: Remove duplicated create mapping while retaining the existing `Publication`
  contract and local-only behavior; rerun the focused store suite.

## Phase 2: Edit Initialization and Assets (RED → GREEN → REFACTOR)

- [x] 2.1 RED: In `apps/web/app/src/components/CreatePostModal.test.ts`, add failing edit
  initialization cases for NOW, NEXT_SLOT, and CUSTOM without stale custom date/time values.
- [x] 2.2 RED: Preserve #223 cases: untouched loaded assets omit `assetIds`, explicit clear sends
  `[]`, replacement sends exact IDs, and missing assets are skipped safely.
- [x] 2.3 GREEN: Update `apps/web/app/src/components/CreatePostModal.vue` to initialize mode/time
  from normalized fields while leaving asset hydration and touch tracking intact.
- [x] 2.4 REFACTOR: Simplify mode initialization without altering PATCH error propagation; rerun the
  focused modal suite.

## Phase 3: Create-to-Edit Integration (RED → GREEN)

- [x] 3.1 RED: Update `apps/web/app/e2e/fixtures/scheduler-mocks.ts` with stable backend-shaped POST
  results and strict unknown-PATCH 404 behavior.
- [x] 3.2 RED: Extend `apps/web/app/e2e/specs/scheduler-create-post.spec.ts` so normal and quick
  create reopen and PATCH the POST-returned ID.
- [x] 3.3 RED: Extend `apps/web/app/e2e/specs/scheduler-edit-post.spec.ts` for NOW/NEXT_SLOT/CUSTOM
  prefill and untouched/clear/replace asset behavior; apply only minimal production changes needed
  for GREEN.

## Phase 4: Focused Verification

- [x] 4.1 Run focused Vitest store/modal suites and scheduler Playwright specs; confirm create→edit
  state is replaced by successful PATCH server truth.
- [x] 4.2 Run existing backend gates for #224 workspace isolation and #225 PATCH 404/problem mapping
  without backend production edits.
- [x] 4.3 With browser MCP, rerun authenticated create with image → edit → save → reopen; verify
  preserved image, real-ID PATCH, normalized schedule/status, successful network responses, and no
  console errors.
