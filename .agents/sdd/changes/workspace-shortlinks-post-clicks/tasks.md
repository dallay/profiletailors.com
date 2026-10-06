# Tasks: Workspace Shortlinks for Publications and Click Metrics

## Review Workload Forecast

| Field | Value |
|---|---|
| Estimated changed lines | 500–800 |
| 400-line budget risk | High |
| Chained PRs recommended | Yes |
| Suggested split | PR 1 → PR 2 → PR 3 |
| Delivery strategy | ask-on-risk |
| Chain strategy | github-stacked-prs |

Decision needed before apply: Yes
Chained PRs recommended: Yes
Chain strategy: github-stacked-prs
400-line budget risk: High

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|---|---|---|---|
| 1 | Persist best-effort redirect clicks | PR 1 | trunk=main; parent_branch=main; base=main; branch=workspace-shortlinks-click-capture; position=1; issue/Linear metadata=not linked (confirm before branch). Migration + backend tests included. |
| 2 | Workspace-scoped metrics query/API | PR 2 | trunk=main; parent_branch=workspace-shortlinks-click-capture; base=workspace-shortlinks-click-capture; branch=workspace-shortlinks-analytics-api; position=2; issue/Linear metadata=not linked (confirm before branch). Depends on PR 1. |
| 3 | Composer integration and per-workspace metrics UI | PR 3 | trunk=main; parent_branch=workspace-shortlinks-analytics-api; base=workspace-shortlinks-analytics-api; branch=workspace-shortlinks-publishing-ui; position=3; issue/Linear metadata=not linked (confirm before branch). Depends on PR 2. |

## Phase 1: Click Capture Foundation (PR 1)

- [ ] 1.1 RED: Test click recording only for active redirects; inactive/missing links and tracking failure preserve redirect behavior.
- [ ] 1.2 Add Liquibase migration under `server/smp/src/main/resources/db/changelog/shortlinks/` for link identity and timestamp only.
- [ ] 1.3 GREEN: Add shortlink persistence port/adapter; await best-effort recording after resolution. Measure latency; never detach work.
- [ ] 1.4 Verify migration and redirect recording with focused persistence/shortlinks tests.

## Phase 2: Workspace Metrics API (PR 2)

- [ ] 2.1 RED: Add query tests for workspace-derived ownership filtering and identical not-found outcomes for unknown/foreign link IDs.
- [ ] 2.2 Implement application query and infrastructure endpoint under existing `/api/v1/links` conventions; derive workspace from authenticated context, never caller-supplied IDs.
- [ ] 2.3 Add API serialization/auth tests and Cucumber scenarios for authorized workspace metrics and cross-workspace denial using `WebTestClient`, versioned Accept, and workspace headers.
- [ ] 2.4 Verify auth/media-type behavior, click counts/time scope, and foreign-resource non-disclosure; run backend check and fast BDD.

## Phase 3: Publishing and Metrics UI (PR 3)

- [ ] 3.1 RED: Add publishing tests for opt-in default, preview, create payload, and link-creation failure retaining original text.
- [ ] 3.2 Integrate shortlink creation in `CreatePostModal.vue` and publishing API/client; replace body URL only after explicit confirmation, show preview, preserve original on failure, and do not alter edit/reschedule.
- [ ] 3.3 RED/GREEN: Add workspace metrics UI tests for loading, empty/error states, recorded redirect counts, and workspace-only API usage; implement UI through the stable feature boundary.
- [ ] 3.4 Verify dashboard lint, type-check, unit tests, build, and critical composer/metrics E2E where feasible.

## Phase 4: Contract and Architecture Documentation

- [ ] 4.1 Reconcile `docs/architecture/adr/0028-defer-shortlinks-beyond-core-v1.md` with the approved bounded integration while preserving historical rationale and explicitly deferred capabilities.
- [ ] 4.2 Update API-versioning/docs and applicable `PRODUCT.md` or publishing documentation with opt-in, click meaning, workspace isolation, and no retention/accuracy guarantee; confirm ADR index/C4 impact and update only if needed.
- [ ] 4.3 Check links and final diff; record verification and unresolved latency or endpoint-contract risks before archive.
