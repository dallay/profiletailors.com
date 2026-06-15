# Tasks: LinkedIn Publishing MVP

## Phase 1: Foundation

- [x] 1.1 Create `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/ModuleMetadata.kt`
  and `PublishingBoundedContext.kt`, then update Modulith verification expectations in
  `server/smp/src/test/kotlin/com/profiletailors/smp/ModularStructureTest.kt`.
- [x] 1.2 Add Liquibase files under `server/smp/src/main/resources/db/changelog/publishing/` plus
  master includes in `db.changelog-master.yaml` for social connections, accounts, publications,
  assets, jobs, and attempts.
- [x] 1.3 Extend
  `server/smp/src/test/kotlin/com/profiletailors/smp/infrastructure/db/LiquibaseBaselineChangelogTest.kt`
  with assertions for the new publishing changelog files.
- [x] 1.4 Add publishing and LinkedIn configuration properties to
  `server/smp/src/main/resources/application.yaml` for worker cadence, retry limits, provider mode,
  OAuth credentials, and enable/disable flags.

## Phase 2: Domain and Application

- [x] 2.1 Create provider-neutral publishing domain models and ports in
  `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/domain/` for connections,
  publications, assets, jobs, attempts, states, and schedule modes.
- [x] 2.2 Add unit tests for lifecycle transitions, cancellation/editability, retry policy, and
  priority ordering in `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/domain/`.
- [x] 2.3 Create application commands/handlers in
  `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/application/` for connect account,
  create publication, edit publication, cancel publication, retry publication, and reschedule
  publication.
- [x] 2.4 Add unit tests for application services in
  `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/application/` using fake
  repositories and fake provider ports.

## Phase 3: Infrastructure Adapters

- [x] 3.1 Implement R2DBC repositories in
  `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/persistence/` and
  repository tests in
  `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/infrastructure/`.
- [x] 3.2 Implement LinkedIn real and fake adapters in
  `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/linkedin/` for OAuth
  completion, profile lookup, media handling, capability validation, and publish execution.
- [x] 3.3 Implement HTTP controllers in
  `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/http/` for connection
  completion and publication CRUD/lifecycle endpoints.
- [x] 3.4 Add WebTestClient controller/integration tests in
  `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/infrastructure/http/` for
  authenticated workspace-scoped publishing flows.

## Phase 4: Queue Execution

- [x] 4.1 Implement persisted scheduling and worker adapters in
  `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/scheduling/` for
  due-job polling, claiming, completion, retry rescheduling, and failure marking.
- [x] 4.2 Wire publishing beans into bootstrap/configuration classes, keeping worker execution
  property-driven and fake-provider mode available for local/test usage.
- [x] 4.3 Add integration tests covering `NOW`, `SCHEDULED_AT`, `NEXT_SLOT`, priority ordering,
  retry exhaustion, and manual retry/reschedule in
  `server/smp/src/test/kotlin/com/profiletailors/smp/integration/` or publishing-specific
  integration support.

## Phase 5: Verification

- [x] 5.1 Run targeted publishing and modulith tests from `server/smp` and fix boundary or
  persistence issues discovered during execution.
- [x] 5.2 Run PostgreSQL-tagged publishing tests if claim/update SQL needs backend verification
  beyond H2 semantics.
- [x] 5.3 Review the implementation against every scenario in
  `openspec/changes/linkedin-publishing-mvp/specs/` and update any missing validation, error
  handling, or workspace-isolation behavior.
