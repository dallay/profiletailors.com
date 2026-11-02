# Tasks: Initial Platform Admin Bootstrap

## Review Workload Forecast

Estimated changed lines: ~5,000 monorepo from `main`; implementation and planning commits are local, with the working-tree review exceptions limited to `.gitignore` and the pending lint-fix commit.
400-line budget risk: High
Chained PRs recommended: No
Chain strategy: size-exception
Delivery strategy: size-exception
Decision needed before apply: No

User approves one full-scope draft PR from `feature/bootstrap-u07-adr-0034` to `main`, regardless of size, for direct review. Open it with known failed and unverified gates listed in the PR description; do not present it as merge-ready. No more unit branches/commits. No Co-Authored-By. No VPS deployment.

| Unit | Goal | Likely PR | Notes |
|---|---|---|---|
| 1 | Complete bootstrap and verification | One PR | `main` → current branch; approved size-exception; owner reviews all. |

Checklist items are implementation/verification batches, not separate PRs. TDD: RED test → GREEN fix → REFACTOR; tests first.

Paths: `D=docs/architecture/adr`; `C=.agents/sdd/changes/initial-platform-admin-bootstrap`; `K=server/smp/src/main/kotlin/com/profiletailors/smp`; `T=server/smp/src/test/kotlin/com/profiletailors/smp`; `R=server/smp/src/main/resources`; `F=server/smp/src/test/resources/features`; `A=apps/web/admin`.

## Phase 1: Contracts and Persistence

- [x] 1.1 Record ADR-0033/0034 in `D/{0033-atomic-bootstrap-owner-grant.md,0034-platform-bootstrap-uses-governance-application-port.md,README.md}` and `C/specs/{platform-admin-bootstrap,invitations,email-verification}/spec.md`; keep ADR-0023 unchanged.
- [ ] 1.2 Verify `R/db/changelog/{db.changelog-master.yaml,governance/008-platform-consent-scope.*,platform-admin/{012-system-bootstrap-actors,013-bootstrap-invitations}.*}` and `T/infrastructure/db/LiquibaseBaselineChangelogTest.kt` for fresh schema, upgrade/backfill, and UUID cutover.

## Phase 2: Backend and Admin

- [ ] 2.1 Complete DTO-only governance operations in `K/governance/application/{ConsentRequests.kt,ConsentHandlers.kt}`; update `K/governance/{domain/ConsentScope.kt,infrastructure/{R2dbcConsentRepository.kt,http/ConsentController.kt}}`, `K/platformadmin/{application/contracts/BootstrapTermsConsentVerifier.kt,infrastructure/GovernanceBootstrapTermsConsentVerifier.kt}`, `T/governance/{domain/ConsentScopeTest.kt,infrastructure/ConsentScopePostgresIntegrationTest.kt}`, and `docs/architecture/iam-platform.md`; no `platformadmin -> governance::domain` imports.
- [ ] 2.2 Verify `K/platformadmin/{application/model/AdminAuditModels.kt,domain/{AdminAuditEvent.kt,PlatformRoleAssignment.kt,BootstrapInvitation.kt},infrastructure/persistence/{R2dbcAdminAuditRepository.kt,R2dbcPlatformRoleAssignmentRepository.kt,R2dbcPlatformAdminBootstrapTransactionRunner.kt,R2dbcPlatformBootstrapInvitationRepository.kt},infrastructure/SecureBootstrapTokenGenerator.kt,application/{IssueBootstrapInvitationHandler.kt,CompleteBootstrapHandler.kt,CompleteBootstrapOwnerGrantHandler.kt}}` and `T/platformadmin/{domain/{AdminAuditEventTest.kt,BootstrapInvitationTest.kt,PlatformRoleAssignmentTest.kt},application/{IssueBootstrapInvitationHandlerTest.kt,CompleteBootstrapHandlerTest.kt,CompleteBootstrapOwnerGrantHandlerTest.kt},infrastructure/SecureBootstrapTokenGeneratorTest.kt,integration/R2dbcPlatformBootstrapInvitationRepositoryPostgresIntegrationTest.kt}`: system actor, hash-only 30-minute token, confirmed replacement, rejection without mutation, Terms consent, and fail-closed behavior.
- [ ] 2.3 Verify `K/identity/application/{LocalAuthHandlers.kt,VerifyEmailHandler.kt,BootstrapVerificationCompletionPort.kt,GetCurrentTermsVersionQuery.kt}` and `T/identity/{application/GetCurrentTermsVersionHandlerTest.kt,integration/LocalAuthHandlersTransactionPostgresIntegrationTest.kt}`; prove token-consumption/grant/audit rollback and no workspace or membership creation.
- [ ] 2.4 Verify `K/platformadmin/infrastructure/http/{PlatformBootstrapController.kt,AdminProblemDetailsHandler.kt}`, `T/platformadmin/infrastructure/http/PlatformBootstrapControllerTest.kt`; reject extra fields if required and map missing/blank tokens to `BOOTSTRAP_UNAVAILABLE`.
- [ ] 2.5 Verify `A/src/{router/index.ts,views/BootstrapView.vue,i18n/{index.ts,types.ts}}`, `A/src/views/{BootstrapView.spec.ts,AuditView.vue,AuditView.spec.ts}`, `A/e2e/specs/bootstrap.spec.ts`, and run `just admin-check`, `just admin-test`, `just admin-build`.

