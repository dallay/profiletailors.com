# Apply Progress: Login Redesign

## Delivery Decision

- **Strategy**: Single PR with explicit `size:exception`
- **Approval**: Explicit maintainer/user approval; workload gate resolved
- **Scope applied**: Deterministic blockers from the latest verification report plus authorized
  login-redesign E2E CI cleanup

## Completed Blocker Fixes

- [x] `RegisterForm.vue` type-check failure fixed with an explicitly typed invalid-field/ref tuple
  while preserving focus on the actual first invalid field.
- [x] Disabled registration now maps the existing authoritative gate to HTTP 503 with
  `REGISTRATION_DISABLED`; BDD proves the request is rejected before account/session mutation.
- [x] `LocalAuthCapabilitiesBddSteps.kt` uses a raw string for the response-code assertion without
  suppression.
- [x] Login-rendering WCAG runtime checks use safe channel defaults and explicit required-element
  guards instead of non-null assertions.
- [x] Login-rendering and register-flow E2E specs conform to Biome formatting without changing
  scenarios, assertions, or coverage instrumentation.

## TDD / Regression Evidence

| Blocker                        | RED                                                                                                                       | GREEN                                                    |
|--------------------------------|---------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------|
| Register form type correctness | Existing focus regression test passed, then `just app-build` reproduced TS2532/TS2551                                     | Focus test remains green; `just app-build` passes        |
| Disabled registration contract | `just backend-bdd-fast` failed: expected 503, received 403; focused handler test failed after encoding 503/uppercase code | Focused handler test and all 128 fast BDD scenarios pass |
| Detekt raw string              | Latest verification reported `StringShouldBeRawString`; initial BDD compile retained the finding location                 | `just backend-check` passes Detekt and backend tests     |

## Commands Run

| Command                                                                                                                                      | Exit | Result                                                                                                                                                       |
|----------------------------------------------------------------------------------------------------------------------------------------------|-----:|--------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `pnpm --filter app test:run -- src/modules/auth/presentation/RegisterForm.spec.ts`                                                           |    0 | Full app suite ran because of CLI forwarding: 107 files, 1,216 passed, 1 todo                                                                                |
| `just app-build` (before fix)                                                                                                                |    1 | Reproduced TS2532/TS2551 at `RegisterForm.vue:49`                                                                                                            |
| `pnpm --filter app test:run -- src/modules/auth/presentation/RegisterForm.spec.ts`                                                           |    0 | 107 files, 1,216 passed, 1 todo; first-invalid focus preserved                                                                                               |
| `just app-build`                                                                                                                             |    0 | Vue type-check and Vite production build passed                                                                                                              |
| `just backend-bdd-fast` (before backend fix)                                                                                                 |    1 | 128 scenarios; disabled registration expected 503 but received 403                                                                                           |
| Focused `IdentityProblemDetailsHandlerTest.registration disabled maps to exact problem detail` (RED)                                         |    1 | Expected 503 contract failed against current 403 mapping                                                                                                     |
| Focused `IdentityProblemDetailsHandlerTest.registration disabled maps to exact problem detail` (GREEN)                                       |    0 | Focused regression passed                                                                                                                                    |
| `just backend-bdd-fast`                                                                                                                      |    0 | 128 scenarios passed, including no-mutation assertion                                                                                                        |
| `just backend-check`                                                                                                                         |    0 | Tests, Postgres integration, Detekt, Spotless, and coverage verification passed                                                                              |
| `just ci`                                                                                                                                    |    1 | Stopped at app Biome: login-redesign E2E formatting and non-null-assertion findings                                                                          |
| `pnpm exec biome check e2e/specs/login-rendering.spec.ts e2e/specs/register-flow.spec.ts` (RED)                                              |    1 | Reproduced five forbidden non-null assertions and formatting failures                                                                                        |
| `pnpm exec biome format --write ... && pnpm exec biome check ...`                                                                            |    0 | Both affected E2E files pass focused Biome                                                                                                                   |
| `pnpm exec playwright test -c e2e/playwright.config.ts e2e/specs/login-rendering.spec.ts e2e/specs/register-flow.spec.ts --project=chromium` |    0 | 14 scenarios passed; statements 71.4%, branches 67.24%, functions 32.49%, lines 71.56%                                                                       |
| `just ci`                                                                                                                                    |    0 | Full eight-stage pipeline passed: lint, 1,216 app tests plus 1 todo, app build, marketing coverage, backend Detekt/unit/128 BDD, and 105 marketing E2E tests |

## Remaining Gate

None. All required deterministic and repository gates pass sequentially.

## Current State

Apply is complete. `state.yaml` recommends `verify` next.
