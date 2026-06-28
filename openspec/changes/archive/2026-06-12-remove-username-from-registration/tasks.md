# Tasks: Remove Username from Registration

## Review Workload Forecast

| Field                   | Value                     |
|-------------------------|---------------------------|
| Estimated changed lines | ~55–80 (mostly deletions) |
| 400-line budget risk    | Low                       |
| Chained PRs recommended | No                        |
| Suggested split         | Single PR                 |
| Delivery strategy       | single-pr                 |
| Chain strategy          | pending                   |

Decision needed before apply: No
Chained PRs recommended: No
Chain strategy: pending
400-line budget risk: Low

### Suggested Work Units

| Unit | Goal                                             | Likely PR | Notes                                    |
|------|--------------------------------------------------|-----------|------------------------------------------|
| 1    | Remove `username` from registration (all layers) | Single PR | One coherent change — no PR split needed |

## Phase 1: Backend DTO & i18n Cleanup (no deps)

- [ ] 1.1 `server/smp/.../LocalAuthController.kt` — Remove `val username: String? = null` from
  `RegisterUserRequest` data class; remove `username = request.username` from `register()` command
  call; update KDoc and `@Schema` annotations
- [ ] 1.2 `apps/web/app/src/i18n/index.ts` — Remove `auth.username` (L75) and
  `auth.usernamePlaceholder` (L78) from `en` locale; remove same keys from `es` locale (L195, L198)

## Phase 2: Frontend Core (API → Store → View)

- [ ] 2.1 `apps/web/app/src/lib/auth-api.ts` — Remove `username?: string` from `RegisterPayload`
  interface (L27); remove `username` from register function's destructured parameter if extracted
- [ ] 2.2 `apps/web/app/src/stores/auth.ts` — Remove `username?: string` from `registerWithPassword`
  payload parameter type (L155)
- [ ] 2.3 `apps/web/app/src/views/AuthView.vue` — Remove `const username = ref('')` (L16); remove
  `username: username.value || undefined` from register payload (L32); remove entire username
  `<div>` block (L111–123)

## Phase 3: Tests (after code changes)

- [ ] 3.1 `server/smp/.../LocalAuthControllerTest.kt` — Remove `username = "yuniel"` from
  `RegisterUserRequest` construction (L56) and from `RegisterUserCommand` expectation (L66)
- [ ] 3.2 `server/smp/.../LocalAuthEndpointIntegrationTest.kt` — Remove `"username" to "yuniel"` (
  L77) and `"username" to "owner"` (L197) from register payloads; update `registerAndExtract()`
  helper; update assertions checking `username` in response
- [ ] 3.3 `server/smp/.../AuthorizationBddSteps.kt` — Remove `"username" to username` from register
  body map (L487); simplify `registerLocalUser()` signature to drop username parameter (L477–478)
- [ ] 3.4 `apps/web/app/src/lib/auth-api.test.ts` — Remove `username: 'newuser'` from register test
  payload (L151) and from expected request body assertion (L162)

## Verification

- [ ] V.1 Run backend tests: `./gradlew :server:smp:test` — all green
- [ ] V.2 Run frontend tests: `pnpm -F app test` — all green
- [ ] V.3 Confirm no dead `auth.username` i18n keys remain:
  `rg "auth\.username" apps/web/app/src/i18n/` — no matches
- [ ] V.4 Confirm API response still includes `username`: integration test asserts response has
  `username` field
