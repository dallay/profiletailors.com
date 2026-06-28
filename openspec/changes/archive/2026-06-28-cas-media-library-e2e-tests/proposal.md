# Proposal: CAS Media Library E2E Tests

## Intent

Implement the approved CAS Media Library test plan as reliable, maintainable automation. The change
validates existing browser behavior and observable CAS request sequences without using tests to
conceal or repair product defects.

## Scope

### In Scope

- Build media-specific Playwright configs, page objects, deterministic fixtures, request ledgers,
  and per-test state.
- Deliver phased slices: real CAS smoke; mocked UI/error/accessibility; composer integration;
  workspace isolation/concurrency where feasible.
- Add CI commands/cadence, failure artifacts, and backend-contract follow-ups only where required
  evidence is non-browser.

### Out of Scope

- Product fixes, lifecycle renaming, API redesign, storage/DB implementation changes, or
  accessibility remediation.
- Proving physical blob uniqueness, locking, GC, reference counting, or 500 MB memory behavior in
  Playwright.
- Browse-from-library composer functionality, broader DAM features, and exhaustive real-service
  scenarios on every PR.

## Capabilities

### New Capabilities

None. The test contract already exists in `openspec/specs/e2e/cas-media-library-test-plan.md`.

### Modified Capabilities

None. This change implements tests without changing product requirements.

## Approach

1. Add isolated media harness and deterministic fixture manifest.
2. Implement `media-smoke-real` against real CAS with run-scoped data and no CAS interception.
3. Implement parallel `media-ui-mocked` coverage for loading, failures, polling, accessibility, and
   responsive states.
4. Extend composer coverage for attachment readiness, limits, failure blocking, and published
   `assetId`.
5. Add workspace switching and barrier-based concurrency only when safe isolation prerequisites
   exist.
6. Add focused WebFlux/PostgreSQL/storage tests only for uncovered non-browser contracts.

## Affected Areas

| Area                        | Impact             | Description                                   |
|-----------------------------|--------------------|-----------------------------------------------|
| `apps/web/app/e2e/`         | Modified           | Media configs, fixtures, page objects, suites |
| `apps/web/app/package.json` | Modified           | Media E2E commands                            |
| `.github/workflows/ci.yml`  | Modified           | Mocked, smoke, and scheduled lanes            |
| `server/smp/src/test/`      | Modified if needed | Missing backend-contract evidence only        |

## Risks

| Risk                                       | Likelihood | Mitigation                                         |
|--------------------------------------------|------------|----------------------------------------------------|
| Shared data causes flaky/destructive tests | High       | Unique run workspaces, markers, idempotent cleanup |
| HAR masks real CAS traffic                 | High       | Separate config; forbid CAS interception           |
| Product drift appears as test failure      | High       | Record known defects; do not normalize/suppress    |

Known drift includes `PROCESSING` versus canonical lifecycle, dedup `200` versus documented `201`,
unnamed card actions, missing field `id`/`name`, and no composer library selector.

## Rollback Plan

Remove media-specific suites/config/scripts and CI jobs; retain the source test plan and existing
scheduler/unit/backend suites unchanged.

## Dependencies

- Seeded isolated users/workspaces, cleanup APIs, test storage, PostgreSQL, and real-backend Vite
  mode.

## Success Criteria

- [ ] Mocked suites are independent, parallel-safe, deterministic, and retain diagnostics on
  failure.
- [ ] Real smoke validates ordered CAS traffic without shared workspace data or HAR interception.
- [ ] Scenario ownership distinguishes browser evidence from backend-contract evidence and documents
  drift.
