# Apply Progress: Initial Platform Admin Bootstrap

## Handoff status

- **Persistence:** OpenSpec-only
- **Tracker branch:** `feature/initial-platform-admin-bootstrap`, based on `main`
- **Current unit:** U02, based on U01 commit `7fe2f7d9` at `feature/bootstrap-u01-planning`; local ref: `feature/bootstrap-u02-history`
- **Position:** `2/16` monorepo units; D01 is a separate deployment-repository unit
- **Base check:** `main` is an ancestor of the tracker branch; U01 is the current `HEAD`
- **Delivery:** `feature-branch-chain`, with local conventional commits authorized per unit
- **Review workload:** The verified candidate is approximately 3,360 changed lines, excluding planning/ADR files and the `.gitignore` modification. U11/U14 estimates remain provisional. See `tasks.md` for all 16 unit estimates and D01.
- **Apply status:** Partial. Implementation remains in progress across the approved chain. U02 reconciles the work history and verification status; it does not claim implementation completion or product acceptance.
- **Publication / deployment:** No push, PR, live checks, or VPS deployment
- **Acceptance:** Not claimed; technical verification and product acceptance remain separate

U02 does not change source or `.gitignore`. Its commit is limited to `exploration.md`, `apply-progress.md`, `tasks.md`, and `state.yaml`. Candidate source and other working-tree changes remain unstaged.

## Completed tasks

- [x] 1.1 ADR-0031: ID-free `PLATFORM` consent; no workspace/membership or bootstrap age check; preserve customer registration.
- [x] 1.2 ADR-0032: `SYSTEM_BOOTSTRAP`, no human principal UUID, never recipient-as-actor.
- [x] 1.3 ADR-0033: identity-owned verification completion, token correlation, transaction boundary, and grant recovery.
- [x] 1.4 Identity-owned current Terms version query; no customer API extension.
- [x] 2.1 ID-free `PLATFORM` scope and UUID-backed `WORKSPACE`/`WAITLIST` scope.
- [x] 3.1 Invitation issuance handler: normalized and confirmed email, hash-only 30-minute token, explicit replacement confirmation, owner/email fail-closed checks, and `SYSTEM_BOOTSTRAP` audit attribution.
- [x] 3.2 Recipient setup: current Terms acceptance is recorded as ID-free `PLATFORM` consent; no age confirmation or workspace provisioning is added to the bootstrap path.
- [x] 4.1 Public requirements/completion API, installation-configured admin origin, CORS inclusion, and private issuance/status/readiness behavior.
- [x] 4.2 Admin `/bootstrap` recipient flow with fragment removal before request, current Terms review/acceptance, and no age field.
- [x] 5.2 Product, API/architecture, operations, and OpenSpec artifacts updated. VPS and live DNS/CORS verification remain excluded.

## RED → GREEN evidence

