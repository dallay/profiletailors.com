# E2E Test Plan: Login Flow

> Generated from browser exploration and codebase analysis on 2026-06-14.

## Scope

This plan covers the **login and authentication flow** across the Profile Tailors SPA
(`apps/web/app/`) and the SMP backend (`server/smp/`). It targets the complete user journey:
login page rendering → form interaction → API integration → session management → logout.

## Test Infrastructure

- **Framework**: Playwright (already configured at `apps/web/marketing/playwright.config.ts`).
  A separate Playwright config should be created at `apps/web/app/playwright.config.ts` for the
  SPA app.
- **App URL**: `http://localhost:5173` (Vite dev server, proxying `/api` to `http://localhost:8080`)
- **Backend URL**: `http://localhost:8080`
- **Browsers**: Chromium (primary), Firefox, WebKit
- **State persistence**: Playwright `storageState` for authenticated sessions
- **API mocking**: Use Playwright route interception for backend-dependent tests
- **Test seed data**: Pre-seeded database with known test user (e.g.
  `e2e-test@profiletailors.com` / `E2eTestPass123!`)

## Architecture Overview

```
┌──────────────────┐         ┌──────────────────┐
│  Vue SPA (5173)  │  /api   │  Spring Boot (8080)│
│  AuthView.vue     │ ──────→ │  LocalAuthController│
│  auth store       │  proxy  │  LoginUserHandler   │
│  auth-api.ts      │         │  R2DBC -> PostgreSQL│
└──────────────────┘         └──────────────────┘
```

### Key Contracts

| Endpoint             | Method | Request               | Success      | Failure                                  |
|----------------------|--------|-----------------------|--------------|------------------------------------------|
| `/api/auth/login`    | POST   | `{ email, password }` | 200 + tokens | 401 InvalidEmailPassword                 |
| `/api/auth/register` | POST   | `{ email, password }` | 200 + tokens | 409 UserAlreadyExists / 400 InvalidInput |
| `/api/auth/refresh`  | POST   | (HttpOnly cookie)     | 200 + tokens | 401 RefreshSessionNotActive              |
| `/api/auth/logout`   | POST   | (HttpOnly cookie)     | 204          | —                                        |
| `/api/auth/me`       | GET    | Bearer token          | 200 profile  | 401                                      |

### Auth Token Flow

```
Login ──→ POST /api/auth/login
              │
              ├─→ 200: { accessToken, principalId, email, username, expiresIn }
              │         + Set-Cookie: refresh-token (HttpOnly, Path=/api/auth)
              │
              └─→ Store accessToken in Pinia (memory only, never persisted)
                    │
                    └─→ GET /api/auth/me (Authorization: Bearer <token>)
                            │
                            └─→ Load user profile into store
```

---

## 1. Login Page Rendering

### 1.1 Full page renders correctly

```
Given a browser navigates to /login
Then the page title is "Profile Tailors — Social Media Management Platform"
And the heading "Welcome back" is visible
And the subtitle "Sign in to continue into your workspace dashboard." is visible
And the badge "LOCAL ACCESS" is visible
And the hero section with 3 feature cards (SECURITY, FOCUS, WORKFLOW) is visible
And the email input field is present with label "EMAIL"
And the password input field is present with label "PASSWORD"
And the "Sign in" submit button is present
And the "Need an account?" text is visible
And the "REGISTER" link points to /register
```

### 1.2 Email input attributes

```
Given a browser navigates to /login
Then the email input has type="email"
And the email input has autocomplete="email"
And the email input is required
And the email input has a placeholder matching "you@example.com"
```

### 1.3 Password input attributes

```
Given a browser navigates to /login
Then the password input has type="password"
And the password input has autocomplete="current-password"
And the password input is required
```

### 1.4 Registration page renders (shared component)

```
Given a browser navigates to /register
Then the heading "Create account" is visible
And the submit button text is "Create account"
And the "Already have an account?" text is visible
And the "SIGN IN" link points to /login
And the email and password inputs are present (no username field)
```

