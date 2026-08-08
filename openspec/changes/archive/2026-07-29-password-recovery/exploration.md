## Exploration: PR 2 frontend password recovery

### Current State
PR 1 is merged at `origin/main` and exposes `POST /api/auth/forgot-password` (202/no body) and `POST /api/auth/reset-password` (204/no body), with RFC 9457 errors carrying `status`, `detail`, and `code`. The Vue app has no recovery API functions, views, routes, validation schemas, locale namespaces, or recovery E2E coverage yet.

Authentication routes are lazy-loaded in `apps/web/app/src/router/index.ts`. The global guard hydrates the refresh-cookie session before applying `guestOnly`; therefore an authenticated visitor opening any guest-only reset URL is redirected to `/` before the reset view mounts. `App.vue` also uses an explicit auth-route name allowlist, so new recovery routes would otherwise render inside `AppShell`.

The active artifacts have drift after PR 1 reconciliation: `tasks.md` defines only PR-2.01..PR-2.06 and references obsolete paths (`auth/api/auth.ts`, `src/locales`, and `LoginView.vue`), while the requested execution map is PR-2.01..PR-2.12. `state.yaml` says `verify → archive` even though PR 2 and PR 3 remain open; `verify-report.md` correctly says not to archive.

### Affected Areas
- `apps/web/app/src/modules/auth/infrastructure/auth-api.ts` — add the two public recovery requests using `requestRaw`, preserving empty 202/204 responses and `ApiError` Problem Details.
- `apps/web/app/src/modules/auth/infrastructure/auth-api.test.ts` — test exact URLs, POST payloads, headers, empty responses, and preservation of `status`/`code`/`detail` for 400/429/503.
- `apps/web/app/src/shared/lib/validation/schemas.ts` — add dedicated forgot/reset schemas; do not reuse login validation because reset requires 12..128 characters and confirmation equality.
- `apps/web/app/src/shared/lib/validation/schemas.test.ts` — test email normalization, blank/invalid email, password boundaries, and mismatch without submitting secrets.
- `apps/web/app/src/modules/auth/presentation/ForgotPasswordView.vue` — create guest recovery request form and generic success state.
- `apps/web/app/src/modules/auth/presentation/ForgotPasswordView.spec.ts` — create component tests for validation, loading lock, generic confirmation, localized 429, and fallback errors.
- `apps/web/app/src/modules/auth/presentation/ResetPasswordView.vue` — create token-aware reset form plus missing-token, invalid-link, loading, and success states.
- `apps/web/app/src/modules/auth/presentation/ResetPasswordView.spec.ts` — create tests for blank/array query tokens, 8/128 boundaries, mismatch, duplicate submission, generic token errors, and no auto-login.
- `apps/web/app/src/modules/auth/presentation/AuthView.vue` — add a keyboard-reachable `RouterLink` to `/forgot-password` in login mode only; preserve the native form semantics added by #494.
- `apps/web/app/src/modules/auth/presentation/AuthView.spec.ts` — assert link visibility, destination, and absence in register mode without weakening current form/password-manager assertions.
- `apps/web/app/src/router/index.ts` — add named lazy routes `forgot-password` and `reset-password`, both currently specified with `meta: { guestOnly: true }`.
- `apps/web/app/src/router/index.guard.test.ts` — test guest access and the authenticated reset-link redirect explicitly.
- `apps/web/app/src/router/index.spec.ts` — assert route names/components/meta; update auth-api mocks if new imports become required.
- `apps/web/app/src/App.vue` — include both route names in the shell-bypass set, preferably through route metadata rather than extending another name list.
- `apps/web/app/src/App.test.ts` — add focused shell-bypass coverage; existing tests currently mock only the dashboard route.
- `apps/web/app/src/shared/i18n/locales/en/passwordRecovery.ts` — create all forgot/reset labels, help, errors, loading, success, and CTA copy.
- `apps/web/app/src/shared/i18n/locales/es/passwordRecovery.ts` — create complete Spanish parity, allowing longer copy to wrap.
- `apps/web/app/src/shared/i18n/locales/en/index.ts` — register the namespace.
- `apps/web/app/src/shared/i18n/locales/es/index.ts` — register the namespace.
- `apps/web/app/src/shared/i18n/i18n-keys.test.ts` — reusable parity guard already exists; add direct locale-copy assertions only if exact contractual messages need locking.
- `apps/web/app/e2e/fixtures/auth-helpers.ts` — add targeted forgot/reset route mocks with `application/problem+json`; preserve the page-level routing convention.
- `apps/web/app/e2e/fixtures/test-data.ts` — add recovery URLs and include them in `GUEST_ROUTES` if the guest-only contract remains.
- `apps/web/app/e2e/pages/password-recovery-page.ts` — create a locale-independent POM using labels, roles, ids, and stable test ids.
- `apps/web/app/e2e/specs/password-reset-frontend.spec.ts` — create the spec-owned PR 2 scenarios for request success, 429, missing/invalid token, reset success, EN/ES, mobile layout, keyboard reachability, and token non-persistence.
- `apps/web/app/e2e/specs/route-guards.spec.ts` — extend authenticated guest-route coverage to both recovery routes and preserve query-token redirect expectations.
- `openspec/changes/password-recovery/tasks.md` — artifact drift only; a later orchestration reconciliation should expand PR 2 from six coarse tasks to PR-2.01..PR-2.12 and correct paths. Do not rewrite it during implementation without approval.
- `openspec/changes/password-recovery/state.yaml` — preserve PR 1 history; do not archive. Orchestration should represent PR 2 continuation without deleting completed PR 1 verification.

