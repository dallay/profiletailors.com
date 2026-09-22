# Design: Back Office Takedown Governance (#671)

## Technical Approach

Cross-workspace takedown on `/api/admin/**` without touching workspace handlers.
`governance.application` exposes query/command ports returning DTOs (never `TakedownReport`).
`platformadmin.application` handlers use `OperatorAccessResolver`,
`ConfigurationIdempotencyService`, and `AdminAuditEvent`. HTTP is thin
`AdminTakedownController`. Asset status via a read port in `config/bridges` (same seam as
`MediaAssetStatusUpdater`). Specs run in parallel (`platform-takedown-admin`; deltas
`admin-authorization`, `platform-admin-audit`, `backoffice-admin-shell`).

## Architecture Decisions

| Decision | Options | Tradeoff | Choice |
|----------|---------|----------|--------|
| Cross-module edge | Reuse handlers / events-only / ports+DTOs | Handlers are `internal`, need `ResourceContext`, return aggregate | Ports+DTOs; ADR-0023 |
| Approve/reject | `ApproveTakedownHandler` / new admin commands | 403 + aggregate leak | New command port; reuse domain `approve`/`dismiss` |
| Authz | `platform.operators.read` / new keys | Reporter email is PII | `platform.governance.read` / `.manage` |
| Audit | Rename MEDIA_* / admin-only / dual | Workspace emails must keep firing | Dual: keep `MEDIA_TAKEDOWN_*`; add `TAKEDOWN_APPROVED`/`REJECTED` |
| Asset status | Duplicate media / reader bridge | No media domain in platformadmin | `MediaAssetStatusReader` in `config/bridges` |
| Idempotency | `X-Idempotency-Key` / `Idempotency-Key` | #672 | `Idempotency-Key` + `ConfigurationIdempotencyService` |
| Pagination | Unpaged Flow / `PagedResult` | #668/#670/#672 | `PagedResult`, `ADMIN_PAGE_MAX_SIZE` |

## Components

```
GovernanceView
  → AdminTakedownController (/api/admin/takedown-reports)
      → AdminTakedownHandlers (authz, idempotency, AdminAuditEvent)
          → AdminTakedownQueryPort / AdminTakedownCommandPort (DTOs)
              → TakedownReportRepository (global get + paged list)
              → TakedownReport.approve / dismiss
              → MediaAssetStatusUpdater (approve → SUSPENDED)
              → MediaAssetStatusReader (detail only)
              → AuditHook MEDIA_TAKEDOWN_* + TakedownApproved/Rejected emails
```

Workspace `/api/governance/takedown/**` unchanged.

## Data Flow

```
GET list → read  → QueryPort.list → PagedResult (no asset status; avoid N+1)
GET id   → read  → QueryPort.get  → DTO + assetStatus
POST approve → manage → Idempotency-Key → CommandPort.approve
           → APPROVED → SUSPENDED → MEDIA_TAKEDOWN_APPROVED
           → TakedownApproved email → AdminAudit TAKEDOWN_APPROVED
POST reject  → dismiss + reason; asset unchanged; MEDIA_TAKEDOWN_REJECTED
```

## Data Model

No new tables. Reuse `takedown_reports`, `platform_admin_audit_events`, configuration
idempotency store. Extend `TakedownReportRepository` with `findByReportId` and paged
`findAll(status?, workspaceId?, page, size)` + count.

DTO (status String, no domain enums): `reportId`, `workspaceId`, `assetId`, `reportedById`,
`reason`, `status`, `rejectionReason`, `reviewedById`, `reviewedAt`, `reporterEmail`,
`mediaReferenceUrl`, `createdAt`, `updatedAt`, `assetStatus` (detail only).
Status: `REPORTED` \| `APPROVED` \| `DISMISSED` \| `SUSPENDED`.

## API Contract

`Accept: application/vnd.api.v1+json`. Admin session. No `X-Workspace-Id`.

| Method | Path | Perm | Notes |
|--------|------|------|-------|
| GET | `/api/admin/takedown-reports?status&workspaceId&page=0&size=25` | read | `PagedResult`; size ≤ 100 |
| GET | `/api/admin/takedown-reports/{reportId}` | read | 404 if missing |
| POST | `/api/admin/takedown-reports/{reportId}/approve` | manage | `Idempotency-Key` |
| POST | `/api/admin/takedown-reports/{reportId}/reject` | manage | `{rejectionReason}` + `Idempotency-Key` |

401 none; 403 `PLATFORM_ACCESS_DENIED`; 404 not found; 409 not reviewable / idempotency;
400 validation. Map `ConfigurationIdempotency*` to 409. Do not reuse
`TakedownReportNotFoundException` (`IllegalArgumentException` → 400); new admin 404/409 types.

## Security

| Role | read | manage |
|------|:----:|:------:|
| OWNER / OPERATOR | ✓ | ✓ |
| AUDITOR | ✓ | — |
| SUPPORT_AGENT | — | — |

Reporter email is PII; BDD 403 for SUPPORT_AGENT. Nav must not use `platform.operators.read`.
Dual audit is success-only (no `TAKEDOWN_REJECTED` for 403). Existing audit `redact()` applies.

## File Changes

Create: `governance/application/AdminTakedownPorts.kt`, `MediaAssetStatusReader.kt`,
`config/bridges/MediaAssetStatusReaderDelegate.kt`,
`platformadmin/application/handler/AdminTakedownHandlers.kt`,
`platformadmin/infrastructure/http/AdminTakedownController.kt`,
`apps/web/admin/src/views/GovernanceView.vue`,
`features/platformadmin/takedown-admin.feature`, ADR-0023.

Modify: `TakedownReportRepository` + R2DBC; `platformadmin/ModuleMetadata.kt`
(`governance :: application`); `PlatformPermission.kt`; `AdminAuditEvent.kt`;
`AdminProblemDetailsHandler.kt`; `nav-registry.ts` (live + `platform.governance.read`);
`router/index.ts` (`GovernanceView` + `governance/:reportId`); `auth.store.ts`;
`i18n/index.ts` + `types.ts`; ADR README; `docs/architecture/c4/03-component.md`.

No deletes. Workspace takedown files untouched.

## Testing Strategy

| Layer | What | Approach |
|-------|------|----------|
| Unit | Ports, handlers, permissions, confirm cancel | JUnit/MockK; Vitest |
| Integration | R2DBC paged/global; 401/403/404/409; idempotency | WebTestClient |
| Architecture | Only `governance :: application`; no `TakedownReport` | `ModularityVerificationTest` |
| BDD | `takedown-admin.feature` `@smoke @fast @postgres` | Role matrix; emails + MEDIA_*; admin audit; `Idempotency-Key` |
| Frontend | Nav live; not `PlannedAreaView`; i18n; confirm | Vitest |

TDD. Gates: `just backend-check`, `just backend-bdd-fast`, admin lint/type-check/test/build.

## Migration / Rollout

No schema migration. Additive permissions. Rollback: revert admin endpoints, keys, nav to
planned/`platform.operators.read`, Modulith edge. Workspace path unchanged.

## Risks

| Risk | Mitigation |
|------|------------|
| Modulith/hexagonal reject | ADR-0023; ports/DTOs only |
| Review >400 lines | Chained PRs at tasks |
| Dual-audit drift | BDD asserts both paths |
| Nav live without route (#670) | Explicit `GovernanceView` same change |
| Existing not-found is 400 | New admin exceptions → 404/409 |

## Open Questions

None blocking. Parallel specs may refine filters; locked decisions stay closed.