### 1.5 Theme toggle on auth page

```
Given a browser navigates to /login
When the user clicks the theme toggle (dark/light)
Then the page theme changes accordingly
And both email and password inputs remain visible and functional
```

---

## 2. Form Validation (Frontend)

### 2.1 Empty fields trigger HTML5 validation

```
Given a browser navigates to /login
When the user clicks "Sign in" with both fields empty
Then the browser shows HTML5 validation for the email field
And no network request is made to /api/auth/login
```

### 2.2 Invalid email format blocked by browser

```
Given a browser navigates to /login
When the user types "not-an-email" in the email field
And types "password123" in the password field
And clicks "Sign in"
Then the browser shows HTML5 validation for invalid email format
And no network request is made
```

### 2.3 Empty password blocked

```
Given a browser navigates to /login
When the user types "valid@email.com" in the email field
And leaves password field empty
And clicks "Sign in"
Then the browser shows HTML5 validation for the password field
And no network request is made
```

---

## 3. Login API — Success Path

### 3.1 Successful login redirects to dashboard

```
Given the backend has a user with email "e2e-test@profiletailors.com"
And the correct password is "E2eTestPass123!"
When the user navigates to /login
And fills email with "e2e-test@profiletailors.com"
And fills password with "E2eTestPass123!"
And clicks "Sign in"
Then the browser makes POST /api/auth/login with { email: "e2e-test@profiletailors.com", password: "E2eTestPass123!" }
And the request includes headers: Content-Type: application/json, Accept: application/vnd.api.v1+json
And the request includes credentials: "include"
And the response status is 200
And the response body contains accessToken (non-empty string)
And the response body contains principalId (non-empty string)
And the response body contains email matching the input email
And the response sets Set-Cookie header for the refresh token
Then the browser redirects to /
And the "Welcome back" dashboard header is visible
And the user display name appears in the navigation
```

### 3.2 Login with redirect preserves destination

```
Given the user is not authenticated
When the browser navigates to /analytics
Then the browser is redirected to /login?redirect=%2Fanalytics
When the user logs in successfully
Then the browser redirects to /analytics (not /)
```

### 3.3 Successful login response includes expected token fields

```
Given a valid login request
When the API responds with AuthTokens
Then the response includes:
  - accessToken: non-empty JWT string
  - tokenType: "Bearer"
  - expiresIn: positive integer
  - principalId: non-empty string matching "user-*" pattern
  - email: the user's email
  - username: non-null string
```

### 3.4 Profile loaded after login (GET /api/auth/me)

```
Given a successful login
After the login response
Then the browser makes GET /api/auth/me with Authorization: Bearer <token>
And the profile is loaded into the UI
```

### 3.5 Login button shows loading state

```
Given the user is on /login
When the user submits the form
Then the submit button is disabled
And the button text becomes "..."
When the API responds
Then the button re-enables
```

---

## 4. Login API — Error Paths

### 4.1 Invalid email or password shows error

```
Given the user navigates to /login
When the user fills email with "wrong@email.com"
And fills password with "incorrect"
And clicks "Sign in"
Then the API returns 401 with ProblemDetail { title: "Invalid credentials", detail: "Invalid email or password." }
And the error banner is visible with the text "Invalid email or password."
And the error banner has red styling (border-error/30, bg-error/10)
And the URL remains /login
And the user is NOT redirected
```

### 4.2 Non-existent email shows same error (no user enumeration)

```
Given the user navigates to /login
When the user fills email with "nonexistent-12345@example.com"
And fills password with "SomePass123!"
Then the API returns 401 "Invalid email or password."
And the error message does NOT reveal whether the email exists
```

### 4.3 Server error (500) shows generic message

```
Given the backend is unavailable or returns 500
When the user attempts to login
Then the error banner shows "Unable to sign in." (or the server's detail message)
And the user stays on /login
```

### 4.4 Network error shows appropriate message