### Approaches
1. **Dedicated recovery views using existing auth primitives** — create two small views that call `auth-api.ts` directly, with dedicated Zod schemas and shared visual composition based on `Card`, `Button`, `Input`, `Label`, `Alert`, `Spinner`, and Lucide status icons.
   - Pros: isolated state, no session side effects, matches `VerifyEmailView`, easiest TDD and review, no new dependency.
   - Cons: some auth-page layout/field styling may be duplicated unless a small presentational wrapper is extracted later.
   - Effort: Medium

2. **Expand `AuthView.vue` into login/register/forgot/reset modes** — drive every auth flow from route name in the existing component.
   - Pros: maximum visual reuse and one auth shell.
   - Cons: increases an already multi-mode component, couples token and success-state logic to login/registration, makes validation and tests harder, and increases regression risk around #494 semantics.
   - Effort: High

3. **Add recovery actions to the Pinia auth store** — wrap API requests in `useAuthStore` loading/error state before building dedicated views.
   - Pros: superficially consistent loading state and centralized calls.
   - Cons: recovery is intentionally unauthenticated and view-local; shared `auth.error`/`isLoading` can leak state between login and recovery, and successful reset must not mutate session state.
   - Effort: Medium

### Recommendation
Use approach 1. Keep `requestPasswordReset` and `resetPassword` as direct public functions in `auth-api.ts`; keep form status local to each view; reuse the existing `ApiError` shape and map by stable `status`/`code`, never by English `detail`. Use `AUTH_RATE_LIMIT_EXCEEDED`/429 for localized throttling, the three reset-token codes for one identical localized invalid-link state, `PASSWORD_RECOVERY_DISABLED`/503 for a localized unavailable state, and a localized generic fallback for network/unknown failures. Never render backend detail for token failures because the frontend contract forbids distinguishing invalid, expired, and used tokens.

Recommended PR-2.01..PR-2.12 TDD order:

1. **PR-2.01** RED/GREEN API client tests and functions in `auth-api.test.ts` / `auth-api.ts`.
2. **PR-2.02** RED/GREEN dedicated validation schemas and boundary tests.
3. **PR-2.03** RED/GREEN `ForgotPasswordView.spec.ts` then `ForgotPasswordView.vue`.
4. **PR-2.04** RED/GREEN `ResetPasswordView.spec.ts` then `ResetPasswordView.vue`.
5. **PR-2.05** RED/GREEN router contracts and guest-guard behavior.
6. **PR-2.06** RED/GREEN `App.vue` auth-shell bypass.
7. **PR-2.07** RED/GREEN login recovery-link coverage, then modify `AuthView.vue`.
8. **PR-2.08** Add EN/ES namespaces and run the existing key-parity test.
9. **PR-2.09** Add E2E API mocks, constants, and recovery POM.
10. **PR-2.10** Add happy/error/missing-token Playwright scenarios in `password-reset-frontend.spec.ts`.
11. **PR-2.11** Add accessibility, keyboard, mobile, EN/ES, authenticated-reset-link, and token-storage assertions.
12. **PR-2.12** Run focused gates and prepare review evidence; do not archive or open a PR without authorization.

