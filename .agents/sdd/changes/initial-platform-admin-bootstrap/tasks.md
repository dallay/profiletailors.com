# Tasks: Initial Platform Admin Bootstrap

## Review Workload Forecast

Estimated changed lines: ~5,000 monorepo; D01 ~90.
Delivery strategy: feature-branch-chain
Suggested split: U01→U23; D01 separate

Decision needed before apply: No
Chained PRs recommended: Yes
Chain strategy: feature-branch-chain
400-line budget risk: High

U01 complete: `7fe2f7d9a41bee56a894c2c8a80d679c425e6251` / `feature/bootstrap-u01-planning`; U02 complete: `961a9381d798c5b068cc2cdb70df195663d12973` / `feature/bootstrap-u02-history` (U03 parent). U03 completes planning/history sync at `feature/bootstrap-u03-planning-sync`. Local conventional commits are authorized; no push/PR/live/VPS. `.gitignore` is pre-existing and must remain unstaged. Estimates are provisional; count staged additions plus deletions and stop above 400; do not split risky hunks.

## Suggested Work Units

| Units | Goal | PR |
|---|---|---|
| U03–U23 | Chain units | Sequential PRs |
| D01 | `/Users/acosta/Dev/dallay/profiletailors-deploy` | Base main; after U22 |

Refs (3/23–23/23): `feature/bootstrap-u03-planning-sync→u04-consent-actors→u05-governance-port-adr→u06-specs→u07-schema→u08-consent-port→u09-audit→u10-invitation-domain→u11-invitation-store→u12-token→u13-issuance→u14-terms-api→u15-recipient→u16-owner-grant→u17-postgres-recovery→u18-recipient-ui→u19-audit-ui→u20-cli-composition→u21-cli-runtime→u22-runtime-config→u23-bdd`; suffixes use `feature/bootstrap-`, each base is prior ref. No Stack metadata.

Estimates U03→U23, in order: 200/330/305/165/186/360/180/140/310/52/317/240/390/300/180/347/32/140/320/86/380; D01 ~90. U15/U23 are tight.
`K=server/smp/src/main/kotlin/com/profiletailors/smp`; `T=server/smp/src/test/kotlin/com/profiletailors/smp`; `F=server/smp/src/test/resources/features`; `R=server/smp/src/main/resources`; `C=.agents/sdd/changes/initial-platform-admin-bootstrap`; `A=apps/web/admin`; `D=docs/architecture/adr`.

## Phase 1: Planning, Decisions, Schema

- [x] 1.1 U03: Reconcile `C/design.md` with the accepted feature-branch chain, application-port and CLI wiring boundaries, and `SYSTEM_BOOTSTRAP`; update `C/{tasks.md,state.yaml,apply-progress.md}` with the 23-unit replan and U01/U02 history.
- [ ] 1.2 U04: Update `D/{0030,0031,0032,README.md}` and `C/specs/consent-scopes/spec.md`; index/validate links.
- [ ] 1.3 U05: Add narrow Terms port `platformadmin→governance::application` ADR-0034; update `D/README.md` for 0033/0034; preserve ADR-0023.
- [ ] 1.4 U06: Reconcile `C/specs/{platform-admin-bootstrap,invitations,email-verification}/spec.md`.
- [ ] 1.5 U07: Add `R/db/changelog` and `T/infrastructure/db/LiquibaseBaselineChangelogTest.kt`; verify upgrade/fresh schema.

## Phase 2: Backend

