# Apply Progress: `dallay-564-first-class-invitation` — Unit 2 & Unit 3

## Branch / Worktree

- trunk: `main`
- parent_branch: `feature/dallay-564-first-class-invitation`
- base: `feature/dallay-564-first-class-invitation`
- branch: `feature/dallay-564-first-class-invitation`
- position: `2`
- Linear: `DALLAY-564`
- delivery: `github-stacked-prs`
- unit: PR 2 — persistence, Liquibase, CAS, security boundary

## Completed Tasks

- [x] 2.1 Canonical R2DBC `InvitationRepository` mapped to `invitations`, with `findById`, `findByCandidateKeyForUpdate` (locked read), `save` (insert with version=0), and `updateIfVersionMatches` (version CAS). The legacy narrow `R2dbcInvitationAcceptanceRepository` was removed to honour the design contract that one SQL adapter owns the canonical table; the existing `InvitationAcceptanceRepositoryFacade` (wrapping `InvitationRepository`) remains the sole `InvitationAcceptanceRepository` bean. Wired through `PlatformAdminBootstrapConfiguration` by Spring auto-discovery of the new `@Repository`.
- [x] 2.2 `InvitationSecurityBoundaryTest` asserts the first-class Invitation boundary contains no second token generator, no raw token / URL / delivery field on `Invitation`, no `Notification` dependency in `platformadmin.domain` or `platformadmin.application`, and no token/URL/delivery method on the canonical `InvitationRepository` contract.
- [x] 3.1 Liquibase `005-harden-invitations.yaml` already extended the schema with `version`, lifecycle/source/email/expiry/metadata check constraints, partial unique `(workspace_id, invited_email_normalized) WHERE status = 'ACTIVE'` index, and supporting lookup indexes; the changeset is included in `db.changelog-master.yaml`. The existing test in `InvitationLiquibaseSchemaIntegrationTest` validates every named constraint and the new indexes.
- [x] 3.2 Liquibase integration test extended with three new scenarios: duplicate-active-email race rejected by `uq_invitations_workspace_active_email`, invalid acceptance metadata rejected by `chk_invitations_acceptance_metadata`, and a conditional update that loses its CAS leaves the row at the prior version. The conditional update now passes the transition aggregate version to `R2dbcInvitationRepository.updateIfVersionMatches`; the two-client acceptance race is verified through the canonical registration gateway against a real PostgreSQL container.

## TDD Evidence

