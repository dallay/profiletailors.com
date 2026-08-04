# E2E Test Plan: Register Flow

> Updated from codebase analysis and auth-security-hardening specs on 2026-06-19.

## Scope

This plan covers the **registration flow** across the Profile Tailors SPA (`apps/web/app/`) and the
SMP backend (`server/smp/`). It targets the complete user journey: register page rendering → form
interaction → API integration → account creation → immediate session creation → email verification
lifecycle → feature gating for unverified users → session management.

---

## Test Infrastructure

- **Framework**: Playwright (already configured at `apps/web/marketing/playwright.config.ts`). A
  separate Playwright config should be created at `apps/web/app/playwright.config.ts` for the SPA
  app.
- **App URL**: `http://localhost:5173` (Vite dev server, proxying `/api` to `http://localhost:8080`)
- **Backend URL**: `http://localhost:8080`
- **Browsers**: Chromium (primary), Firefox, WebKit
- **State persistence**: Playwright `storageState` for authenticated sessions
- **API mocking**: Use Playwright route interception for backend-dependent tests
- **Test data**: Each test should use unique emails (e.g.,
  `e2e-register-{timestamp}@profiletailors.com`)

---

## Architecture Overview

```
┌──────────────────┐         ┌──────────────────┐
│  Vue SPA (5173)  │  /api   │  Spring Boot (8080)│
│  AuthView.vue     │ ──────→ │  LocalAuthController│
│  auth store       │  proxy  │  RegisterUserHandler  │
│  auth-api.ts      │         │  WorkspaceProvisioning│
│  Pinia            │         │  R2DBC -> PostgreSQL│
└──────────────────┘         └──────────────────┘
```

### Registration Flow

```
User submits /register form
    │
    ├─→ POST /api/auth/register
    │     ├─→ 201 Created: AuthTokens
    │     │         { accessToken, tokenType, expiresIn, principalId,
    │     │           email, username, emailStatus: "PENDING", workspaceId? }
    │     │         + Set-Cookie: refresh-token (HttpOnly)
    │     │
    │     ├─→ Store accessToken in Pinia (memory only)
    │     ├─→ Session is immediately authenticated
    │     ├─→ GET /api/auth/me (Authorization: Bearer <token>)
    │     ├─→ Dashboard loads with unverified-user session
    │     └─→ Restricted features remain gated until verification
    │
    └─→ Error paths: 409 (duplicate), 400 (validation)
```

### Unverified Session Policy

```
PENDING user logs in/registers successfully
    │
    ├─→ Authentication flow succeeds
    │     ├─→ login allowed
    │     ├─→ refresh allowed
    │     └─→ emailStatus claim remains PENDING
    │
    └─→ Feature-gated operations blocked
          ├─→ connect social provider → 403 EMAIL_VERIFICATION_REQUIRED
          ├─→ publish/schedule content → 403 EMAIL_VERIFICATION_REQUIRED
          ├─→ invite team members → 403 EMAIL_VERIFICATION_REQUIRED
          └─→ UI shows verification reminder / blocked-state messaging
```

### Key Contracts

| Endpoint             | Method | Request                    | Success          | Failure                                  |
| -------------------- | ------ | -------------------------- | ---------------- | ---------------------------------------- |
| `/api/auth/register` | POST   | `{ email, password }`      | 201 + AuthTokens | 409 UserAlreadyExists / 400 InvalidInput |
| `/api/auth/login`    | POST   | `{ email, password }`      | 200 + AuthTokens | 401 InvalidCredentials                   |
| `/api/auth/refresh`  | POST   | (HttpOnly cookie)          | 200 + AuthTokens | 401 RefreshSessionNotActive              |
| `/api/auth/logout`   | POST   | (HttpOnly cookie)          | 204              | —                                        |
| `/api/auth/me`       | GET    | Bearer token               | 200 profile      | 401                                      |
| feature-gated APIs   | any    | authenticated PENDING user | —                | 403 EMAIL_VERIFICATION_REQUIRED          |

---

## 1. Register Page Rendering

### 1.1 Full page renders correctly

