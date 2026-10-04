# Design: Backoffice Admin Shell

## Technical Approach

Registry-driven shell: one `NAV_REGISTRY` module feeds nav rendering (`AdminLayout`), placeholder
route generation (`router/index.ts`), and i18n labels. Live routes stay untouched; planned areas
share one inert view. Guard logic is reused, not rewritten. Maps to proposal approach + both specs.

## Architecture Decisions

| Option                                                                      | Tradeoff                                                               | Decision                                                                                                                                                                                                                                                              |
|-----------------------------------------------------------------------------|------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Registry lives in `src/router/nav-registry.ts`, imported by layout + router | Tiny new module vs inline array in layout                              | **New module**: single source; one-entry extensibility; router can generate routes from it                                                                                                                                                                            |
| One shared `PlannedAreaView.vue` vs per-area stubs                          | Per-area files imply real screens; shared view is honest               | **Shared view**: static message from route meta, zero fetch, zero props beyond label key                                                                                                                                                                              |
| Generate planned child routes from registry vs hand-written routes          | Hand-written drifts from nav                                           | **Generated**: `status === 'planned'` entries become `{ path, name, component: PlannedAreaView, meta: { permission } }`; existing `beforeEach` covers them unchanged                                                                                                  |
| Planned-area gating permission                                              | No server permission exists for notifications/governance/configuration | **Reuse nearest existing key** (e.g. `platform.dashboard.read` for overview, `platform.operators.read` for governance/configuration, `platform.audit.read` for audit-adjacent); swap when real APIs land. Never invent a permission string the server doesn't enforce |
| Mirror fix in `auth.store.ts`                                               | —                                                                      | **Additive only**: append `'platform.publishing.stale.read'` to OWNER + OPERATOR arrays; no other change                                                                                                                                                              |

## Data Flow

    NAV_REGISTRY ──→ AdminLayout navItems ──(hasPermission filter)──→ sidebar
         │
         └──→ router children (planned only) ──→ beforeEach (unchanged) ──→ PlannedAreaView (static)

Guard chain stays: `isAuthenticated → hasPlatformAccess → hasPermission(meta.permission)` → login /
access-denied.

## File Changes

| File                                               | Action | Description                                                                                                               |
|----------------------------------------------------|--------|---------------------------------------------------------------------------------------------------------------------------|
| `apps/web/admin/src/router/nav-registry.ts`        | Create | `NavEntry { key, routeName, path, permission, status: 'live' \| 'planned', icon, labelKey }` + `NAV_REGISTRY`             |
| `apps/web/admin/src/router/index.ts`               | Modify | Import registry; append generated planned children; guard untouched                                                       |
| `apps/web/admin/src/layouts/AdminLayout.vue`       | Modify | Replace inline 4-item array with registry import + existing `hasPermission` filter; add `direct-invitations` via registry |
| `apps/web/admin/src/views/PlannedAreaView.vue`     | Create | Static planned message (`t('planned.message')` + area label); no fetch, no store use                                      |
| `apps/web/admin/src/stores/auth.store.ts`          | Modify | Add `platform.publishing.stale.read` to OWNER + OPERATOR                                                                  |
| `apps/web/admin/src/i18n/index.ts`, `types.ts`     | Modify | `nav.*` labels per entry + `planned.message`; EN + ES                                                                     |
| `apps/web/admin/src/router/nav-registry.spec.ts`   | Create | Registry/nav-filtering tests                                                                                              |
| `apps/web/admin/src/views/PlannedAreaView.spec.ts` | Create | Inert-render + zero-fetch tests                                                                                           |

## Interfaces / Contracts

```ts
// nav-registry.ts
import type { PlatformPermission } from '@/stores/auth.store'
export type NavStatus = 'live' | 'planned'
export interface NavEntry {
  key: string; routeName: string; path: string
  permission: PlatformPermission; status: NavStatus
  icon: string; labelKey: string // e.g. 'nav.directInvitations'
}
export const NAV_REGISTRY: readonly NavEntry[] = [/* … */]
```

`RouteMeta` already supports `permission`; planned routes add no new meta field (status resolved
from registry by `routeName`).

## Testing Strategy

| Layer              | What                                                                             | Approach                                                                |
|--------------------|----------------------------------------------------------------------------------|-------------------------------------------------------------------------|
| Unit (Vitest)      | Nav filtering per role; mirror includes `publishing.stale.read`                  | Registry + store tests, follow `DirectInvitationsView.spec.ts` pattern  |
| Unit (Vitest)      | Planned route denied without permission; renders planned message with zero fetch | Router-guard test + `PlannedAreaView` mount asserting no `request` call |
| Type-check / build | `admin-check`, `admin-build` (`just` recipes)                                    | CI evidence; no new E2E lane                                            |

## Migration / Rollout

No migration required. Rollback: `git revert` → 4-item nav; no backend/config state.

## Extensibility Guide

To add a future area: append one `NavEntry` to `NAV_REGISTRY` + two i18n labels. If
`status: 'live'`, add its real route/view; if `'planned'`, route + gating appear automatically.

Implemented as:

1. One `NavEntry` in `apps/web/admin/src/router/nav-registry.ts` (`key`, `routeName`, `path`,
   `permission`, `status`, `icon`, `labelKey`). Planned entries MUST reuse a server-enforced
   permission (nearest existing key: `platform.dashboard.read` for overview/notifications,
   `platform.operators.read` for governance/configuration); swap to the real key when its admin API
   lands.
2. Two labels (`nav.<area>`) in `apps/web/admin/src/i18n/index.ts` (`en` + `es`, typed via
   `MessageSchema.nav`).
3. Only when `status: 'live'`: a real route in `router/index.ts` children + its view.
   `status: 'planned'` entries generate their `PlannedAreaView` route automatically with the entry's
   `permission` in `meta`, covered by the unchanged `beforeEach` guard.

`AdminLayout` renders via `visibleNavEntries(authStore.hasPermission)` — no layout change per area.

## Open Questions

- [ ] Server enum has **14** keys; authz delta says 13 — confirm count and fix doc header
  accordingly
- [ ] Confirm nearest-permission mapping for notifications/governance/configuration placeholders
