# Delta for Code Hygiene

## ADDED Requirements

### Requirement: Dead Suppression Removal in MediaHandlers

The system MUST NOT contain `@Suppress("TooGenericExceptionCaught")` in `server/smp/src/main/kotlin/com/profiletailors/smp/media/application/MediaHandlers.kt`. The annotation is dead because `detekt.yml:202-215` excludes `**/application/**` from that rule. Surrounding code MUST remain byte-identical otherwise.

#### Scenario: Dead annotation is gone

- GIVEN `MediaHandlers.kt` at its proposal revision
- WHEN the change is applied
- THEN `grep TooGenericExceptionCaught` over `MediaHandlers.kt` returns zero matches

#### Scenario: Deletion-only diff

- GIVEN the applied change
- WHEN `git diff` is inspected for `MediaHandlers.kt`
- THEN the diff shows exactly 1 deleted line and zero added or modified logic lines

### Requirement: Deferred Baseline-Coupled Debt in StaleAssetReconciler

The system SHALL retain `@Suppress("TooGenericExceptionCaught")` + trailing comment at `server/smp/src/main/kotlin/com/profiletailors/smp/media/application/StaleAssetReconciler.kt:96` byte-identical until a future lote removes it together with a tool-run baseline regeneration. The annotation is dead w.r.t. the Detekt rule but load-bearing w.r.t. baseline stability: `detekt-baseline.xml:88` embeds the annotation text in the `LongMethod:processBlob` ID, so deletion resurfaces LongMethod as a new finding. The baseline MUST NOT be hand-edited.

#### Scenario: Annotation retained as recorded debt

- GIVEN the applied change
- WHEN `grep TooGenericExceptionCaught` over `media/application` runs
- THEN exactly 1 match remains at `StaleAssetReconciler.kt:96`, byte-identical to the proposal revision

#### Scenario: Removal deferred with baseline regeneration

- GIVEN a future lote under epic #1019
- WHEN `StaleAssetReconciler.kt:96` is removed
- THEN `detekt-baseline.xml` is regenerated/shrunk via tool run in the same lote, never by hand-edit

### Requirement: Static-Analysis Gates Stay Green

`just backend-lint` MUST PASS with zero new Detekt findings, and zero new `@Suppress` annotations MUST be introduced anywhere in the change (`ForbiddenSuppress` stays clean).

#### Scenario: Backend lint passes

- GIVEN the `MediaHandlers.kt` annotation removed
- WHEN `just backend-lint` runs
- THEN it exits PASS with no new findings (baseline only shrinks or stays equal)

#### Scenario: No new suppressions

- GIVEN the applied change
- WHEN the diff is scanned for `@Suppress`
- THEN zero added `@Suppress` lines exist

### Requirement: Architecture and Config Integrity

`just backend-check` MUST PASS (`HexagonalArchTest`, `ComponentScanArchTest` green), `detekt-baseline.xml` MUST gain zero new entries, and `detekt.yml`, `shared/`, `package.json`, and `pnpm-lock.yaml` MUST be untouched.

#### Scenario: Backend check passes

- GIVEN the `MediaHandlers.kt` annotation removed
- WHEN `just backend-check` runs
- THEN it exits PASS with architecture tests green

#### Scenario: Protected files untouched

- GIVEN the applied change
- WHEN `git status --porcelain` is inspected
- THEN only `MediaHandlers.kt` plus OpenSpec artifacts are modified