```
Given a browser navigates to /register
Then the page title is "Profile Tailors — Social Media Management Platform"
And the heading "Create account" is visible
And the subtitle "Start managing your channels with local email and password access." is visible
And the badge "PROFILE TAILORS" is visible
And the hero section with 3 feature cards (SECURITY, FOCUS, WORKFLOW) is visible
And the email input field is present with label "EMAIL"
And the password input field is present with label "PASSWORD"
And the "Create account" submit button is present
And the "Already have an account?" text is visible
And the "SIGN IN" link points to /login
```

### 1.2 Email input attributes

```
Given a browser navigates to /register
Then the email input has type="email"
And the email input has autocomplete="email"
And the email input is required
And the email input has a placeholder matching "you@example.com"
```

### 1.3 Password input attributes

```
Given a browser navigates to /register
Then the password input has type="password"
And the password input has autocomplete="current-password"
And the password input is required
And the password input has placeholder "At least 12 characters"
```

### 1.4 Navigation between login and register

```
Given a browser navigates to /login
When the user clicks "Register"
Then the browser navigates to /register
And the heading "Create account" is visible

Given a browser navigates to /register
When the user clicks "Sign in"
Then the browser navigates to /login
And the heading "Welcome back" is visible
```

### 1.5 Theme toggle on register page

```
Given a browser navigates to /register
When the user clicks the theme toggle (dark/light)
Then the page theme changes accordingly
And both email and password inputs remain visible and functional
```

### 1.6 Form is cleared when switching modes

```
Given a browser navigates to /register
When the user fills email with "test@example.com"
And fills password with "password123"
And clicks "Sign in" link
Then the email field is empty on /login
And the password field is empty on /login
```

---

## 2. Form Validation (Frontend)

### 2.1 Empty fields trigger HTML5 validation

```
Given a browser navigates to /register
When the user clicks "Create account" with both fields empty
Then the browser shows HTML5 validation for the email field
And no network request is made to /api/auth/register
```

### 2.2 Invalid email format blocked by browser

```
Given a browser navigates to /register
When the user types "not-an-email" in the email field
And types "password123" in the password field
And clicks "Create account"
Then the browser shows HTML5 validation for invalid email format
And no network request is made
```

### 2.3 Empty password blocked

```
Given a browser navigates to /register
When the user types "valid@email.com" in the email field
And leaves password field empty
And clicks "Create account"
Then the browser shows HTML5 validation for the password field
And no network request is made
```

### 2.4 Client-side password length validation (ASVS L2)

```
Given a browser navigates to /register
When the user types "valid@email.com" in the email field
And types "Ab1" (3 chars) in the password field
Then a client-side "password too short" error is shown
And no network request is made
```

---

## 3. Registration API — Success Path

### 3.1 Successful registration creates account and authenticated session

```
Given the user navigates to /register
When the user fills email with "new-e2e-user-{timestamp}@profiletailors.com"
And fills password with "SecurePass123!"
And clicks "Create account"
Then the browser makes POST /api/auth/register with { email, password }
And the request includes headers: Content-Type: application/json, Accept: application/vnd.api.v1+json
And the request includes credentials: "include"
And the response status is 201 Created
And the response body conforms to AuthTokens
And the response body contains accessToken (non-empty string)
And the response body contains tokenType: "Bearer"
And the response body contains expiresIn (positive integer)
And the response body contains principalId (matches "user-*" pattern)
And the response body contains email matching the input (normalized to lowercase)
And the response body contains username (derived from email)
And the response body contains emailStatus: "PENDING"
And the response SHALL NOT conform to the legacy RegistrationResult-only schema
And the response sets Set-Cookie header for the refresh token (HttpOnly)
Then the browser redirects to /
And the user is immediately authenticated
And the "Welcome back" dashboard header is visible
And the user display name appears in the navigation
```

### 3.2 Registration with email normalization (lowercase)

```
Given the user navigates to /register
When the user fills email with "Test@Example.COM"
And fills password with "SecurePass123!"
And clicks "Create account"
Then the API normalizes email to "test@example.com"
And the response email is "test@example.com"
And subsequent login with "TEST@EXAMPLE.COM" also works (case-insensitive)
```

### 3.3 Registration with whitespace in email is trimmed

```
Given the user navigates to /register
When the user fills email with "  test@example.com  "
And fills password with "SecurePass123!"
And clicks "Create account"
Then the API trims whitespace
And registration succeeds
```