## Phase 3: CLI and Self-Hosting Configuration

- [ ] 3.1 Route `K/SmpApplication.kt` through `K/config/PlatformAdminBootstrapCommand.kt`; test `T/{PlatformAdminBootstrapCommandDispatchTest.kt,SmpApplicationTests.kt}`; preserve isolated startup and no root infrastructure import.
- [ ] 3.2 Fix CLI configtree access to `SMP_DB_PASSWORD`; verify `K/platformadmin/infrastructure/cli/{InteractivePlatformAdminBootstrapCli.kt,PlatformAdminBootstrapCliConfiguration.kt}`, `T/platformadmin/infrastructure/cli/{InteractivePlatformAdminBootstrapCliTest.kt,PlatformAdminBootstrapCliConfigurationTest.kt}`; build `:server:smp:bootBuildImage` and verify TTY-only invocation, isolation, and one terminal link.
- [ ] 3.3 Correct self-hosting templates/docs in `.env.example`, `K/identity/infrastructure/security/*`, `R/application.yaml`, `infra/apps/smp/{production,swarm}/*`, `docs/{production-secrets.md,infrastructure/platform-admin-bootstrap.md}`, `apps/web/{PRODUCT.md,admin/{PRODUCT.md,README.md}}`; resolve missing production env and swarm `DASHBOARD_IMAGE`, pass `just production-config`/`just swarm-config`, commit no secrets.

## Phase 4: Acceptance Gates and Follow-up

- [ ] 4.1 Fix both `just backend-bdd-fast` failures (tenant-consent `BadSqlGrammarException`; bootstrap owner-grant `AssertionError`) and Gradle aggregation `NoSuchFileException`; pass fast and PostgreSQL BDD using `T/bdd/{BddDatabaseSupport.kt,PlatformAdminBddSteps.kt}`, `T/bdd/glue/{ConsentScopeBddSteps.kt,PlatformAdminBootstrapBddSteps.kt}`, `F/{governance-consent-scopes.feature,platformadmin/bootstrap.feature}`.
- [ ] 4.2 `just backend-lint` passed on 2026-11-03 after fixing the 17 Detekt findings without suppressions. Focused `ModularStructureTest`, `ModularityVerificationTest`, `HexagonalArchTest`, and `ComponentScanArchTest` now pass. The broader backend check timed out with a PostgreSQL connection failure in `ResourcePreviewEndpointPostgresIntegrationTest`; rerun the full gate with valid local infrastructure and account for its two skipped tests. Do not weaken rules.
- [ ] 4.3 Pass `just backend-test-postgres` with migration/backfill, fresh-schema, conditional token-consumption, completion, consent, owner-grant, audit rollback/recovery; rerun `just docs-links` after timeout.
- [ ] 4.4 Complete legal review for ADR-0031/0033. Track official origin/CORS as a separate repository change in `profiletailors-deploy` (`production/{.env.example,docker-compose.yml}`, `tests/cloudflare-tunnel-config-validation.sh`, `docs/operations.md`); repo is outside this PR and must not be edited now. No live CORS/DNS claims.

The draft PR is for review only. Do not merge or claim acceptance until every applicable gate passes. Do not deploy to the VPS.
