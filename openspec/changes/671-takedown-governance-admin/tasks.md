# Tasks: Back Office Takedown Governance (#671)

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~950–1400 (PR1 ~300, PR2 ~400, PR3 ~400–700) |
| 400-line budget risk | High |
| Chained PRs recommended | Yes |
| Suggested split | PR 1 governance ports/DTO/reader → PR 2 platformadmin API → PR 3 BDD + admin UI |
| Delivery strategy | auto-chain |
| Chain strategy | feature-branch-chain |

Decision needed before apply: No
Chained PRs recommended: Yes
Chain strategy: feature-branch-chain
400-line budget risk: High

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|------|------|-----------|-------|
| 1 | Governance ports + DTO + asset-status reader; no HTTP admin | PR 1 | `trunk=main`; `parent_branch=main`; `base=main`; `branch=issue-671-governance-ports`; `position=1`; #671 |
| 2 | platformadmin handlers/controller/permissions/audit/idempotency | PR 2 | `trunk=main`; `parent_branch=issue-671-governance-ports`; `base=issue-671-governance-ports`; `branch=issue-671-admin-api`; `position=2`; #671 |
| 3 | BDD + GovernanceView + nav live + i18n + auth.store + C4 | PR 3 | `trunk=main`; `parent_branch=issue-671-admin-api`; `base=issue-671-admin-api`; `branch=issue-671-bdd-ui`; `position=3`; #671 |

No GitHub Stack metadata. Child PRs retarget the previous branch.

TDD: RED then GREEN. No comments. Workspace `/api/governance/takedown/**` untouched.

## Phase 1: Governance ports + reader (PR1)

- [x] 1.1 **T-1.1** RED `governance/application/AdminTakedownPorts.kt`. Deliverable: `AdminTakedownQueryPort`/`CommandPort` + DTO (status `String`; page type owned by governance, not `platformadmin.PagedResult`). AC: list/get/approve/reject; `assetStatus` detail-only; no `TakedownReport`. Deps: none.
- [x] 1.2 **T-1.2** RED `R2dbcTakedownReportRepositoryTest.kt` global `findByReportId` + paged `findAll(status?, workspaceId?, page, size)` + count. AC: omitted filters unrestricted; two workspaces. Deps: none.
- [x] 1.3 **T-1.3** GREEN `TakedownReportRepository.kt` + `R2dbcTakedownReportRepository.kt`. Deliverable: new methods only. AC: 1.2 green; `findByWorkspace` unchanged. Deps: 1.2.
- [x] 1.4 **T-1.4** RED `MediaAssetStatusReader` tests. Deliverable: application port `(workspaceId, assetId) → status String?`. AC: READY/SUSPENDED; missing → null; no media types. Deps: none.
- [x] 1.5 **T-1.5** GREEN `MediaAssetStatusReader.kt` + `config/bridges/MediaAssetStatusReaderDelegate.kt` (mirror `MediaAssetStatusDelegate`). AC: 1.4 green; platformadmin still must not depend on media. Deps: 1.4.
- [x] 1.6 **T-1.6** RED adapter tests. AC: approve → APPROVED + SUSPENDED + `MEDIA_TAKEDOWN_APPROVED` + `TakedownApproved`; reject → DISMISSED + asset unchanged + `MEDIA_TAKEDOWN_REJECTED` + `TakedownRejected`; missing → not-found (not `TakedownReportNotFoundException`); not `REPORTED` → not-reviewable; operator id arg; no `ResourceContext`. Deps: 1.1, 1.4.
- [x] 1.7 **T-1.7** GREEN governance adapters using `TakedownReport.approve`/`dismiss`, `MediaAssetStatusUpdater`, `AuditHook`, emails. AC: 1.6 green; workspace handlers unedited; DTO never leaks aggregate. Deps: 1.3, 1.5, 1.6.
- [x] 1.8 **T-1.8** Hexagonal/Konsist: ports in `governance.application`; no `TakedownReport` outside governance. AC: `just backend-test-fast` architecture owners green. Deps: 1.7.

## Phase 2: platformadmin API (PR2)

