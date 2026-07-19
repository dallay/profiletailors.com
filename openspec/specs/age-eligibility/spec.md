# Age Eligibility Enforcement Specification

**Capability:** `age-eligibility`

## Description

The age-eligibility capability ensures that during self-service registration, every user
affirmatively confirms they are 18+ and accepts the Terms of Service and Privacy Policy before an
account is created. Both confirmations are recorded as separate `CONTRACT_ACCEPTANCE` consent records
using the existing `RecordConsentHandler`, with explicit policy version references and distinct
purposes for independent withdrawal. No Date of Birth is collected — the mechanism is checkbox-only,
relying on safe-harbor principles for age-gating. A documented residual-controls procedure handles
suspected underage accounts discovered post-registration.

---

## Requirements

### RQ-001: Age Eligibility Checkbox in Registration Form

**Title:** Age Eligibility Confirmation

The registration form SHALL include a mandatory checkbox labeled "I confirm that I am 18 years of
age or older". The checkbox MUST be unchecked by default. The form MUST NOT submit unless the
checkbox is checked. The frontend `registerSchema` in `schemas.ts` SHALL include a
`confirmedAgeEligibility: z.literal(true, ...)` field to enforce this client-side.

**Verification:** Visual inspection + DOM query of the registration form, validated against the
Zod schema.

### RQ-002: Terms Acceptance Checkbox in Registration Form

**Title:** Terms and Privacy Acceptance

The registration form SHALL include a mandatory checkbox labeled "I accept the Terms of Service and
Privacy Policy". The checkbox MUST be unchecked by default. The form MUST NOT submit unless the
checkbox is checked. The `registerSchema` SHALL include an `acceptedTerms: z.literal(true, ...)`
field. The `RegisterPayload` SHALL include `acceptedTermsVersion: string` (value: `"terms-v1.0.0"`)
and `confirmedAgeEligibility: boolean` sent to the backend.

**Verification:** Visual inspection + DOM query of the form, Zod schema validation test, API
payload inspection.

### RQ-003: Backend Validation of Eligibility and Acceptance

**Title:** Backend Eligibility Validation

The backend SHALL validate both `confirmedAgeEligibility` and `acceptedTermsVersion` in the
`RegisterUserCommand` before any persistence. If `confirmedAgeEligibility` is not `true`, the
handler MUST reject with HTTP 422 and an error message indicating age confirmation is required.
If `acceptedTermsVersion` is blank, null, or not present, the handler MUST reject with HTTP 422
and an error message indicating terms acceptance is required. Validation MUST happen in the
`validateRegistration()` method of `RegisterUserHandler`, before the duplicate-check and
transaction.

**Verification:** Backend integration test: POST to `/api/auth/register` with
`confirmedAgeEligibility: false` and `acceptedTermsVersion: ""` asserts 422 response with
appropriate error.

### RQ-004: Consent Recording via RecordConsentHandler

**Title:** Consent Record Persistence

After successful validation and workspace provisioning, the system SHALL create TWO separate
`CONTRACT_ACCEPTANCE` consent records using the existing `RecordConsentHandler`, each with a
distinct purpose to allow independent withdrawal:

1. **Age eligibility consent:** `purpose = "age-eligibility.18-plus"`, `policyVersion = "terms-v1.0.0"`
2. **Terms acceptance consent:** `purpose = "terms.acceptance"`, `policyVersion = <acceptedTermsVersion from command>`

Both consent records SHALL use `SubjectReference.workspace(workspaceId)` where `workspaceId` is
captured from `provisionDefaultWorkspace()`. The consent recording MUST happen inside the same
atomic transaction as registration, after `provisionDefaultWorkspace()` but before event
publication. If either consent record fails, the entire registration transaction SHALL roll back.

> **Design Rationale (AD-06):** Two separate records allow age eligibility and terms acceptance to
> be withdrawn independently. A single combined record would couple these concerns, making it
> impossible to revoke one without the other.

**Verification:** Integration test asserts two rows exist in `consent_records` table after successful
registration, with correct `consentType`, `workspaceId`, `subjectReference`, `purpose`, and
`policyVersion` for each.

### RQ-005: Policy Versioning

**Title:** Explicit Policy Version Tracking

The `acceptedTermsVersion` sent by the frontend SHALL be `"terms-v1.0.0"`, corresponding to the
current Terms of Service `v1.0.0` as defined in the `legal-pages` capability (`terms-001`). The
Privacy Policy version is `"privacy-v1.0.0"`. Both are referenced by the two consent records:
the age eligibility record stores `"terms-v1.0.0"` as a hardcoded constant, and the terms
acceptance record stores the `acceptedTermsVersion` value sent by the frontend.

**Verification:** Hardcoded version string confirmed in frontend payload and backend consent record.

