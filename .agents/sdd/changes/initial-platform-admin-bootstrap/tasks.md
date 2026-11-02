# Tasks: Initial Platform Admin Bootstrap

## Review Workload Forecast

Estimated changed lines: ~5,300 monorepo; D01 ~90

delivery_strategy: feature-branch-chain

Decision needed before apply: No
Chained PRs recommended: Yes
Chain strategy: feature-branch-chain
400-line budget risk: High

U01=`7fe2f7d9a41bee56a894c2c8a80d679c425e6251`@`feature/bootstrap-u01-planning`;U02=`961a9381d798c5b068cc2cdb70df195663d12973`@`feature/bootstrap-u02-history`;U03=`e46a23f758f6d1269b2c9fc4ffdca3a9b8a8fd67`@`feature/bootstrap-u03-planning-sync`;U04=`28802e5e4807977f1b327cdc8becb0f37de1a8e1`@`feature/bootstrap-u04-consent-actors`. U05 plan-sync ref `feature/bootstrap-u05-plan-rebaseline`, based on U04. U01-U04 are complete; U05 is current. Local conventional commits are authorized per unit; no push/PR/live/VPS. Keep source candidate and the pre-existing `.gitignore` modification unstaged and leave `.gitignore` untouched.

Aliases: `C=.agents/sdd/changes/initial-platform-admin-bootstrap`;`D=docs/architecture/adr`;`K=server/smp/src/main/kotlin/com/profiletailors/smp`;`T=server/smp/src/test/kotlin/com/profiletailors/smp`;`R=server/smp/src/main/resources`;`A=apps/web/admin`.

Scope: 24 monorepo work units (U01-U24), each ≤400 staged lines including docs/tests; each unit bases on its immediate predecessor. D01 is external and follows U24.

TDD: RED test → GREEN fix → REFACTOR; tests first.

### Suggested Work Units

- `U05-plan-rebaseline feature/bootstrap-u05-plan-rebaseline ← feature/bootstrap-u04-consent-actors`
- `U06-ADR0033-specs feature/bootstrap-u06-adr-0033 ← feature/bootstrap-u05-plan-rebaseline`
- `U07-ADR0034-port feature/bootstrap-u07-adr-0034 ← feature/bootstrap-u06-adr-0033`
- `U08-schema feature/bootstrap-u08-schema ← feature/bootstrap-u07-adr-0034`
- `U09-governance-port feature/bootstrap-u09-governance-port ← feature/bootstrap-u08-schema`
- `U10-audit-actors feature/bootstrap-u10-audit-actors ← feature/bootstrap-u09-governance-port`
- `U11-invitation-domain feature/bootstrap-u11-invitation-domain ← feature/bootstrap-u10-audit-actors`
- `U12-invitation-store feature/bootstrap-u12-invitation-store ← feature/bootstrap-u11-invitation-domain`
- `U13-token-generator feature/bootstrap-u13-token-generator ← feature/bootstrap-u12-invitation-store`
- `U14-invitation-issuance feature/bootstrap-u14-invitation-issuance ← feature/bootstrap-u13-token-generator`
- `U15-terms-api feature/bootstrap-u15-terms-api ← feature/bootstrap-u14-invitation-issuance`
- `U16-bootstrap-completion feature/bootstrap-u16-bootstrap-completion ← feature/bootstrap-u15-terms-api`
- `U17-verification-owner-grant feature/bootstrap-u17-verification-owner-grant ← feature/bootstrap-u16-bootstrap-completion`
- `U18-postgres-recovery feature/bootstrap-u18-postgres-recovery ← feature/bootstrap-u17-verification-owner-grant`
- `U19-recipient-ui feature/bootstrap-u19-recipient-ui ← feature/bootstrap-u18-postgres-recovery`
- `U20-audit-ui feature/bootstrap-u20-audit-ui ← feature/bootstrap-u19-recipient-ui`
- `U21-cli-composition feature/bootstrap-u21-cli-composition ← feature/bootstrap-u20-audit-ui`
- `U22-cli-runtime feature/bootstrap-u22-cli-runtime ← feature/bootstrap-u21-cli-composition`
- `U23-runtime-config feature/bootstrap-u23-runtime-config ← feature/bootstrap-u22-cli-runtime`
- `U24-BDD-closeout feature/bootstrap-u24-bdd-closeout ← feature/bootstrap-u23-runtime-config`
- `D01-deploy-config feature/bootstrap-d01-production-config ← main (profiletailors-deploy; after U24)`

