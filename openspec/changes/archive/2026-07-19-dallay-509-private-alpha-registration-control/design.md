# Design: Private Alpha Registration Control

## Technical Approach

Add one fail-safe Identity infrastructure property and enforce it at the only present registration
inbound adapter, before `Mediator.send`. Project the same value through one public allow-listed
endpoint. The Vue SPA reads that capability through an isolated Pinia store: `/register` awaits it
and redirects closed, while `/login` renders immediately and only hides its registration link until
a successful enabled response.

## Architecture Decisions

| Decision              | Options / tradeoff                                                                                                                                     | Choice and rationale                                                                                                                                                                                                                                                                                                                                                                   |
|-----------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Gate placement        | Controller is narrow but does not cover hypothetical internal callers; handler policy broadens scope and couples runtime availability to the use case. | Gate at the start of `RegisterUserHandler.handle`, before user creation or side effects. The handler obtains the registration-enabled policy through a `RegistrationAvailabilityPort` implemented by `RegistrationAvailabilityAdapter`. This ensures every caller dispatching `RegisterUserCommand` observes the same constraint, not just the HTTP controller.                        |
| Configuration         | Build-time SPA flag can drift; generic config API leaks operations data.                                                                               | `RegistrationConfigurationProperties` with prefix `app.identity.registration`, field `enabled: Boolean = false`; bind YAML `enabled: ${SMP_REGISTRATION_ENABLED:false}`. Backend value is authoritative and non-secret.                                                                                                                                                                |
| Capability projection | CQRS query adds ceremony for a static infrastructure value; direct config projection is minimal.                                                       | `PublicCapabilitiesController` invokes `GetPublicCapabilitiesHandler` with a `GetPublicCapabilitiesQuery`, which delegates to `RegistrationAvailabilityPort` and returns `PublicCapabilities(registrationEnabled)`. This follows the project's CQRS pattern and keeps infrastructure concerns out of the application layer. Only `GET /api/capabilities/public` is permitted publicly. |
| Vue state             | Auth store risks coupling capability failure to session hydration; ad-hoc fetch duplicates state between router/view.                                  | Separate `public-capabilities.store.ts` with cached, shared `load()` and fail-closed `registrationEnabled`. Router loads only for `/register`; `AuthView` loads independently without delaying login.                                                                                                                                                                                  |

## Data Flow

```text
GET /api/capabilities/public -> PublicCapabilitiesController -> Mediator -> GetPublicCapabilitiesHandler -> RegistrationAvailabilityPort -> Vue capability store
POST /api/auth/register -> LocalAuthController -> Mediator -> RegisterUserHandler -> enabled? -> existing atomic workflow
                                                                         | false
                                                                         -> RegistrationDisabledException -> Problem Details 403
```

## File Changes

| File                                                                                                                                                                             | Action        | Description                                                         |
|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------|---------------------------------------------------------------------|
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/RegistrationConfigurationProperties.kt`                                                               | Create        | Typed `app.identity.registration` binding.                          |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/IdentityBootstrapConfiguration.kt`                                                                    | Modify        | Enable the properties bean.                                         |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/application/LocalAuthExceptions.kt`                                                                                  | Modify        | Add `RegistrationDisabledException`.                                |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/http/LocalAuthController.kt`                                                                          | Modify        | Pre-dispatch gate.                                                  |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/http/IdentityProblemDetailsHandler.kt`                                                                | Modify        | Stable 403 mapping.                                                 |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/http/PublicCapabilitiesController.kt`                                                                 | Create        | Minimal public GET and DTO.                                         |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/security/IdentitySecurityConfiguration.kt`                                                            | Modify        | Permit only GET capability path.                                    |
| `server/smp/src/main/resources/application.yaml`, `.env.example`, `infra/apps/smp/{production,swarm}/{.env.example,compose.yaml,stack.yaml}`                                     | Modify        | Bind/document/pass `SMP_REGISTRATION_ENABLED=false`.                |
| `server/smp/src/test/kotlin/com/profiletailors/smp/identity/infrastructure/http/{LocalAuthControllerTest,IdentityProblemDetailsHandlerTest,PublicCapabilitiesControllerTest}.kt` | Modify/Create | Gate, mapping, exact DTO tests.                                     |
| `server/smp/src/test/kotlin/com/profiletailors/smp/integration/LocalAuthEndpointIntegrationTest.kt`                                                                              | Modify        | Disabled zero-artifact plus login/refresh; enabled registration.    |
| `apps/web/app/src/modules/auth/infrastructure/auth-api.ts`                                                                                                                       | Modify        | Add typed `fetchPublicCapabilities()`.                              |
| `apps/web/app/src/modules/auth/infrastructure/public-capabilities.store.ts`                                                                                                      | Create        | Isolated cached load; errors resolve closed.                        |
| `apps/web/app/src/router/index.ts`                                                                                                                                               | Modify        | Guard `/register` without coupling `/login`.                        |
| `apps/web/app/src/modules/auth/presentation/AuthView.vue`                                                                                                                        | Modify        | Hide registration entry/form unless enabled; closed-state fallback. |
| `apps/web/app/src/shared/i18n/locales/{en,es}/auth.ts`                                                                                                                           | Modify        | Localized closed-registration copy.                                 |
| Existing auth API, router guard, store/view specs                                                                                                                                | Modify        | Enabled/disabled/error scenarios.                                   |

## Interfaces / Contracts

`GET /api/capabilities/public` returns exactly `{"registrationEnabled": boolean}`. Disabled
registration returns `403 application/problem+json` with `type=/problems/registration-disabled`,
title `Registration disabled`, status `403`, non-sensitive detail, and code `registration_disabled`.

## Testing Strategy

| Layer       | Red → green → refactor                                                                                                                                                                                                       |
|-------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Unit        | First fail controller no-dispatch, Problem Detail, exact capability DTO, API/store/view/router cases; then minimal code.                                                                                                     |
| Integration | First fail disabled HTTP zero persistence/event/session and preserved login/refresh; retain enabled `201`.                                                                                                                   |
| Commands    | Use `just backend-test-fast` after backend red/green slices and `just frontend-test` with Vitest file filtering for `auth-api.test.ts`, `AuthView.spec.ts`, and `index.guard.test.ts`; finish both commands without filters. |

## Migration / Rollout

No data migration. Missing configuration intentionally changes compatibility to
registration-disabled; operators explicitly set `SMP_REGISTRATION_ENABLED=true` where signup is
intended. Deploy backend before SPA so the capability exists; older SPA may still show signup but
backend denies safely. Rollback removes the contract/gate or explicitly enables the switch.

## Open Questions

None.
