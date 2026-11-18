# Tasks: Keycloak Human Authentication

## Review Workload Forecast

| Field | Value |
|---|---|
| Review budget | 400 changed lines by default; approved size exception for this change |
| Estimated workload | High: backend security/lifecycle and two browser clients |
| Chained PRs recommended | No |
| Chain strategy | size-exception |
| Work-unit balance | Work Units 1–5 are internal implementation sequencing only; they are not separate PRs. |
| Current slice | Unit 2: backend BFF and security |
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

- [x] 2.1 RED: Test client routes, safe returns, callback failure, API `401` versus browser redirect, mixed credentials, and chain isolation.
- [x] 2.2 GREEN: Implement and test the reactive BFF and explicit `identity.infrastructure` target security chains, including the post-cutover rejection behavior for non-MCP machine credentials and unchanged MCP auth. This builds and verifies the target chains; it MUST NOT activate early retirement of existing non-MCP bearer/API-key/service-account authentication. Keep existing non-MCP auth available before the hard cutover until BFF replacement, consumer remediation, and operational readiness are complete. ADR-0035 permits bounded coexistence until cutover.
- [x] 2.3 RED: Test that session creation is gated by linked-principal local authentication/account eligibility and accepts claims only from Spring Security's validated OIDC boundary; test session/cookie, CSRF including logout, admin `acr=2`/OTP, app-only admin denial, local authorization, and fresh-MFA mutations.
- [x] 2.4 GREEN: Wire only verified claims from Spring Security's validated OIDC boundary; implement session-creation gating, CSRF, admin LoA2, local authorization, and critical-mutation policies; verify Spring cookie name.
- [x] 2.5 RED: Add Cucumber coverage for observable login/lifecycle/cutover behavior under `server/smp/src/test/resources/features/` and matching BDD glue.
- [x] 2.6 GREEN: Retire local human login/refresh/signup/verification/recovery at hard cutover; invalidate `pt_refresh`; prevent BFF-to-local-token exchange and password-auth rollback.
- [x] 2.7 REFACTOR: Verify validated-claims trust wiring and session-creation gating, then run focused tests, `just backend-check`, `just backend-bdd-fast`, PostgreSQL migration/persistence verification, and existing architecture checks. The prior BDD run has six known failures; track them as a preexisting verification blocker, isolate and report them, and do not treat them as evidence for or against this feature.
- [x] 2.8 Contract resolved: implement only `POST /api/admin/users/{principalId}/identity-link` with explicit `{issuer, subject}`, required `Idempotency-Key`, `201` creation, and established Problem Details status mapping as specified in the active design/spec. No admin UI is required.
- [x] 2.9 RED: Add identity application tests for the explicitly supplied existing `principalId`, issuer, and subject; exact configured-issuer acceptance/rejection; subject validation; and nonexistent-principal rejection without mutation.
- [x] 2.10 GREEN: Implement the framework-free identity-link use case/port and identity persistence; require exact configured issuer and an existing principal, then persist only the supplied validated pair.
- [x] 2.11 REFACTOR: Verify the identity-link application and persistence boundary without adding `principal_id` uniqueness or any per-principal link-count rule; confirm `principals.subject` and `principals.provider` remain unchanged.
- [x] 2.12 RED: Test repeated and concurrent `(issuer, subject)` submissions as safe conflicts, including indistinguishable ownership behavior and no transfer, overwrite, principal mutation, or email-based resolution.
- [x] 2.13 GREEN: Map unique-key conflicts to the established non-disclosing conflict outcome; preserve existing links and principal data on every conflict.
- [x] 2.14 REFACTOR: Verify database unique `(issuer, subject)` enforcement and absence of principal cardinality enforcement with PostgreSQL persistence/concurrency tests.
- [x] 2.15 RED: Test that authorization requires local `PlatformPermission.USERS_MANAGE` and validated `pt-admin` `acr=2` plus fresh-MFA state; test authenticated success, rejection, and failure audit fields and exclusion of authentication artifacts.
- [x] 2.16 GREEN: Enforce `USERS_MANAGE` at the application boundary and admin ACR/fresh-MFA in the authorization filter; record success, rejection, and failure through existing audit contracts with a dedicated action and safe reason category.
- [x] 2.17 RED: Add Cucumber scenarios and glue for successful creation, invalid issuer/subject, missing target, missing local permission, duplicate conflict, and persistence failure/audit outcomes using approved test fixtures.
- [x] 2.18 GREEN: Wire the thin HTTP adapter and BDD flow through the approved application seam; validate required `Idempotency-Key` and apply only idempotency behavior demonstrated by the existing implementation.
- [x] 2.19 REFACTOR: Verify `400/401/403/404/409` mappings, non-disclosing identity-pair conflicts, audit contents, no mutation on rejection, and required header behavior; run focused tests, PostgreSQL tests, `just backend-bdd-fast`, and applicable architecture checks. Keep the six preexisting BDD failures visible as a verification blocker; do not attribute or conceal them by skipping tests.

## Phase 3: Dashboard SPA — Work Unit 3

- [x] 3.1 RED: Test `pt-app` login, session/logout, safe return, failure, no local password, and unsafe-request CSRF.
- [x] 3.2 GREEN: Implement dashboard BFF/CSRF behavior in `apps/web/app/src/modules/auth/**` and `src/router/index.ts`.
- [x] 3.3 REFACTOR: Run app lint, type-check, unit tests, build, and critical Playwright flows.

## Phase 4: Admin SPA and cutover readiness — Work Units 4–5

- [x] 4.1 RED: Test separate `pt-admin` login, step-up/one-OTP, and direct admin API denial for app-only sessions.
- [x] 4.2 GREEN: Implement admin BFF in `apps/web/admin/src/stores/auth.store.ts` and `src/router/index.ts`; dashboard state grants no admin access.
- [x] 4.3 REFACTOR: Run admin checks/tests/build and critical app/admin browser flows.
- [x] 4.4 Update affected auth, lifecycle, API, deployment, and operations contracts; verify hard cutover and unchanged MCP contracts.
- [ ] 4.5 Cutover gates only: validate Keycloak realm/clients/OTP, DNS/TLS/proxy callbacks, cookie/session/CSRF/logout behavior. These gates do not block code implementation.
- [ ] 4.6 HARD-CUTOVER GATE: Only after BFF replacement is implemented and verified for both browser clients, every non-MCP consumer is retired or migrated and confirmed, and all operational readiness gates pass, remove non-MCP bearer/API-key/service-account authentication. Verify no such credential authenticates any non-MCP route after cutover, legacy human credentials are invalidated, unauthenticated APIs return 401, and MCP API-key plus `workspace-scoped-oauth` behavior remains unchanged. Until this gate is authorized and completed, retain existing non-MCP auth; do not interpret 2.2 target-chain implementation as permission to disable it.
- [x] 4.7 Update applicable platform-admin API/operator documentation with the approved identity-link workflow, explicit inputs, authorization, conflicts, and audit rules. No admin UI is required by the design.
