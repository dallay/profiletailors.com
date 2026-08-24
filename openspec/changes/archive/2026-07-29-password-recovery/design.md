# Design: Password Recovery

## Technical Approach

Preserve PR 1’s backend contracts and PR 3’s additive hardening seams. PR 2 adds two isolated Vue
recovery views using existing auth UI primitives, direct functions in `auth-api.ts`, dedicated Zod
schemas, EN/ES resources, and focused Vitest/Playwright coverage. Recovery never writes the raw
query token to browser storage, analytics, logs, or shared auth state.

## Architecture Decisions

| Decision            | Options / tradeoff                                                             | Choice and rationale                                                                                                                                                                                                                                                                                                          |
|---------------------|--------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Route access        | Mark both `guestOnly`; exempt reset in guard; model capabilities independently | `/forgot-password` gets `guestOnly: true`; `/reset-password` omits both `guestOnly` and `requiresAuth`. The reset token is the capability, so either session state may reach it. The existing guard remains fail-closed: protected routes still require auth and only actual `guestOnly` routes redirect authenticated users. |
| Shell selection     | Route-name allowlist vs metadata                                               | Add `meta: { standalone: true }` to both recovery routes (and migrate the existing auth routes in the same focused change); `App.vue` renders `RouterView` when `route.meta.standalone === true`, otherwise `AppShell`. This prevents recovery from bootstrapping workspace UI without growing a second route list.           |
| State/API ownership | Pinia actions vs view-local state                                              | Views call `requestPasswordReset`/`resetPassword` directly and own pending/error/success state. Recovery must not mutate authentication state or auto-login.                                                                                                                                                                  |
| Error mapping       | Backend detail vs stable contract                                              | Map `ApiError.status`/`code`: 429 or `AUTH_RATE_LIMIT_EXCEEDED` → throttled; token codes `INVALID_PASSWORD_RESET_TOKEN`, `EXPIRED_PASSWORD_RESET_TOKEN`, `USED_PASSWORD_RESET_TOKEN` → one identical invalid-link state; 503/recovery-disabled → unavailable; otherwise generic. Never render token-error detail.             |
| Localization header | Omit locale vs propagate active locale                                         | Recovery API functions attach `Accept-Language` from the active `vue-i18n` locale while `requestRaw` preserves the versioned `Accept`. This lets PR 1 select the email language without involving the auth store.                                                                                                             |

## Data Flow

```text
Login AuthView ─RouterLink→ /forgot-password [guestOnly, standalone]
  ForgotPasswordView → validate → requestPasswordReset(email, active locale)
                     → 202 → generic confirmation

email URL → /reset-password?token=… [standalone, session-agnostic]
  ResetPasswordView → read token in memory → validate → resetPassword(...)
                    → PR 1 atomically changes password + revokes refresh sessions
                    → 204 → success state → explicit /login link (no auto-login)
```

## File Changes

| File                                                                                    | Action        | PR 2 responsibility                                                                                 |
|-----------------------------------------------------------------------------------------|---------------|-----------------------------------------------------------------------------------------------------|
| `apps/web/app/src/modules/auth/infrastructure/auth-api.ts`                              | Modify        | Add void recovery calls, locale header, preserve empty 202/204 and `ApiError`.                      |
| `apps/web/app/src/modules/auth/presentation/{ForgotPasswordView,ResetPasswordView}.vue` | Create        | Accessible responsive forms and terminal states; token remains view-local.                          |
| `apps/web/app/src/modules/auth/presentation/AuthView.vue`                               | Modify        | Login-only keyboard-reachable forgot-password `RouterLink`; preserve current native form semantics. |
| `apps/web/app/src/shared/lib/validation/schemas.ts`                                     | Modify        | Dedicated normalized email and 12..128/matching-password schemas.                                   |
| `apps/web/app/src/router/index.ts`, `apps/web/app/src/App.vue`                          | Modify        | Lazy routes, independent access metadata, metadata-driven shell bypass.                             |
| `apps/web/app/src/shared/i18n/locales/{en,es}/passwordRecovery.ts` and locale indexes   | Create/Modify | Complete namespace parity; Spanish copy may wrap.                                                   |
| Corresponding `*.test.ts`, `*.spec.ts`, and `e2e/{fixtures,pages,specs}`                | Create/Modify | Focused contract, component, guard, shell, localization, and browser coverage.                      |

### Preserved PR 1 / PR 3 Design

| Slice | Preserved decisions                                                                                                                                                                                                                                                |
|-------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| PR 1  | Independent hash-only reset tokens; atomic token consumption, credential update, and refresh-session revocation; post-commit email dispatch; bounded request-duration equalization; feature flag and IP/email limits; 202/204 public API with no session issuance. |
| PR 3  | Additive post-commit audit/telemetry, bounded PII-free metrics, notification retry/terminal-failure persistence, and retention cleanup through hardening-specific ports. It must not alter PR 1 atomicity, token schema, HTTP contracts, or retain secrets.        |

No PR 1 or PR 3 code/artifact is modified by PR 2.

## Interfaces / Contracts

```ts
type RecoveryRouteMeta = {
  requiresAuth?: true
  guestOnly?: true
  standalone?: true
}

requestPasswordReset(email: string): Promise<void>
resetPassword(payload: { token: string; newPassword: string }): Promise<void>
```

`requestPasswordReset` resolves the current EN/ES locale internally for `Accept-Language`. Neither
function accepts or returns session tokens.

## Testing Strategy

| Layer            | Focus                                                                                                                                                                                                    |
|------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Unit/API         | Exact POST bodies/headers, empty responses, `ApiError` status/code retention, schema boundaries/parity.                                                                                                  |
| Component/router | Missing/array/blank token, duplicate lock, mapped states, no store login, authenticated reset allowed, authenticated forgot redirected, protected routes unchanged, standalone shell bypass.             |
| Playwright       | EN/ES happy/error flows, login-link keyboard access, mobile/no overflow, labels/live regions/focus, and no token in local/session storage or analytics. Use targeted `page.route` Problem Details mocks. |

## Migration / Rollout

No data migration. PR 2 depends on merged PR 1 and does not gate or alter PR 3. Successful reset
deliberately leaves the browser unauthenticated and directs the user to login after backend session
revocation.

## Open Questions

None. The user decision intentionally supersedes the stale REQ-UI-07 guest-only wording; spec
reconciliation is deferred because this phase is restricted to `design.md`.
