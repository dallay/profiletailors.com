# Design: Login Redesign

## Technical Approach

Reconcile the partial worktree against merged password-recovery behavior, then make `AuthView.vue` a
route orchestrator over focused forms and an `AuthShell`. Login renders immediately while
capabilities load; registration and both recovery routes use in-place capability gates that preserve
URL/query state. Backend advertisement and enforcement read the same existing configuration sources.
No authentication protocol or recovery semantics change.

## Architecture Decisions

| Decision              | Options / tradeoff                                                                                                  | Choice and rationale                                                                                                                                                                                                                                                                                                                                                     |
|-----------------------|---------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Component boundaries  | Keep monolith (smaller diff, coupled state); unify all auth modes (high token/session coupling); focused components | `AuthShell`, `LoginForm`, `RegisterForm`, `PasswordField`, `AuthLegalLinks`, with `AuthView` orchestrating login/register. Recovery shares only shell/presentation where safe, preserving merged isolation.                                                                                                                                                              |
| Sensitive state       | Parent/store persistence vs form-local state                                                                        | `AuthView` owns only email. Each form owns passwords, confirmation, consent, visibility, errors, and pending state; route-mode unmount clears secrets naturally. Tokens remain query-only in `ResetPasswordView`, never store/localStorage/logs.                                                                                                                         |
| Capability gating     | Redirect guards vs in-route gates                                                                                   | Remove synthetic unavailable routes and capability redirects. Route components wait, then render form or unavailable state at the original URL. Login never waits. Reset remains non-`guestOnly`, so authenticated emailed-token use works.                                                                                                                              |
| Capability state      | Trust DTO vs normalize at boundary                                                                                  | API returns a partial/untrusted DTO; Pinia normalizes exact booleans to `{registrationEnabled:false,passwordRecoveryEnabled:false}`, deduplicates loads, exposes `resolved/loading/error`, and supports retry. Restricted features fail closed.                                                                                                                          |
| Backend configuration | Add `profile-tailors.features.*` flags vs reuse enforcement config                                                  | Use `app.identity.registration` and `app.identity.password-recovery` for both command enforcement and `GET /api/capabilities/public`. Remove the duplicate adapter/property and all `ssoProviders` types/fields. This prevents advertised/enforced drift.                                                                                                                |
| Legal/assets/theme    | SPA legal routes/new asset vs existing marketing pages/shared assets                                                | `AuthLegalLinks` uses locale-aware external marketing `/terms/`, `/privacy/` paths (not invented SPA routes). Render the shared dark-on-light and light-on-dark Profile Tailors logotype assets according to the SPA-selected light/dark theme, pair them with accessible Profile Tailors text, and verify correct behavior even when the OS color preference disagrees. |

## Data Flow

```text
/login ── render LoginForm ── POST /api/auth/login
   └── async capabilities ── normalize/store ── reveal optional links

/register|forgot|reset ── load/dedupe ── resolved?
                                      ├─ enabled ── form ── backend enforcement
                                      └─ false/error ── in-route unavailable state
/reset-password?token=T ── gate (session-independent) ── ResetPasswordView ── POST reset
```

## File Changes

| File                                                                                                             | Action | Description                                                                                          |
|------------------------------------------------------------------------------------------------------------------|--------|------------------------------------------------------------------------------------------------------|
| `apps/web/app/src/modules/auth/presentation/{AuthShell,LoginForm,RegisterForm,PasswordField,AuthLegalLinks}.vue` | Create | Focused layout, forms, reusable password input, external legal links.                                |
| `apps/web/app/src/modules/auth/presentation/AuthView.vue`                                                        | Modify | Route orchestration, non-sensitive email ownership, non-blocking capability load.                    |
| `apps/web/app/src/modules/auth/presentation/{ForgotPasswordView,ResetPasswordView}.vue`                          | Modify | In-place gates/shared shell; preserve generic errors and token handling.                             |
| `apps/web/app/src/modules/auth/presentation/{RegistrationUnavailable,PasswordRecoveryUnavailable}.vue`           | Modify | Reusable in-route states with named login navigation.                                                |
| `apps/web/app/src/modules/auth/domain/public-capabilities.ts`                                                    | Modify | Two-boolean normalized contract; delete SSO types.                                                   |
| `apps/web/app/src/modules/auth/infrastructure/{auth-api,public-capabilities.store}.ts`                           | Modify | Untrusted DTO, strict normalization, resolved/retry semantics; delete SSO.                           |
| `apps/web/app/src/router/index.ts`                                                                               | Modify | Remove synthetic routes/redirect gates; preserve named standalone routes and session-agnostic reset. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/application/PublicCapabilities.kt`                   | Modify | Two-boolean query result; no SSO projection.                                                         |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/http/PublicCapabilitiesController.kt` | Modify | Two-boolean public response.                                                                         |
| `server/smp/.../ConfigBasedPasswordRecoveryAvailability.kt` and `PasswordRecoveryAvailabilityPort.kt`            | Delete | Partial duplicate configuration abstraction; project existing properties into the query instead.     |
| Existing frontend/backend auth tests and `server/smp/src/test/resources/features/local-auth.feature`             | Modify | Replace stale layout/SSO/redirect expectations; add capability enforcement scenarios.                |

## Interfaces / Contracts

`GET /api/capabilities/public` returns only required booleans:

```json
{"registrationEnabled":true,"passwordRecoveryEnabled":true}
```

Malformed or missing frontend fields normalize to `false`. Backend disabled recovery rejects both
request and completion without creating/consuming tokens or changing credentials.

## Testing Strategy

| Layer           | What to Test                                                                                                              | Approach                                                                                                                                                   |
|-----------------|---------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Unit            | Forms, focus/ARIA, password toggle, state clearing, store normalization/dedupe/retry, backend mapping/config              | TDD: failing Vitest/JUnit test before each production change; consolidate duplicate store suites.                                                          |
| Integration/BDD | Capability endpoint and disabled registration/request/reset enforcement                                                   | WebFlux tests plus mandatory `local-auth.feature` scenarios and BDD glue; assert reset tokens remain unconsumed.                                           |
| E2E             | EN/ES login/register/recovery, authenticated reset, loading/failure gates, keyboard, 320px, dark theme, asset/legal links | Update Playwright contracts while retaining merged privacy and generic-error scenarios; add targeted accessibility/visual checks without a new dependency. |

## Migration / Rollout

Inventory partial/untracked files first; retain merged recovery code/tests, selectively replace
partial capability/router work, delete SSO and duplicate-config scaffolding, then land backend
contract and frontend normalization together. Do not commit `.fig` experiments. No data migration or
feature flag addition is required; rollback restores the old login UI/capability projection while
retaining recovery.

## Open Questions

None.