### RQ-006: Residual Controls for Underage Accounts

**Title:** Documented Underage Account Procedure

A documented procedure in `docs/compliance/underage-account-procedure.md` SHALL define the process
for handling suspected underage accounts, including: report intake, investigation steps, account
suspension, data deletion or retention decisions, and appeal process. The procedure MUST be
reviewable and versioned.

**Verification:** File existence check on `docs/compliance/underage-account-procedure.md`, review
of defined procedure steps.

---

## Scenarios

### Scenario: Successful registration with age eligibility and terms acceptance

- GIVEN a new user navigates to the registration form
- AND the user checks "I confirm that I am 18 years of age or older"
- AND the user checks "I accept the Terms of Service and Privacy Policy"
- WHEN the user submits the form with valid email and password
- THEN the system SHALL create the user account
- AND the system SHALL create two `CONTRACT_ACCEPTANCE` consent records:
  - one with `purpose: "age-eligibility.18-plus"` and `policyVersion: "terms-v1.0.0"`
  - one with `purpose: "terms.acceptance"` and `policyVersion: "terms-v1.0.0"`
- AND both consent records SHALL reference the workspace via `SubjectReference.workspace(workspaceId)`
- AND the response SHALL be HTTP 201 with AuthTokens
- AND the user SHALL be redirected to the dashboard

### Scenario: Registration rejected when age eligibility checkbox is unchecked

- GIVEN a new user navigates to the registration form
- AND the user does NOT check the age eligibility checkbox
- WHEN the user submits the form
- THEN the frontend SHALL prevent submission via client-side validation
- AND the user SHALL see an error message indicating age confirmation is required
-
- GIVEN a client sends a POST to `/api/auth/register` with `confirmedAgeEligibility: false`
- WHEN the backend processes the request
- THEN the backend SHALL reject with HTTP 422
- AND the error SHALL indicate age confirmation is required
- AND no user account SHALL be created
- AND no consent record SHALL be persisted

### Scenario: Registration rejected when terms acceptance checkbox is unchecked

- GIVEN a client sends a POST to `/api/auth/register` with `acceptedTermsVersion: ""`
- WHEN the backend processes the request
- THEN the backend SHALL reject with HTTP 422
- AND the error SHALL indicate terms acceptance is required
- AND no user account SHALL be created
- AND no consent record SHALL be persisted

### Scenario: Registration rejected when both confirmations are missing

- GIVEN a client sends a POST to `/api/auth/register` with `confirmedAgeEligibility: false` AND `acceptedTermsVersion` blank
- WHEN the backend processes the request
- THEN the backend SHALL reject with HTTP 422
- AND the error SHALL include both missing age confirmation and missing terms acceptance

### Scenario: Consent records created with correct workspace reference

- GIVEN a successful registration completes
- WHEN the consent records are inspected
- THEN the `workspaceId` SHALL match the workspace created by `provisionDefaultWorkspace()`
- AND the `subjectReference.kind` SHALL be `WORKSPACE`
- AND the `subjectReference.value` SHALL be the workspace ID
- AND the `consentType` SHALL be `CONTRACT_ACCEPTANCE` for both records
- AND the first record SHALL have `purpose: "age-eligibility.18-plus"` and `policyVersion: "terms-v1.0.0"`
- AND the second record SHALL have `purpose: "terms.acceptance"` and `policyVersion: "terms-v1.0.0"`

### Scenario: Frontend tampering does not bypass backend validation

- GIVEN a malicious client sends a POST to `/api/auth/register` with `confirmedAgeEligibility: false`
- AND the `acceptedTermsVersion` is missing
- WHEN the backend processes the request
- THEN the backend SHALL reject regardless of what the frontend rendered
- AND no user account SHALL be created

---

## Policy Versions

| Policy                | Version      | Source                                     |
|-----------------------|--------------|--------------------------------------------|
| Terms of Service      | `terms-v1.0.0` | `legal-pages` spec, requirement `terms-001` |
| Privacy Policy        | `privacy-v1.0.0` | `legal-pages` spec, requirement `privacy-001` |

The `policyVersion` in the age eligibility consent record stores the hardcoded constant
`"terms-v1.0.0"`. The `policyVersion` in the terms acceptance consent record stores the
`acceptedTermsVersion` value sent by the frontend at registration time. When policy versions
change, the frontend SHALL update `acceptedTermsVersion` to reflect the new version, and the
consent records SHALL capture the version the user accepted at registration time.

---

## Out of Scope

- Date of Birth collection or real-age verification
- Document verification (ID checks, parental consent)
- Per-country or per-market age gating (global 18+ for now)
- Age gating on marketing/landing pages
- Dashboard notices, account settings, or post-registration flows
- Periodic re-verification of age
- Re-verification of terms acceptance on policy updates
