# Delta for Code Hygiene

## ADDED Requirements

### Requirement: Dead Suppression Removal in MediaHandlers

The system MUST NOT contain `@Suppress("TooGenericExceptionCaught")` in
`server/smp/src/main/kotlin/com/profiletailors/smp/media/application/MediaHandlers.kt`. The
annotation is dead because `detekt.yml:202-215` excludes `**/application/**` from that rule.
Surrounding code MUST remain byte-identical otherwise.

#### Scenario: Dead annotation is gone

- GIVEN `MediaHandlers.kt` at its proposal revision
- WHEN the change is applied
- THEN `grep TooGenericExceptionCaught` over `MediaHandlers.kt` returns zero matches

#### Scenario: Deletion-only diff

- GIVEN the applied change
- WHEN `git diff` is inspected for `MediaHandlers.kt`
- THEN the diff shows exactly 1 deleted line and zero added or modified logic lines

### Requirement: Deferred Baseline-Coupled Debt in StaleAssetReconciler

The system SHALL retain `@Suppress("TooGenericExceptionCaught")` + trailing comment at
`server/smp/src/main/kotlin/com/profiletailors/smp/media/application/StaleAssetReconciler.kt:96`
byte-identical for this change. The annotation is dead w.r.t. the Detekt rule but load-bearing
w.r.t. baseline stability: `detekt-baseline.xml:88` embeds the annotation text in the
`LongMethod:processBlob` ID, so deletion resurfaces LongMethod as a new finding. Any potential
future removal is a separately scoped change and would require tool-run baseline regeneration;
removal is not required by this specification. The baseline MUST NOT be hand-edited.

#### Scenario: Annotation retained as recorded debt

- GIVEN the applied change
- WHEN `grep TooGenericExceptionCaught` over `media/application` runs
- THEN exactly 1 match remains at `StaleAssetReconciler.kt:96`, byte-identical to the proposal
  revision

#### Scenario: Removal deferred with baseline regeneration

- GIVEN a future batch under epic #1019
- WHEN `StaleAssetReconciler.kt:96` is removed
- THEN `detekt-baseline.xml` is regenerated/shrunk via tool run in the same batch, never by
  hand-edit

### Requirement: Static-Analysis Gates Stay Green

`just backend-lint` MUST PASS with zero new Detekt findings, and zero new `@Suppress` annotations
MUST be introduced anywhere in the change (`ForbiddenSuppress` stays clean).

#### Scenario: Backend lint passes

- GIVEN the `MediaHandlers.kt` annotation removed
- WHEN `just backend-lint` runs
- THEN it exits PASS with no new findings (baseline only shrinks or stays equal)

#### Scenario: No new suppressions

- GIVEN the applied change
- WHEN Kotlin source changes are scanned for added `@Suppress` lines or `ForbiddenSuppress` results
  are inspected
- THEN zero added `@Suppress` lines exist in Kotlin source and `ForbiddenSuppress` stays clean;
  OpenSpec documentation text is excluded from the scan

### Requirement: Architecture and Config Integrity

`just backend-check` MUST PASS (`HexagonalArchTest`, `ComponentScanArchTest` green),
`detekt-baseline.xml` MUST gain zero new entries, and `detekt.yml`, `shared/`, `package.json`, and
`pnpm-lock.yaml` MUST be untouched.

#### Scenario: Backend check passes

- GIVEN the `MediaHandlers.kt` annotation removed
- WHEN `just backend-check` runs
- THEN it exits PASS with architecture tests green

#### Scenario: Protected files untouched

- GIVEN the change is applied to a clean checkout or its PR base revision is identified
- WHEN `git status --porcelain` is inspected in that clean checkout or the PR diff is compared with
  its base
- THEN only `MediaHandlers.kt` plus OpenSpec artifacts are modified

### Requirement: Dead Suppression Removal in Tenancy Handlers

