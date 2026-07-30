# Delta for App Shell

## MODIFIED Requirements

### Requirement: Auth Route Gate

`App.vue` MUST render standalone route content without the authenticated shell for named routes `login`, `register`, `forgot-password`, `reset-password`, and `verify-email`. All other routes MUST retain the authenticated shell. Reset-password MUST be classified standalone without requiring a guest session.

(Previously: only login/register rendered `AuthView` outside the shell; all other routes rendered `AppShell`.)

#### Scenario: Authentication and recovery routes are standalone
- GIVEN the active route is login, register, forgot-password, reset-password, or verify-email
- WHEN `App.vue` renders
- THEN the route content MUST render without `AppShell`
- AND authenticated-shell providers MUST NOT mount

#### Scenario: Reset remains standalone for authenticated user
- GIVEN an authenticated user opens the named reset-password route
- WHEN `App.vue` renders
- THEN reset content MUST render outside `AppShell`
- AND session state MUST NOT redirect the user away

#### Scenario: Non-auth route renders the shell
- GIVEN the active route is outside the standalone auth/recovery set
- WHEN `App.vue` renders
- THEN `AppShell` MUST render
- AND standalone authentication content MUST NOT replace it
