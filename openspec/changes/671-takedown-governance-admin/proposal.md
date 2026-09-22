# Proposal: Back Office Takedown Governance (#671)

## Intent

Workspace takedown exists (approve/reject/list, emails, `MEDIA_TAKEDOWN_*`) but is workspace-scoped, unpaged, without detail, asset-status, or `AdminAuditEvent`. Expose it via `/api/admin/**` without duplicating domain or reusing workspace handlers (403 + aggregate leak).

## Scope

### In Scope

- Governance ports/DTOs: global list/filter, detail + asset status, approve/reject
- Thin `/api/admin/**`; `PagedResult`; mutations use `Idempotency-Key` (#672, not `X-`)
- Keys `platform.governance.read` (OWNER+OPERATOR+AUDITOR) and `platform.governance.manage` (OWNER+OPERATOR). No `SUPPORT_AGENT` (reporter email PII). Do not reuse `platform.operators.read`
- Dual audit: `AdminAuditAction.TAKEDOWN_APPROVED`/`TAKEDOWN_REJECTED`; keep `MEDIA_TAKEDOWN_*`
- Asset-status reader/bridge; no duplicated media domain
- Modulith `platformadmin → governance::application` via ports/DTOs (ADR flagged for design)
- BDD + nav `governance` planned→live on `platform.governance.read` + `GovernanceView` (list/filter/detail/approve/reject + confirm); no #670 live-nav-without-route

### Out of Scope

- Duplicate domain; wrapping workspace handlers; dashboard `GovernanceTakedownView`
- Counter-notice, public DMCA, workspace path-drift, generic compliance automation
- Reopening locked permission/audit/UI/Modulith/idempotency decisions

## Capabilities

### New Capabilities

- `platform-takedown-admin`: cross-workspace list/filter/detail (asset status) and approve/reject

### Modified Capabilities

- `admin-authorization`: add `platform.governance.read`/`manage`; governance nav leaves `platform.operators.read`
- `platform-admin-audit`: add `TAKEDOWN_APPROVED`/`TAKEDOWN_REJECTED`
- `backoffice-admin-shell`: `governance` planned→live + `GovernanceView`

## Approach

Governance application ports return DTOs (never `TakedownReport`). platformadmin owns thin HTTP, authz, and admin audit. Approve/reject reuse the existing state machine and emails. Asset status via a media read port. Pagination follows #668/#670/#672.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `governance/application` | New | Admin ports/DTOs; no aggregate export |
| `platformadmin` | Modified | Permissions, audit, controller, Modulith edge |
| `apps/web/admin/.../nav-registry.ts` | Modified | `governance` live; `platform.governance.read` |
| `apps/web/admin/.../GovernanceView.vue` | New | List/filter/detail/approve/reject + confirm |
| `server/smp/.../features/` | New | Admin takedown BDD |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Modulith/hexagonal reject `platformadmin → governance` | Med | Ports/DTOs only; ADR in design |
| Reporter email PII to SUPPORT_AGENT | Low | No read grant; BDD 403 |
| Nav live without route (#670) | Low | View + route with API |
| Dual-audit drift | Med | Both audit paths in tests |
| Review size >400 lines | High | Chained PRs at tasks |

## Rollback Plan

Revert admin endpoints, permissions, nav to planned/`platform.operators.read`, and the Modulith edge. Workspace takedown unchanged.

## Dependencies

- #657 authz; #358 workspace takedown; #672 `Idempotency-Key`; #656

## Success Criteria

- [ ] OWNER/OPERATOR/AUDITOR list+filter+detail with asset status; SUPPORT_AGENT 403
- [ ] OWNER/OPERATOR approve/reject; AUDITOR 403; emails + `MEDIA_TAKEDOWN_*` still fire
- [ ] Admin audit `TAKEDOWN_APPROVED`/`TAKEDOWN_REJECTED`; mutations use `Idempotency-Key`
- [ ] `governance` nav live and routed to `GovernanceView`; BDD pass
- [ ] Workspace `/api/governance/**` handlers unchanged

## Next

sdd-spec: `platform-takedown-admin`; deltas `admin-authorization`, `platform-admin-audit`, `backoffice-admin-shell`. Locked decisions closed. ADR in design.