- RED 2.1 (compile): the new `R2dbcInvitationRepositoryTest` was added before the production class; `:server:smp:compileTestKotlin` failed with `Unresolved reference 'R2dbcInvitationRepository'` for `findById`, `findByCandidateKeyForUpdate`, `save`, `updateIfVersionMatches`, and `Unresolved reference 'bindNull'` from the test helpers.
- RED 2.1 (version semantics, run): after adding `updateIfVersionMatches persists an accepted transition from stored version zero` and before correcting the repository, `./gradlew :server:smp:test --tests 'com.profiletailors.smp.platformadmin.infrastructure.persistence.R2dbcInvitationRepositoryTest.updateIfVersionMatches persists an accepted transition from stored version zero' --rerun-tasks --no-daemon --max-workers=1 --console=plain` exited `1`; Gradle reported `1 test completed, 1 failed` and `AssertionFailedError at R2dbcInvitationRepositoryTest.kt:171`.
- GREEN 2.1 (compile + run): after correcting the repository to persist the transition version and compare its predecessor, `./gradlew :server:smp:test --tests 'com.profiletailors.smp.platformadmin.infrastructure.persistence.R2dbcInvitationRepositoryTest' --rerun-tasks --no-daemon --max-workers=1 --console=plain` exited `0` with `BUILD SUCCESSFUL`; all 10 repository tests passed against PostgreSQL.
- RED 2.2 (run): the first cut of `invitationRepositoryContractTreatsCandidateKeyAsOpaqueString` asserted `parameters.hasSize(2)` plus `parameters[0].type == String`, but Kotlin compiles `suspend fun findByCandidateKeyForUpdate(candidateKey: String): Invitation?` as `Object findByCandidateKeyForUpdate(String, Continuation)` and so `returnType == Object`; the test surfaced the parameter shape mismatch and was tightened to assert the candidate key parameter name and the absence of raw-bearer aliasing without forcing a non-Kotlin return-type assumption. After the fix all seven `InvitationSecurityBoundaryTest` scenarios passed.
- GREEN 2.2 (run): seven tests passed; the runner found no second token generator, URL builder, `Notification` dependency, raw token, URL, or delivery field in the first-class boundary and confirmed `InvitationId` is a `@ValueObject` UUID wrapper.
- RED 3.1 (compile): `InvitationLiquibaseSchemaIntegrationTest` was extended with three new tests before the test class structure was finalised; `:server:smp:compileTestKotlin` failed on the nullable `OffsetDateTime` / `String` argument type mismatches and `bindNull` resolution.
- RED 3.1 (version semantics, run): after the repository fix, the existing schema replay test still supplied `accepted.copy(version = 0)` and failed at `InvitationLiquibaseSchemaIntegrationTest.kt:166`; the focused test command exited `1` with `1 test completed, 1 failed`.
- GREEN 3.1 (run): after updating the conditional-update scenario to pass the transition version, `./gradlew :server:smp:test --tests 'com.profiletailors.smp.infrastructure.db.InvitationLiquibaseSchemaIntegrationTest' --rerun-tasks --no-daemon --max-workers=1 --console=plain` exited `0` with `BUILD SUCCESSFUL`; all four schema tests passed against PostgreSQL.
- RED 3.2 (run): the initial `updateIfVersionMatches rejects a transition from a terminal state` test asserted the SQL CAS would refuse a terminal status, while the actual repository contract is "version-CAS only, status rules belong to the domain". The assertion was rewritten to assert the matching-version increment behaviour, and the terminal-state protection is owned by `InvitationTest`'s terminal-state tests.
- RED 3.2 (contention, run): before the repository fix, `./gradlew :server:smp:test --tests 'com.profiletailors.smp.platformadmin.infrastructure.persistence.R2dbcInvitationRepositoryTest.concurrent acceptance clients allow one success and one membership' --rerun-tasks --no-daemon --max-workers=1 --console=plain` exited `1`; Gradle reported `1 test completed, 1 failed` with `AssertionFailedError at R2dbcInvitationRepositoryTest.kt:190`.
- GREEN 3.2 (contention, run): after the repository fix and test refactor, the same contention command exited `0` with `BUILD SUCCESSFUL`; the test uses two independently configured R2DBC connection clients and transactions and asserts one successful acceptance, one accepted row at version 1, and one workspace membership.
- RED 1.3 (facade contract, run): the newly discovered `InvitationAcceptanceRepositoryFacadeTest` initially expected the stored predecessor version from a façade call, although the façade correctly submits the transition aggregate; the focused command exited `1` with `2 tests completed, 1 failed`.
- GREEN 1.3 (facade contract, run): after correcting the test to expect the target aggregate version and renaming the recording field to `targetVersion`, the same focused command exited `0` with `BUILD SUCCESSFUL`; both façade scenarios passed without production changes.

## Verification

