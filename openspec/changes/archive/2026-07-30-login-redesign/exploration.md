## Exploration: login-redesign

### Current State

`main` already contains the password-recovery frontend from `15d26f77`: `AuthView.vue` links to
recovery in login mode; `ForgotPasswordView.vue` and `ResetPasswordView.vue` are dedicated
standalone views; `auth-api.ts` sends `POST /api/auth/forgot-password` with `Accept-Language` and
`POST /api/auth/reset-password`; dedicated Zod schemas, EN/ES copy, Vitest coverage, Playwright
coverage, and named recovery routes exist. `8ff6f71f` is backend hardening only (retry, failure
persistence, cleanup, audit, and telemetry) and does not redesign the frontend. It preserves the
public error/capability needs that the redesign must respect.

The committed login/register UI remains a 337-line, two-column `AuthView.vue`: promotional hero and
feature cards on the left, one shared login/register form on the right, all state and submit logic
in the route component, disabled Google/Apple placeholders, hardcoded route paths, text-based
password visibility, uppercase labels, and no Profile Tailors SVG. Switching modes currently clears
email as well as secrets. Recovery views are separate card-based components and already own their
own safe error mapping and token handling.

The worktree is not a clean `main` baseline. `HEAD` is behind current `main` by `8d648037` and
carries a partial login-redesign delta relative to `main`; it also has untracked implementation
files. The on-disk partial work expands public capabilities to `registrationEnabled`,
`passwordRecoveryEnabled`, and `ssoProviders`; adds fail-closed store behavior and unavailable
screens; and adds router guards. This is not a complete or internally consistent realization of the
plan: it conflicts with the plan's explicit no-SSO-scaffolding rule, uses redirect-only unavailable
routes, does not gate `/reset-password`, and introduces duplicated tests and stale mock property
names.

Public routes/named routes currently present are `login` (`/login`, guest-only, standalone),
`register` (`/register`, guest-only, standalone), `forgot-password` (`/forgot-password`, guest-only,
standalone), `reset-password` (`/reset-password`, standalone and intentionally accessible to
authenticated users), and `verify-email` (`/verify-email`, standalone). The partial branch also adds
`registration-unavailable` and `password-recovery-unavailable`. There are no `terms` or `privacy`
routes, so the plan's named legal links cannot yet be implemented as written.

The shared assets are icon-only 528×584 SVGs, not horizontal wordmarks despite the `logotype`
filenames. `profiletailors-logotype.svg` is adaptive (black by default, white under
`prefers-color-scheme: dark`); `profiletailors-logotype-light.svg` is fixed-path artwork without the
adaptive style. Neither is used by the SPA. The adaptive asset is the safer default for an `<img>`
only if browser SVG media-query behavior is accepted and tested; otherwise explicit theme selection
is more deterministic.

Existing tests include `AuthView.spec.ts`, forgot/reset component specs, auth API tests, two
public-capabilities store test files in the partial worktree, router contract/real-guard tests,
`App.test.ts` standalone-shell coverage, and Playwright suites for login rendering/validation/API
behavior, registration, route guards, and a 559-line password-recovery flow. Current login E2E
assertions lock the old hero/feature-card UI, while recovery tests lock generic confirmation, safe
error mapping, authenticated reset-link access, locale behavior, mobile/keyboard behavior, and token
non-persistence. No login/register visual-regression snapshots or axe suite exist.

### Affected Areas

- `apps/web/app/src/modules/auth/presentation/AuthView.vue` — replace the monolithic two-column
  login/register implementation with a route orchestrator while preserving redirect and capability
  behavior.
- `apps/web/app/src/modules/auth/presentation/ForgotPasswordView.vue` — preserve recovery semantics
  from `15d26f77`; optionally adopt only the shared outer shell.
- `apps/web/app/src/modules/auth/presentation/ResetPasswordView.vue` — preserve
  token/error/session-agnostic behavior and add the missing capability gate without making it
  guest-only.
- `apps/web/app/src/modules/auth/presentation/AuthView.spec.ts` — rewrite old-structure assertions
  and add capability, state-ownership, focus, loading, and named-route contracts.
- `apps/web/app/src/modules/auth/infrastructure/public-capabilities.store.ts` — reconcile the
  partial expansion, remove speculative SSO, validate booleans by runtime type, and provide
  resolved/retry semantics.
- `apps/web/app/src/modules/auth/infrastructure/auth-api.ts` — keep recovery requests unchanged
  while making the public capability response untrusted/partial at the infrastructure boundary.
- `apps/web/app/src/router/index.ts` — retain named auth/recovery routes, preserve authenticated
  reset access, add capability-aware rendering for both recovery routes, and resolve missing legal
  routes.
- `apps/web/app/src/router/index.guard.test.ts` and `apps/web/app/src/router/index.spec.ts` —
  protect the recovery route contract introduced by `15d26f77` and test capability behavior against
  the real router.
- `apps/web/app/src/modules/auth/presentation/ForgotPasswordView.spec.ts` and
  `ResetPasswordView.spec.ts` — regression boundary for generic errors, disabled recovery, duplicate
  submission, and token safety.
- `apps/web/app/e2e/specs/login-*.spec.ts` and `register-flow.spec.ts` — currently encode the old
  hero layout and conflicting autocomplete/state expectations; must be realigned.
- `apps/web/app/e2e/specs/password-reset-frontend.spec.ts` and `route-guards.spec.ts` — preserve
  merged recovery behavior, especially authenticated reset-link access and token privacy.
