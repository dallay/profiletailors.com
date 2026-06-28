# Proposal: Remove Username from Registration

## Intent

Remove the `username` field from registration to reduce friction. The backend already auto-derives
username from the email prefix when null/empty, so the field adds UX cost with no benefit at
sign-up. Username editing will land later in profile settings.

## Scope

### In Scope

- Remove username input from `AuthView.vue` registration form
- Remove `username` from `RegisterPayload` in `auth-api.ts`
- Remove `username` from `registerWithPassword` in `stores/auth.ts`
- Clean up i18n strings referencing username in registration
- Remove `username` from `RegisterUserRequest` in `LocalAuthController.kt`
- Update all frontend and backend tests

### Out of Scope

- Adding username editing in profile settings (deferred)
- Removing `username` column from the DB schema
- Changing the API response format (username still returned)

## Capabilities

### New Capabilities

None — registration is not a standalone spec capability.

### Modified Capabilities

None — no existing spec defines username as a registration requirement.

## Approach

1. **Frontend** — Delete the username `<input>` block, its validation logic, and its i18n keys.
   Remove `username` from `RegisterPayload` and `registerWithPassword()`. Update any test fixtures
   referencing it.
2. **Backend** — Remove `val username` from `RegisterUserRequest` data class. The
   `RegisterUserHandler` auto-derive logic already accepts `null` and works unchanged.
3. **Tests** — Remove `username` from all `RegisterUserRequest` construction sites in tests. Ensure
   auto-derive coverage still passes.
4. **No DB migration** — column stays, data stays, API response stays.

## Affected Areas

| Area                                        | Impact   | Description                                    |
|---------------------------------------------|----------|------------------------------------------------|
| `apps/web/app/src/views/AuthView.vue`       | Modified | Remove username input + validation             |
| `apps/web/app/src/lib/auth-api.ts`          | Modified | Remove username from `RegisterPayload`         |
| `apps/web/app/src/stores/auth.ts`           | Modified | Remove username from `registerWithPassword`    |
| `apps/web/app/src/i18n/*.ts`                | Modified | Remove registration username strings           |
| `server/smp/.../LocalAuthController.kt`     | Modified | Remove username from `RegisterUserRequest` DTO |
| `server/smp/.../LocalAuthControllerTest.kt` | Modified | Remove username from test fixtures             |
| `server/smp/.../RegisterUserHandlerTest.kt` | Modified | Remove username from test cases                |

## Risks

| Risk                                           | Likelihood | Mitigation                                                 |
|------------------------------------------------|------------|------------------------------------------------------------|
| API consumers depend on username in request    | Low        | Check no external clients exist (internal API only)        |
| Auto-derive has edge cases with unusual emails | Low        | Existing tests cover `null` and empty; run full test suite |

## Rollback Plan

Revert all 7 files via `git revert <merge-commit>`. No DB migration means zero-data-loss revert.
Deploy revert as standard PR.

## Dependencies

None.

## Success Criteria

- [ ] Registration form has no username field — happy path works with email + password only
- [ ] API request to `/api/auth/register` succeeds without `username` in body
- [ ] Backend auto-derives username from email prefix for every registration
- [ ] All existing tests pass (`./gradlew test`)
- [ ] No i18n dead strings remain for registration username
