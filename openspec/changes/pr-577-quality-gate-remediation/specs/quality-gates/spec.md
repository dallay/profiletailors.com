# Delta for Quality Gates

## ADDED Requirements

### Requirement: All PR #577 Sonar findings are remediated

The change MUST close all 14 listed Sonar findings without changing delivered behavior. The ten
`InputWithoutLabelCheck` findings MUST associate stable `id`/`for` pairs with the three
`AnalyticsView` controls, six `IdeasView` controls, and one `HashtagSuggestionPanel` saved-set name
input. The four code smells MUST be removed by extracting the `CreatePostModal` nested ternary,
making `HashtagAnalysisPort` functional or documenting an equivalent function-type justification,
using Kotlin `Unit` rather than `Void` in `HashtagsController`, and removing the useless Elvis in
`IdeasCommandHandlers`.

#### Scenario: Sonar findings and labels are clean

- GIVEN the remediation is applied
- WHEN Sonar analysis and focused component accessibility tests run
- THEN all 14 target locations report no open findings
- AND each listed input is labelable through its stable associated label without changing its user-visible behavior

### Requirement: Frontend behavior is covered at focused seams

The frontend MUST add behavior tests rather than snapshot-only or render-only tests. Store,
composable, and API-adapter tests MUST isolate transport with deterministic fakes; view/component
tests MUST assert user-visible state and emitted/requested effects.

#### Scenario: Ideas board behavior is exercised

- GIVEN an Ideas store or mounted `IdeasView` with controlled API responses
- WHEN tests load, mutate, move, rollback, edit columns, capture, save, delete, convert, or clear state
- THEN success, workspace/auth guard, normalization/fallback, loading, error, drag/drop, and cleanup paths are asserted

#### Scenario: Analytics, composer, and hashtag behavior is exercised

- GIVEN mocked API responses and Blob/download seams
- WHEN tests use Analytics presets/custom dates/export, AI optimize/validate/generate/accept/error paths, or hashtag suggest/apply/save/delete paths
- THEN empty/data/error/finally states, request shapes, minimum-content guards, 30-tag limits, and emitted actions are asserted

### Requirement: Backend Kover coverage uses unit and integration seams

The backend MUST add focused unit tests for Ideas, Analytics, and hashtag handlers, services,
controllers, validation, CSV construction, and error responses using in-memory fakes where the
boundary permits. It MUST add PostgreSQL/Testcontainers integration tests for R2DBC JSON mapping,
CRUD/upsert/order behavior, aggregates/pagination/zero-rate and best-time mapping, export rows,
saved-set mapping, and workspace isolation. BDD scenarios MUST remain contract coverage, not the
substitute for Kover-targeted tests.

#### Scenario: Backend behavior and coverage reports are report-backed

- GIVEN the focused unit and repository integration suites pass
- WHEN the unchanged quality workflow generates fresh Kover reports
- THEN the covered Ideas/Analytics/hashtag paths are included and project/patch gates meet the configured 80% target through the combined suite, with no claim based on a single test or test count

#### Scenario: WebFlux request context remains regression-safe

- GIVEN an authenticated request enters `AuthenticatedPrincipalContextWebFilter`
- WHEN the downstream chain completes
- THEN it is invoked exactly once and principal context is cleared without clearing unrelated request context
- AND the existing identity/tenancy regression tests remain passing

### Requirement: Quality-gate and delivery contracts remain unchanged

The change MUST NOT modify Codecov/Sonar thresholds, exclusions, report scope, workflow semantics,
delivery order, or product behavior. Existing CI, backend BDD, and frontend E2E contracts MUST be
preserved.

#### Scenario: Existing gates and suites are preserved

- GIVEN `codecov.yml`, `sonar-project.properties`, and `.github/workflows/quality-gate.yml` are compared before and after remediation
- WHEN the quality checks, CI, BDD, and E2E suites run
- THEN no threshold or exclusion change is present and existing workflows pass with the remediation reports
