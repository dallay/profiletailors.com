# Tasks: Private Alpha Registration Control

## Review Workload Forecast

| Field | Value |
|---|---|
| Estimated changed lines | 650–900 |
| 400-line budget risk | High |
| Chained PRs recommended | Yes |
| Suggested split | PR 1 backend → PR 2 frontend/config docs |
| Delivery strategy | stacked-prs |
| Chain strategy | stacked-to-main |

Decision needed before apply: No — stacked PRs approved
Chained PRs recommended: Yes
Chain strategy: stacked-to-main
400-line budget risk: High

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|---|---|---|---|
| 1 | Backend gate and public capability | PR 1 | Base main; focused backend tests included |
| 2 | SPA fail-closed UX and deployment docs | PR 2 | Base PR 1 branch; frontend tests/config included |

## Phase 1: Backend Configuration and Contract (PR 1)

- [x] 1.1 RED: Add binding tests proving `RegistrationConfigurationProperties.enabled` defaults `false` and explicit `true` binds from `app.identity.registration`.
- [x] 1.2 GREEN/REFACTOR: Create `RegistrationConfigurationProperties.kt`, register it in `IdentityBootstrapConfiguration.kt`, and bind `${SMP_REGISTRATION_ENABLED:false}` in `application.yaml`; run focused backend tests.
- [x] 1.3 RED: Add controller/handler tests proving disabled registration returns exact 403 Problem Details before `Mediator.send`, and enabled registration preserves dispatch and `201` behavior.
- [x] 1.4 GREEN/REFACTOR: Add `RegistrationDisabledException`, pre-dispatch gate in `LocalAuthController.kt`, and mapping in `IdentityProblemDetailsHandler.kt`; run `just backend-test-fast`.
- [x] 1.5 RED: Add `PublicCapabilitiesControllerTest.kt` cases for both states, exact one-field DTO, unauthenticated GET, and rejection of non-allow-listed public routes.
- [x] 1.6 GREEN/REFACTOR: Create `PublicCapabilitiesController.kt`/DTO and narrowly permit `GET /api/capabilities/public` in `IdentitySecurityConfiguration.kt`; rerun focused tests.
- [x] 1.7 RED: Extend `LocalAuthEndpointIntegrationTest.kt` for disabled zero persistence/event/session mutation, enabled atomic registration, and successful login/refresh in both states.
- [x] 1.8 GREEN/REFACTOR: Make only necessary wiring fixes; run `just backend-test-fast` without broad build.

## Phase 2: Frontend Capability and UX (PR 2)

- [x] 2.1 RED: Add `auth-api` and Pinia tests for typed capability loading, cached sharing, isolated auth bootstrap, and fail-closed errors.
- [x] 2.2 GREEN/REFACTOR: Add `fetchPublicCapabilities()` and `public-capabilities.store.ts`; run filtered `just frontend-test` cases.
- [x] 2.3 RED: Add router/AuthView tests for enabled registration, disabled redirect/hidden controls, capability-failure closure, and immediately usable login.
- [x] 2.4 GREEN/REFACTOR: Wire `/register`, `AuthView.vue`, and EN/ES auth copy without delaying login; rerun filtered tests, then `just frontend-test`.

## Phase 3: Configuration and SDD Verification

- [x] 3.1 RED/check: Assert deployment examples omit or set safe `false`; then document/pass `SMP_REGISTRATION_ENABLED=false` in `.env.example` and `infra/apps/smp/{production,swarm}` files without secrets.
- [x] 3.2 Verify `just backend-test-fast` and `just frontend-test`; avoid broad builds unless focused verification exposes a compilation/integration need.
- [x] 3.3 Mark checklist during apply and preserve proposal/spec/design alignment for subsequent `sdd-verify`.