```
Given the backend is unreachable (network failure)
When the user attempts to login
Then the error banner shows a connection-related message
And the user stays on /login
```

### 4.5 Rate limiting (if applicable)

```
Given the user makes multiple failed login attempts
After N consecutive failures
Then the API returns 429 Too Many Requests (if rate limiting is configured)
And the error banner shows a rate-limit message
```

---

## 5. Registration

### 5.1 Successful registration creates account and logs in

```
Given the user navigates to /register
When the user fills email with "new-e2e-user@example.com"
And fills password with "SecurePass123!"
And clicks "Create account"
Then the API returns 200 with AuthTokens
And a refresh-token cookie is set
And the user is redirected to /
And the dashboard is visible
```

### 5.2 Duplicate email returns 409

```
Given a user already exists with email "existing@example.com"
When the user attempts to register with that email
Then the API returns 409 with ProblemDetail { title: "User already exists", detail: "A user with email 'existing@example.com' already exists." }
And the error banner shows "A user with email 'existing@example.com' already exists."
And the user stays on /register
```

### 5.3 Registration with invalid email

```
Given the user navigates to /register
When the user fills email with "invalid"
And fills password with "ValidPass123!"
Then the frontend HTML5 validation blocks the request (if type="email")
Or the API returns 400 with "Invalid registration input" (if bypassed)
```

### 5.4 Registration with short password (< 8 chars)

```
Given the user navigates to /register
When the user fills email with "newuser@example.com"
And fills password with "Ab1" (3 chars)
Then the API returns 400 InvalidRegistrationInputException
And the error detail says "Password must contain at least 8 characters."
```

### 5.5 Registration with whitespace in email is normalized

```
Given the user registers with email "  Test@Example.com  "
When the request is sent
Then the backend normalizes: trims whitespace and lowercases to "test@example.com"
And the registration succeeds
And subsequent login with "Test@Example.com" also works (case-insensitive)
```

---

## 6. Authentication State / Session Hydration

### 6.1 Page refresh maintains session via refresh token cookie

```
Given the user is logged in (has valid refresh-token cookie)
When the user refreshes the page
Then the app calls POST /api/auth/refresh on load
And the refresh token is rotated (new cookie set)
And a new access token is obtained
And the user stays on the dashboard (authenticated)
And the user display name is still visible
```

### 6.2 Expired session shows login page after refresh

```
Given the user has an expired/invalid refresh-token cookie
When the user refreshes the page
Then POST /api/auth/refresh returns 401
And the user is redirected to /login
And the session is cleared
```

### 6.3 App starts without any session shows login

```
Given a fresh browser with no cookies
When the user navigates to /
Then POST /api/auth/refresh returns 401 (no cookie)
And the user is redirected to /login
```

### 6.4 Hydration does not block page render

```
Given the user navigates to any page
While POST /api/auth/refresh is in flight
The page should not show a blank/loading state for auth-sensitive UI
```

---

## 7. Token Refresh (Silent 401 Retry)

### 7.1 API fetch retries on 401 with successful refresh

```
Given the user is authenticated with accessToken "expired-token"
When the user triggers an API call that returns 401
Then the auth store calls POST /api/auth/refresh
And the refresh succeeds (new access token returned)
And the original API call is retried with the new token
And the original operation completes successfully
And the user remains authenticated
```

### 7.2 API fetch retry fails → logout

```
Given the user is authenticated with accessToken "expired-token"
When the user triggers an API call that returns 401
And POST /api/auth/refresh also returns 401 (session expired)
Then the store clears the session
And the user is redirected to /login
```

### 7.3 Non-401 errors skip refresh

```
Given the user makes an API call
When the server returns 403 Forbidden
Then no refresh attempt is made
And the error is propagated to the caller
```

---

## 8. Logout

### 8.1 Logout clears session and redirects to login