- [ ] 2.1 **T-2.1** RED/GREEN `PlatformPermission.kt` + `PlatformPermissionTest.kt`: `GOVERNANCE_READ`/`MANAGE`. AC: OWNER+OPERATOR both; AUDITOR read; SUPPORT_AGENT neither; no `platform.operators.read`. Deps: none.
- [ ] 2.2 **T-2.2** RED/GREEN `AdminAuditEvent.kt`: `TAKEDOWN_APPROVED`/`TAKEDOWN_REJECTED`. AC: registry contains both; `MEDIA_TAKEDOWN_*` unnamed. Deps: none.
- [ ] 2.3 **T-2.3** RED/GREEN admin 404/409 types + `AdminProblemDetailsHandler.kt`. AC: not-found 404; not-reviewable 409; `ConfigurationIdempotency*` → 409; no `IllegalArgumentException` 400 for missing report. Deps: none.
- [ ] 2.4 **T-2.4** RED `AdminTakedownHandlersTest.kt`. AC: read/manage via `OperatorAccessResolver`; SUPPORT_AGENT 403 no email; AUDITOR mutate 403 + admin audit `REJECTED` ids-only; success dual audit; idempotent replay; 409 no transition. Deps: 1.7, 2.1, 2.2.
- [ ] 2.5 **T-2.5** GREEN `platformadmin/application/handler/AdminTakedownHandlers.kt`. AC: 2.4 green; no `TakedownReport` import; `MEDIA_*` success-only. Deps: 2.4.
- [ ] 2.6 **T-2.6** RED `AdminTakedownControllerTest.kt` WebTestClient. AC: 401 none; 403 `PLATFORM_ACCESS_DENIED`; GET list/detail 200 + `PagedResult`/`ADMIN_PAGE_MAX_SIZE`; POST approve/reject require `Idempotency-Key`; `Accept: application/vnd.api.v1+json`; no `X-Workspace-Id`. Deps: 2.3, 2.5.
- [ ] 2.7 **T-2.7** GREEN `AdminTakedownController.kt` `/api/admin/takedown-reports`. AC: 2.6 green; thin HTTP; reuse `ConfigurationIdempotencyService`; workspace controllers unedited. Deps: 2.6.
- [ ] 2.8 **T-2.8** `platformadmin/ModuleMetadata.kt` add `governance :: application` only. AC: `ModularityVerificationTest`/`ModularStructureTest` pass; fail if `TakedownReport` imported. Deps: 2.5.
- [ ] 2.9 **T-2.9** `RedactSensitiveMetadataTest` ids-only metadata (report/workspace/asset); never reporter email. AC: `redact()` keeps ids. Deps: 2.5.

## Phase 3: BDD (PR3)

- [ ] 3.1 **T-3.1** RED `server/smp/src/test/resources/features/platformadmin/takedown-admin.feature` `@smoke @fast @postgres`. Deliverable: spec scenarios (list/filter/401; detail+assetStatus/404; approve/reject lifecycle; idempotency replay; SUPPORT_AGENT 403; AUDITOR 403 mutate; 409 decided; reporter email no secrets; dual audit). Deps: 2.7.
- [ ] 3.2 **T-3.2** GREEN `bdd/glue/TakedownAdminBddSteps.kt`. AC: `BddDatabaseSupport`; `Accept: application/vnd.api.v1+json`; `Idempotency-Key`; role tokens; 3.1 green. Deps: 3.1.
- [ ] 3.3 **T-3.3** `just backend-bdd-fast`. AC: new scenarios pass; workspace takedown features unchanged. Deps: 3.2.

## Phase 4: Admin frontend (PR3)

- [ ] 4.1 **T-4.1** RED/GREEN `auth.store.ts` + test: mirror server governance matrix. AC: OWNER/OPERATOR both; AUDITOR read; SUPPORT_AGENT neither. Deps: 2.1.
- [ ] 4.2 **T-4.2** RED/GREEN `nav-registry.ts` + `nav-registry.spec.ts`: `governance` `live` on `platform.governance.read` (not `platform.operators.read`). AC: not planned; overview stays planned. Deps: 4.1.
- [ ] 4.3 **T-4.3** GREEN `router/index.ts`: `GovernanceView` + `governance/:reportId`; drop from `plannedNavEntries()` map. AC: not `PlannedAreaView`. Deps: 4.2.
- [ ] 4.4 **T-4.4** RED `GovernanceView.spec.ts`. AC: list/filter/detail+assetStatus; confirm before mutate; `Idempotency-Key`; AUDITOR no controls; no-read → access-denied + zero fetch. Deps: 4.1.
- [ ] 4.5 **T-4.5** GREEN `apps/web/admin/src/views/GovernanceView.vue`. AC: 4.4 green. Deps: 4.3, 4.4.
- [ ] 4.6 **T-4.6** GREEN `i18n/index.ts` + `types.ts` EN+ES governance labels. AC: no hardcoded copy. Deps: 4.5.

## Phase 5: C4 / docs / gates (PR3)

- [ ] 5.1 **T-5.1** `docs/architecture/c4/03-component.md`: `Rel(platformadmin, governance, "Admin takedown ports")`. AC: ADR-0023 follow-up. Deps: 2.8.
- [ ] 5.2 **T-5.2** Cite-only: ADR-0023 Accepted in `docs/architecture/adr/README.md`; no ADR rewrite. Deps: none.
- [ ] 5.3 **T-5.3** `just backend-check`, `just backend-bdd-fast`, `just admin-check`, `just admin-test`, `just admin-build`. AC: no new suppressions/baselines/`any`. Deps: 3.3, 4.6, 5.1.
