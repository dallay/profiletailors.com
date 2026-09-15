# Proposal: Backoffice Admin Shell

## Intent

Admin nav is incomplete: `direct-invitations` route has no sidebar entry; planned areas (overview, notifications, governance, configuration) have no seams. Complete nav without building full screens. `/admin` = existing admin SPA shell root (`/` under `AdminLayout`, `apps/web/admin`); no dashboard-app change.

## Scope

### In Scope
- Missing `direct-invitations` nav entry (API exists)
- Extensible nav registry: adding a future area = one entry
- Inert placeholder seams for overview/users/waitlist/invitations/notifications/governance/configuration/audit: explicit "planned", permission-gated, zero fetch
- Permission-mirror reconciliation (`publishing.stale.read` drift; spec "15 keys" vs 13 rows)
- i18n EN/ES labels; Vitest for guard/nav-filtering + `admin-check`/`admin-build`

### Out of Scope
- Full screens for planned areas (no admin APIs for notifications/governance/configuration)
- Literal `/admin` prefix; backend, `shared/web`, telemetry, vanity analytics changes

## Capabilities

### New Capabilities
- `backoffice-admin-shell`: shell layout, nav registry, routing seams, inert placeholders

### Modified Capabilities
- `admin-authorization`: fix registry doc drift + frontend mirror alignment; enforcement unchanged (server authoritative)

## Approach

- Central nav registry: `{ key, route, permission, status: live|planned }`; `AdminLayout` renders from it, filtered by `hasPermission`
- One shared `PlannedAreaView`: static message only, no fetch, no implied permissions
- Frontend gating additive only; server (`OperatorAccessResolver`, default-deny) authoritative per #659
- Flatter admin profile (views/stores/layouts/router/i18n); may consume `@profiletailors/shared-web`

## Alternatives

- **Literal `/admin` prefix**: rejected — breaks separate-SPA + portless topology; deploy/CORS/proxy churn; high effort
- **Build all screens now**: rejected — out of scope; missing backends would imply unenforced permissions

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `apps/web/admin/src/router/` | Modified | nav registry + placeholder routes |
| `apps/web/admin/src/layouts/AdminLayout.vue` | Modified | nav rendered from registry |
| `apps/web/admin/src/views/` | New | shared inert `PlannedAreaView` |
| `apps/web/admin/src/i18n/` | Modified | EN/ES labels |
| `apps/web/admin/src/stores/auth.store.ts` | Modified | mirror drift fix |
| `server/.../platformadmin/` | None | read-only reference |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Nav implies unenforced permissions | Med | inert, gated, zero-fetch placeholders |
| Drift widens | Low | reconcile mirror in same change |
| Scope creep to full screens | Med | hard out-of-scope list |
| No admin E2E lane | Med | Vitest + type-check/build evidence |

## Rollback Plan

`git revert`; shell returns to 4-item nav. No migrations, backend state, or config change.

## Dependencies

- #659 (admin authorization boundary) — satisfied constraint: server default-deny authoritative; frontend additive only

## Success Criteria

- [ ] Shell root renders layout + nav for authorized admin
- [ ] `direct-invitations` in nav; planned areas show planned state, no fetch
- [ ] Frontend gating matches server authz; `admin-check` + `admin-build` pass
