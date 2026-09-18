## Exploration: Back Office admin shell and navigation structure

### Current State

- Admin is a **separate SPA** (`apps/web/admin`, `pt-admin.localhost` locally,
  `admin.profiletailors.com` in production), not a `/admin` path inside the
  dashboard app (`apps/web/app` has no `/admin` route — verified, no matches).
  The issue's "/admin route" therefore means the admin shell root, which today
  is `/` served by `AdminLayout.vue`.
- Shell already exists and is functional: `App.vue` renders `RouterView`;
  `src/router/index.ts` defines public `/login` + `/access-denied` and an
  auth-gated `/` parent (`AdminLayout`) with 7 child routes, each carrying a
  `meta.permission` checked in a `beforeEach` guard (`isAuthenticated` →
  `hasPlatformAccess` → `hasPermission`, else redirect to login/access-denied).
- `AdminLayout.vue` renders a sidebar whose `navItems` are filtered by
  `authStore.hasPermission(...)`. **Only 4 items are listed**: dashboard,
  waitlist, users, audit.
- Auth: `useAdminAuthStore` hydrates via `POST /api/auth/refresh` then
  `GET /api/admin/session`; mirrors a frontend `ROLE_PERMISSIONS` map for
  OWNER/OPERATOR/SUPPORT_AGENT/AUDITOR. Backend is authoritative: every
  `platformadmin` controller (`/api/admin/**`) re-resolves via
  `OperatorAccessResolver` + `effectivePermissions()` and returns 401/403 or
  throws `PlatformAccessDeniedException` (default-deny, see
  `openspec/specs/admin-authorization/spec.md`).
- Existing views: `DashboardView`, `WaitlistView`, `WaitlistEntryView`,
  `UsersView`, `UserDetailView`, `AuditView`, `DirectInvitationsView`,
  `LoginView`, `AccessDeniedView`. i18n (`en`/`es`) covers nav/auth/dashboard/
  waitlist/users/audit/operators/directInvitations/errors.
- `shared/web` exports only consent contracts; admin depends on it as a
  workspace package but consumes no admin-specific contract. No page-access
  telemetry exists in admin src (grep for track/analytics/page-view: no hits).
- Recipes exist: `just admin-test`, `just admin-check`, `just admin-build`
  (all wired into `just ci`/`ci-local`). No admin Playwright/E2E lane exists
  (E2E covers marketing + app media only).

### Affected Areas

- `apps/web/admin/src/router/index.ts` — route table + guard; where stub/seam
  routes for planned areas would be registered.
- `apps/web/admin/src/layouts/AdminLayout.vue` — sidebar nav; `direct-invitations`
  route exists but is **missing from nav**, operators has i18n but no route/view.
- `apps/web/admin/src/stores/auth.store.ts` — frontend permission mirror; drift
  vs server map (see Risks).
- `apps/web/admin/src/i18n/index.ts` + `types.ts` — nav labels for new sections
  (EN+ES) would extend `MessageSchema.nav`.
- `apps/web/admin/src/views/` — new placeholder vs full views live here.
- `server/smp/.../platformadmin/` — read-only reference; no change expected
  (shell consumes existing `/api/admin/**` APIs).
- `openspec/specs/admin-authorization/spec.md` — permission registry source of
  truth for gating alignment.

### Approaches

1. **Incremental shell hardening (recommended)** — Keep `/` shell; add the
   missing `direct-invitations` (+ `operators` if backend query suffices) nav
   entries, and permission-gated placeholder routes for planned areas
   (overview→dashboard alias, notifications, governance, configuration) that
   render an explicit "planned / not yet available" state without fetching
   data they are not authorized for.
   - Pros: smallest diff; matches "nav exposes planned areas without requiring
     every screen"; no backend change; stays within flatter admin profile.
   - Cons: placeholders must be clearly marked planned to avoid implying
     permissions; still needs design decision per stub.
   - Effort: Low
2. **Introduce a literal `/admin` path prefix** (either inside dashboard app or
   by remounting the admin SPA under `/admin`).
   - Pros: literal match to issue wording.
   - Cons: contradicts the established separate-SPA + portless topology and
     `frontend-architecture` admin profile; touches deploy/CORS/proxy config;
     dashboard feature-module rules would then apply to admin code.
   - Effort: High
3. **Build all planned screens in this change** (overview, notifications,
   governance, configuration, operators UI).
   - Pros: no stubs needed.
   - Cons: explicitly out of scope; several areas have **no admin API**
     (notifications, governance-admin, configuration); high risk of implying
     permissions the API does not enforce.
   - Effort: High

### Recommendation

Approach 1. Clarify that "/admin route" = the existing admin SPA shell root
(`/` under `AdminLayout`), not a new path in the dashboard app. Scope the
change to: (a) nav completeness for what already has APIs (add invitations;
   decide operators), (b) explicit planned-area seams with permission-gated
   placeholder routes + EN/ES labels, (c) fix the frontend/backend permission
   drift, (d) Vitest for guard/nav-filtering + type-check/build. No backend,
   no `shared/web` change, no telemetry beyond existing conventions (none).

### Risks

- **Terminology ambiguity**: "/admin route" does not exist as a path; dashboard
  app and admin SPA are separate deployments. Needs orchestrator/user
  confirmation before proposal.
- **Permission drift (confirmed)**: frontend `ROLE_PERMISSIONS` omits
  `platform.publishing.stale.read` for OWNER/OPERATOR while the server grants
  it; spec header claims "15 keys" but table lists 13 rows (missing
  `invitations.create`, `publishing.stale.read`, `invitations.read` row gap).
  Any shell work should reconcile, not widen, this gap.
- **Nav implying permissions**: rendering links for notifications/governance/
  configuration with no backing admin API risks violating the "UI must not
  imply permissions API does not enforce" rule — placeholders must be inert.
- **Missing backends**: no `/api/admin/**` endpoints found for notifications,
  governance-admin, or configuration (only user-facing `/api/governance/*`);
  full screens for those areas are blocked on API work.
- **Dependency/RFC unverifiable**: no change folder matching issue dependency
  #659 and no RFC #669 §§32/33/37/45 found in `docs/` or `openspec/`; closest
  existing work is `dallay-568-direct-invitation-admin-commands`. Proposal
  needs those inputs supplied.
- **No admin E2E lane**: acceptance Gherkin ("authorized admin opens /admin →
  shell loads + nav visible") has no Playwright home; propose Vitest +
  type-check/build evidence, or scope a new E2E lane explicitly.

### Ready for Proposal

Yes, once the orchestrator confirms: (1) "/admin" = existing admin SPA shell
root (no dashboard-app routing change); (2) which planned areas get inert
placeholder routes vs full omission in this slice; (3) supplies or waives
dependency #659 + RFC #669 sections. Otherwise proposal can proceed on
Approach 1 with the risks above recorded.