| Slice | RED evidence | GREEN evidence |
|---|---|---|
| Consent domain scope | `node scripts/gradle-run.mjs :server:smp:test --tests com.profiletailors.smp.governance.domain.ConsentScopeTest --no-daemon -PexcludeTags=modularity,postgres` failed because `ConsentScope.Platform` did not exist. | Same focused test passed after the three-scope model and workspace-only response mapping were implemented. |
| Consent migration | `node scripts/gradle-run.mjs :server:smp:test --tests com.profiletailors.smp.infrastructure.db.LiquibaseBaselineChangelogTest --no-daemon -PexcludeTags=modularity,postgres` failed because the new migration resource was absent. | Liquibase resource test passed after the changeset and master include were added. Static migration assertions cover explicit backfill, event/projection scope agreement, constraints, and the platform uniqueness index. |
| Consent persistence | `node scripts/gradle-run.mjs :server:smp:postgresIntegrationTest --tests com.profiletailors.smp.governance.infrastructure.ConsentScopePostgresIntegrationTest --no-daemon` failed when an ID-free platform row met the old non-null workspace constraint. | The same PostgreSQL integration test passed after migration/repository updates. Coverage includes explicit `PLATFORM` projection/event scope, idempotency, history, withdrawal, workspace isolation, and rejecting an event whose scope kind disagrees with its projection. |
| Actor provenance | `PlatformRoleAssignmentTest` and `AdminAuditEventTest` first failed to compile because role/audit models required human UUID actors and lacked `AdminActorKind`. | Focused domain tests passed with `SYSTEM_BOOTSTRAP`, null human principal, empty role snapshot, and bootstrap-only action validation. `R2dbcPlatformRoleAssignmentRepositoryPostgresIntegrationTest` and `R2dbcAdminAuditRepositoryPostgresIntegrationTest` passed after the actor migration. |
| Invitation issuance | `node scripts/gradle-run.mjs :server:smp:test --tests com.profiletailors.smp.platformadmin.application.IssueBootstrapInvitationHandlerTest --no-daemon -PexcludeTags=modularity,postgres` failed because the application/domain contracts were absent. | The focused unit test passed for normalization, hash-only storage, 30-minute expiry, replacement confirmation, existing-account rejection, owner fail-closed behavior, and system audit attribution. The PostgreSQL bootstrap repository test passed for conditional claim, expiry, completion, replay, and concurrent replacement. |
| Token generation and CORS | The token-generator test failed because `SecureBootstrapTokenGenerator` was absent; the CORS test failed to compile because `adminPublicOrigin` was not modeled. | `SecureBootstrapTokenGeneratorTest` and `RefreshSessionOriginValidationWebFilterTest` passed. |
| Identity Terms and verification port | `LocalAuthHandlersTest` failed to compile after adding the expected identity-owned completion-port call. | `LocalAuthHandlersTest` and `GetCurrentTermsVersionHandlerTest` passed. The PostgreSQL conditional-token-consumption/role-grant rollback path has not yet been verified end to end. |
| Bootstrap completion and role grant | `CompleteBootstrapHandlerTest` and `CompleteBootstrapOwnerGrantHandlerTest` failed to compile because their handlers/contracts were absent. | Both focused test classes passed. They cover current Terms, platform consent, one existing verification event, non-elevation, system actor assignment/audit, and no grant when current Terms consent is absent. |
| Recipient API | `PlatformBootstrapControllerTest` failed to compile because the public controller was absent. `SmpApplicationTests.bootstrapRequirementsArePublicAndExposeOnlyCurrentTermsVersion` initially failed because the route was not public. | Controller tests passed for the exact requirements projection, `202` empty completion response, and generic unavailable error. The `SmpApplicationTests` requirements route and normal-context CLI-exclusion checks passed. |
| Admin recipient view | `just admin-test` failed because `BootstrapView.vue` did not exist. The audit-view test failed because it did not display system actors. | `just admin-test` passed with 151 tests. The admin mocked Playwright command passed 16 tests, including the bootstrap fragment-clearing and submission flow. `just admin-check`, `just admin-build`, and `pnpm exec biome check .` passed after the final admin UI changes. |
| Private CLI dispatch/context | CLI tests first failed because CLI and explicit context types were absent. | `InteractivePlatformAdminBootstrapCliTest`, `PlatformAdminBootstrapCliConfigurationTest`, and `PlatformAdminBootstrapCommandDispatchTest` passed. The tests verify one terminal link, no password prompt, exact command dispatch, and a non-web context without component scanning, Liquibase, event consumers, or schedulers. |

### BDD result

`just backend-bdd-fast` completed its Cucumber execution with **329 scenarios, 2 failures**, then the Gradle result aggregation reported `java.nio.file.NoSuchFileException` for `server/smp/build/test-results/bddFastTest/binary/in-progress-results-generic.bin`.

Failed scenarios:

- `Public waitlist consent scopes > Tenant consent endpoints cannot query or withdraw platform Terms consent` — `BadSqlGrammarException`.
- `Initial platform admin bootstrap > Recipient completes bootstrap and existing verification grants the system-attributed owner role` — `AssertionError`.

The BDD gate is **not passing**. An earlier run timed out before producing final results. The completed run above is the latest full result. The failed scenarios require diagnosis and correction before apply can be considered complete.