### 3.4 Registration with username derived from email

```
Given the user registers with email "john.doe@example.com"
When the registration succeeds
Then the response includes username: "john.doe"
```

### 3.5 Workspace provisioned automatically

```
Given a successful registration
Then a default workspace is provisioned for the user
And the workspace name matches the derived username
And the user can navigate to /scheduler without errors
```

### 3.6 Registration button shows loading state

```
Given the user is on /register
When the user submits the form
Then the submit button is disabled
And the button text becomes "..."
When the API responds
Then the button re-enables
```

### 3.7 Registration with redirect preserves destination

```
Given the user is not authenticated
When the browser navigates to /register?redirect=%2Fscheduler
And the user registers successfully
Then the browser redirects to /scheduler (not /)
```

---

## 4. Registration API — Error Paths

### 4.1 Duplicate email returns 409

```
Given a user already exists with email "existing@profiletailors.com"
When the user attempts to register with that email
And fills password with "SecurePass123!"
And clicks "Create account"
Then the API returns 409 with ProblemDetail:
  {
    "title": "User already exists",
    "detail": "A user with email 'existing@profiletailors.com' already exists.",
    "status": 409
  }
And the error banner is visible with the text "A user with email 'existing@profiletailors.com' already exists."
And the user stays on /register
And the email field retains the input value
And the password field is cleared
```

### 4.2 Email already taken (case-insensitive check)

```
Given a user already exists with email "test@example.com"
When the user attempts to register with "TEST@EXAMPLE.COM"
And fills password with "SecurePass123!"
Then the API returns 409 (case-insensitive duplicate check)
```

### 4.3 Password too short (< 12 chars)

```
Given the user navigates to /register
When the user fills email with "newuser@example.com"
And fills password with "Ab1" (3 chars)
And clicks "Create account"
Then the API returns 400 with ProblemDetail:
  {
    "title": "Invalid registration input",
    "detail": "Password must contain at least 12 characters.",
    "status": 400
  }
And the error banner shows "Password must contain at least 12 characters."
And the user stays on /register
```

### 4.4 Password too long (> 128 chars)

```
Given the user navigates to /register
When the user fills email with "newuser@example.com"
And fills password with a string of 129 "a" characters
And clicks "Create account"
Then the API returns 400 with validation error
And the error message mentions maximum length
```

### 4.5 Invalid email format (server-side validation)

```
Given the user navigates to /register
When the user fills email with "not-valid-email"
And fills password with "SecurePass123!"
And clicks "Create account"
Then the API returns 400 with validation error
And the error message says "A valid email is required."
```

### 4.6 Empty email blocked by validation

```
Given the user navigates to /register
When the user fills password with "SecurePass123!"
And leaves email empty
And clicks "Create account"
Then the API returns 400 with validation error
And the error message says "Email is required."
```

### 4.7 Empty password blocked by validation

```
Given the user navigates to /register
When the user fills email with "valid@email.com"
And leaves password empty
And clicks "Create account"
Then the API returns 400 with validation error
And the error message says "Password is required."
```

### 4.8 Server error (500) shows generic message

```
Given the backend is unavailable or returns 500
When the user attempts to register
Then the error banner shows "Unable to create your account."
And the user stays on /register
```

### 4.9 Network error shows appropriate message

```
Given the backend is unreachable (network failure)
When the user attempts to register
Then the error banner shows a connection-related message
And the user stays on /register
```

### 4.10 Rate limiting (if applicable)

```
Given the user makes multiple registration attempts
After N consecutive requests
Then the API returns 429 Too Many Requests (if rate limiting is configured)
And the error banner shows a rate-limit message
```

---

## 5. Post-Registration Session Management

### 5.1 Session immediately active after registration

```
Given a successful registration
When the user lands on /
Then the user is authenticated
And the "Welcome back, {username}" heading is visible
And the sidebar shows navigation options (Scheduler, Analytics, Settings)
```

### 5.2 Page refresh maintains session via refresh token cookie for PENDING user