The system MUST NOT contain the 4 dead suppressions: `TenancyOwnershipHandlersInternal.kt:35`
(`TooGenericExceptionCaught`, dead via `detekt.yml:214` `**/application/**` exclusion), `:66`
(`ThrowsCount`, 1 throw), `:223` (`ThrowsCount`, 2 throws),
`UpdateWorkspaceMembershipStatusHandler.kt:62` (`TooGenericExceptionCaught`, same exclusion).
Surrounding code MUST remain byte-identical. `UpdateWorkspaceMembershipStatusHandler.kt:25`
(`ThrowsCount`) is RETAINED as deferred debt — Detekt counts 4 throws (2 `?: throw` plus 2
catch-block rethrows) against strict `max: 3`, so the paper premise of 2 throws was wrong and
removal trips lint.

#### Scenario: Dead annotations are gone

- GIVEN the change is applied
- WHEN both tenancy files are grepped for the 4 listed suppressions
- THEN zero matches return, while `UpdateWorkspaceMembershipStatusHandler.kt:25` still carries
  `@Suppress("ThrowsCount")`

#### Scenario: Deletion-only diff

- GIVEN the applied change
- WHEN `git diff` is inspected for both files
- THEN it shows exactly 4 removed suppressions, 1 narrowed line, 1 retained line (`:25`), zero
  logic/signature/test changes

### Requirement: Narrowed Suppression at TransferWorkspaceOwnershipHandler

`TenancyOwnershipHandlersInternal.kt:140` SHALL be narrowed from
`@Suppress("ThrowsCount", "LongMethod")` to `@Suppress("LongMethod")`. `ThrowsCount` is dead at
exactly 3 throws vs strict `max: 3`; `LongMethod` is live at ~66 effective LOC vs 60.

#### Scenario: ThrowsCount dropped, LongMethod retained

- GIVEN the change is applied
- WHEN line 140 is inspected
- THEN it reads `@Suppress("LongMethod")` with no `ThrowsCount` token

### Requirement: Deferred LongMethod Debt on Transfer Handler