## Phase 1: Planning, Contracts, Schema

- [x] 1.1 U05 (≤220): Reconcile `C/{tasks.md,state.yaml,apply-progress.md,design.md}`; record U01-U04 commits, U04 as the base, the 24-unit sequence, and open gates.
- [ ] 1.2 U06 (≤323): Add `D/0033-atomic-bootstrap-owner-grant.md`, `C/specs/{platform-admin-bootstrap,invitations,email-verification}/spec.md`, `D/README.md` row 0033; links resolve within U06/prior only.
- [ ] 1.3 U07 (≤145): Add `D/0034-platform-bootstrap-uses-governance-application-port.md`, `D/README.md` row 0034; cross-link ADR-0033, keep ADR-0023 unchanged, verify links.
- [ ] 1.4 U08 (≤186): Add `R/db/changelog/{db.changelog-master.yaml,governance/008-platform-consent-scope.*,platform-admin/{012-system-bootstrap-actors,013-bootstrap-invitations}.*}`; test `T/infrastructure/db/LiquibaseBaselineChangelogTest.kt` upgrade/fresh.

## Phase 2: Backend

- [ ] 2.1 U09 (≤300): Change `K/governance/application/{ConsentRequests.kt,ConsentHandlers.kt};K/governance/{domain/ConsentScope.kt,infrastructure/{R2dbcConsentRepository.kt,http/ConsentController.kt}};K/platformadmin/{application/contracts/BootstrapTermsConsentVerifier.kt,infrastructure/GovernanceBootstrapTermsConsentVerifier.kt};T/governance/{domain/ConsentScopeTest.kt,infrastructure/ConsentScopePostgresIntegrationTest.kt}`. `platformadmin → governance::application` only.
- [ ] 2.2 U10 (≤180): Update `K/platformadmin/{application/model/AdminAuditModels.kt,domain/{AdminAuditEvent.kt,PlatformRoleAssignment.kt},infrastructure/persistence/{R2dbcAdminAuditRepository.kt,R2dbcPlatformRoleAssignmentRepository.kt}}`, `T/platformadmin/{domain/*Test.kt,integration/*PostgresIntegrationTest.kt}`, `docs/architecture/iam-platform.md`.
- [ ] 2.3 U11 (≤140): Enforce `K/platformadmin/domain/BootstrapInvitation.kt`; test `T/platformadmin/domain/BootstrapInvitationTest.kt` invariants.
- [ ] 2.4 U12 (≤330): Implement `K/platformadmin/infrastructure/persistence/{R2dbcPlatformAdminBootstrapTransactionRunner.kt,R2dbcPlatformBootstrapInvitationRepository.kt}`; test `T/platformadmin/integration/R2dbcPlatformBootstrapInvitationRepositoryPostgresIntegrationTest.kt`.
- [ ] 2.5 U13 (≤52): Implement/test `K/platformadmin/infrastructure/SecureBootstrapTokenGenerator.kt`; verify hash-only storage.
- [ ] 2.6 U14 (≤317): Implement `K/platformadmin/application/IssueBootstrapInvitationHandler.kt`; test `T/platformadmin/application/IssueBootstrapInvitationHandlerTest.kt` replacement/rejection/fail-closed behavior.
- [ ] 2.7 U15 (≤240): Add `K/identity/application/GetCurrentTermsVersionQuery.kt`, `K/platformadmin/infrastructure/http/{PlatformBootstrapController.kt,AdminProblemDetailsHandler.kt}`, `T/identity/application/GetCurrentTermsVersionHandlerTest.kt`.
- [ ] 2.8 U16 (≤395): Implement `K/platformadmin/application/CompleteBootstrapHandler.kt`; test `T/platformadmin/application/CompleteBootstrapHandlerTest.kt` for stale Terms/consent/no-write.
- [ ] 2.9 U17 (≤300): Wire `K/identity/application/{BootstrapVerificationCompletionPort.kt,VerifyEmailHandler.kt}` and `K/platformadmin/{application/CompleteBootstrapOwnerGrantHandler.kt,infrastructure/PlatformBootstrapVerificationCompletionAdapter.kt}`; preserve registration.
- [ ] 2.10 U18 (≤210): Test `T/identity/integration/LocalAuthHandlersTransactionPostgresIntegrationTest.kt` rollback; run `just backend-test-postgres`.

