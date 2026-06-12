# Delta for Registration

## Overview

Remove the `username` field from the registration form and API request payload. The backend continues auto-deriving username from the email prefix. Username remains in API responses for future profile settings.

## REMOVED Requirements

### Requirement: Username Field in Registration Form

The registration form MUST NOT include a username input field.

The frontend `AuthView.vue` MUST render only email and password fields for registration.
The `RegisterPayload` type in `auth-api.ts` MUST NOT include `username`.
The `registerWithPassword` method in `stores/auth.ts` MUST NOT accept `username`.
The i18n keys `auth.username` and `auth.usernamePlaceholder` MUST be removed.

#### Scenario: Registration form renders without username

- GIVEN a user navigates to the registration page
- WHEN the form renders
- THEN the user MUST see email and password inputs only
- AND the user MUST NOT see a username input

### Requirement: Username Field in Registration API Request

The registration API endpoint MUST NOT accept `username` in the request body.

The `RegisterUserRequest` DTO in `LocalAuthController.kt` MUST remove the `username` field.
Only `email` and `password` SHALL be accepted as request body parameters.

#### Scenario: Register request succeeds without username

- GIVEN a client sends a POST to `/api/auth/register`
- AND the body contains `{"email": "alice@example.com", "password": "Secret123!"}`
- WHEN the server processes the request
- THEN the response MUST be `201 Created`
- AND the user SHALL be created with username derived from the email prefix (`alice`)

#### Scenario: Register request with unknown username field is silently ignored

- GIVEN a client sends a POST to `/api/auth/register`
- AND the body includes `{"email": "bob@example.com", "password": "Secret123!", "username": "bob"}`
- WHEN the server processes the request
- THEN the server MUST silently ignore the unknown `username` field via Jackson default behavior
- AND the response MUST be `201 Created`
- AND the username SHALL be auto-derived from the email prefix (`bob`)

## PRESERVED Requirements

### Requirement: Backend Auto-Derives Username from Email

The system MUST continue to auto-derive the username from the email prefix when creating a user account.

The `RegisterUserHandler` MUST derive the username by taking the email prefix (the part before `@`).
The derived username MUST be stored in the `username` column in the database.

#### Scenario: Username derived from simple email prefix

- GIVEN a user registers with email `alice@example.com`
- WHEN the account is created
- THEN the username MUST be set to `alice`
- AND the API response MUST include `username: "alice"`

#### Scenario: Username derived from email with special characters

- GIVEN a user registers with email `alice.smith+test@example.com`
- WHEN the account is created
- THEN the username MUST be derived from the full email prefix `alice.smith+test`

### Requirement: Username in API Responses

The system MUST continue to include `username` in API responses.

The `AuthTokens` and `CurrentUserProfile` response DTOs MUST retain the `username` field.
Removing `username` from the response is out of scope for this change.

#### Scenario: Login response includes username

- GIVEN a registered user with a derived username
- WHEN the user logs in
- THEN the response MUST include `username` in the token payload

#### Scenario: Profile response includes username

- GIVEN an authenticated user
- WHEN they request their current profile
- THEN the response MUST include the `username` field

## Acceptance Criteria

| ID | Criterion | Validation |
|----|-----------|------------|
| AC-1 | Registration form has no username field | Visual inspection + DOM query |
| AC-2 | API register succeeds without `username` in body | Integration test |
| AC-3 | Username auto-derived from email prefix | Unit test coverage |
| AC-4 | Existing tests pass | `./gradlew test` green |
| AC-5 | No dead i18n strings for registration username | Grep confirms removal |
| AC-6 | API responses still include `username` | Integration test asserts response |

## Constraints

- No DB migration — `username` column stays, data stays
- Username removal is registration-only; profile settings edit is deferred
- All existing tests MUST pass with zero regressions
