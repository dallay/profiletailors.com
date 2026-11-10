# Tasks: Keycloak Human Authentication

## Review Workload Forecast

| Field | Value |
|---|---|
| Review budget | 400 changed lines by default; approved size exception for this change |
| Estimated workload | High: backend security/lifecycle and two browser clients |
| Chained PRs recommended | No |
| Chain strategy | size-exception |
| Work-unit balance | Work Units 1–5 are internal implementation sequencing only; they are not separate PRs. |
| Current slice | Unit 1: identity and consumer inventory |
| Delivery boundary | One PR: trunk=`main`; base=`main`; branch=`feature/keycloak-auth-migration`; issue=none |
| Size-exception approval | The user explicitly approves one PR above the default 400 changed-line budget. Rationale: the user prefers one coherent PR and accepts the review-size risk. This replaces the earlier `feature-branch-chain` strategy. |
| PR timing | Create one PR only after implementation, verification, and QA are complete. Do not create it now. |

Decision needed before apply: No
Chained PRs recommended: No
400-line budget risk: High
Delivery strategy: size-exception
Next work unit: Unit 2 — Backend BFF and security

## Phase 1: Identity and inventory — Work Unit 1

- [x] 1.1 Inventory every non-MCP bearer/API-key/service-account consumer and route, recording methods, credential, owner, and retirement or migration disposition; complete each action before cutover without an allowlist.
- [x] 1.2 RED: Test validated `(issuer, subject)`-only resolution, manual/approved links, missing/ambiguous/disabled/disallowed-link failure, and linked-principal stability when email matches another principal.
- [x] 1.3 RED: Add PostgreSQL tests for separate identity mapping to an existing `principal_id`, unchanged `principals.subject`/`principals.provider`, FK, unique issuer+subject under duplicate/concurrent inserts, and absence of `principal_id` uniqueness or cardinality assertions.
- [x] 1.4 RED: Test verified-email conflict skips sync, preserves local email and linked principal, and alone does not deny identity resolution; test unverified-email no-sync and rejection when local authentication/account eligibility fails. Existing `server/smp/src/test/resources/features/resource-preview.feature:17-22` proves valid JWT authentication is independent from per-resource denial (403 for a disallowed target scope); no new resource scenario is required.
- [x] 1.5 GREEN: Add Liquibase migration and infrastructure persistence for the separate identity-link relation, enforcing issuer+subject uniqueness and principal FK; preserve local principal fields and existing email uniqueness.
- [x] 1.6 GREEN: Implement validated-link resolution and post-link verified-email synchronization across `identity/{domain,application,infrastructure}`; preserve manual/approved provisioning and never select a principal by email.
- [x] 1.7 REFACTOR: Refactor and verify the identity model and R2DBC migration with identity unit tests, PostgreSQL persistence tests, and `just backend-check`. Session-creation gating and runtime per-resource authorization are outside Work Unit 1.

## Phase 2: Backend BFF and security — Work Unit 2

- [ ] 2.1 RED: Test client routes, safe returns, callback failure, API `401` versus browser redirect, mixed credentials, and chain isolation.
- [ ] 2.2 GREEN: Implement the reactive BFF and explicit `identity.infrastructure` security chains; reject non-MCP machine credentials and preserve MCP auth.
- [ ] 2.3 RED: Test that session creation is gated by linked-principal local authentication/account eligibility and accepts claims only from Spring Security's validated OIDC boundary; test session/cookie, CSRF including logout, admin `acr=2`/OTP, app-only admin denial, local authorization, and fresh-MFA mutations.
- [ ] 2.4 GREEN: Wire only verified claims from Spring Security's validated OIDC boundary; implement session-creation gating, CSRF, admin LoA2, local authorization, and critical-mutation policies; verify Spring cookie name.
- [ ] 2.5 RED: Add Cucumber coverage for observable login/lifecycle/cutover behavior under `server/smp/src/test/resources/features/` and matching BDD glue.
- [ ] 2.6 GREEN: Retire local human login/refresh/signup/verification/recovery at hard cutover; invalidate `pt_refresh`; prevent BFF-to-local-token exchange and password-auth rollback.
- [ ] 2.7 REFACTOR: Verify validated-claims trust wiring and session-creation gating, then run focused tests, `just backend-check`, `just backend-bdd-fast`, PostgreSQL migration/persistence verification, and existing architecture checks.

## Phase 3: Dashboard SPA — Work Unit 3

- [ ] 3.1 RED: Test `pt-app` login, session/logout, safe return, failure, no local password, and unsafe-request CSRF.
- [ ] 3.2 GREEN: Implement dashboard BFF/CSRF behavior in `apps/web/app/src/modules/auth/**` and `src/router/index.ts`.
- [ ] 3.3 REFACTOR: Run app lint, type-check, unit tests, build, and critical Playwright flows.

## Phase 4: Admin SPA and cutover readiness — Work Units 4–5

- [ ] 4.1 RED: Test separate `pt-admin` login, step-up/one-OTP, and direct admin API denial for app-only sessions.
- [ ] 4.2 GREEN: Implement admin BFF in `apps/web/admin/src/stores/auth.store.ts` and `src/router/index.ts`; dashboard state grants no admin access.
- [ ] 4.3 REFACTOR: Run admin checks/tests/build and critical app/admin browser flows.
- [ ] 4.4 Update affected auth, lifecycle, API, deployment, and operations contracts; verify hard cutover and unchanged MCP contracts.
- [ ] 4.5 Cutover gates only: validate Keycloak realm/clients/OTP, DNS/TLS/proxy callbacks, cookie/session/CSRF/logout behavior. These gates do not block code implementation.