## Verification commands and status

| Command | Status | Evidence |
|---|---|---|
| Focused consent domain tests | Passed | `ConsentScopeTest` |
| Focused Liquibase resource tests | Passed before the latest migration include | New scope and actor resources were tested; final 013 resource check still needs rerun. |
| Focused consent PostgreSQL integration | Passed | ID-free scope, event/projection, uniqueness/idempotency, withdrawal/history, and isolation tests. |
| Focused actor PostgreSQL integration | Passed | Role assignment and audit repository integration tests. |
| Focused bootstrap repository PostgreSQL integration | Passed | Claim/expiry/completion/replay plus concurrent confirmed replacement. |
| `just admin-test` | Passed | 151 tests. |
| `just admin-check` | Passed | Vue/TypeScript check. |
| `just admin-build` | Passed | Production admin build completed. |
| `pnpm exec biome check .` from `apps/web/admin` | Passed | 69 files checked, no fixes applied. |
| Admin mocked Playwright | Passed | 16 tests; the wrapper ran the complete mocked lane despite the supplied grep argument. |
| `just backend-bdd-fast` | Failed | 329 scenarios, 2 failures, then `NoSuchFileException` during Gradle result aggregation; details above. |
| `just backend-check` | Failed | Detekt reported 22 issues in changed code; `ModularStructureTest` failed with platformadmin-to-`governance::domain` and root-to-non-exposed-platformadmin-CLI-infrastructure dependencies; 3,353 tests completed with 1 failure and 2 skipped. |
| `just backend-test-postgres` | Running when last checked; no result claimed | The task had not completed at the last status check. |
| `just infra-up` | Not run | Not required for the focused Testcontainers tests. |
| `:server:smp:bootBuildImage` | Not run | Actual packaged image/TTY invocation remains an open gate. |
| `just docs-lint` | Failed | 71 pre-existing markdownlint findings in 8 `.agents/rpi/plan/tasks/` files; changed docs were not among them. |
| Targeted markdownlint (`pnpm exec markdownlint-cli2 --no-globs ...`) | Passed | 7 changed documentation files checked; the OpenSpec directory remains excluded by repository configuration. |
| `just docs-links` | Timed out | Lychee cache refresh and repo-wide link scan did not complete within 120 seconds; no pass is claimed. |
| `just production-config` | Blocked | `infra/apps/smp/production/.env` is absent. No environment file was created. |
| `just swarm-config` | Blocked | `infra/apps/smp/swarm/.env` lacks `DASHBOARD_IMAGE`. No environment file was created. |
| VPS deployment / live DNS / live CORS | Not run | Explicitly out of scope and not authorized. |

## Files changed

### Backend and persistence

