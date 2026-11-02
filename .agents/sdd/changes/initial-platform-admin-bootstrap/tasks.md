# Tasks: Initial Platform Admin Bootstrap

## Review Workload Forecast

Estimated lines: ~3,360 candidate, excluding planning/ADR/`.gitignore`.
Delivery strategy: feature-branch-chain
Decision needed before apply: No
Chained PRs recommended: Yes
Chain strategy: feature-branch-chain
400-line budget risk: High

## Local Delivery Contract

Tracker: `feature/initial-platform-admin-bootstrap` based on `main`. At re-plan start, the candidate was uncommitted. Commit U01–U16 sequentially on the tracker using one authorized conventional commit per unit (`docs(sdd)` U01–02; `docs(architecture)` U03–04; `feat(platform-admin)` U05–16); set the listed local `feature/...` ref at each unit commit. Bases: `main`, then each immediate predecessor ref. No GitHub Stack metadata. Keep `.gitignore` unstaged. Record refs/commits as materialized. No push/PR/live checks/VPS deployment.

## Suggested Work Units

| Unit | Base → local ref | Lines | Goal / files |
|---|---|---:|---|
| U01 | `main` → `feature/bootstrap-u01-planning` | 217 | Reconcile `C/{proposal.md,design.md,state.yaml,tasks.md}` with approved strategy and authorization. |
| U02 | U01 → `feature/bootstrap-u02-history` | 330 | Reconcile history/status in `C/{exploration.md,apply-progress.md}`. |
| U03 | U02 → `feature/bootstrap-u03-consent` | 328 | Consent/actor decisions: `docs/architecture/adr/{0030-separate-public-waitlist-consent-scope.md,0031-explicit-platform-consent-scope.md,0032-bootstrap-system-actor-provenance.md,README.md}`; `C/specs/consent-scopes/spec.md`. Index ADR-0031/0032. |
| U04 | U03 → `feature/bootstrap-u04-bootstrap-contracts` | 327 | Bootstrap decisions/contracts: `docs/architecture/adr/{0033-atomic-bootstrap-owner-grant.md,README.md}`; `C/specs/{platform-admin-bootstrap,invitations,email-verification}/spec.md`. Append ADR-0033 in a repeated README update. |
| U05 | U04 → `feature/bootstrap-u05-schema` | 190 | Schema: `SR/db/changelog/{db.changelog-master.yaml,governance/008-platform-consent-scope.{sql,yaml},platform-admin/{012-system-bootstrap-actors,013-bootstrap-invitations}.{sql,yaml}}`; `ST/infrastructure/db/LiquibaseBaselineChangelogTest.kt`. |
| U06 | U05 → `feature/bootstrap-u06-consent-runtime` | 300 | Consent runtime/tests: `SM/governance/{domain/ConsentScope.kt,infrastructure/{R2dbcConsentRepository.kt,http/ConsentController.kt}}`; `ST/governance/{domain/ConsentScopeTest.kt,infrastructure/ConsentScopePostgresIntegrationTest.kt}`; `ST/bdd/glue/ConsentScopeBddSteps.kt`; `server/smp/src/test/resources/features/governance-consent-scopes.feature`. |
| U07 | U06 → `feature/bootstrap-u07-audit` | 207 | Actor/audit: `SM/platformadmin/{domain/{AdminAuditEvent.kt,PlatformRoleAssignment.kt},application/model/AdminAuditModels.kt,infrastructure/persistence/{R2dbcAdminAuditRepository.kt,R2dbcPlatformRoleAssignmentRepository.kt}}`; `ST/platformadmin/{domain/{AdminAuditEventTest.kt,PlatformRoleAssignmentTest.kt},integration/{R2dbcAdminAuditRepositoryPostgresIntegrationTest.kt,R2dbcPlatformRoleAssignmentRepositoryPostgresIntegrationTest.kt,R2dbcAdminUserQueryPostgresIntegrationTest.kt},infrastructure/http/AdminAuditControllerTest.kt}`; `docs/architecture/iam-platform.md`. |
| U08 | U07 → `feature/bootstrap-u08-invitations` | 385 | Invitation lifecycle/concurrency: `SM/platformadmin/{domain/BootstrapInvitation.kt,infrastructure/persistence/R2dbcPlatformBootstrapInvitationRepository.kt}`; `ST/platformadmin/integration/R2dbcPlatformBootstrapInvitationRepositoryPostgresIntegrationTest.kt`. |
| U09 | U08 → `feature/bootstrap-u09-issuance` | 372 | Issuance: `SM/platformadmin/{application/IssueBootstrapInvitationHandler.kt,infrastructure/SecureBootstrapTokenGenerator.kt}`; `ST/platformadmin/application/IssueBootstrapInvitationHandlerTest.kt`; `ST/platformadmin/infrastructure/SecureBootstrapTokenGeneratorTest.kt`. |
| U10 | U09 → `feature/bootstrap-u10-terms-api` | 305 | Terms/API: `SM/identity/{application/GetCurrentTermsVersionQuery.kt,infrastructure/security/{IdentitySecurityConfiguration.kt,RefreshSessionOriginValidationWebFilter.kt}}`; `SM/platformadmin/{application/{CompleteBootstrapCommand.kt,contracts/BootstrapTermsConsentVerifier.kt},infrastructure/{GovernanceBootstrapTermsConsentVerifier.kt,http/{PlatformBootstrapController.kt,AdminProblemDetailsHandler.kt}}}`; `ST/identity/{application/GetCurrentTermsVersionHandlerTest.kt,infrastructure/security/RefreshSessionOriginValidationWebFilterTest.kt}`; `ST/platformadmin/infrastructure/http/PlatformBootstrapControllerTest.kt`. |
| U11 | U10 → `feature/bootstrap-u11-recipient-setup` | 395* | Recipient setup/Modulith: `SM/platformadmin/application/CompleteBootstrapHandler.kt`; `ST/platformadmin/application/CompleteBootstrapHandlerTest.kt`. |
| U12 | U11 → `feature/bootstrap-u12-owner-grant` | 365 | Verification/grant: `SM/identity/application/{BootstrapVerificationCompletionPort.kt,IdentityRegistrationGateway.kt,LocalAuthHandlers.kt}`; `SM/identity/infrastructure/R2dbcIdentityRegistrationGateway.kt`; `SM/platformadmin/{application/CompleteBootstrapOwnerGrantHandler.kt,infrastructure/{PlatformBootstrapVerificationCompletionAdapter.kt,persistence/R2dbcPlatformAdminBootstrapTransactionRunner.kt}}`; `ST/identity/{application/LocalAuthHandlersTest.kt,integration/LocalAuthHandlersTransactionPostgresIntegrationTest.kt}`; `ST/platformadmin/application/CompleteBootstrapOwnerGrantHandlerTest.kt`. |
| U13 | U12 → `feature/bootstrap-u13-admin-ui` | 379 | Admin/product: `apps/web/admin/{src/{router/index.ts,i18n/{index.ts,types.ts},views/{BootstrapView.vue,BootstrapView.spec.ts,AuditView.vue,AuditView.spec.ts}},e2e/specs/bootstrap.spec.ts,PRODUCT.md,README.md}`; `apps/web/PRODUCT.md`. |
| U14 | U13 → `feature/bootstrap-u14-cli-configtree` | 396* | CLI/configtree: `SM/SmpApplication.kt`; `SM/platformadmin/infrastructure/cli/{InteractivePlatformAdminBootstrapCli.kt,PlatformAdminBootstrapCliConfiguration.kt}`; `ST/{SmpApplicationTests.kt,PlatformAdminBootstrapCommandDispatchTest.kt}`; `ST/platformadmin/infrastructure/cli/{InteractivePlatformAdminBootstrapCliTest.kt,PlatformAdminBootstrapCliConfigurationTest.kt}`. |
| U15 | U14 → `feature/bootstrap-u15-runtime-config` | 90 | Set `SMP_ADMIN_PUBLIC_ORIGIN`/`SMP_CORS_ALLOWED_ORIGINS`: `.env.example`; `SR/application.yaml`; `infra/apps/smp/{production/{.env.example,compose.yaml,prepare.sh},swarm/{.env.example,deploy.sh,stack.yaml}}`; `docs/{README.md,production-secrets.md,infrastructure/platform-admin-bootstrap.md}`. |
| U16 | U15 → `feature/bootstrap-u16-bdd` | 320 | Bootstrap BDD integration: `ST/bdd/{BddDatabaseSupport.kt,PlatformAdminBddSteps.kt}`; `ST/bdd/glue/PlatformAdminBootstrapBddSteps.kt`; `server/smp/src/test/resources/features/platformadmin/bootstrap.feature`. |

