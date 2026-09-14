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

The system SHALL retain `@Suppress("TooGenericExceptionCaught")` + trailing comment at `server/smp/src/main/kotlin/com/profiletailors/smp/media/application/StaleAssetReconciler.kt:96` byte-identical for this change. The annotation is dead w.r.t. the Detekt rule but load-bearing w.r.t. baseline stability: `detekt-baseline.xml:88` embeds the annotation text in the `LongMethod:processBlob` ID, so deletion resurfaces LongMethod as a new finding. Any potential future removal is a separately scoped change and would require tool-run baseline regeneration; removal is not required by this specification. The baseline MUST NOT be hand-edited.

#### Scenario: Annotation retained as recorded debt

- GIVEN the applied change
- WHEN `grep TooGenericExceptionCaught` over `media/application` runs
- THEN exactly 1 match remains at `StaleAssetReconciler.kt:96`, byte-identical to the proposal revision

#### Scenario: Removal deferred with baseline regeneration

- GIVEN a future batch under epic #1019
- WHEN `StaleAssetReconciler.kt:96` is removed
- THEN `detekt-baseline.xml` is regenerated/shrunk via tool run in the same batch, never by hand-edit

### Requirement: Static-Analysis Gates Stay Green

`just backend-lint` MUST PASS with zero new Detekt findings, and zero new `@Suppress` annotations MUST be introduced anywhere in the change (`ForbiddenSuppress` stays clean).

#### Scenario: Backend lint passes

- GIVEN the `MediaHandlers.kt` annotation removed
- WHEN `just backend-lint` runs
- THEN it exits PASS with no new findings (baseline only shrinks or stays equal)

#### Scenario: No new suppressions

- GIVEN the applied change
- WHEN Kotlin source changes are scanned for added `@Suppress` lines or `ForbiddenSuppress` results are inspected
- THEN zero added `@Suppress` lines exist in Kotlin source and `ForbiddenSuppress` stays clean; OpenSpec documentation text is excluded from the scan

### Requirement: Architecture and Config Integrity

`just backend-check` MUST PASS (`HexagonalArchTest`, `ComponentScanArchTest` green), `detekt-baseline.xml` MUST gain zero new entries, and `detekt.yml`, `shared/`, `package.json`, and `pnpm-lock.yaml` MUST be untouched.

#### Scenario: Backend check passes

- GIVEN the `MediaHandlers.kt` annotation removed
- WHEN `just backend-check` runs
- THEN it exits PASS with architecture tests green

#### Scenario: Protected files untouched

- GIVEN the change is applied to a clean checkout or its PR base revision is identified
- WHEN `git status --porcelain` is inspected in that clean checkout or the PR diff is compared with its base
- THEN only `MediaHandlers.kt` plus OpenSpec artifacts are modified

### Requirement: Dead Suppression Removal in Tenancy Handlers

The system MUST NOT contain the 4 dead suppressions: `TenancyOwnershipHandlersInternal.kt:35` (`TooGenericExceptionCaught`, dead via `detekt.yml:214` `**/application/**` exclusion), `:66` (`ThrowsCount`, 1 throw), `:223` (`ThrowsCount`, 2 throws), `UpdateWorkspaceMembershipStatusHandler.kt:62` (`TooGenericExceptionCaught`, same exclusion). Surrounding code MUST remain byte-identical. `UpdateWorkspaceMembershipStatusHandler.kt:25` (`ThrowsCount`) is RETAINED as deferred debt — Detekt counts 4 throws (2 `?: throw` plus 2 catch-block rethrows) against strict `max: 3`, so the paper premise of 2 throws was wrong and removal trips lint.

#### Scenario: Dead annotations are gone

- GIVEN the change is applied
- WHEN both tenancy files are grepped for the 4 listed suppressions
- THEN zero matches return, while `UpdateWorkspaceMembershipStatusHandler.kt:25` still carries `@Suppress("ThrowsCount")`

#### Scenario: Deletion-only diff

- GIVEN the applied change
- WHEN `git diff` is inspected for both files
- THEN it shows exactly 4 removed suppressions, 1 narrowed line, 1 retained line (`:25`), zero logic/signature/test changes

### Requirement: Narrowed Suppression at TransferWorkspaceOwnershipHandler

`TenancyOwnershipHandlersInternal.kt:140` SHALL be narrowed from `@Suppress("ThrowsCount", "LongMethod")` to `@Suppress("LongMethod")`. `ThrowsCount` is dead at exactly 3 throws vs strict `max: 3`; `LongMethod` is live at ~66 effective LOC vs 60.

#### Scenario: ThrowsCount dropped, LongMethod retained

- GIVEN the change is applied
- WHEN line 140 is inspected
- THEN it reads `@Suppress("LongMethod")` with no `ThrowsCount` token

### Requirement: Deferred LongMethod Debt on Transfer Handler

The system SHALL retain the narrowed `LongMethod` suppression byte-identical, as recorded deferred debt (same pattern as Lote 1's `StaleAssetReconciler` retention). `UpdateWorkspaceMembershipStatusHandler.kt:25` (`ThrowsCount`, 4 counted throws > max 3) is likewise retained as deferred debt. Removal of either is a separately scoped change; the baseline MUST NOT be hand-edited.

#### Scenario: Annotation retained as recorded debt

- GIVEN the applied change
- WHEN tenancy application sources are grepped for `LongMethod`
- THEN exactly 1 match remains at `TenancyOwnershipHandlersInternal.kt:140`

### Requirement: Static-Analysis Gates Stay Green (Tenancy Batch)

`just backend-lint` MUST PASS with zero new Detekt findings, and zero new `@Suppress` annotations MUST be introduced anywhere in the change.

#### Scenario: Backend lint passes

- GIVEN the 4 removals + 1 narrowing + 1 retention applied
- WHEN `just backend-lint` runs
- THEN it exits PASS with no new findings (baseline only shrinks or stays equal)

#### Scenario: No new suppressions

- GIVEN the applied change
- WHEN Kotlin source changes are scanned for added `@Suppress` lines
- THEN zero added `@Suppress` lines exist; OpenSpec text is excluded from the scan

### Requirement: Architecture and Config Integrity (Tenancy Batch)

`just backend-check` MUST PASS (`HexagonalArchTest`, `ComponentScanArchTest` green), and `detekt-baseline.xml`, `detekt.yml`, `shared/`, logic, signatures, and tests MUST be untouched.

#### Scenario: Backend check passes

- GIVEN the annotation-only change applied
- WHEN `just backend-check` runs
- THEN it exits PASS with architecture tests green

#### Scenario: Protected files untouched

- GIVEN the change is applied
- WHEN `git status --porcelain` is inspected
- THEN only the 2 tenancy files plus OpenSpec artifacts are modified
