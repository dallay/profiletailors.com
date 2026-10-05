# Login Experience Specification

## Purpose

Define the observable login/register redesign without changing authentication protocols or adding
SSO.

## Requirements

### Requirement: Responsive Themed Authentication Layout

Login and registration MUST use a centered single-column presentation, remain usable from 320px
upward, and preserve readable contrast and visible focus in light and dark themes. The promotional
hero, feature cards, SSO controls, and SSO scaffolding MUST NOT appear.

#### Scenario: Responsive rendering

- GIVEN login or registration at a 320px or desktop viewport
- WHEN the page renders
- THEN all fields and actions MUST fit without horizontal scrolling
- AND the authentication task MUST appear in one centered column

#### Scenario: Theme rendering

- GIVEN the active theme is light or dark
- WHEN the page renders
- THEN text, controls, errors, and focus indicators MUST remain perceivable at WCAG AA contrast

### Requirement: Correct Profile Tailors Branding

The page MUST render the shared dark-on-light and light-on-dark Profile Tailors logotype assets
according to the SPA-selected light/dark theme, with an accessible Profile Tailors text equivalent
and transactional login/register copy. Asset selection MUST follow the explicit SPA theme even when
the operating-system `prefers-color-scheme` value disagrees, and the icon-only asset MUST NOT be
described as a wordmark.

#### Scenario: Shared asset renders

- GIVEN either authentication mode
- WHEN branding is inspected under the SPA-selected light or dark theme
- THEN the corresponding shared logotype asset and accessible “Profile Tailors” name MUST be present
- AND the symbol MUST remain visible even when the operating-system color preference disagrees

### Requirement: Accessible Form Behavior

Fields MUST have visible labels and suitable email/password autocomplete semantics. Password
visibility controls MUST expose their action and pressed state. Submission validation MUST expose
field errors through `aria-invalid` and `aria-describedby`, then focus the first invalid field;
field errors MUST NOT use `role="alert"`.

#### Scenario: Invalid submission

- GIVEN required values are missing or invalid
- WHEN the enabled primary action is submitted
- THEN all applicable field errors MUST render accessibly
- AND focus MUST move to the first invalid field without an API request

#### Scenario: Password visibility

- GIVEN a password is concealed
- WHEN its named visibility control is activated
- THEN the value MUST become visible and the control MUST announce the new state

### Requirement: Submission and Error States

During submission, the form MUST set `aria-busy`, prevent duplicate requests, make text fields
readonly, disable mutable controls/navigation and the primary action, and show mode-specific loading
text. Authentication failures MUST show generic form-level alerts and focus them without revealing
account existence; network failures MUST remain retryable.

#### Scenario: Loading prevents duplicates

- GIVEN a valid form submission is pending
- WHEN the user submits again
- THEN exactly one request MUST remain in flight
- AND the form MUST expose its busy and loading state

#### Scenario: Authentication failure

- GIVEN the server rejects credentials
- WHEN the response is presented
- THEN a generic `role="alert"` message MUST receive focus
- AND it MUST NOT identify whether the email exists

### Requirement: State and Navigation Contracts

Switching through named `login` and `register` routes MUST preserve email but MUST clear passwords
and consent state. Registration entry MUST appear only when enabled; sign-in MUST always remain
available. Terms and Privacy links MUST target the existing public `/terms` and `/privacy` pages
without inventing SPA routes.

#### Scenario: Mode switch

- GIVEN an email, password, and registration consent have been entered
- WHEN navigation switches authentication mode by named route
- THEN the email MUST remain
- AND passwords and consent MUST be cleared

#### Scenario: Legal navigation

- GIVEN authentication content is visible
- WHEN Terms or Privacy is activated
- THEN navigation MUST reach the corresponding existing public legal page