Estimates are provisional, including tests/docs. U11/U14 are uncertain: 395/396 leave only 5/4 lines; verify exact diff before commit and stop above 400. No Detekt/Modulith weakening.

## Separate Deployment Repository Unit

| Unit | Dependency/base | Estimate | Scope and local validation |
|---|---|---:|---|
| D01 | Separate repo `/Users/acosta/Dev/dallay/profiletailors-deploy`, base `main`; after U15 | 90 | Separate authorized local conventional commit, outside monorepo refs: update `production/{.env.example,docker-compose.yml}`, `tests/cloudflare-tunnel-config-validation.sh`, `docs/operations.md` for origin/CORS. Sample: `https://admin.profiletailors.com`. Validate `bash -n tests/cloudflare-tunnel-config-validation.sh`, `bash tests/cloudflare-tunnel-config-validation.sh`, and `docker stack config --compose-file production/docker-compose.yml` (last two require local `.env` and Docker). No push/PR/deploy. |

## Open Verification Tasks

- [ ] U16: fix BDD tenant/platform SQL and bootstrap-grant failures; rerun lane.
- [ ] U12: rerun final 013 test; verify upgrade/backfill, fresh PostgreSQL, and token/consent/grant/audit rollback/recovery.
- [ ] U10: test absent/blank tokens and extra fields; preserve safe errors.
- [ ] U14: fix configtree secret; build `:server:smp:bootBuildImage`; verify TTY.
- [ ] Resolve 22 Detekt/Modulith failures; rerun backend/PostgreSQL/admin/config/docs-link gates. Blockers: missing production `.env`/swarm `DASHBOARD_IMAGE`, docs-links timeout, ADR-0031/0033 legal review. No remote/deployment.