- `server/smp/src/main/kotlin/com/profiletailors/smp/SmpApplication.kt`
- `server/smp/src/main/kotlin/com/profiletailors/smp/governance/domain/ConsentScope.kt`
- `server/smp/src/main/kotlin/com/profiletailors/smp/governance/infrastructure/R2dbcConsentRepository.kt`
- `server/smp/src/main/kotlin/com/profiletailors/smp/governance/infrastructure/http/ConsentController.kt`
- `server/smp/src/main/kotlin/com/profiletailors/smp/identity/application/BootstrapVerificationCompletionPort.kt`
- `server/smp/src/main/kotlin/com/profiletailors/smp/identity/application/GetCurrentTermsVersionQuery.kt`
- `server/smp/src/main/kotlin/com/profiletailors/smp/identity/application/IdentityRegistrationGateway.kt`
- `server/smp/src/main/kotlin/com/profiletailors/smp/identity/application/LocalAuthHandlers.kt`
- `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/R2dbcIdentityRegistrationGateway.kt`
- `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/security/IdentitySecurityConfiguration.kt`
- `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/security/RefreshSessionOriginValidationWebFilter.kt`
- `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/application/CompleteBootstrapHandler.kt`
- `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/application/CompleteBootstrapOwnerGrantHandler.kt`
- `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/application/IssueBootstrapInvitationHandler.kt`
- `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/application/contracts/BootstrapTermsConsentVerifier.kt`
- `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/application/model/AdminAuditModels.kt`
- `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/domain/AdminAuditEvent.kt`
- `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/domain/BootstrapInvitation.kt`
- `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/domain/PlatformRoleAssignment.kt`
- `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/infrastructure/GovernanceBootstrapTermsConsentVerifier.kt`
- `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/infrastructure/PlatformBootstrapVerificationCompletionAdapter.kt`
- `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/infrastructure/SecureBootstrapTokenGenerator.kt`
- `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/infrastructure/cli/InteractivePlatformAdminBootstrapCli.kt`
- `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/infrastructure/cli/PlatformAdminBootstrapCliConfiguration.kt`
- `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/infrastructure/http/PlatformBootstrapController.kt`
- `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/infrastructure/http/AdminProblemDetailsHandler.kt`
- `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/infrastructure/persistence/R2dbcPlatformAdminBootstrapTransactionRunner.kt`
- `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/infrastructure/persistence/R2dbcPlatformBootstrapInvitationRepository.kt`
- `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/infrastructure/persistence/R2dbcAdminAuditRepository.kt`
- `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/infrastructure/persistence/R2dbcPlatformRoleAssignmentRepository.kt`
- `server/smp/src/main/resources/application.yaml`
- `server/smp/src/main/resources/db/changelog/db.changelog-master.yaml`
- `server/smp/src/main/resources/db/changelog/governance/008-platform-consent-scope.yaml`
- `server/smp/src/main/resources/db/changelog/governance/008-platform-consent-scope.sql`
- `server/smp/src/main/resources/db/changelog/platform-admin/012-system-bootstrap-actors.yaml`
- `server/smp/src/main/resources/db/changelog/platform-admin/012-system-bootstrap-actors.sql`
- `server/smp/src/main/resources/db/changelog/platform-admin/013-bootstrap-invitations.yaml`
- `server/smp/src/main/resources/db/changelog/platform-admin/013-bootstrap-invitations.sql`

### Admin SPA and delivery configuration

- `apps/web/admin/src/router/index.ts`
- `apps/web/admin/src/views/BootstrapView.vue`
- `apps/web/admin/src/views/BootstrapView.spec.ts`
- `apps/web/admin/src/views/AuditView.vue`
- `apps/web/admin/src/views/AuditView.spec.ts`
- `apps/web/admin/src/i18n/index.ts`
- `apps/web/admin/src/i18n/types.ts`
- `apps/web/admin/e2e/specs/bootstrap.spec.ts`
- `apps/web/admin/README.md`
- `infra/apps/smp/production/compose.yaml`
- `infra/apps/smp/production/.env.example`
- `infra/apps/smp/production/prepare.sh`
- `infra/apps/smp/swarm/stack.yaml`
- `infra/apps/smp/swarm/.env.example`
- `infra/apps/smp/swarm/deploy.sh`
- `.env.example`

### Product, operations, architecture, and tests