## Phase 3: Admin, CLI, Runtime

- [ ] 3.1 U19 (≤347): Implement `A/src/{router/index.ts,views/BootstrapView.vue,i18n/{index.ts,types.ts}}`, `A/src/views/BootstrapView.spec.ts`, `A/e2e/specs/bootstrap.spec.ts`; run admin-check/test/build.
- [ ] 3.2 U20 (≤40): Update `A/src/views/{AuditView.vue,AuditView.spec.ts}`, `apps/web/{PRODUCT.md,admin/{PRODUCT.md,README.md}}`.
- [ ] 3.3 U21 (≤140): Dispatch `K/SmpApplication.kt` via wiring-only `com.profiletailors.smp.config` (`K/config/PlatformAdminBootstrapCommand.kt`); test `T/{PlatformAdminBootstrapCommandDispatchTest.kt,SmpApplicationTests.kt}`. No root infrastructure import.
- [ ] 3.4 U22 (≤320): Fix `K/platformadmin/infrastructure/cli/*` and `T/platformadmin/infrastructure/cli/*`; verify configtree/isolation/TTY/one link/`:server:smp:bootBuildImage`.
- [ ] 3.5 U23 (≤90): Update `.env.example`, `K/identity/infrastructure/security/*`, `R/application.yaml`, `infra/apps/smp/{production,swarm}/*`, `docs/{production-secrets.md,infrastructure/platform-admin-bootstrap.md}`; run `just production-config` and `just swarm-config`.

## Phase 4: BDD and Closeout

- [ ] 4.1 U24 (≤380): Fix `T/bdd/*`, `T/bdd/glue/*`, `F/{governance-consent-scopes.feature,platformadmin/bootstrap.feature}`/aggregation; run `just backend-bdd-fast`, `just backend-bdd-postgres`, `just backend-check`, `just backend-lint`, `ModularStructureTest`, `ModularityVerificationTest`, `HexagonalArchTest`, `ComponentScanArchTest`.
- [ ] 4.2 D01 (≤90): In `/Users/acosta/Dev/dallay/profiletailors-deploy`, update `production/{.env.example,docker-compose.yml},tests/cloudflare-tunnel-config-validation.sh,docs/operations.md`; validate existing inputs only.

Detekt (17): U10 `PlatformRoleAssignmentTest/ClassOrdering`; U11 `BootstrapInvitation/UnnecessaryParentheses`×3; U12 `advisory-lock/UnderscoresInNumericLiterals`; U14 `IssueBootstrapInvitationHandler/ThrowsCount`; U16 `CompleteBootstrapHandler/LongMethod+ThrowsCount`,`test/ClassOrdering`; U17 `VerifyEmailHandler/ThrowsCount`; U21 `SmpApplication/StringShouldBeRawString`; U22 `CLI/TooManyFunctions+FunctionNameMaxLength×2`; U24 `cleanupStatements/LongMethod`,`BDD/MaxLineLength+StringShouldBeRawString`. No suppressions/baseline/rule changes.

Open gates: BDD(2 failures+aggregation); backend-check(1 unknown,2 skipped); DB(recovery/migrations); CLI(configtree/image-tty); prod/Swarm(inputs); docs-links(timeout); ADR-0031/0033(legal review).