Focused execution should use the command hub, but the current `Justfile` has a material naming bug: `just frontend-test`, `frontend-lint`, `frontend-build`, and `frontend-test-e2e` target the Astro marketing directory, not the Vue app. The valid Vue app build recipe is `just app-build`; the available E2E recipes only cover media. Until command-hub recipes are corrected, focused app tests require the underlying existing scripts (for example `pnpm --filter app test:run -- <files>` and `pnpm --filter app exec playwright test -c e2e/playwright.config.ts e2e/specs/password-reset-frontend.spec.ts`), while `just app-build` is the app-level type-check/build gate. Do not misreport the marketing recipes as PR 2 verification.

The current component library already contains all needed reusable pieces: `Button`, `Card`/`CardHeader`/`CardTitle`/`CardContent`, `Input`, `Label`, `Alert`/`AlertTitle`/`AlertDescription`, and `Spinner`; `VerifyEmailView.vue` is the closest state-card and `AuthView.vue` is the closest responsive auth-form reference. No shadcn-vue installation or dependency addition is needed.

Accessibility/responsive acceptance should require native `<form>` submission, programmatic labels, `autocomplete="email"` for forgot and `autocomplete="new-password"` for both reset fields, `aria-invalid` plus error associations, `role="alert"` for errors, `aria-live="polite"`/`output` for async success, disabled submit while pending, visible keyboard focus, minimum practical touch targets, no horizontal overflow at the existing Pixel 5 project, and wrapping Spanish text. Password visibility controls, if included, must be `type="button"`, localized, and expose `aria-pressed`.

Review forecast: approximately 650–950 changed lines including unit/E2E tests across roughly 18–24 files. That exceeds the repository's stated 400-line comfort budget but remains one coherent frontend capability with no backend or dependency changes. It still fits one PR if implementation avoids extracting a new auth design system and keeps E2E scenarios focused; split only if the diff grows beyond about 1,000 lines or route/shell refactoring becomes independently reviewable.

### Risks
- The required `guestOnly` metadata means an authenticated visitor who follows a reset email is redirected to `/` and cannot use the link. This is current spec behavior, not an implementation accident. Product should explicitly accept it or revise REQ-UI-07 before apply; silently exempting reset would violate the approved spec.
- `App.vue` has a separate route-name allowlist from router metadata; forgetting it wraps recovery screens in the authenticated shell.
- `requestRaw` currently does not send `Accept-Language`; PR 1 selects EN/ES email from that header. The forgot request must send the active locale, or Spanish UI users receive English recovery email.
- Mapping by backend `detail` would leak distinctions or break localization; map by `code`/`status` only.
- The raw token remains in browser history/address bar by design. It must not enter storage, analytics, logs, error text, snapshots, or test diagnostics; after successful reset, consider `router.replace('/reset-password/success')` only if routing design is approved, because the current spec only forbids persistence.
- Existing i18n E2E notes say auth pages cannot switch locale, while REQ-UI-14 and success criteria require EN/ES coverage. Unit tests can set i18n directly; E2E needs a preloaded `pt_settings_v1` locale before first navigation.
- `i18n-keys.test.ts` validates that referenced keys exist in the union of locales, not strict EN/ES parity for every key. New recovery locale objects need an explicit parity assertion or type-safe composition.
- Current E2E HAR replay has no recovery entries. Targeted `page.route` mocks are safer than expanding the shared HAR for this PR.
- The working tree contains unrelated untracked `server/smp/tmp/`; PR 2 must not stage it.
- OpenSpec state and task numbering are stale. Archiving now would falsely close PR 2/PR 3 and overwrite the PR 1 lifecycle narrative.

### Ready for Proposal
Yes for implementation planning, with one explicit gate: confirm whether authenticated visitors must continue to be redirected away from `/reset-password?token=...` as REQ-UI-07 currently requires. The orchestrator should also reconcile PR-2.01..PR-2.12 task granularity and correct stale frontend paths without erasing PR 1 completion/verification history.