```
Given the user just registered and is on /
And the authenticated user's emailStatus is "PENDING"
When the user refreshes the page
Then POST /api/auth/refresh returns tokens
And the refresh token is rotated (new cookie set)
And a new access token is obtained
And the new access token includes emailStatus: "PENDING"
And the user stays on the dashboard (authenticated)
And the user display name is still visible
```

### 5.3 Authenticated user redirected from /register

```
Given the user is authenticated (has valid refresh-token cookie)
When the user navigates to /register
Then the browser redirects to /
```

### 5.4 Authenticated user redirected from /login

```
Given the user is authenticated
When the user navigates to /login
Then the browser redirects to /
```

---

## 6. Email Verification Flow

### 6.1 Email verification token generated on registration

```
Given a successful registration
Then the backend generates an email verification token
And stores a hashed version in the database
And publishes a UserRegistered domain event for email dispatch
And the user record has emailStatus: "PENDING"
```

### 6.2 Unverified user can log in and receives a PENDING session

```
Given a user registers but does not verify email
When the user logs in with correct credentials
Then login succeeds
And the response contains emailStatus: "PENDING"
And the access token claims include emailStatus: "PENDING"
And the user can access the dashboard
And no feature gating is applied within the authentication flow itself
```

### 6.3 Resend verification email

```
Given a user registers but loses the verification email
When the user POSTs to /api/auth/resend-verification with { email }
Then the API returns 202 Accepted
And a new verification token is generated
And the old token is invalidated
```

### 6.4 Resend with non-existent email returns 202 (no enumeration)

```
Given a user POSTs to /api/auth/resend-verification with non-existent email
Then the API returns 202 Accepted (no indication of existence)
```

### 6.5 Verify email with valid token

```
Given a user has a valid verification token
When the user POSTs to /api/auth/verify-email with { token }
Then the API returns tokens
And the user's emailStatus changes to "VERIFIED"
And the returned access token claims include emailStatus: "VERIFIED"
And the user can log in without issues
```

### 6.6 Verify email with expired/invalid token

```
Given a user has an invalid or expired verification token
When the user POSTs to /api/auth/verify-email with { token }
Then the API returns 400 with "Invalid verification token."
```

---

## 7. Feature Gating for Unverified Users

### 7.1 PENDING user can access dashboard but not gated features

```
Given an authenticated user has emailStatus "PENDING"
When the user lands on /
Then the dashboard is visible
And the session remains valid
And gated actions are blocked outside the authentication flow
```

### 7.2 PENDING user is blocked from connecting a social provider

```
Given an authenticated user has emailStatus "PENDING"
When the user tries to connect a social provider
Then the action is blocked
And the UI shows "Verify your email to continue" or equivalent messaging
Or the API returns 403 with code "EMAIL_VERIFICATION_REQUIRED"
```

### 7.3 PENDING user is blocked from publishing or scheduling content

```
Given an authenticated user has emailStatus "PENDING"
When the user tries to publish or schedule a post
Then the API returns 403 Forbidden
And the problem detail contains:
  - title: "Email verification required"
  - detail: "Please verify your email before using this feature."
  - status: 403
  - code: "EMAIL_VERIFICATION_REQUIRED"
  - type: "https://api.profiletailors.com/errors/email-verification-required"
```

### 7.4 PENDING user is blocked from inviting team members

```
Given an authenticated user has emailStatus "PENDING"
When the user tries to invite another user to the workspace
Then the action is blocked
And the API returns 403 with code "EMAIL_VERIFICATION_REQUIRED"
```

### 7.5 Previously gated actions become available after verification

```
Given an authenticated user has emailStatus "PENDING"
And gated actions are blocked
When the user verifies their email successfully
Then emailStatus becomes "VERIFIED"
And previously restricted actions become available
```

### 7.6 Billing / future gated features honor the same policy contract

```
Given the system defines gated AuthFeatures such as ACCESS_BILLING
When a user with emailStatus "PENDING" attempts a gated feature
Then the enforcement behavior is the same structured 403 EMAIL_VERIFICATION_REQUIRED response
```

---

## 8. Internationalization (i18n)

### 8.1 Register page in English

```
Given the locale is set to English
When the user navigates to /register
Then the heading reads "Create account"
And the submit button reads "Create account"
And the alternate label reads "Already have an account?"
And the alternate link reads "SIGN IN"
And the email placeholder is "you@example.com"
And the password placeholder is "At least 12 characters"
```

