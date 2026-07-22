## Exploration: DALLAY-509 — Private Alpha registration control

### Current State
The public Vue SPA exposes `/register` as an unconditional guest-only route. `AuthView.vue` switches between login and registration from the route name, shows links between both modes, and calls Pinia `registerWithPassword`, which delegates to `POST /api/auth/register`. There is no frontend runtime bootstrap/capability mechanism; the only dashboard runtime environment input found is `VITE_API_BASE_URL`, which is build-time configuration.

The backend `LocalAuthController.register` unconditionally dispatches `RegisterUserCommand`. `RegisterUserHandler` validates input, rejects duplicate identities, atomically creates identity, password credential, workspace, consent, and verification-token state, then publishes `UserRegistered` and issues access/refresh credentials. Spring Security permits registration, login, refresh, logout, verification, and resend endpoints publicly. Login and refresh are independent of registration and therefore can remain available to existing users.

Spring Boot already uses typed `@ConfigurationProperties` and environment-backed values in `application.yaml`, but there is no registration availability property or public application-capabilities endpoint. Existing registration/auth specs require successful registration and authoritative backend credential/session behavior; DALLAY-509 needs a delta requirement rather than weakening those contracts implicitly.

### Affected Areas
- `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/http/LocalAuthController.kt` — authoritative HTTP boundary where disabled registration can be rejected before command dispatch and before any mutation.
- `server/smp/src/main/kotlin/com/profiletailors/smp/identity/application/LocalAuthHandlers.kt` — existing registration transaction must remain unchanged and unreachable when disabled; handler-level gating is an alternative with broader coverage.
- `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/http/IdentityProblemDetailsHandler.kt` and `LocalAuthExceptions.kt` — existing Problem Details mapping is the natural place for a stable registration-unavailable response contract.
- `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/security/IdentitySecurityConfiguration.kt` — confirms `/api/auth/register` remains unauthenticated; disabling must be an availability rule, not authentication enforcement.
- `server/smp/src/main/resources/application.yaml` — environment-backed, non-secret boolean configuration belongs here, following existing typed-property patterns.
- `.env.example` and deployment environment templates — document the non-secret operator switch and safe Private Alpha value without embedding credentials.
- `server/smp/src/test/kotlin/com/profiletailors/smp/identity/infrastructure/http/LocalAuthControllerTest.kt` — focused enabled/disabled behavior and proof that disabled mode never dispatches registration.
- `server/smp/src/test/kotlin/com/profiletailors/smp/integration/LocalAuthEndpointIntegrationTest.kt` — HTTP contract, zero registration artifacts when disabled, and login for an existing user while registration is disabled.
- `apps/web/app/src/router/index.ts` — registration route is currently unconditional and has no capability guard.
- `apps/web/app/src/modules/auth/presentation/AuthView.vue` — currently always offers the registration form/link; it must reflect backend availability while preserving login.
- `apps/web/app/src/modules/auth/infrastructure/auth-api.ts` — suitable client adapter for a public, non-secret capability read if one is added.
- `apps/web/app/src/modules/auth/infrastructure/auth.store.ts` — current session bootstrap only; no application-capability state exists.
- `apps/web/app/src/modules/auth/presentation/AuthView.spec.ts` and `auth-api.test.ts` — relevant frontend tests for enabled/disabled rendering and capability contract.
- `openspec/specs/registration/spec.md`, `openspec/specs/iam/spec.md` — existing registration and authoritative-auth contracts that the proposal/spec must extend while preserving login/refresh.
- `docs/architecture/iam-platform.md` and `docs/architecture/adr/0009-jwt-and-httponly-cookie-authentication.md` — establish Identity ownership and backend-authoritative session semantics.

### Approaches
1. **Backend gate plus public capability endpoint** — Add a typed backend boolean, reject disabled `POST /api/auth/register` with a stable Problem Details response before mediator dispatch, and expose only `registrationEnabled` through a small unauthenticated runtime capability contract consumed by the SPA.
   - Pros: Backend remains authoritative; SPA always reflects the deployed backend rather than build-time assumptions; direct API calls are blocked; login/refresh remain untouched; supports separately deployed frontend/backend environments.
   - Cons: Adds one small public API contract and frontend loading/failure behavior; endpoint naming and exact response/status must be specified in proposal/spec instead of invented during exploration.
   - Effort: Medium

2. **Backend gate plus frontend build-time flag** — Enforce the backend boolean and independently use a `VITE_*` flag to hide registration UI.
   - Pros: Small frontend change; no new capability endpoint.
   - Cons: Two sources of truth can drift; requires rebuilding the SPA per environment; stale UI may advertise registration that the backend rejects; unsuitable as the preferred reflection mechanism when backend is authoritative.
   - Effort: Low

3. **Backend-only gate** — Reject registration server-side and let the existing SPA surface the error after form submission.
   - Pros: Smallest secure backend scope; direct API access is controlled.
   - Cons: Violates the requirement that the frontend reflect availability; poor Private Alpha UX; keeps `/register` and registration links visible.
   - Effort: Low

### Recommendation
Use approach 1. Define one typed, non-secret backend property under the existing `app`/identity configuration namespace, with a secure Private Alpha deployment value of disabled. Apply the gate at the authoritative registration HTTP/use-case entry before `RegisterUserCommand` is dispatched, returning a stable machine-readable Problem Details response (prefer a client-visible availability status such as `403 Forbidden`; finalize exact status/title/code in spec). Do not alter login, refresh, logout, verification, resend, credential state, or the atomic registration transaction.

Because no runtime frontend capability mechanism exists, expose the minimum public read-only capability needed by the unauthenticated auth screen; do not expose environment names, secrets, or the full Spring configuration. The SPA should load that capability, omit/disable registration entry points and redirect or render a closed-registration state for `/register`, while always preserving `/login`. Fail closed for registration UI if capability loading fails, but keep login usable. A build-time flag may be retained only as an optional emergency UX override, never as enforcement or the authoritative source.

Minimum tests should prove: enabled registration still returns `201` and dispatches/persists normally; disabled registration returns the specified Problem Details response and performs zero dispatch/mutations; an existing user can still log in and refresh while registration is disabled; the capability reports both states; the SPA shows registration only when enabled and keeps login available when disabled or capability loading fails. Configuration documentation should list the boolean, defaults/profile guidance, and examples without values that are secrets.

### Risks
- A permissive default could accidentally reopen public registration in a missed deployment; proposal/spec must explicitly choose and document default/profile behavior and deployment override.
- Frontend capability fetch failure can block login if coupled to session bootstrap; capability loading must be isolated and fail closed only for registration.
- Gating only the Vue route is bypassable; the backend check must happen before mediator dispatch and database/event/session side effects.
- Gating only the controller leaves future internal callers able to dispatch registration; the design phase should decide whether an application port/policy check is warranted while keeping the minimum change small.
- A generic configuration dump or Actuator exposure could leak operational data; expose a narrow allow-listed DTO only.
- Existing specs assert successful registration; the change requires explicit enabled/disabled delta scenarios and preservation of authentication for existing users.

### Ready for Proposal
Yes — propose the backend-authoritative typed switch, a narrow public registration capability contract because none exists today, fail-closed registration UI behavior, stable disabled-response semantics, enabled/disabled backend and frontend tests, and non-secret configuration documentation. The proposal must settle the property default and exact HTTP Problem Details contract before design/apply.
