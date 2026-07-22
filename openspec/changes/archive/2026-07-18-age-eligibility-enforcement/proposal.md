# Proposal: Age Eligibility Enforcement & Child-Data Safeguards

## Overview

**Priority:** DALLAY-492 — P0, release-blocker.

Profile Tailors needs to avoid being classified as a child-directed service. This proposal enforces
that only users 18+ can register, with documented residual controls for accounts suspected of
belonging to minors. This is not about privacy — it is about contractual eligibility and avoiding
classification as a child service.

---

## Changes

| Version | Date       | Author          | Changes          |
|---------|------------|-----------------|------------------|
| v1.0.0  | 2026-07-18 | Compliance Team | Initial proposal |

---

## Intent

Profile Tailors needs to stay out of child-directed service regimes. We will enforce that only
18+ users can register, with documented residual controls for suspicious accounts. This is not
about privacy — it is about contractual eligibility and avoiding classification as a child service.

---

## Scope

### In Scope

- Age confirmation checkbox (18+) in registration form — no Date of Birth collection
- Terms and Privacy Policy acceptance checkbox in registration
- Backend validation: `RegisterUserHandler` rejects if age eligibility or terms acceptance is
  missing
- Consent recording using existing `RecordConsentHandler` (type `CONTRACT_ACCEPTANCE`)
- Policy version (Terms v1.0.0, Privacy v1.0.0) referenced in consent records
- `workspaceId` for consent records: use the workspace created during `provisionDefaultWorkspace`
- Clear error messages without dark patterns
- Documented residual controls procedure for accounts suspected of belonging to minors

### Out of Scope

- Date of Birth collection or real age verification
- Document verification (ID checks, parental consent)
- Per-market/country differentiation (applies globally for now)
- Age gating on marketing pages (landing page does not require age gate)
- Dashboard notices, account settings, or post-registration flows
- Periodic age re-verification

---

## Capabilities

### New Capabilities

- `age-eligibility`: Age eligibility confirmation during self-service registration, with consent
  records and documented residual controls

### Modified Capabilities

- `legal-pages`: Terms now reference an `eligibleAge` of 18 years. Requirement `terms-001` already
  contemplates `eligible age` — confirm the spec reflects the exact value.
- `identity` (new spec if not exists): `RegisterUser` flow incorporates age eligibility check and
  consent recording as a mandatory step

---

## Approach

**Checkbox-only strategy** — the user confirms two things:

1. "I am 18 years or older" (age eligibility)
2. "I accept the Terms of Service and Privacy Policy" (terms acceptance)

**Backend:** `RegisterUserCommand` receives `acceptedTermsVersion` and
`confirmedAgeEligibility: Boolean`. `RegisterUserHandler.validateRegistration()` rejects if
`confirmedAgeEligibility != true` or `acceptedTermsVersion` is missing. Consent recording happens *
*inside the same transaction**, after `provisionDefaultWorkspace()` but before publishing the
`UserRegistered` event. We use `SubjectReference.workspace(workspaceId)` because consent is at the
workspace level of the newly created workspace.

**Frontend:** `AuthView.vue` adds two checkboxes to the registration form. `registerSchema` in
`schemas.ts` is extended with `acceptedTerms` and `confirmedAgeEligibility`. `RegisterPayload`
passes `acceptedTermsVersion` and `confirmedAgeEligibility` to the backend.

**Policy versioning:** legal pages specs are in `v1.0.0` (from `legal-pages` spec). The
`policyVersion` in the consent record will reference the terms version the user accepted.

**Residual controls:** procedure documented in `docs/compliance/underage-account-procedure.md` for
report, investigation, suspension, and appeal.

---

## Affected Areas

| Area                                                         | Impact   | Description                                                   |
|--------------------------------------------------------------|----------|---------------------------------------------------------------|
| `server/smp/identity/application/LocalAuthApi.kt`            | Modified | `RegisterUserCommand` new fields                              |
| `server/smp/identity/application/LocalAuthHandlers.kt`       | Modified | `RegisterUserHandler` validates eligibility + records consent |
| `server/smp/identity/http/LocalAuthController.kt`            | Modified | `RegisterUserRequest` new fields + validation                 |
| `apps/web/app/src/shared/lib/validation/schemas.ts`          | Modified | `registerSchema` with checkbox fields                         |
| `apps/web/app/src/modules/auth/infrastructure/auth-api.ts`   | Modified | `RegisterPayload` type, `register()` params                   |
| `apps/web/app/src/modules/auth/presentation/AuthView.vue`    | Modified | Checkboxes in template + form logic                           |
| `apps/web/app/src/modules/auth/infrastructure/auth.store.ts` | Modified | `registerWithPassword` new params                             |
| `docs/compliance/underage-account-procedure.md`              | New      | Documented residual controls                                  |
| `openspec/specs/age-eligibility/spec.md`                     | New      | Spec for this capability                                      |

---

## Risks

| Risk                                                             | Probability | Mitigation                                                                                                             |
|------------------------------------------------------------------|-------------|------------------------------------------------------------------------------------------------------------------------|
| Underage users lie on the checkbox                               | High        | Legally acceptable — we are complying with safest harbor. The residual procedure documents what to do if discovered.   |
| `workspaceId` unavailable during consent recording               | Low         | Generated inside `provisionDefaultWorkspace()` in the same transaction. We only need to capture the created workspace. |
| Frontend sends `confirmedAgeEligibility: false` via manipulation | Low         | Backend validates and rejects — never trust the client.                                                                |

---

## Rollback Plan

- **Frontend:** revert changes in `AuthView.vue`, `schemas.ts`, `auth-api.ts`, `auth.store.ts` (all
  isolated to the registration form)
- **Backend:** revert changes in `RegisterUserCommand`, `RegisterUserHandler`,
  `LocalAuthController` — additive changes that break nothing existing
- **Consent records:** no migration needed — `RecordConsentHandler` already exists and the
  `consent_records` table supports the data
- **Residual docs:** no rollback needed; documentation only

---

## Dependencies

- DALLAY-491 (Consent Records) — **merged**, `RecordConsentHandler` available
- DALLAY-488 (Legal Pages) — **archived**, Terms v1.0.0 exists
- `legal-pages` spec defines `eligibleAge` in terms-001 — confirm it is set to 18

---

## Success Criteria

- [ ] User cannot register without checking age eligibility checkbox
- [ ] User cannot register without checking terms acceptance checkbox
- [ ] `consent_records` table has one `CONTRACT_ACCEPTANCE` record for each successful registration
- [ ] `consent_records` uses `workspaceId` from the workspace created in the transaction
- [ ] `consent_records` uses `policyVersion` from the accepted terms
- [ ] Backend test: registration with checkbox `false` returns 422
- [ ] Backend test: successful registration creates idempotent consent record
- [ ] Frontend test: form validation blocks submit without checkboxes
- [ ] Documentation `docs/compliance/underage-account-procedure.md` exists