### 8.2 Register page in Spanish

```
Given the locale is set to Spanish
When the user navigates to /register
Then the heading reads "Crear cuenta"
And the submit button reads "Crear cuenta"
And the alternate label reads "¿Ya tienes una cuenta?"
And the alternate link reads "Iniciar sesión"
And the email placeholder is "tu@ejemplo.com"
And the password placeholder is "Al menos 12 caracteres"
```

### 8.3 Language switch on register page

```
Given the user is on /register
When the user switches from EN to ES
Then all visible text changes to Spanish
When the user switches back to EN
Then all visible text changes back to English
```

### 8.4 Error messages respect current language

```
Given the user is on /register with Spanish locale
When registration fails with password too short
Then the error message is displayed in Spanish
```

---

## 9. Security

### 9.1 Access token never persisted to localStorage

```
Given the user registers successfully
When the JavaScript runtime inspects localStorage
Then no auth tokens or access tokens are stored
And only non-sensitive data (if any) is persisted
```

### 9.2 Refresh token is HttpOnly cookie

```
Given a successful registration
When the Set-Cookie header is inspected
Then the refresh-token cookie has:
  - HttpOnly flag set
  - Secure flag set (in production)
  - Path set to "/api/auth" (not "/")
  - SameSite set appropriately
```

### 9.3 No credential exposure in URL

```
Given the user submits the registration form
Then the password is never visible in the URL
And the email is never visible in the URL
```

### 9.4 Password never logged or exposed in responses

```
Given a successful registration
When the API response is inspected
Then the password is not present in the response body
And the password hash is not present in the response body
```

### 9.5 Email enumeration prevention

```
Given a user attempts to register with existing email
When the API returns 409
Then the error message does not reveal additional account details
And the timing of the response is similar to successful registrations
```

### 9.6 CSRF protection via cookie + headers

```
Given the API expects specific headers
Then POST /api/auth/* requires Content-Type: application/json and Accept: application/vnd.api.v1+json
```

---

## 10. Error Banner Visual States

### 10.1 Error banner hidden by default

```
Given the user navigates to /register
Then no error banner is visible
```

### 10.2 Error banner appears on registration failure

```
Given a registration attempt fails
Then the error banner appears with:
  - Red border (border-error/30)
  - Red background (bg-error/10)
  - Red text (text-error)
  - The error detail message
```

### 10.3 Error banner clears on new form submission

```
Given a previous registration failure left an error banner
When the user submits the form again
Then the error banner is cleared
And the button shows loading state
```

### 10.4 Error banner cleared on navigation away

```
Given an error banner is visible on /register
When the user navigates to /login (via link)
Then the error is cleared
```

### 9.5 Password field cleared after error

```
Given a registration attempt fails
When the user attempts to correct the password
Then the password field is empty
```

---

## 11. Responsive Design

### 10.1 Register page renders on mobile viewport

```
Given a browser with iPhone 12 viewport (390x844)
When the user navigates to /register
Then the form is readable
And the email input is tappable
And the "Create account" button is tappable
And the page does not require horizontal scrolling
And the hero section adapts (feature cards stack vertically)
```

### 10.2 Register page renders on tablet viewport

```
Given a browser with iPad viewport (768x1024)
When the user navigates to /register
Then the layout adapts (grid changes from single to multi-column)
And the form remains usable
```

---

## 12. Accessibility

### 11.1 Register form has proper labels

```
Given the register form is rendered
Then each input has an associated <label> element
And each label has meaningful text ("EMAIL", "PASSWORD")
```

### 11.2 Keyboard navigation works

```
Given the user is on /register
When the user tabs through the form
Then the focus moves from email → password → submit button → SIGN IN link
And the focused element is visually indicated
```

### 11.3 Error messages are accessible

```
Given a registration attempt fails
When the error banner is displayed
Then the error is announced to screen readers
And the error is programmatically associated with the form
```

### 11.4 Color contrast meets WCAG AA

```
Given the register page in dark mode
Then all text meets WCAG AA contrast ratios
Given the register page in light mode
Then all text meets WCAG AA contrast ratios
```

### 11.5 Form submission has proper feedback

