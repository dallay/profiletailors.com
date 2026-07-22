# Design: Age Eligibility Enforcement

## Architecture Overview

Register flow extended with two consent gates after workspace provisioning:

```
AuthView.vue ──POST /api/auth/register──→ RegisterUserHandler
                                                 │
                      validateRegistration() ◄────┤    ← confirmedAgeEligibility & acceptedTermsVersion
                                                 │
                      runRegistrationTransaction()│
                        ├─ createUserIdentity()   │
                        ├─ createPasswordCred()   │
                        ├─ provisionDefaultWorkspace() → returns workspaceId
                        ├─ recordConsent(age 18+) │    ← CONTRACT_ACCEPTANCE, purpose=age-eligibility.18-plus
                        ├─ recordConsent(terms)   │    ← CONTRACT_ACCEPTANCE, purpose=terms.acceptance
                        └─ createEmailVerifToken()│
                                                 │
                      publish UserRegistered ────→
                      issueAuthSession() ────────→ 201 {AuthTokens}
```

Both consent records written **inside the transaction** after workspace provisioning, before event
publication. If recording fails, the entire registration rolls back.

---

## Architecture Decisions

| Option                                                  | Tradeoffs                                                                          | Decision                                                                                                                 |
|---------------------------------------------------------|------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------|
| **Consent recording inside vs. outside transaction**    | Inside = consistent (rolls back if fails). Outside = user created without consent. | ✅ **Inside** `runRegistrationTransaction`, after `provisionDefaultWorkspace()`                                           |
| **Error type for age/terms rejection**                  | Reuse `InvalidRegistrationInputException` (400) vs. new exception (422)            | ✅ **`RegistrationValidationException`** → 422. Current format validation (email/password) stays 400 via existing handler |
| **RecordConsentHandler access**                         | `internal` blocks cross-module injection                                           | ✅ **Remove `internal`** from `RecordConsentHandler` class — Spring can then inject it into identity module               |
| **`policyVersion` source**                              | Hardcode vs. config vs. Accept-Language                                            | ✅ **Hardcode `"terms-v1.0.0"`** as constant in handler for now. Configurable later                                       |
| **`locale` in consent record**                          | Required by `ConsentRecord` init                                                   | ✅ **Default to `"en"`** via constant. Not available in registration flow yet                                             |
| **Age eligibility + terms: one or two consent records** | One record means harder to withdraw independently                                  | ✅ **Two separate records**, each with distinct `purpose`                                                                 |

---

## Component Design

### Backend: `RegisterUserCommand` — modify

**File**: `server/smp/.../identity/application/LocalAuthApi.kt`

Add fields:

```kotlin
data class RegisterUserCommand(
    val email: String,
    val password: String,
    val username: String? = null,
    val confirmedAgeEligibility: Boolean,
    val acceptedTermsVersion: String?,  // nullable for DTO mapping, validated in handler
)
```

### Backend: `RegisterUserHandler` — modify

**File**: `server/smp/.../identity/application/LocalAuthHandlers.kt`

1. Inject `RecordConsentHandler`
2. Extend `validateRegistration()`: check `confirmedAgeEligibility == true` and
   `acceptedTermsVersion.isNotBlank()`
3. Inside `runRegistrationTransaction`, after `provisionDefaultWorkspace()`:

```kotlin
val workspace = workspaceProvisioningService.provisionDefaultWorkspace(...)

recordConsentHandler.handle(
    RecordConsentCommand(
        workspaceId = workspace.workspaceId,
        subjectReference = SubjectReference.workspace(workspace.workspaceId),
        consentType = ConsentType.CONTRACT_ACCEPTANCE,
        purpose = "age-eligibility.18-plus",
        policyVersion = AGE_ELIGIBILITY_POLICY_VERSION,
        source = CONSENT_SOURCE,
        locale = CONSENT_LOCALE,
    ),
)
recordConsentHandler.handle(
    RecordConsentCommand(
        workspaceId = workspace.workspaceId,
        subjectReference = SubjectReference.workspace(workspace.workspaceId),
        consentType = ConsentType.CONTRACT_ACCEPTANCE,
        purpose = "terms.acceptance",
        policyVersion = command.acceptedTermsVersion!!,
        source = CONSENT_SOURCE,
        locale = CONSENT_LOCALE,
    ),
)
```

### Backend: `RegisterUserRequest` — modify

**File**: `server/smp/.../identity/infrastructure/http/LocalAuthController.kt`

Add Jakarta Validation annotations:

```kotlin
@field:AssertTrue(message = "You must confirm you are 18 or older")
val confirmedAgeEligibility: Boolean,

@field:NotBlank(message = "You must accept the terms of service")
val acceptedTermsVersion: String,
```

### Backend: `IdentityProblemDetailsHandler` — modify

**File**: `server/smp/.../identity/infrastructure/http/IdentityProblemDetailsHandler.kt`

Add handler for new exception → 422 Unprocessable Entity.

### Backend: `RecordConsentHandler` — modify (access only)

**File**: `server/smp/.../governance/application/RecordConsentHandler.kt`

Change `internal class` → `class` (remove `internal`).

### Backend: New — `RegistrationValidationException`

**File**: `server/smp/.../identity/application/LocalAuthExceptions.kt`

```kotlin
class RegistrationValidationException(message: String) : RuntimeException(message)
```

### Frontend: `schemas.ts` — modify

**File**: `apps/web/app/src/shared/lib/validation/schemas.ts`