```
Given the user is authenticated on /dashboard
When the user triggers logout (via navigation menu)
Then POST /api/auth/logout is called
And the refresh-token cookie is cleared
And the store clears access token and user data
And the browser navigates to /login
And the login form is visible
```

### 8.2 After logout, protected routes redirect to login

```
Given the user has logged out
When the user manually navigates to /scheduler
Then the browser redirects to /login?redirect=%2Fscheduler
```

### 8.3 Logout is idempotent

```
Given the user is on /login (no active session)
When the user triggers logout (if possible from that state)
Then no error is thrown
And the user stays on /login
```

---

## 9. Route Guards

### 9.1 All protected routes redirect unauthenticated users

```
Given the user is not authenticated
For each protected route:
  - /scheduler
  - /analytics
  - /settings
  - /integrations/linkedin/callback
When the user navigates to the route
Then the browser redirects to /login?redirect=<encoded-path>
```

### 9.2 Guest routes redirect authenticated users

```
Given the user is authenticated
When the user navigates to /login or /register
Then the browser redirects to /
```

### 9.3 Redirect parameter propagates correctly through login flow

```
Given the user navigates to /settings
And is redirected to /login?redirect=%2Fsettings
When the user logs in successfully
Then the browser navigates to /settings (not /)
```

---

## 10. Internationalization (i18n)

### 10.1 Login page in English

```
Given the locale is set to English
When the user navigates to /login
Then the heading reads "Welcome back"
And the submit button reads "Sign in"
And the alternate label reads "Need an account?"
And the alternate link reads "REGISTER"
And the email placeholder is "you@example.com"
```

### 10.2 Login page in Spanish

```
Given the locale is set to Spanish
When the user navigates to /login
Then the heading reads "Bienvenido de nuevo"
And the submit button reads "Iniciar sesión"
And the alternate label reads "¿Necesitas una cuenta?"
And the alternate link reads "CREAR CUENTA"
And the email placeholder is "tu@ejemplo.com"
```

### 10.3 Language switch on login page

```
Given the user is on /login
When the user switches from EN to ES
Then all visible text changes to Spanish
When the user switches back to EN
Then all visible text changes back to English
```

### 10.4 Error messages respect current language

```
Given the user is on /login with Spanish locale
When login fails with invalid credentials
Then the error message is displayed in Spanish (if i18n is applied to API errors)
```

---

## 11. Security

### 11.1 Access token never persisted to localStorage

```
Given the user logs in successfully
When the JavaScript runtime inspects localStorage
Then no auth tokens or access tokens are stored
And only non-sensitive data (if any) is persisted
```

### 11.2 Refresh token is HttpOnly cookie

```
Given a successful login
When the Set-Cookie header is inspected
Then the refresh-token cookie has:
  - HttpOnly flag set
  - Secure flag set (in production)
  - Path set to "/api/auth" (not "/")
  - SameSite set appropriately
```

### 11.3 No credential exposure in URL

```
Given the user submits the login form
Then the password is never visible in the URL
And the email is never visible in the URL
```

### 11.4 Failed login response does not expose user existence

```
Given an invalid email "nonexistent@example.com"
When login fails
Then the error is "Invalid email or password."
And the error does NOT say "User not found" vs "Wrong password"
```

### 11.5 Cross-site request forgery consideration

```
Given the API expects specific headers
Then POST /api/auth/* requires Content-Type: application/json and Accept: application/vnd.api.v1+json
```

---

## 12. Error Banner Visual States

### 12.1 Error banner hidden by default

```
Given the user navigates to /login
Then no error banner is visible
```

### 12.2 Error banner appears on login failure

```
Given a login attempt fails
Then the error banner appears with:
  - Red border (border-error/30)
  - Red background (bg-error/10)
  - Red text (text-error)
  - The error detail message
```

### 12.3 Error banner clears on new form submission

```
Given a previous login failure left an error banner
When the user submits the form again
Then the error banner is cleared
And the button shows loading state
```

### 12.4 Error banner cleared on navigation away