```
Given the user submits the form
When the request is in progress
Then the button is disabled
And the button text indicates loading ("...")
```

---

## 13. Test Scenarios Matrix

| ID   | Area               | Scenario                                     | Priority | Auth Required | API Required |
| ---- | ------------------ | -------------------------------------------- | -------- | ------------- | ------------ |
| 1.1  | Rendering          | Register page renders fully                  | P0       | No            | No           |
| 1.2  | Rendering          | Email input attributes                       | P1       | No            | No           |
| 1.3  | Rendering          | Password input attributes                    | P1       | No            | No           |
| 1.4  | Navigation         | Navigate between login and register          | P0       | No            | No           |
| 2.1  | Validation         | Empty fields blocked                         | P0       | No            | No           |
| 2.2  | Validation         | Invalid email format blocked                 | P0       | No            | No           |
| 3.1  | Registration API   | Successful registration + redirect           | P0       | No            | Yes          |
| 3.2  | Registration API   | Email normalization (lowercase)              | P1       | No            | Yes          |
| 3.3  | Registration API   | Whitespace trimmed                           | P1       | No            | Yes          |
| 3.5  | Registration API   | Workspace provisioned                        | P1       | No            | Yes          |
| 3.6  | Registration API   | Loading state on button                      | P1       | No            | Yes          |
| 3.7  | Registration API   | Registration with redirect                   | P0       | No            | Yes          |
| 4.1  | Registration API   | Duplicate email returns 409                  | P0       | No            | Yes          |
| 4.2  | Registration API   | Case-insensitive duplicate check             | P1       | No            | Yes          |
| 4.3  | Registration API   | Password too short returns 400               | P0       | No            | Yes          |
| 4.4  | Registration API   | Password too long returns 400                | P1       | No            | Yes          |
| 4.5  | Registration API   | Invalid email format (server)                | P0       | No            | Yes          |
| 4.8  | Registration API   | Server error shows generic message           | P1       | No            | Yes          |
| 5.1  | Session            | Session immediately active                   | P0       | No            | Yes          |
| 5.2  | Session            | Session survives page refresh                | P0       | Yes           | Yes          |
| 5.3  | Route Guards       | Authenticated user redirected from /register | P0       | Yes           | No           |
| 6.1  | Email Verification | Verification token generated                 | P1       | No            | Yes          |
| 6.3  | Email Verification | Resend verification email                    | P2       | No            | Yes          |
| 6.5  | Email Verification | Verify email with valid token                | P1       | No            | Yes          |
| 7.1  | i18n               | English locale                               | P1       | No            | No           |
| 7.2  | i18n               | Spanish locale                               | P1       | No            | No           |
| 8.1  | Security           | No token in localStorage                     | P0       | Yes           | Yes          |
| 8.2  | Security           | HttpOnly cookie flags                        | P1       | Yes           | Yes          |
| 9.2  | Error Banner       | Error display styling                        | P2       | No            | Yes          |
| 10.1 | Responsive         | Mobile viewport                              | P1       | No            | No           |
| 11.1 | Accessibility      | Form labels                                  | P2       | No            | No           |
| 11.2 | Accessibility      | Keyboard navigation                          | P2       | No            | No           |

**Priority definitions:**

- **P0**: Critical path — must pass for release. Covers happy path registration, validation,
  duplicate handling, session persistence.
- **P1**: Important — should pass for release. Covers i18n, email verification, security headers,
  mobile rendering.
- **P2**: Nice to have — quality polish. Covers visual styling, accessibility, edge cases.

---

## 14. Implementation Notes for Test Authors

### Test Setup

```typescript
// Example: Register a new user for testing
async function registerNewUser(page: Page, email: string, password: string) {
  await page.goto('/register')
  await page.fill('#email', email)
  await page.fill('#password', password)
  await page.click('button[type="submit"]')
  await page.waitForURL('/')
}
```

### Test Data

For each test, use a unique email to avoid conflicts:

```typescript
const testEmail = `e2e-register-${Date.now()}@profiletailors.com`
const testPassword = 'SecurePass123!'
```

### API Mocking Strategy

- **Happy path tests**: Use real backend with seeded test data
- **Error path tests**: Mock `POST /api/auth/register` via Playwright route interception
- **Network error tests**: Use Playwright `page.route` to abort requests