The system SHALL retain the narrowed `LongMethod` suppression byte-identical, as recorded deferred
debt (same pattern as Lote 1's `StaleAssetReconciler` retention).
`UpdateWorkspaceMembershipStatusHandler.kt:25` (`ThrowsCount`, 4 counted throws > max 3) is likewise
retained as deferred debt. Removal of either is a separately scoped change; the baseline MUST NOT be
hand-edited.

#### Scenario: Annotation retained as recorded debt

- GIVEN the applied change
- WHEN tenancy application sources are grepped for `LongMethod`
- THEN exactly 1 match remains at `TenancyOwnershipHandlersInternal.kt:140`

### Requirement: Static-Analysis Gates Stay Green (Tenancy Batch)

`just backend-lint` MUST PASS with zero new Detekt findings, and zero new `@Suppress` annotations
MUST be introduced anywhere in the change.

#### Scenario: Backend lint passes

- GIVEN the 4 removals + 1 narrowing + 1 retention applied
- WHEN `just backend-lint` runs
- THEN it exits PASS with no new findings (baseline only shrinks or stays equal)

#### Scenario: No new suppressions

- GIVEN the applied change
- WHEN Kotlin source changes are scanned for added `@Suppress` lines
- THEN zero added `@Suppress` lines exist; OpenSpec text is excluded from the scan

### Requirement: Architecture and Config Integrity (Tenancy Batch)

`just backend-check` MUST PASS (`HexagonalArchTest`, `ComponentScanArchTest` green), and
`detekt-baseline.xml`, `detekt.yml`, `shared/`, logic, signatures, and tests MUST be untouched.

#### Scenario: Backend check passes

- GIVEN the annotation-only change applied
- WHEN `just backend-check` runs
- THEN it exits PASS with architecture tests green

#### Scenario: Protected files untouched

- GIVEN the change is applied
- WHEN `git status --porcelain` is inspected
- THEN only the 2 tenancy files plus OpenSpec artifacts are modified

### Requirement: Verified-Live Suppressions (Lote 3 Sweep)

The 2026-09-14 Lote 3 sweep under epic #1019 deleted and lint-tested 4 candidate suppressions;
`just backend-lint` proved all 4 live, so all are RETAINED byte-identical with zero production diff:
`ResetPasswordHandler.kt:15` (`ThrowsCount`, `handle` carries 6 throws), `AuditEventModels.kt:28`
(`ThrowsCount`, `decode` carries 6 throws including catch rethrows),
`R2dbcComplianceEvidenceRepository.kt:22` (`StringLiteralDuplication`, findings at :64 and :66),
`McpErrorMapper.kt:171` (`StringShouldBeRawString`, escaped regex). No baseline entry references
these symbols; the baseline MUST NOT be hand-edited.

#### Scenario: Live suppressions retained with proof

- GIVEN the sweep applied and reverted
- WHEN the 4 files are diffed against the base
- THEN zero production lines differ and the lint findings above stand as retention rationale

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

### Requirement: Remaining UNUSED_PARAMETER Removals (Publishing R02)

The system MUST NOT contain the 9 remaining `UNUSED_PARAMETER` suppressions in
`PublishingProblemDetailsHandler.kt` (empirical recount supersedes the "8 deferred sites" wording
above): PublicationStateTransition, SocialContentPostNotFound, SocialContentActorNotFound,
SocialContentPostIsolation, ExpiredOAuthState, InvalidOAuthState, InvalidSocialContentCursor,
MediaServiceUnavailable, AssetNotReady handlers, all removed via the R01 rename-and-drop pattern
with bodies byte-identical and all new names within `FunctionNameMaxLength` (30). 4 stale KDoc
blocks documenting deleted params were removed with them. What remains in the file is
`TooManyFunctions` (`:35`) plus the 7 used-parameter handlers, untouched.

#### Scenario: Nine renames with identical behavior

- GIVEN the R02 change applied
- WHEN each renamed handler is invoked and `git diff` is inspected
- THEN every exception maps to its pre-rename status/title, and each hunk shows only signature/KDoc
  lines changed

#### Scenario: Gates stay green

- GIVEN the 9 renames applied
- WHEN `just backend-lint`, `just backend-check`, and `just backend-bdd-fast` run
- THEN all exit PASS (`publishing-publications.feature` 8/8 green), with zero new suppressions and
  baseline/config/shared untouched

### Requirement: Elimination of Unused Parameter and Generic Catch Debt across Identity, Media, MCP, Authorization

The system MUST NOT contain `@Suppress("UNUSED_PARAMETER")` in `AuthorizationProblemDetailsHandler.kt`,
`UnsplashProblemDetailsHandler.kt`, `R2dbcPrincipalIdentityLookup.kt`, `R2dbcIdentityRegistrationGateway.kt`,
or `PasswordResetTokenExceptions.kt`. The system MUST NOT contain `@Suppress("TooGenericExceptionCaught")`
in `McpAuditEmitter.kt`. Handlers SHALL drop unused parameters or convert parameters to class properties,
R2DBC mappers SHALL accept `Readable` without unused `RowMetadata`, and `McpAuditEmitter` SHALL catch
`JsonProcessingException` and `IllegalArgumentException` instead of catching generic `RuntimeException`.

#### Scenario: All 6 target suppressions removed

- GIVEN the applied changes across the 6 target files
- WHEN grepping for `UNUSED_PARAMETER` in authorization, media http, identity infrastructure, and identity application, and `TooGenericExceptionCaught` in mcp
- THEN zero `@Suppress` matches return across all 6 files

#### Scenario: Static analysis and unit tests pass

- GIVEN the refactored code and unit test updates
- WHEN `node scripts/gradle-run.mjs detekt` and related backend unit tests run
- THEN all pass with zero new findings and zero new suppressions introduced