- `server/smp/src/main/kotlin/com/profiletailors/smp/identity/application/PublicCapabilities.kt` and
  `infrastructure/http/PublicCapabilitiesController.kt` — partial branch expansion exists, but SSO
  types violate scope and the password-recovery flag must use the same effective configuration as
  endpoint enforcement.
- `shared/assets/profiletailors-logotype.svg` and
  `shared/assets/profiletailors-logotype-light.svg` — candidate icon assets; neither is currently
  integrated into the SPA.
- `tmp/plans/2026-07-29-login-redesign-design.md` — useful design input but not an approved OpenSpec
  contract; several statements conflict with current routes, assets, and recovery behavior.

### Approaches

1. **Reconcile the partial branch, then extract a shared auth shell and focused forms** — keep the
   merged recovery flows intact, remove speculative SSO artifacts, normalize capabilities, implement
   capability-aware wrappers in-place, and split login/register state into `LoginForm` and
   `RegisterForm` under `AuthShell`.
    - Pros: Matches the intended architecture; limits sensitive state lifetime; preserves email
      across modes; gives recovery a reusable outer shell; allows focused TDD; minimizes regression
      risk to `15d26f77`.
    - Cons: Requires careful cleanup of partial/untracked work and broad test updates; legal
      routes/assets need explicit product decisions.
    - Effort: High

2. **Visually restyle the existing monolithic `AuthView.vue`** — remove the hero, add the SVG and
   centered layout, but keep one component and existing route/state model.
    - Pros: Smaller production diff and quicker visual result; fewer new components.
    - Cons: Retains mixed login/register responsibilities, makes capability/focus/loading logic
      harder, cannot naturally preserve only email while clearing secrets, and compounds an already
      large component.
    - Effort: Medium

3. **Create a unified auth-flow component for login, register, forgot, and reset** — move all public
   auth modes under one route-driven component.
    - Pros: Maximum visual consistency and one shell.
    - Cons: Reverses the successful recovery separation from `15d26f77`, couples reset tokens to
      session forms, increases security and regression risk, and creates a complex multi-mode state
      machine.
    - Effort: High

### Recommendation

Use approach 1, but the proposal must begin with a reconciliation rule: treat current `main` plus
the merged recovery contracts as authoritative, review the partial worktree changes rather than
assuming they are accepted, and delete out-of-scope SSO types/placeholders. Keep
`ForgotPasswordView` and `ResetPasswordView` behavior isolated; share only `AuthShell`, legal links,
and reusable password-field presentation where appropriate.

Implement unavailable states inside `/register`, `/forgot-password`, and `/reset-password` through
route components/wrappers, not redirects to synthetic URLs, so the requested URL and reset token
remain stable. `/reset-password` must remain session-agnostic because the merged tests intentionally
allow an authenticated user to use an emailed reset capability. Login must render without waiting
for capabilities; restricted routes must wait for resolution and fail closed.

Before proposal approval, resolve two product-contract gaps: define actual `terms` and `privacy`
routes (or remove the named-route requirement), and choose the logo integration strategy. The
adaptive `profiletailors-logotype.svg` best matches light/dark requirements, but it is a tall icon,
not a wordmark, and needs an accessible text alternative plus visual verification.

### Risks

- The worktree contains pre-existing partial and untracked implementation. Applying later phases
  without first inventorying ownership could overwrite work or accidentally commit `login.fig`,
  `login-design.fig`, or contradictory experiments.
- The partial public capability model introduces `ssoProviders` and the UI already has disabled
  Google/Apple controls, directly violating the plan's no-SSO-scaffolding boundary.
- The partial router guards redirect to synthetic unavailable URLs and omit `/reset-password`; this
  loses requested-route context and leaves reset available when the capability is disabled.
- Making reset guest-only would regress the merged recovery contract: authenticated users are
  deliberately allowed to open `/reset-password?token=...`.
- Backend capability defaults/configuration are inconsistent in the partial work (
  `profile-tailors.features...` defaults true) versus the plan's fail-closed proposal and the
  existing `app.identity.password-recovery` enforcement source; two flags could advertise one state
  and enforce another.
- `AuthView.spec.ts` mocks `capabilityChecked` while on-disk code reads `capabilitiesLoaded`;
  existing tests may be false confidence or fail until reconciled. Duplicate
  `public-capabilities.store.test.ts`/`.spec.ts` suites add drift.
- Existing E2E tests assert the old hero, feature cards, cleared email, and conflicting autocomplete
  values; redesign requires intentional contract updates, not blanket snapshot replacement.
- `terms` and `privacy` named routes do not exist, while the current registration links are plain
  anchors to unresolved SPA paths.
- The adaptive SVG depends on `prefers-color-scheme`, which may diverge from an app-level theme
  override; the fixed light SVG can become invisible on light surfaces if selected incorrectly.
- Recovery privacy constraints from `15d26f77`/`8ff6f71f` remain strict: never log or persist reset
  tokens, raw email, credentials, backend enumeration details, or sensitive telemetry.

### Ready for Proposal

Yes, with explicit proposal decisions to: preserve merged recovery behavior; reconcile and
selectively discard the partial worktree implementation; remove SSO scaffolding; keep authenticated
reset links usable; gate disabled capabilities in-place; define legal routes; and adopt/test one of
the provided SVG icons rather than assuming it is a horizontal logotype.