```
Given an error banner is visible on /login
When the user navigates to /register (via link)
Then the error is cleared
```

---

## 13. Registration-to-Login Flow

### 13.1 New user can immediately login after registration

```
Given a new user registers successfully (and is auto-logged in)
When the user logs out
And then logs in with the same email and password
Then login succeeds
And the user is redirected to /
```

### 13.2 Switch between login and register preserves form state? (Intentional non-requirement)

```
Given the user fills the email field on /login
When the user clicks "REGISTER" to go to /register
Then the email field on /register is empty (form state is not preserved across routes)
```

---

## 14. Responsive Design

### 14.1 Login page renders on mobile viewport

```
Given a browser with iPhone 12 viewport (390x844)
When the user navigates to /login
Then the form is readable
And the email input is tappable
And the "Sign in" button is tappable
And the page does not require horizontal scrolling
```

### 14.2 Login page renders on tablet viewport

```
Given a browser with iPad viewport (768x1024)
When the user navigates to /login
Then the layout adapts (grid changes from single to multi-column)
```

---

## 15. Accessibility

### 15.1 Login form has proper labels

```
Given the login form is rendered
Then each input has an associated <label> element
And each label has meaningful text
```

### 15.2 Keyboard navigation works

```
Given the user is on /login
When the user tabs through the form
Then the focus moves from email → password → submit button → REGISTER link
And the focused element is visually indicated
```

### 15.3 Color contrast meets WCAG AA

```
Given the login page in dark mode
Then all text meets WCAG AA contrast ratios
Given the login page in light mode
Then all text meets WCAG AA contrast ratios
```

---

## 16. Test Scenarios Matrix

| ID   | Area          | Scenario                                 | Priority | Auth Required | API Required |
|------|---------------|------------------------------------------|----------|---------------|--------------|
| 1.1  | Rendering     | Login page renders fully                 | P0       | No            | No           |
| 1.4  | Rendering     | Registration page renders                | P0       | No            | No           |
| 3.1  | Login API     | Successful login + redirect              | P0       | No            | Yes          |
| 3.2  | Login API     | Login preserves redirect param           | P0       | No            | Yes          |
| 4.1  | Login API     | Invalid credentials error                | P0       | No            | Yes          |
| 4.2  | Login API     | No user enumeration                      | P1       | No            | Yes          |
| 6.1  | Session       | Session survives page refresh            | P0       | Yes           | Yes          |
| 6.2  | Session       | Expired session → login page             | P0       | Yes           | Yes          |
| 6.3  | Session       | No session → login redirect              | P0       | No            | Yes          |
| 7.1  | Token Refresh | 401 + successful refresh retry           | P1       | Yes           | Yes          |
| 7.2  | Token Refresh | 401 + failed refresh → logout            | P1       | Yes           | Yes          |
| 8.1  | Logout        | Logout clears session                    | P0       | Yes           | Yes          |
| 9.1  | Route Guards  | Protected routes redirect to login       | P0       | No            | Yes          |
| 9.2  | Route Guards  | Guest routes redirect when authenticated | P0       | Yes           | No           |
| 10.1 | i18n          | English locale                           | P1       | No            | No           |
| 10.2 | i18n          | Spanish locale                           | P1       | No            | No           |
| 11.1 | Security      | No token in localStorage                 | P0       | Yes           | Yes          |
| 11.2 | Security      | HttpOnly cookie flags                    | P1       | Yes           | Yes          |
| 12.2 | Error Banner  | Error display styling                    | P2       | No            | Yes          |
| 14.1 | Responsive    | Mobile viewport                          | P1       | No            | No           |
| 15.1 | Accessibility | Form labels                              | P2       | No            | No           |

**Priority definitions:**

- **P0**: Critical path — must pass for release. Covers happy path login, basic error handling,
  session persistence, and route guards.
- **P1**: Important — should pass for release. Covers i18n, token refresh, security headers, mobile
  rendering.
- **P2**: Nice to have — quality polish. Covers visual styling, accessibility, edge cases.