- `apps/web/PRODUCT.md`
- `apps/web/admin/PRODUCT.md`
- `docs/README.md`
- `docs/architecture/iam-platform.md`
- `docs/production-secrets.md`
- `docs/infrastructure/platform-admin-bootstrap.md`
- `server/smp/src/test/kotlin/com/profiletailors/smp/PlatformAdminBootstrapCommandDispatchTest.kt`
- `server/smp/src/test/kotlin/com/profiletailors/smp/SmpApplicationTests.kt`
- `server/smp/src/test/kotlin/com/profiletailors/smp/bdd/BddDatabaseSupport.kt`
- `server/smp/src/test/kotlin/com/profiletailors/smp/bdd/PlatformAdminBddSteps.kt`
- `server/smp/src/test/kotlin/com/profiletailors/smp/bdd/glue/ConsentScopeBddSteps.kt`
- `server/smp/src/test/kotlin/com/profiletailors/smp/bdd/glue/PlatformAdminBootstrapBddSteps.kt`
- `server/smp/src/test/resources/features/governance-consent-scopes.feature`
- `server/smp/src/test/resources/features/platformadmin/bootstrap.feature`
- `server/smp/src/test/kotlin/com/profiletailors/smp/governance/domain/ConsentScopeTest.kt`
- `server/smp/src/test/kotlin/com/profiletailors/smp/governance/infrastructure/ConsentScopePostgresIntegrationTest.kt`
- `server/smp/src/test/kotlin/com/profiletailors/smp/identity/application/GetCurrentTermsVersionHandlerTest.kt`
- `server/smp/src/test/kotlin/com/profiletailors/smp/identity/application/LocalAuthHandlersTest.kt`
- `server/smp/src/test/kotlin/com/profiletailors/smp/identity/infrastructure/security/RefreshSessionOriginValidationWebFilterTest.kt`
- `server/smp/src/test/kotlin/com/profiletailors/smp/infrastructure/db/LiquibaseBaselineChangelogTest.kt`
- `server/smp/src/test/kotlin/com/profiletailors/smp/platformadmin/application/CompleteBootstrapHandlerTest.kt`
- `server/smp/src/test/kotlin/com/profiletailors/smp/platformadmin/application/CompleteBootstrapOwnerGrantHandlerTest.kt`
- `server/smp/src/test/kotlin/com/profiletailors/smp/platformadmin/application/IssueBootstrapInvitationHandlerTest.kt`
- `server/smp/src/test/kotlin/com/profiletailors/smp/platformadmin/domain/AdminAuditEventTest.kt`
- `server/smp/src/test/kotlin/com/profiletailors/smp/platformadmin/domain/PlatformRoleAssignmentTest.kt`
- `server/smp/src/test/kotlin/com/profiletailors/smp/platformadmin/infrastructure/SecureBootstrapTokenGeneratorTest.kt`
- `server/smp/src/test/kotlin/com/profiletailors/smp/platformadmin/infrastructure/cli/InteractivePlatformAdminBootstrapCliTest.kt`
- `server/smp/src/test/kotlin/com/profiletailors/smp/platformadmin/infrastructure/cli/PlatformAdminBootstrapCliConfigurationTest.kt`
- `server/smp/src/test/kotlin/com/profiletailors/smp/platformadmin/infrastructure/http/AdminAuditControllerTest.kt`
- `server/smp/src/test/kotlin/com/profiletailors/smp/platformadmin/infrastructure/http/PlatformBootstrapControllerTest.kt`
- `server/smp/src/test/kotlin/com/profiletailors/smp/platformadmin/integration/R2dbcAdminAuditRepositoryPostgresIntegrationTest.kt`
- `server/smp/src/test/kotlin/com/profiletailors/smp/platformadmin/integration/R2dbcAdminUserQueryPostgresIntegrationTest.kt`
- `server/smp/src/test/kotlin/com/profiletailors/smp/platformadmin/integration/R2dbcPlatformBootstrapInvitationRepositoryPostgresIntegrationTest.kt`
- `server/smp/src/test/kotlin/com/profiletailors/smp/platformadmin/integration/R2dbcPlatformRoleAssignmentRepositoryPostgresIntegrationTest.kt`
- `.agents/sdd/changes/initial-platform-admin-bootstrap/tasks.md`
- `.agents/sdd/changes/initial-platform-admin-bootstrap/state.yaml`
- `.agents/sdd/changes/initial-platform-admin-bootstrap/apply-progress.md`

The following changes were present before apply and were preserved: `.gitignore`, ADR-0030,
`docs/architecture/adr/README.md`, ADR-0031/0032/0033, and the existing OpenSpec proposal/spec/design
artifacts.

## Deviations and risks

