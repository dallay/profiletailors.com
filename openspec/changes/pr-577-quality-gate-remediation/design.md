# Design: PR #577 Quality-Gate Remediation

## Technical Approach

Keep the PR physically unified but organize implementation into reviewable, ordered lanes:

1. **Sonar lane:** make the 14 minimum semantic/static fixes first—stable `id`/`for` label
   associations in Analytics, Ideas, and hashtags; the composer branch rewrite; and the three
   Kotlin smell fixes. No threshold, exclusion, workflow, or architecture changes.
2. **Coverage lane:** use strict TDD to add behavior tests only at existing frontend feature seams
   and backend domain/application/infrastructure boundaries. Prioritize files named by fresh LCOV and
   Kover missing-line reports, stopping when the unchanged 80% project/patch gates pass with margin.

This keeps Sonar reliability remediation distinguishable from coverage additions while preserving
feature-module boundaries and the backend dependency direction `domain <- application <- infrastructure`.

## Architecture Decisions

### Decision: One PR, separated commit groups

**Choice:** Keep PR #577 unified, with separate Sonar, frontend coverage, backend coverage, and
verification commits.
**Alternatives considered:** Splitting the PR, or changing thresholds/exclusions.
**Rationale:** Splitting conflicts with the delivery decision; gate weakening is invalid. Commit
boundaries preserve reviewability without changing delivery order.

### Decision: Test existing seams, not monolith internals

**Choice:** Test Pinia actions/composables, Vue user-visible branches, CQRS handlers/controllers,
and repository adapters through their existing ports.
**Alternatives considered:** Broad snapshots, E2E as coverage, or production refactoring to make
tests easier.
**Rationale:** Behavior assertions are less brittle and preserve architecture; BDD/E2E are not
included in the quality-gate Kover command.

### Decision: Fakes for application tests; PostgreSQL for R2DBC behavior

**Choice:** Use in-memory ports and fixed resource-context providers for handlers, capturing
mediators for controller dispatch tests, `WebTestClient` for HTTP validation/status contracts, and
the existing Testcontainers/Liquibase support for SQL and JSON mapping.
**Alternatives considered:** Fake-only repository tests or full Spring context tests for every case.
**Rationale:** This gives fast Kover-relevant branch coverage while exercising real R2DBC SQL,
workspace isolation, and mapping where fakes cannot provide confidence.

## Data Flow

```text
Vue component -> Pinia store/composable -> API adapter -> mocked fetch -> reactive render
WebTestClient -> Controller -> Mediator/handler -> domain port -> R2DBC -> PostgreSQL container
LCOV + Kover XML -> Sonar/Codecov-compatible report review -> gate decision
```

Frontend fixtures use the existing `createPinia`/`setActivePinia`, Vue Test Utils `mount`, mocked
`vue-i18n`/UI wrappers, typed factories for ideas/columns/analytics, and controlled API responses.
Drag/drop and Teleport are mocked or queried through stable test IDs; tests assert behavior, never
snapshots. Backend fixtures reuse capturing mediators and fixed workspace context patterns already
used by controller tests.

## File Changes

| File | Action | Description |
|---|---|---|
| `apps/web/app/src/modules/{ideas,analytics}/**` | Modify/Create tests | Store/view behavior, loading/error/empty branches, mutations, export, and date flows. |
| `apps/web/app/src/modules/publishing/{services,presentation}/**` | Modify/Create tests | API request shapes, hashtag composable/panel branches, and missing AI modal paths. |
| `server/smp/src/test/kotlin/com/profiletailors/smp/{ideas,analytics,hashtags}/**` | Create/Modify | Handler, controller, service, and Kover-relevant PostgreSQL repository tests. |
| `apps/web/app/src/modules/{ideas,dashboard,publishing}/**` and `server/smp/src/main/kotlin/com/profiletailors/smp/{ideas,analytics,hashtags}/**` | Modify | Only the 14 Sonar fixes; preserve behavior and package boundaries. |
| `server/smp/src/test/kotlin/com/profiletailors/smp/identity/**` | Preserve/Run | Retain the authenticated-principal single-chain regression test. |
| `codecov.yml`, `sonar-project.properties`, `.github/workflows/quality-gate.yml` | Preserve | No threshold, scope, exclusion, or workflow-semantic changes. |

## Interfaces / Contracts

- Frontend tests MUST exercise the existing store/composable APIs rather than introduce test-only
  production exports.
- Handler tests MUST provide `ResourceContextProvider` with a workspace and assert port calls,
  normalization, defaults, errors, and rollback behavior.
- Controller tests MUST preserve existing `Mediator` dispatch contracts and WebFlux problem-detail,
  validation, content-type, and CSV download behavior.
- Repository tests MUST use `PostgresTestContainerSupport`/Liquibase cleanup and verify JSON mapping,
  CRUD/upsert, date aggregation, pagination, zero-rate calculations, ordering, and workspace boundaries.

## Testing Strategy

Each slice follows **failing test -> minimum fix -> focused test -> refactor**. Run frontend Vitest
coverage and backend `test`/`postgresIntegrationTest` plus Kover XML using the same paths as
`.github/workflows/quality-gate.yml`; BDD remains contract coverage, not a Kover substitute.

For local Codecov/Sonar-equivalent measurement, inspect `apps/web/{marketing,app}/coverage/lcov.info`
and every configured `build/reports/kover/report.xml`, compare changed executable lines against
`main`, and review per-file missing lines before pushing. If credentials/tools are available, run
the repository `sonar-project.properties` with the generated reports; otherwise use the same
post-push Sonar/Codecov reports as the source of truth. Do not infer success from test counts.

## Migration / Rollout

No migration required. Roll back by reverting the remediation commit group; application data and
quality-gate configuration are unchanged.

## Open Questions

- [ ] Fresh Codecov/Sonar runs must establish the final patch denominator; the explored eight-line
  discrepancy is not a planning constant.
- [ ] Local verification requires Docker/Testcontainers and configured Sonar/Codecov credentials
  for full parity; missing credentials do not justify changing the gates.
