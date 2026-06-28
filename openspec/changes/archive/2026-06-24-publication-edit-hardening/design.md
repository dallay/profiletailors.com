# Design: Publication Edit Hardening

## Overview

This change hardens the archived composer-based publication editing feature by closing three
follow-up gaps:

1. **Backend quality gate debt** — `R2dbcPublishingRepositories.kt` contains pre-existing Detekt
   findings (`LargeClass`, `LongMethod`) that block `just backend-check`.
2. **Focused edit-mode unit coverage** — `CreatePostModal` implements edit-mode behavior but several
   branches were only structurally verified or indirectly covered.
3. **End-to-end edit-flow protection** — the scheduler edit journey needs explicit Playwright
   regression coverage.

## Design Decisions

### 1. Refactor the publishing persistence adapter without changing contracts

The persistence refactor must remain behavior-preserving. The safe direction is to extract small
private helpers and/or file-local collaborators around:

- SQL binding for publication writes
- update-vs-insert orchestration in `insertOrUpdate`
- repeated asset hydration/link helpers

The goal is not to redesign the repository abstraction, but to reduce method/class size while
preserving the `PublicationRepository` contract and existing tests.

### 2. Add dedicated CreatePostModal edit-mode tests

Focus on the exact verification gaps previously marked partial:

- locked/disabed channel selector in edit mode
- create-only controls hidden in edit mode
- `updatePost()` branch instead of `schedulePost()`
- `updated` event emission
- edit-mode prefill for schedule mode, priority, media, and content
- error handling on failed update

Tests should remain unit-level and use existing Pinia/mocking patterns in `CreatePostModal.test.ts`.

### 3. Add Playwright coverage for the scheduler edit flow

Add a dedicated Playwright spec that exercises:

- opening a publication detail modal
- clicking Edit
- verifying composer edit mode opens with prefilled values
- saving an update
- verifying the scheduler reflects the updated publication

Prefer existing scheduler test fixtures/utilities and keep the scenario isolated to the edit flow.

## Verification Strategy

- Backend: targeted publishing tests + `just backend-check`
- Frontend unit: `pnpm test` in `apps/web/app`
- Frontend lint: `pnpm lint` in `apps/web/app`
- E2E: targeted Playwright spec for scheduler edit flow