- [ ] 2.1 U08: Update `K/governance/{domain/ConsentScope.kt,infrastructure/R2dbcConsentRepository.kt}`; add `K/governance/application/{ConsentRequests.kt,ConsentHandlers.kt}` DTO record/check; test PostgreSQL; map in governance, no platformadmin→governance::domain imports.
- [ ] 2.2 U09: Update `K/platformadmin/domain/{AdminAuditEvent.kt,PlatformRoleAssignment.kt}` persistence/tests and `docs/architecture/iam-platform.md`.
- [ ] 2.3 U10: Test `K/platformadmin/domain/BootstrapInvitation.kt` invariants.
- [ ] 2.4 U11: Test `K/platformadmin/infrastructure/persistence/R2dbcPlatformBootstrapInvitationRepository.kt` replay/concurrency.
- [ ] 2.5 U12: Test `K/platformadmin/infrastructure/SecureBootstrapTokenGenerator.kt`.
- [ ] 2.6 U13: Test `K/platformadmin/application/IssueBootstrapInvitationHandler.kt` replacement/fail-closed behavior.
- [ ] 2.7 U14: Test `K/identity/application/GetCurrentTermsVersionQuery.kt`, `K/platformadmin/infrastructure/http/PlatformBootstrapController.kt`, and safe token errors.
- [ ] 2.8 U15: Test `K/platformadmin/application/CompleteBootstrapHandler.kt` via consent port only.
- [ ] 2.9 U16: Test `K/identity/application/LocalAuthHandlers.kt` and `K/platformadmin/application/CompleteBootstrapOwnerGrantHandler.kt`; preserve registration.
- [ ] 2.10 U17: Add `T/identity/integration/LocalAuthHandlersTransactionPostgresIntegrationTest.kt`; prove rollback.

## Phase 3: Admin, CLI, Runtime

- [ ] 3.1 U18: Add `A/src/{router/index.ts,views/BootstrapView.vue}`, locales, Vitest/Playwright; run admin gates.
- [ ] 3.2 U19: Update `A/src/views/AuditView.vue` tests and `apps/web/{PRODUCT.md,admin/PRODUCT.md,admin/README.md}`.
- [ ] 3.3 U20: Route `K/SmpApplication.kt` through wiring-only `K/config/PlatformAdminBootstrapCommand.kt`; no CLI-infra root import.
- [ ] 3.4 U21: Fix `K/platformadmin/infrastructure/cli/PlatformAdminBootstrapCliConfiguration.kt` configtree/tests; verify image TTY.
- [ ] 3.5 U22: Update `.env.example`, `infra/apps/smp/{production,swarm}`, and `docs/{production-secrets.md,infrastructure/platform-admin-bootstrap.md}`.

## Phase 4: BDD and Verification

- [ ] 4.1 U23: Fix `T/bdd/{BddDatabaseSupport.kt,glue/{ConsentScopeBddSteps.kt,PlatformAdminBootstrapBddSteps.kt}}` and `F/{governance-consent-scopes.feature,platformadmin/bootstrap.feature}`; rerun lanes.
- [ ] 4.2 D01: Update repo `production/{.env.example,docker-compose.yml}`, `tests/cloudflare-tunnel-config-validation.sh`, `docs/operations.md`; check only with existing inputs; no creation/deploy.

Run `just backend-lint` early: U09 `PlatformRoleAssignmentTest` ClassOrdering; U10 `BootstrapInvitation` UnnecessaryParentheses×3; U11 advisory-lock UnderscoresInNumericLiterals; U13 IssueBootstrapInvitationHandler ThrowsCount; U15 CompleteBootstrapHandler LongMethod/ThrowsCount + test ClassOrdering; U16 VerifyEmailHandler ThrowsCount; U20 SmpApplication StringShouldBeRawString; U21 CLI TooManyFunctions/FunctionNameMaxLength×2; U23 `cleanupStatements` LongMethod/BDD MaxLineLength/StringShouldBeRawString. Total 17. No suppression/baseline/rule weakening. Keep named interfaces narrow, no wildcard; config wiring-only and Modulith tests unchanged.

## Open Verification Gates

- `just backend-check`: one unidentified failure, two skipped.
- `just backend-bdd-fast`: 329 scenarios, two failures (tenant isolation `BadSqlGrammarException`; owner grant `AssertionError`), plus aggregation `NoSuchFileException`.
- PostgreSQL recovery and CLI configtree/image TTY remain unverified.
- Production `.env` absent; swarm `.env` lacks `DASHBOARD_IMAGE`. Do not create `.env` files; config checks blocked.
- D01 external after U22; docs-links timed out; ADR-0031/0033 legal review open; no compliance/deploy claim.