---

## 17. Implementation Notes for Test Authors

### Test Setup

For tests requiring authentication:

1. Use Playwright `page.request` to call `POST /api/auth/login` with test credentials
2. Save the resulting storage state (cookies) via `page.context().storageState()`
3. Reuse the storage state in authenticated test suites via Playwright projects

```typescript
// Example setup: authenticate once
async function authenticateAs(page: Page, email: string, password: string) {
  await page.request.post('http://localhost:8080/api/auth/login', {
    data: { email, password },
    headers: { 'Content-Type': 'application/json', Accept: 'application/vnd.api.v1+json' },
  })
}
```

### Test Teardown

For tests that create users:

1. Delete the test user via admin API or direct DB query to keep tests idempotent

### API Mocking Strategy

- **Backend-dependent tests** (login success/failure, session): Use real backend with seeded test
  data
- **Token refresh tests**: Mock `POST /api/auth/refresh` via Playwright route interception
- **Network error tests**: Use Playwright `page.route` to abort requests

### Test Isolation

- Each test should use a separate browser context (Playwright contexts are isolated by default)
- Clear cookies between test scenarios using `context.clearCookies()`
- Do not share authenticated state across test files

### Data Cleanup

```sql
-- Test user cleanup (run after test suite)
DELETE FROM credentials WHERE principal_id IN (
  SELECT principal_id FROM identity WHERE email LIKE 'e2e-test-%'
);
DELETE FROM identity WHERE email LIKE 'e2e-test-%';
```

---

## 18. CI Integration

- **Trigger**: On PR creation and push to main
- **Environment**: Requires running SPA dev server + backend + database
- **Configuration**: optional `E2E_TEST_USER_EMAIL`, plus `API_BASE_URL`; the test-only password is public fixture data and is never reused outside test environments
- **Parallelism**: Playwright projects can run in parallel by browser
- **Artifacts**: Playwright HTML report, screenshots on failure, trace on retry

---

## Appendix A: Routes Map

| Path                              | Component                | Auth Required | Guest Only |
|-----------------------------------|--------------------------|---------------|------------|
| `/login`                          | AuthView (login mode)    | No            | Yes        |
| `/register`                       | AuthView (register mode) | No            | Yes        |
| `/`                               | HomeView (dashboard)     | Yes           | No         |
| `/scheduler`                      | SchedulerView            | Yes           | No         |
| `/analytics`                      | AnalyticsView            | Yes           | No         |
| `/settings`                       | SettingsView             | Yes           | No         |
| `/integrations/linkedin/callback` | LinkedInCallbackView     | Yes           | No         |

## Appendix B: API Contract Reference

### POST /api/auth/login

```json
// Request
{ "email": "user@example.com", "password": "SecureP@ssw0rd" }

// 200 Response
{
  "accessToken": "example-access-token",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "principalId": "user-<uuid>",
  "email": "user@example.com",
  "username": "user"
}

// 401 Response
{
  "title": "Invalid credentials",
  "detail": "Invalid email or password.",
  "status": 401
}
```

### POST /api/auth/register

```json
// Request
{ "email": "user@example.com", "password": "SecureP@ssw0rd" }

// 409 Response (duplicate email)
{
  "title": "User already exists",
  "detail": "A user with email 'user@example.com' already exists.",
  "status": 409
}

// 400 Response (invalid input)
{
  "title": "Invalid registration input",
  "detail": "Password must contain at least 8 characters.",
  "status": 400
}
```

### POST /api/auth/refresh

```
// Request: No body — reads refresh-token from HttpOnly cookie
// 200 Response: Same structure as login response
// 401 Response: { "title": "Refresh session invalid", "status": 401 }
```

### POST /api/auth/logout

```
// Request: No body — reads refresh-token from HttpOnly cookie
// 204 Response: No Content
// Side effect: Set-Cookie: refresh-token=; Max-Age=0; Path=/api/auth; HttpOnly
```
