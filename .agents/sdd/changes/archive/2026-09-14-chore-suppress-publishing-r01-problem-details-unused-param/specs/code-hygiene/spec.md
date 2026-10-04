# Delta for Code Hygiene

## ADDED Requirements

### Requirement: Live Suppression Removal in PublishingProblemDetailsHandler

The system MUST NOT contain the 3 `UNUSED_PARAMETER` suppressions in
`PublishingProblemDetailsHandler.kt`: `:39` (`ProviderNotConfiguredException`), `:71`
(`PublicationNotFoundException`), `:158` (`RecurringScheduleNotFoundException`). Each handler SHALL
be renamed (`handleProviderNotConfigured`, `handlePublicationNotFound`,
`handleRecurringScheduleMissing`) with the unread exception parameter dropped. Parameters that ARE
read (`reason`/`denial`/`message`/`jobId`) MUST stay untouched.

#### Scenario: Three suppressions gone via rename-and-drop

- GIVEN the change is applied
- WHEN `PublishingProblemDetailsHandler.kt` is grepped for the 3 listed sites
- THEN zero `@Suppress("UNUSED_PARAMETER")` remain at those sites and each renamed handler takes
  zero exception parameter

#### Scenario: Lint-oracle pre-check cited

- GIVEN each of the 3 sites before removal
- WHEN its `@Suppress` is deleted and `just backend-lint` runs
- THEN Detekt flags it live, or the site converts to deletion-only with oracle evidence cited

### Requirement: Behavior-Preserving Handler Rename

Each renamed handler MUST map the same exception to the same status/title `ProblemDetail` as before
the rename. A new web-slice regression test SHALL assert one case per renamed handler (fails
pre-rename, passes post-rename); existing `infrastructure/http` web tests and
`publishing-publications.feature` MUST stay green.

#### Scenario: Exception mapping unchanged

- GIVEN the 3 renamed handlers
- WHEN each mapped exception is raised in the web slice
- THEN the response carries the pre-change status/title `ProblemDetail`

#### Scenario: Existing publishing gates green

- GIVEN the rename applied
- WHEN `infrastructure/http` web tests and `publishing-publications.feature` run
- THEN all pass with zero behavior diff

### Requirement: Static-Analysis Gates Stay Green (Publishing R01)

`just backend-lint` MUST PASS with zero new Detekt findings, and zero new `@Suppress` annotations
MUST be introduced anywhere in the change.

#### Scenario: Backend lint passes

- GIVEN the 3 renames + drops applied
- WHEN `just backend-lint` runs
- THEN it exits PASS with no new findings (baseline only shrinks or stays equal)

#### Scenario: No new suppressions

- GIVEN the applied change
- WHEN Kotlin source changes are scanned for added `@Suppress` lines
- THEN zero added `@Suppress` lines exist; OpenSpec text is excluded from the scan

### Requirement: Architecture and Config Integrity (Publishing R01)

`just backend-check` MUST PASS (`HexagonalArchTest`, `ComponentScanArchTest` green).
`TooManyFunctions` (`:35`), the remaining 8 `UNUSED_PARAMETER` sites, signatures visible outside the
class, `detekt-baseline.xml`, `detekt.yml`, and `shared/` MUST be untouched.

#### Scenario: Backend check passes

- GIVEN the rename-only change applied
- WHEN `just backend-check` runs
- THEN it exits PASS with architecture tests green

#### Scenario: Protected surface untouched

- GIVEN the change is applied
- WHEN `git status --porcelain` and the handler diff are inspected
- THEN only `PublishingProblemDetailsHandler.kt` + its web-slice test plus OpenSpec artifacts are
  modified, with `:35`, the 8 deferred sites, and config/baseline byte-identical
