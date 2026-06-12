# Design: Remove Username from Registration

## Technical Approach

Remove the `username` field from the registration form UI, API request DTO, and client payload types. The backend `RegisterUserCommand` stays unchanged — its handler already auto-derives username from email prefix when `null`. No DB schema changes. Username remains in API responses (`AuthTokens`, `CurrentUserProfile`).

## Architecture Decisions

### Decision: Keep `RegisterUserCommand.username` as `String? = null`

| Option | Tradeoff | Decision |
|--------|----------|----------|
| Remove field from command | Cleaner but breaks existing handler contract; minor refactor | Keep — null-safe default already works |
| Add username-editing to profile settings now | Out of scope; increases change surface | Deferred — design doc exists for future |

### Decision: Leave `validateRegistration()` username check in place

| Option | Tradeoff | Decision |
|--------|----------|----------|
| Remove `username.isBlank()` check | Dead code removal, cleaner validation | Optional cleanup — harmless since `normalizedUsername` is always non-blank when email is valid |
| Keep it | No behavioral change, minimal noise | Acceptable either way; handler owns it |

### Decision: No DB migration

Username column stays. Data stays. Future profile settings will let users edit it. Removing the column now adds rollback risk with zero benefit.

## Data Flow

```
Before:
  Browser → register({email, password, username?}) → Controller(RegisterUserRequest {email, password, username?})
                                                          ↓
                                                    RegisterUserCommand(email, password, username?)
                                                          ↓
                                                    Handler: username?? ?? emailPrefix  ← unchanged

After:
  Browser → register({email, password}) → Controller(RegisterUserRequest {email, password})
                                                      ↓
                                                RegisterUserCommand(email, password, username = null)
                                                      ↓
                                                Handler: null → emailPrefix  ← unchanged path
```

## File Changes

| File | Action | Description |
|------|--------|-------------|
| `apps/web/app/src/views/AuthView.vue` | Modify | Remove `username` ref (L16), remove from payload (L32), remove `<div>` block (L111-123) |
| `apps/web/app/src/lib/auth-api.ts` | Modify | Remove `username?: string` from `RegisterPayload` (L27) |
| `apps/web/app/src/stores/auth.ts` | Modify | Remove `username?: string` from `registerWithPassword` payload type (L155) |
| `apps/web/app/src/i18n/index.ts` | Modify | Remove `auth.username` (L75) and `auth.usernamePlaceholder` (L78) from `en`; remove L195 and L198 from `es` |
| `server/.../LocalAuthController.kt` | Modify | Remove `val username: String? = null` (L134-141) from `RegisterUserRequest`; remove `username = request.username` from command call (L50); update KDoc |
| `server/.../LocalAuthControllerTest.kt` | Modify | Remove `username = "yuniel"` from `RegisterUserRequest` construction (L56) and from `RegisterUserCommand` expected (L66) |
| `server/.../LocalAuthEndpointIntegrationTest.kt` | Modify | Remove `"username" to "yuniel"` (L77) and `"username" to "owner"` (L197) from register payloads |
| `server/.../AuthorizationBddSteps.kt` | Modify | Remove `"username" to username` (L487) from register body map |
| `apps/web/app/src/lib/auth-api.test.ts` | Modify | Remove `username: 'newuser'` from register test payload (L151) and assert (L162) |

**Totals:** 0 new, 9 modified, 0 deleted

## Interfaces / Contracts

**`RegisterPayload` (before):**
```ts
interface RegisterPayload extends LoginPayload {
  username?: string
}
```

**`RegisterPayload` (after):**
```ts
interface RegisterPayload extends LoginPayload {
  // username removed — backend auto-derives from email
}
```

**`RegisterUserRequest` (before):**
```kt
data class RegisterUserRequest(
    val email: String,
    val password: String,
    val username: String? = null,
)
```

**`RegisterUserRequest` (after):**
```kt
data class RegisterUserRequest(
    val email: String,
    val password: String,
)
```

**No change** to `AuthTokens`, `CurrentUserProfile`, `RegisterUserCommand`, `RegisterUserHandler.handle()`, or `validateRegistration()` (optional).

## Testing Strategy

| Layer | What to Test | Approach |
|-------|-------------|----------|
| Unit | `register()` calls backend without `username` | Frontend test: assert body has only `email` + `password` |
| Integration | Registration succeeds with email-only payload | Remove `username` from test payload; assert 200 + auto-derived username in response |
| Integration | Registration still rejects invalid email/password | Existing negative tests pass unchanged |
| Unit | Handler auto-derives username from email | Existing `RegisterUserHandler` tests (already pass `null`) |

## Migration / Rollout

No migration required. No feature flag needed. The backend already handles `null` username, so rolling this change is safe at any time. Frontend ships first — users see one fewer input. Backend DTO change is wire-compatible (field was optional).

## Open Questions

None.
