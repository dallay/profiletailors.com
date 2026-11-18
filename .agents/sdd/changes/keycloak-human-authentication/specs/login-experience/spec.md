# Delta for Login Experience

## MODIFIED Requirements

### Requirement: Responsive Themed Authentication Layout

Login and registration MUST use a centered single-column presentation, remain usable from 320px
upward, and preserve readable contrast and visible focus in light and dark themes. The promotional
hero, feature cards, and generic SSO scaffolding MUST NOT appear. After the approved
human-authentication cutover, the login entry MUST start the Keycloak BFF flow and MUST NOT collect a
local human password.

#### Scenario: Responsive rendering

- GIVEN login or registration at a 320px or desktop viewport
- WHEN the page renders
- THEN all fields and actions MUST fit without horizontal scrolling
- AND the authentication task MUST appear in one centered column

#### Scenario: Theme rendering

- GIVEN the active theme is light or dark
- WHEN the page renders
- THEN text, controls, errors, and focus indicators MUST remain perceivable at WCAG AA contrast

#### Scenario: Keycloak login entry

- GIVEN the approved human-authentication cutover is complete
- WHEN a guest opens the login route
- THEN the login entry MUST start the Keycloak BFF flow
- AND the page MUST NOT collect a local human password

### Requirement: Submission and Error States

During any approved pre-cutover form submission, the form MUST set `aria-busy`, prevent duplicate
requests, make text fields readonly, disable mutable controls/navigation and the primary action, and
show mode-specific loading text. After the approved human-authentication cutover, Keycloak
authentication failures MUST show a safe generic form-level error without exposing account existence;
network failures MUST remain retryable.

#### Scenario: Loading prevents duplicates

- GIVEN a valid form submission is pending
- WHEN the user submits again
- THEN exactly one request MUST remain in flight
- AND the form MUST expose its busy and loading state

#### Scenario: Keycloak authentication failure

- GIVEN Keycloak authentication fails or cannot be completed
- WHEN the response is presented
- THEN a generic `role="alert"` message MUST receive focus
- AND it MUST NOT identify whether the email exists

### Requirement: State and Navigation Contracts

Switching through named `login` and `register` routes MUST preserve email but MUST clear any
pre-cutover password and consent state. Registration entry MUST appear only when enabled by the
current registration policy; sign-in MUST always remain available. After the approved
human-authentication cutover, signup and credential creation MUST be performed by Keycloak. Terms and
Privacy links MUST target the existing public `/terms` and `/privacy` pages without inventing SPA
routes.

#### Scenario: Mode switch

- GIVEN an email, password, and registration consent have been entered
- WHEN navigation switches authentication mode by named route
- THEN the email MUST remain
- AND passwords and consent MUST be cleared

#### Scenario: Legal navigation

- GIVEN authentication content is visible
- WHEN Terms or Privacy is activated
- THEN navigation MUST reach the corresponding existing public legal page

## REMOVED Requirements

### Requirement: Accessible Form Behavior

Fields MUST have visible labels and suitable email/password autocomplete semantics. Password visibility
controls MUST expose their action and pressed state. Submission validation MUST expose field errors
through `aria-invalid` and `aria-describedby`, then focus the first invalid field; field errors MUST
NOT use `role="alert"`.

#### Scenario: Invalid submission

- GIVEN required values are missing or invalid
- WHEN the primary action is submitted
- THEN all applicable field errors MUST render accessibly
- AND focus MUST move to the first invalid field without an API request

#### Scenario: Password visibility

- GIVEN a password is concealed
- WHEN its named visibility control is activated
- THEN the value MUST become visible and the control MUST announce the new state

## ADDED Requirements

### Requirement: Dashboard and Admin Use Separate OIDC Clients

After the approved human-authentication cutover, the dashboard MUST authenticate through the
confidential `pt-app` client and admin MUST authenticate through the confidential `pt-admin` client.
Both clients MUST use issuer `https://keycloak.ahome.quest/realms/pt` on Keycloak `26.3.5` and their
exact BFF callbacks. `pt-app` MUST NOT have a minimum ACR requirement. Admin login MUST use the
dedicated `browser-admin-mfa` flow, require `Minimum ACR Value = 2`, and issue LoA 2 with `acr=2` only
after password plus OTP. The BFF MUST validate issuer, subject, expected audience, admin client
identity, and `acr=2`. Admin login MUST require password plus OTP regardless of whether the request
omits `acr_values` or requests level 1. Starting admin login from an existing dashboard SSO session
MUST still require OTP, and OTP MUST be requested only once for that admin login. This behavior
requires acceptance testing; this requirement does not assert that it has been tested.

#### Scenario: Dashboard login uses its confidential client callback

- GIVEN a guest starts login from the dashboard
- WHEN the BFF redirects the guest to Keycloak and receives the callback
- THEN Keycloak MUST authenticate through `pt-app`
- AND the client MUST NOT require a minimum ACR value
- AND the callback MUST be `https://api.profiletailors.com/login/oauth2/code/pt-app`

#### Scenario: Admin login requires one OTP step despite dashboard SSO

- GIVEN a user has an active dashboard SSO session
- WHEN the user starts admin login without `acr_values` or requesting level 1
- THEN Keycloak MUST authenticate through `pt-admin` using `browser-admin-mfa`
- AND the flow MUST require `Minimum ACR Value = 2` and password and OTP before issuing LoA 2 with `acr=2`
- AND OTP MUST be requested only once during the admin login
- AND the BFF MUST validate issuer `https://keycloak.ahome.quest/realms/pt`, subject, expected audience, admin client identity, and `acr=2`
- AND the callback MUST be `https://api.profiletailors.com/login/oauth2/code/pt-admin`

#### Scenario: Dashboard-only session cannot access admin API

- GIVEN a user has only a valid `pt-app` dashboard session
- WHEN the user calls `/api/admin/**` directly
- THEN the request MUST be denied without valid `pt-admin` authentication state with `acr=2`

### Requirement: Accessible Authentication Actions

Any fields presented by an enabled pre-cutover flow MUST have visible labels and suitable autocomplete
semantics. After the approved human-authentication cutover, the Profile Tailors login entry MUST
provide an accessible action to begin Keycloak authentication and MUST NOT present a local password
field. Validation for other fields MUST expose errors through `aria-invalid` and `aria-describedby`,
then focus the first invalid field; field errors MUST NOT use `role="alert"`.

#### Scenario: Invalid submission

- GIVEN required values are missing or invalid
- WHEN the enabled primary action is submitted
- THEN all applicable field errors MUST render accessibly
- AND focus MUST move to the first invalid field without an API request

#### Scenario: Keycloak login entry

- GIVEN the approved human-authentication cutover is complete
- WHEN a guest opens the login route
- THEN an accessible action to begin Keycloak authentication MUST be available
- AND no local human password field MUST be rendered

#### Scenario: Password visibility in an approved pre-cutover flow

- GIVEN a password is concealed in an approved pre-cutover flow
- WHEN its named visibility control is activated
- THEN the value MUST become visible and the control MUST announce the new state