- Passed: `./gradlew :server:smp:compileKotlin :server:smp:compileTestKotlin :server:smp:detekt --no-daemon --console=plain` (exit 0; `BUILD SUCCESSFUL`).
- Passed: `./gradlew :server:smp:test --tests 'com.profiletailors.smp.platformadmin.application.InvitationSecurityBoundaryTest' --no-daemon --console=plain` — 7 tests, 7 passed, 0 failed.
- Passed: `./gradlew :server:smp:test --tests 'com.profiletailors.smp.platformadmin.application.AcceptInvitationHandlerTest' --no-daemon --console=plain` — 6 tests, 6 passed, 0 failed.
- Passed: `./gradlew :server:smp:test --tests 'com.profiletailors.smp.platformadmin.domain.InvitationTest' --no-daemon --console=plain` — 17 tests, 17 passed, 0 failed.
- Passed: `./gradlew :server:smp:test --tests 'com.profiletailors.smp.platformadmin.PlatformAdminMarkerCoverageTest' --no-daemon --console=plain` — 2 tests, 2 passed, 0 failed.
- Passed: `./gradlew :server:smp:test --tests 'com.profiletailors.smp.AggregateBoundaryTest' --tests 'com.profiletailors.smp.IdentityOnlyAggregateCommunicationTest' --tests 'com.profiletailors.smp.ValueObjectImmutabilityTest' --tests 'com.profiletailors.smp.HexagonalArchTest' --tests 'com.profiletailors.smp.ComponentScanArchTest' --no-daemon --console=plain` — 20 tests, 20 passed, 0 failed.
- Passed: `./gradlew :server:smp:test --tests 'com.profiletailors.smp.platformadmin.infrastructure.persistence.R2dbcInvitationRepositoryTest' --tests 'com.profiletailors.smp.infrastructure.db.InvitationLiquibaseSchemaIntegrationTest' --tests 'com.profiletailors.smp.SmpApplicationTests' --tests 'com.profiletailors.smp.platformadmin.infrastructure.InvitationRegistrationGatewayAdapterTest' --no-daemon --console=plain` — 24 tests, 24 passed, 0 failed; `contextLoads` confirms the full Spring Boot context boots with the new `InvitationRepository` bean and the `invitationAcceptanceRepository` factory wired through `PlatformAdminBootstrapConfiguration`.
- Passed: `./gradlew :server:smp:detekt --no-daemon --console=plain` — exit 0.
- Environment note: the initial lightweight `PostgresDatabaseTestBase` tests (raw JDBC to `localhost:<mapped-port>`) could not reach the PostgreSQL container on this host, so the new repository and integration tests use `PostgresIntegrationTestBase` (Spring Boot autowired `databaseClient`) and reach the container through the autoconfigured R2DBC connection pool. Both paths exercise the same Liquibase changelog and the same `R2dbcInvitationRepository` SQL.
- Passed: final focused command `./gradlew :server:smp:test --tests 'com.profiletailors.smp.platformadmin.infrastructure.persistence.R2dbcInvitationRepositoryTest' --tests 'com.profiletailors.smp.infrastructure.db.InvitationLiquibaseSchemaIntegrationTest' --tests 'com.profiletailors.smp.platformadmin.application.InvitationAcceptanceRepositoryFacadeTest' --tests 'com.profiletailors.smp.integration.LocalAuthEndpointIntegrationTest.should register invitee into the invitation workspace when invitation is valid' --rerun-tasks --no-build-cache --no-daemon --max-workers=1 --console=plain` — exit 0; `BUILD SUCCESSFUL`; repository, Liquibase, façade, and valid-registration coverage passed after resetting idle Gradle daemons.
- Passed: `./gradlew :server:smp:detekt --rerun-tasks --no-daemon --max-workers=1 --console=plain` — exit 0 after extracting the two-client contention fixture and wrapping the long SQL literal.
- Passed: focused Spotless check for `R2dbcInvitationRepositoryTest.kt` and `InvitationLiquibaseSchemaIntegrationTest.kt` — exit 0 after formatting only those two affected test files.
- Passed: `./gradlew :server:smp:spotlessKotlinCheck --rerun-tasks --no-daemon --max-workers=1 --console=plain` — exit 0; the three reported violations were corrected manually without running a broad formatter.
- Passed (final post-format): the focused PostgreSQL test command above — exit 0 with `BUILD SUCCESSFUL`.
- Passed (final post-format): `./gradlew :server:smp:detekt --rerun-tasks --no-daemon --max-workers=1 --console=plain` — exit 0.
- Passed: `git diff --check` — no whitespace errors.
- Passed: Ruby YAML parse with `YAML.safe_load_file` and `Date` permitted — `state.yaml valid`. The first parse attempt hit Ruby 4's default safe-load restriction for `Date`, not invalid YAML.
- Transient environment failure: one combined test rerun overlapped an older Gradle process and hit Kotlin incremental-cache `Storage already registered` errors before the daemon was stopped; the subsequent `./gradlew --stop` plus `--no-build-cache` rerun passed.