```typescript
export const registerSchema = authCredentialsSchema.extend({
  confirmPassword: z.string().trim().min(1, 'confirmPasswordRequired'),
  confirmedAgeEligibility: z.literal(true, { message: 'ageEligibilityRequired' }),
  acceptedTerms: z.literal(true, { message: 'termsRequired' }),
}).refine(...)
```

### Frontend: `AuthView.vue` — modify

**File**: `apps/web/app/src/modules/auth/presentation/AuthView.vue`

Add two checkboxes after confirmPassword field in the template. Both `v-model` bound to refs. Submit
disabled unless both checked.

### Frontend: `auth-api.ts` — modify

**File**: `apps/web/app/src/modules/auth/infrastructure/auth-api.ts`

Update `register()` to accept and pass `confirmedAgeEligibility` and `acceptedTermsVersion`.

### Frontend: `auth.store.ts` — modify

**File**: `apps/web/app/src/modules/auth/infrastructure/auth.store.ts`

Update `registerWithPassword()` payload type to include new fields; pass them through to
`register()`.

### New: Compliance documentation

**File**: `docs/compliance/underage-account-procedure.md`

Document: reporting channel, investigation steps, suspension/deletion protocol, appeal mechanism,
data retention policy.

---

## Data Model Changes

None. `ConsentRecord` model already supports `CONTRACT_ACCEPTANCE` with `purpose`, `policyVersion`,
`workspaceId`, and `SubjectReference.workspace()`. No Liquibase migration needed.

---

## API Contract

`POST /api/auth/register` (v1) — modified:

```jsonc
// Request
{
  "email": "user@example.com",
  "password": "SecureP@ssw0rd",
  "confirmedAgeEligibility": true,      // NEW: required, must be true
  "acceptedTermsVersion": "terms-v1.0.0" // NEW: required, not blank
}

// Response 201 — unchanged { accessToken, tokenType, expiresIn, principalId, email, username, emailStatus, workspaceId }

// Response 422 — new
{
  "title": "Registration validation failed",
  "status": 422,
  "detail": "You must confirm you are 18 or older."
}
```

---

## Error Handling

| Condition                         | HTTP Status | Exception                           | Message                                            |
|-----------------------------------|-------------|-------------------------------------|----------------------------------------------------|
| `confirmedAgeEligibility != true` | 422         | `RegistrationValidationException`   | "You must confirm that you are 18 years or older." |
| `acceptedTermsVersion` blank      | 422         | `RegistrationValidationException`   | "You must accept the Terms of Service."            |
| Email/password format invalid     | 400         | `InvalidRegistrationInputException` | (existing)                                         |

Frontend: form error section displays `auth.ageEligibilityRequired` / `auth.termsRequired` / server
error detail.

---

## i18n

New keys in `locales/{en,es}/auth.ts`:

| Key                      | EN                                               | ES                                                          |
|--------------------------|--------------------------------------------------|-------------------------------------------------------------|
| `ageEligibilityLabel`    | I am 18 years or older                           | Soy mayor de 18 años                                        |
| `termsLabel`             | I accept the Terms of Service and Privacy Policy | Acepto los Términos de Servicio y la Política de Privacidad |
| `ageEligibilityRequired` | You must confirm you are 18 or older             | Debes confirmar que eres mayor de 18 años                   |
| `termsRequired`          | You must accept the terms                        | Debes aceptar los términos                                  |

---

## Testing Strategy

| Layer                             | What                                         | How                                                                            |
|-----------------------------------|----------------------------------------------|--------------------------------------------------------------------------------|
| **Unit** — `RegisterUserHandler`  | Rejects when `confirmedAgeEligibility=false` | Existing `LocalAuthHandlersTest` — add test cases for each validation path     |
| **Unit** — `RegisterUserHandler`  | Rejects when `acceptedTermsVersion` blank    | Same test class                                                                |
| **Unit** — `RegisterUserHandler`  | Creates two consent records on success       | Verify `RecordConsentHandler` double invocation with correct purposes          |
| **Unit** — `RecordConsentHandler` | Idempotent recording (existing)              | Already covered                                                                |
| **Unit** — controller             | DTO validation rejects missing fields        | Existing `LocalAuthControllerTest` — update `RegisterUserRequest` construction |
| **Integration** — endpoint        | Full register flow with consent DB records   | Existing `LocalAuthHandlersTransactionPostgresIntegrationTest`                 |
| **Frontend** — `AuthView.spec.ts` | Form blocks submit without checkboxes        | Add test: mount in register mode, submit without checks, verify error shown    |
| **Frontend** — `schemas.spec.ts`  | Schema rejects `false` values                | Add test: `registerSchema.safeParse({confirmedAgeEligibility: false})`         |
| **E2E**                           | Browser test: registration with checkboxes   | Playwright — add to existing auth spec                                         |

---

## Migration / Rollout

No migration required. Changes are additive — existing unconfirmed registrations are unaffected.
Rollback: revert frontend form changes, remove fields from command/DTO, remove consent recording
logic, add `internal` back to `RecordConsentHandler`. Consent records are append-only — no data
cleanup needed.

---

## Open Questions

- [ ] Should `locale` be added to `RegisterUserCommand` (from Accept-Language header) or kept as a
  constant for now? Constant is simpler for this phase.
- [ ] Exact `policyVersion` strings: confirm `"terms-v1.0.0"` matches the legal pages spec terms-001
  version.