### Test Isolation

- Each test should use a separate browser context (Playwright contexts are isolated by default)
- Clear cookies between test scenarios using `context.clearCookies()`
- Do not share authenticated state across test files
- Use unique email addresses per test to avoid conflicts

### Data Cleanup

```sql
-- Test user cleanup (run after test suite)
DELETE FROM refresh_sessions WHERE principal_id IN (
  SELECT principal_id FROM identity WHERE email LIKE 'e2e-register-%'
);
DELETE FROM credentials WHERE principal_id IN (
  SELECT principal_id FROM identity WHERE email LIKE 'e2e-register-%'
);
DELETE FROM identity WHERE email LIKE 'e2e-register-%';
DELETE FROM workspace_members WHERE principal_id IN (
  SELECT principal_id FROM identity WHERE email LIKE 'e2e-register-%'
);
DELETE FROM workspaces WHERE name LIKE 'e2e-register-%';
```

---

## 15. CI Integration

- **Trigger**: On PR creation and push to main
- **Environment**: Requires running SPA dev server + backend + database
- **Prerequisites**: PostgreSQL running, dev seed data loaded
- **Parallelism**: Playwright projects can run in parallel by browser
- **Artifacts**: Playwright HTML report, screenshots on failure, trace on retry

---

## Appendix A: Routes Map

| Path         | Component     | Auth Required | Guest Only |
| ------------ | ------------- | ------------- | ---------- |
| `/login`     | AuthView      | No            | Yes        |
| `/register`  | AuthView      | No            | Yes        |
| `/`          | HomeView      | Yes           | No         |
| `/scheduler` | SchedulerView | Yes           | No         |
| `/analytics` | AnalyticsView | Yes           | No         |
| `/settings`  | SettingsView  | Yes           | No         |

---

## Appendix B: API Contract Reference

### POST /api/auth/register

```json
// Request
{ "email": "user@example.com", "password": "SecureP@ssw0rd" }

// 201 Created Response
{
  "principalId": "user-<uuid>",
  "email": "user@example.com",
  "username": "user",
  "emailStatus": "PENDING"
}

// 409 Conflict (duplicate email)
{
  "title": "User already exists",
  "detail": "A user with email 'user@example.com' already exists.",
  "status": 409
}

// 400 Bad Request (validation error)
{
  "title": "Invalid registration input",
  "detail": "Password must contain at least 12 characters.",
  "status": 400
}
```

### POST /api/auth/resend-verification

```json
// Request
{ "email": "user@example.com" }

// 202 Accepted Response (always returns 202)
```

### POST /api/auth/verify-email

```json
// Request
{ "token": "VERIFICATION_TOKEN_PLACEHOLDER" }

// 200 Response (same as login response)
{
  "accessToken": "...",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "principalId": "user-<uuid>",
  "email": "user@example.com",
  "username": "user",
  "emailStatus": "VERIFIED",
  "workspaceId": "workspace-<uuid>"
}

// 400 Bad Request
{
  "title": "Invalid verification token",
  "detail": "Invalid verification token.",
  "status": 400
}
```

### POST /api/auth/refresh

```
// Request: No body — reads refresh-token from HttpOnly cookie
// 200 Response: Same structure as register response
// 401 Response: { "title": "Refresh session invalid", "status": 401 }
```

---

## Appendix C: Registration Flow State Machine

```
┌─────────────┐
│  /register  │
│   (clean)   │
└──────┬──────┘
       │
       ▼
┌──────────────────┐
│  User fills form │
└──────┬───────────┘
       │
       ▼
┌──────────────────┐
│  HTML5 validate  │
│  (email, req)    │
└──────┬───────────┘
       │ pass
       ▼
┌──────────────────┐
│ POST /register   │
└──────┬───────────┘
       │
   ┌───┴───┐
   │       │
  201     4xx
   │       │
   ▼       ▼
┌──────────┐  ┌──────────────────┐
│ Redirect │  │ Show error banner│
│ to /     │  │ Stay on /register│
└──────────┘  └──────────────────┘
   │
   ▼
┌──────────────────┐
│ Dashboard loads  │
│ + session cookie │
└──────────────────┘
```
