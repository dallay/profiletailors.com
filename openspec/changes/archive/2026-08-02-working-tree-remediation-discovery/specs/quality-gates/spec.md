# Delta for Quality Gates

## ADDED Requirements

### Requirement: Detekt Baseline Removal

`server/smp/config/detekt/baseline.xml` MUST be deleted — it is an unreferenced stub ("baseline to be generated") with no lint effect — and `just backend-lint` MUST remain green after deletion. The active baseline `server/smp/detekt-baseline.xml` (227 suppressions, referenced by the detekt plugin) is NOT part of this change and MUST be left untouched; no suppressed-finding resolution is required for it.

#### Scenario: Backend lint is green after the stub deletion

- GIVEN `server/smp/config/detekt/baseline.xml` has been deleted
- WHEN `just backend-lint` runs
- THEN detekt MUST report zero errors
- AND the active `server/smp/detekt-baseline.xml` MUST remain in place and unchanged

### Requirement: Ideas Formatting Normalization

The ideas feature source (`IdeasApi.kt`, `IdeasCommandHandlers.kt`, `IdeaModels.kt`, `IdeasController.kt`, `R2dbcIdeaRepositories.kt`) MUST conform to the repo formatting standard (Spotless/Kotlin format). The `requireConnectedSocialAccountId` helper in `IdeasCommandHandlers.kt` MUST be renamed to `requireConnectedAccountId` everywhere it is referenced. Formatting changes MUST NOT alter behavior.

#### Scenario: Ideas sources are format-clean

- GIVEN the ideas feature source files
- WHEN `just backend-lint` / Spotless check runs
- THEN no formatting violations MUST be reported

#### Scenario: Renamed helper still enforces the same rule

- GIVEN a command handler calls the connected-account requirement helper
- WHEN the handler runs
- THEN the behavior MUST be identical under the renamed `requireConnectedAccountId`
- AND no caller references the old name

## TDD Requirement

Formatting is verified by `just backend-lint` (detekt + Spotless). The rename is covered by existing handler tests (`just backend-test-fast`). No new tests required beyond the gate itself — the stub baseline deletion is the regression check (the active baseline is untouched).