- **Transaction owner:** `VerifyEmailHandler` retains its existing identity-owned `AtomicTransactionRunner` and invokes the identity-owned completion port within that R2DBC transaction. The platformadmin grant handler performs database work in the existing transaction rather than opening a nested platformadmin-owned runner. This preserves the required atomic writes, but it differs from the design document's wording that the platformadmin coordinator owns the runner; reconcile before closure.
- **Review workload:** the former `size-exception` plan is superseded by `feature-branch-chain`. Keep each monorepo unit within 400 changed lines; the verified candidate is approximately 3,360 lines across 16 units plus separate D01.
- **Out-of-scope artifact drift:** `design.md` still contains the former `size-exception` and no-commit wording. U02 is limited to the four assigned artifacts, so `design.md` remains unchanged and requires a separately authorized follow-up.
- **Backend checks:** `just backend-check` did not pass. `ModularStructureTest` reports forbidden platformadmin dependencies on `governance::domain` from `CompleteBootstrapHandler` and `GovernanceBootstrapTermsConsentVerifier`, a platformadmin dependency on `identity::infrastructure` from the CLI configuration's `R2dbcPrincipalIdentityLookup`, and root-module dependencies on non-exposed CLI infrastructure types from `SmpApplication.kt`. Detekt reported 22 issues, including `LongMethod`, `ThrowsCount`, `TooManyFunctions`, `FunctionNameMaxLength`, `MaxLineLength`, `StringShouldBeRawString`, `UnnecessaryParentheses`, `UnderscoresInNumericLiterals`, and `ClassOrdering`. No suppressions were added.
- **Migration context:** governance `008` and platform-admin `013` are gated by `@uuid-native-identifiers`. Production operations must apply them only after the approved UUID cutover procedure; default `prod` startup does not apply them.
- **CLI secret source:** production Compose mounts `SMP_DB_PASSWORD` as `/run/secrets/SMP_DB_PASSWORD` and exposes it through `SPRING_CONFIG_IMPORT=configtree:/run/secrets/`. The isolated `AnnotationConfigApplicationContext` does not process Spring Boot config-data imports, while the CLI configuration currently requires the value as an environment placeholder. The actual container command is therefore blocked until the CLI reads the mounted secret directly or loads the configtree through a safe, explicit mechanism.
- **Request schema:** the DTO declares only the three approved bootstrap fields and the admin client sends only those fields. Backend rejection of extra JSON fields has not been tested; confirm the deployed Jackson behavior against the “accepts exactly” contract before closure.
- **Unusable-token edge case:** the request DTO marks `invitationToken` as `@NotBlank`. Missing or blank values may be handled by bean validation before the bootstrap handler and may not receive the required `BOOTSTRAP_UNAVAILABLE` code. Add a regression test and map this case consistently before closure.
- **Unverified atomicity:** the identity-owned port is called inside the conditional email-verification transaction, and unit tests cover orchestration. PostgreSQL rollback/recovery of a failed owner grant/audit has not been proven.
- **BDD:** two scenarios fail in the full fast suite; no product acceptance is claimed.
- **Image:** the actual `bootBuildImage` image and TTY invocation have not been tested.
- **Legal review:** ADR-0031 and ADR-0033 still require separate legal review; this implementation does not establish compliance.
- **Publication and deployment:** local conventional commits per unit are authorized. No push, PR, live checks, VPS deployment, or live DNS/CORS verification occurred.

## Remaining work

- [ ] Diagnose and fix the two failing Cucumber scenarios and Gradle result aggregation, then rerun the full fast BDD lane.
- [ ] Prove conditional token consumption, completion, consent, owner grant, and audit rollback/recovery with PostgreSQL.
- [ ] Verify supported-schema upgrade/backfill and fresh PostgreSQL initialization with no owner/workspace/membership rows.
- [ ] Resolve the 22 Detekt findings and `ModularStructureTest` failures without weakening architecture rules or adding suppressions.
- [ ] Fix the CLI configtree secret gap; build `:server:smp:bootBuildImage` and verify the exact TTY-only invocation, startup isolation, and one terminal link.
- [ ] Resolve the missing local environment inputs for `just production-config` and `just swarm-config`; rerun both checks.
- [ ] Rerun docs-link validation, which timed out; complete final diff/secret/suppression checks.
- [ ] Complete legal review for ADR-0031 and ADR-0033.
- [ ] Do not deploy or perform live DNS/CORS validation without separate authorization.