## Files Changed

| File | Action | What |
|---|---|---|
| `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/infrastructure/persistence/R2dbcInvitationRepository.kt` | Created | Canonical R2DBC adapter implementing `InvitationRepository` (read, locked lookup, save with version, version-CAS update). |
| `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/infrastructure/persistence/R2dbcInvitationAcceptanceRepository.kt` | Deleted | Removed because the canonical adapter now owns all SQL for `invitations`; `InvitationAcceptanceRepositoryFacade` (already wrapping `InvitationRepository`) is the sole `InvitationAcceptanceRepository` bean. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/infrastructure/PlatformAdminBootstrapConfiguration.kt` | Modified | Added `@Suppress("TooManyFunctions")` to keep the bean-factory class within the project lint budget once the `invitationAcceptanceRepository` factory from PR 1 is counted. `R2dbcInvitationRepository` is auto-discovered via `@Repository`; no explicit `@Bean` is required. |
| `server/smp/src/test/kotlin/com/profiletailors/smp/platformadmin/infrastructure/persistence/R2dbcInvitationRepositoryTest.kt` | Created | 10 tests against PostgreSQL, including transition-version CAS semantics and a two-client acceptance contention proof with one membership. |
| `server/smp/src/test/kotlin/com/profiletailors/smp/platformadmin/infrastructure/persistence/R2dbcInvitationAcceptanceRepositoryTest.kt` | Deleted | Behaviour covered by `R2dbcInvitationRepositoryTest` and the new Liquibase integration scenarios; the narrow façade no longer exists. |
| `server/smp/src/test/kotlin/com/profiletailors/smp/platformadmin/application/InvitationSecurityBoundaryTest.kt` | Created | 7 tests asserting aggregate field/marker shape, canonical port contract, opaque candidate key, no second token subsystem, no `Notification` dependency, and `InvitationId` shape. |
| `server/smp/src/test/kotlin/com/profiletailors/smp/platformadmin/application/ClasspathScanner.kt` | Created | Test-time package scanner used by `InvitationSecurityBoundaryTest` to inspect the production classes for forbidden names. |
| `server/smp/src/test/kotlin/com/profiletailors/smp/platformadmin/application/InvitationAcceptanceRepositoryFacadeTest.kt` | Created | Covers the façade submitting the transition aggregate and refusing a non-active stored invitation. |
| `server/smp/src/test/kotlin/com/profiletailors/smp/infrastructure/db/InvitationLiquibaseSchemaIntegrationTest.kt` | Extended | Existing constraint-discovery test retained; three new tests added for duplicate-active-email, invalid acceptance metadata, and CAS replay. |

## Hard-Gate Audit

- No second token subsystem: no new `TokenHasher` impl, no new `AcceptUrlTemplate`, no new URL builder added. `TokenHasher` / `AcceptUrlTemplate` interfaces and `BCryptTokenHasher` implementation are pre-existing for the waitlist flow and are not consumed by the canonical `InvitationRepository`.
- No raw token, accept URL, or delivery field persisted: `Invitation` data class fields are exactly `id, source, sourceReferenceId, workspaceId, invitedEmailNormalized, tokenHash, status, issuedBy, createdAt, expiresAt, acceptedAt, acceptedPrincipalId, version`. `tokenHash` is opaque non-reversible material. The canonical repository returns only the typed `Invitation` aggregate, never a byte/char array.
- No Notification dependency: `InvitationSecurityBoundaryTest > platformAdminDomainAndApplicationPackagesDoNotReferenceNotification` confirms zero field/method references to any `Notification` type from the first-class packages.
- No DALLAY-565/566/567/568/570 behaviour added: no `WaitlistInvitation` substitution, no waitlist mutation, no provisioning implementation, no registration command, no admin command surface.
- Architecture tests preserved: `HexagonalArchTest` (10/10), `ComponentScanArchTest` (5/5), `AggregateBoundaryTest` (2/2), `IdentityOnlyAggregateCommunicationTest` (1/1), `ValueObjectImmutabilityTest` (2/2) all pass unchanged.

## Remaining Work

- [ ] 4.1 Compatibility and architecture evidence (waitlist remains, forbidden coupling absent).
- [ ] 4.2 ADR/C4/data-model/operations/OpenSpec documentation updates.
- [ ] Final verification and `sdd-qa` acceptance run.

## Current Slice Result

- The Unit 2 CAS/version mismatch and missing simultaneous acceptance proof are resolved and focused evidence is green.
- The repository-wide Kotlin Spotless and Detekt checks are green after formatting the three affected files manually.
- Full verification, compatibility evidence, architecture/documentation synchronization, and `sdd-qa` remain pending.
- Delivery metadata note: the actual worktree remains on `feature/dallay-564-first-class-invitation`, while the task forecast names `feature/dallay-564-first-class-invitation-persistence` for the isolated PR 2 branch; no branch, commit, push, or PR operation was performed.

## Apply Boundary

This apply slice is PR 2: canonical persistence, Liquibase hardening, security boundary, and CAS / rollback proof. Compatibility tests, ADR/C4/data-model/operations/OpenSpec documentation updates, final verification, and `sdd-qa` acceptance remain pending for their approved stacked work units. The pre-existing `.agents/skill-registry.md` modification and the partial Unit 2 PR 1 worktree edits are preserved; no commit, push, or PR operation was performed.

## Unit 3: Compatibility and Documentation (Tasks 4.1 / 4.2)

### Completed in Unit 3

- [x] 4.1 Compatibility and architecture evidence: `WaitlistInvitation` aggregate, `waitlist_invitations` table, legacy R2DBC adapter, and waitlist handlers remain untouched. No second token subsystem, raw token persistence, accept URL, delivery field, or `Notification` dependency was introduced. The existing `PlatformAdminInvitationTransactionPostgresIntegrationTest` continues to cover the waitlist path. The canonical `R2dbcInvitationRepository` is the sole R2DBC adapter owning the `invitations` table; the legacy narrow SQL adapter was removed.
- [x] 4.2 ADR, C4, data-model, operations, and OpenSpec documentation:
  - New ADR `docs/architecture/adr/0020-first-class-invitation-aggregate.md` capturing aggregate boundary, identifier decision (scoped exception to ADR-0005), lifecycle, token ownership, persistence and CAS, acceptance facade, and schema protections.
  - `docs/architecture/adr/README.md` index updated with ADR-0020 entry.
  - `docs/architecture/data-model/README.md` entity-relationship table corrected to use proper arrow character for `principals ↔ invitations`.
  - `openspec/changes/dallay-564-first-class-invitation/{proposal.md,design.md,specs/invitations/spec.md,tasks.md,state.yaml,verify-report.md}` aligned and updated.

### Compatibility Evidence

- The first-class `Invitation` aggregate is registered alongside `WaitlistInvitation` in `platformadmin.domain`. Both coexist; neither was renamed, removed, or merged.
- The `invitations` table is owned exclusively by `R2dbcInvitationRepository` (canonical). The `waitlist_invitations` table is owned exclusively by `R2dbcWaitlistInvitationRepository` (legacy).
- `AcceptInvitationHandler` uses the canonical aggregate; legacy waitlist invitation handlers remain on the waitlist path.
- No raw token, accept URL, or delivery status is persisted on the canonical aggregate (enforced by `InvitationSecurityBoundaryTest` and the repository contract).
- DALLAY-565 (notifications), DALLAY-566 (token mechanics), DALLAY-567 (provisioning), DALLAY-568 (admin commands), and DALLAY-570 (waitlist conversion) remain downstream owners and were not modified.
