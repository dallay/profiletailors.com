# Proposal: Publication Edit Hardening

## Context

The archived `edit-publication-composer` change shipped successfully, but verification surfaced
three follow-up hardening items:

1. Pre-existing Detekt violations in `R2dbcPublishingRepositories.kt` (`LargeClass`, `LongMethod`)
   still block `just backend-check` / CI.
2. `CreatePostModal` edit mode lacks dedicated unit tests for several edit-specific branches.
3. The end-to-end edit-publication flow lacks dedicated Playwright coverage.

## Goal

Harden the publication editing feature so the backend quality gate passes, edit-mode behavior is
covered with focused unit tests, and the complete user flow is protected with E2E coverage.

## Scope

### In Scope

- Refactor `R2dbcPublishingRepositories.kt` to remove the current Detekt violations without changing
  behavior.
- Add dedicated `CreatePostModal` edit-mode unit tests.
- Add Playwright coverage for the full scheduler edit flow.
- Verify with targeted backend, frontend unit, lint, and E2E commands.

### Out of Scope

- Large-scale repository pattern rewrites outside publishing persistence.
- Broader scheduler UX changes unrelated to edit-flow correctness.
- New publishing features beyond hardening existing behavior.

## Approach

1. Refactor persistence code by extracting cohesive helpers from `R2dbcPublishingRepositories.kt`,
   especially around `insertOrUpdate` and any large responsibility clusters causing Detekt.
2. Add focused unit tests for `CreatePostModal` edit mode branches previously marked partial in
   verification.
3. Add a dedicated Playwright spec for scheduler publication editing.
4. Re-run quality gates and archive once all three hardening tracks are verified.
