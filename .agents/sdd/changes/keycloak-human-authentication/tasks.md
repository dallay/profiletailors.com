# Tasks: Keycloak Human Authentication

## Review Workload Forecast

| Field | Value |
|---|---|
| Review budget | 400 changed lines |
| Estimated workload | High: backend identity/security, lifecycle, and two browser clients |
| Chained PRs recommended | Yes |
| Chain strategy | feature-branch-chain |
| Work-unit balance | Ordered slices: identity; backend BFF/security; dashboard; admin; lifecycle/cutover. Each includes its behavior tests and relevant contract updates. |
| Current slice | Unit 1: identity and consumer inventory |
| Layer boundary | trunk=`main`; parent_branch=`main`; base=`main`; branch=`feature/keycloak-auth-migration`; position=1; issue=none |

Decision needed before apply: No
Chained PRs recommended: Yes
400-line budget risk: High

## Phase 1: Identity and inventory — Work Unit 1

- [x] 1.1 Inventory every non-MCP bearer/API-key/service-account consumer and route, recording methods, credential, owner, and retirement or migration disposition; complete each action before cutover without an allowlist.
- [ ] 1.2 RED: Add identity tests for unique validated `(issuer, sub)` mapping, no email linking, fail-closed ambiguity, verified conflict-free email sync, and persistence uniqueness.
- [ ] 1.3 GREEN: Implement local identity resolution and verified-email synchronization in `server/smp/.../identity/{domain,application,infrastructure}`; preserve manual provisioning and local authorization ownership.
- [ ] 1.4 REFACTOR: Rerun unit/PostgreSQL tests; confirm identity, account, workspace, platform permission, and audit continuity.

## Phase 2: Backend BFF and security — Work Unit 2

- [ ] 2.1 RED: Test exact `pt-app`/`pt-admin` OIDC routes, safe returns, callback failure, JSON/API `401` versus OIDC browser redirect, mixed-credential rejection, and chain isolation.
- [ ] 2.2 GREEN: Implement reactive BFF, local principal resolution, and explicit security chains in `identity.infrastructure`; reject all non-MCP machine credentials and preserve MCP API-key/workspace OAuth unchanged.
- [ ] 2.3 RED: Test session TTL/cookie, `GET /api/auth/csrf` and unsafe-request/logout header, admin issuer/subject/audience/client/`acr=2`, one-OTP step-up, app-only denial at `/api/admin/**`, local authorization, and fresh-MFA critical mutations.
- [ ] 2.4 GREEN: Implement approved session, CSRF, admin LoA2, local authorization, and critical-mutation policies; verify exact Spring cookie name.
- [ ] 2.5 RED: Add Cucumber scenarios for observable login/lifecycle/cutover behavior in `server/smp/src/test/resources/features/` and matching glue under `server/smp/src/test/kotlin/.../bdd/glue/`.
- [ ] 2.6 GREEN: Retire local human login/refresh/signup/verification/recovery at hard cutover; invalidate `pt_refresh`; prevent BFF-to-local-token exchange and password-auth rollback.
- [ ] 2.7 REFACTOR: Run focused tests, `just backend-check`, `just backend-bdd-fast`, PostgreSQL tests where persistence changes, and existing architecture checks; add no duplicate architecture suite.

## Phase 3: Dashboard SPA — Work Unit 3

- [ ] 3.1 RED: Add Vitest/Playwright coverage for `pt-app` login, session hydration/logout, safe return, callback failure, no local password, and CSRF on unsafe requests.
- [ ] 3.2 GREEN: Implement dashboard BFF session and CSRF behavior in `apps/web/app/src/modules/auth/**` and `apps/web/app/src/router/index.ts`.
- [ ] 3.3 REFACTOR: Run app lint, type-check, unit tests, build, and critical Playwright flows.

## Phase 4: Admin SPA and cutover readiness — Work Units 4–5

- [ ] 4.1 RED: Add admin Vitest/Playwright tests for separate `pt-admin` login, step-up denial, one OTP, and direct `/api/admin/**` denial for app-only session.
- [ ] 4.2 GREEN: Implement admin BFF session flow in `apps/web/admin/src/stores/auth.store.ts` and `apps/web/admin/src/router/index.ts`; never treat dashboard session as admin authentication.
- [ ] 4.3 REFACTOR: Run admin check, tests, build, and critical Playwright flows for both SPAs, logout, CSRF, callback failure, and admin denial.
- [ ] 4.4 Update affected auth/lifecycle/API/deployment/operations contracts; verify no local human credential path or non-MCP machine route survives cutover and MCP contracts remain unchanged.
- [ ] 4.5 Cutover readiness only: validate Keycloak 26.3.5 clients/realm, password+OTP with absent/lower ACR and existing `pt-app` SSO, one OTP, DNS/TLS/proxy and exact callbacks, cookie name/attributes/scope, TTL, CSRF, and logout. These runtime gates do not block code implementation.
