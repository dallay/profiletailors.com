# Proposal: Login Redesign

## Intent

Replace the two-column login/register screen with focused, accessible authentication while
preserving merged password-recovery security and routing contracts. Reconcile the partial worktree
implementation.

## Scope

### In Scope

- Build a centered auth shell with focused login/register forms, responsive styling, accessible
  validation, focus, password visibility, and submission states.
- Preserve email across login/register navigation while clearing passwords and consent state; keep
  login usable while public capabilities load or fail.
- Normalize `registrationEnabled` and `passwordRecoveryEnabled` defensively; restricted routes fail
  closed and render unavailable states at the requested URL.
- Preserve forgot/reset generic errors, token privacy, and session-agnostic `/reset-password`; add
  backend capability exposure/enforcement only where the current shared configuration is not already
  authoritative.
- Use the shared dark-on-light and light-on-dark Profile Tailors logotype assets selected explicitly
  by the SPA light/dark theme, with accessible Profile Tailors text; link legal copy to existing
  marketing `/terms` and `/privacy` pages without inventing SPA routes.

### Out of Scope

- SSO fields, providers, buttons, OAuth callbacks, placeholders, or scaffolding.
- Rewriting recovery behavior, authentication protocols, registration rules, or merged backend
  hardening.
- Committing design experiments or adding dependencies.

## Capabilities

### New Capabilities

- `login-experience`: Login/register layout, state, navigation, accessibility, and branding
  contracts.
- `password-recovery-ui`: Recovery behavior, session-agnostic reset access, and in-route unavailable
  states.

### Modified Capabilities

- `public-application-capabilities`: Add allow-listed `passwordRecoveryEnabled`; remove speculative
  SSO output.
- `registration`: Preserve backend enforcement while defining in-route unavailable and non-blocking
  login behavior.
- `app-shell`: Recognize all standalone auth/recovery routes without wrapping them in the
  authenticated shell.

## Approach

Treat merged recovery behavior and existing configuration as authoritative. Remove partial
SSO/synthetic unavailable-route work, extract `AuthShell`, `LoginForm`, `RegisterForm`, and
`PasswordField`, and gate restricted route content after capability resolution. Reuse the existing
`app.identity.password-recovery` source for both advertisement and enforcement; do not create a
second flag.

## Affected Areas

| Area                                        | Impact       | Description                                    |
|---------------------------------------------|--------------|------------------------------------------------|
| `apps/web/app/src/modules/auth/`            | Modified/New | Components, capability normalization, tests    |
| `apps/web/app/src/router/index.ts`          | Modified     | Named routes and in-place gates                |
| `server/smp/.../identity/`                  | Modified     | Remove SSO projection; align capability source |
| `shared/assets/profiletailors-logotype.svg` | Reused       | Branded adaptive symbol                        |

## Migration, Risks, and Rollback

Partial files may conflict with recovery tests; SVG theming and cross-app legal URLs need browser
verification. Land contract changes together. Roll back the redesign and capability delta while
retaining merged recovery endpoints, views, and tests.

## Outcomes

- [ ] Login works during capability loading/failure; restricted routes preserve URL/query and fail
  closed.
- [ ] Authenticated users can reset passwords; tokens, emails, credentials, and enumeration details
  are never logged or persisted.
- [ ] No SSO contract/code remains; backend denies disabled registration and both recovery
  operations from authoritative configuration.
- [ ] EN/ES unit, router, BDD/integration, Playwright, responsive, dark-mode, keyboard, and
  accessibility coverage passes.
