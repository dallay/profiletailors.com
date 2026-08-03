# Delta for E2E (Login/Register Flows)

## MODIFIED Requirements

### Requirement: Password Minimum Spec-Sync (login-flow.md)

The E2E login plan MUST assert a 12-character password minimum in every password-policy reference. Section 5.4 ("Registration with short password") MUST expect error detail `"Password must contain at least 12 characters."` (Previously: the plan asserted "at least 8 characters"; the working tree already updates §5.4 and Appendix B.)

#### Scenario: 11-character password rejected

- GIVEN a browser at `/register` with email `newuser@example.com`
- WHEN the user submits password `Ab1` (3 chars)
- THEN the API returns 400 with detail `"Password must contain at least 12 characters."`

#### Scenario: Spec asserts 12-char minimum in API contract

- GIVEN the login-flow plan documents the `/api/auth/register` error contract
- THEN the Appendix B 400 example MUST show `"Password must contain at least 12 characters."`

### Requirement: Password Minimum Spec-Sync (register-flow.md)

The E2E register plan MUST assert a 12-character minimum everywhere. Section 1.3 and 8.1 (EN) MUST show placeholder `"At least 12 characters"`; section 4.3 MUST expect 400 detail `"Password must contain at least 12 characters."`. (Previously: plan asserted "At least 8 characters" in EN and stale `"Al menos 8 caracteres"` in the ES section 8.2 — the ES copy gap MUST be closed to say `"Al menos 12 caracteres"`.)

#### Scenario: Spanish register copy says "12"

- GIVEN the locale is set to Spanish at `/register`
- WHEN the page renders
- THEN the password placeholder MUST read `"Al menos 12 caracteres"`
- AND a regression assertion MUST verify the ES message contains "12"

#### Scenario: 12-character password accepted

- GIVEN a browser at `/register` with a unique email
- WHEN the user submits password `SecurePass123!` (12+ chars)
- THEN the API returns 201 with AuthTokens

#### Scenario: RegisterForm validation parity

- GIVEN the app `registerSchema` enforces `min(12)` (parity with reset schema)
- WHEN the user enters an 11-character password in the register form
- THEN an inline client-side error is shown and no network request is made

## TDD Requirement

Each behavior scenario MUST have a failing-first test. Existing in-tree tests: `apps/web/app/e2e/specs/registration.spec.ts` (8→12 assertion), `schemas.test.ts` (registerSchema min(12) mirror), BDD `registration.feature` (11 rejected / 12 accepted). New regression required: ES message asserts "12" (currently untested).
