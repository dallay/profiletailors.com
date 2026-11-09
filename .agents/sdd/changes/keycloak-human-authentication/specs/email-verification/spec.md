# Delta for Email Verification

## ADDED Requirements

### Requirement: Post-Cutover Email Verification Uses Keycloak

After the approved human-authentication cutover is complete, human email verification MUST be
performed by Keycloak. Profile Tailors MUST NOT issue, validate, or consume local human
email-verification tokens. The existing local token lifecycle, endpoints, and delivery requirements
apply only to pre-cutover operation and MUST NOT override this requirement. Profile Tailors retains local product/account state and applies its local eligibility policies.
After Profile Tailors links a Keycloak identity to a local principal by `(issuer, sub)`, it MUST
synchronize the local email only from validated Keycloak state that marks the email as verified, and
only when the email does not conflict with another local principal or local email. Email MUST NOT be
used to select, link, or merge accounts. When Keycloak state is unverified, Profile Tailors MUST NOT
synchronize the email and MUST apply existing local eligibility policies. When a verified Keycloak
email conflicts with another local principal or local email, Profile Tailors MUST NOT synchronize or
replace the local email. The email conflict alone MUST NOT deny login. Profile Tailors MUST establish
or continue a relying-party session only if the already-linked local principal passes existing local
authentication and account eligibility. Local resource and platform authorization MUST be evaluated
under local rules when the principal requests the protected resource. Email MUST NOT change which
principal is selected. Missing, ambiguous, disabled, or disallowed identity mappings MUST still fail
closed.
This contract does not declare the integration implemented or tested.

#### Scenario: Human signup requires email verification after cutover

- GIVEN a human signs up through Keycloak after the approved cutover
- WHEN email verification is required
- THEN Keycloak MUST perform the verification flow
- AND Profile Tailors MUST NOT send or consume a local verification token

#### Scenario: Local verification endpoint receives a token after cutover

- GIVEN the approved human-authentication cutover is complete
- WHEN a client submits a local email-verification token
- THEN Profile Tailors MUST NOT verify the human email or create an authenticated session from that token

#### Scenario: Verified Keycloak email has no local conflict

- GIVEN a Keycloak identity is linked to a local principal by `(issuer, sub)`
- AND validated Keycloak state marks its email as verified
- AND the email conflicts with no other local principal or local email
- WHEN Profile Tailors evaluates the linked identity's email
- THEN Profile Tailors MUST synchronize the local email from the validated Keycloak state

#### Scenario: Keycloak email is not verified

- GIVEN a Keycloak identity is linked to a local principal by `(issuer, sub)`
- AND validated Keycloak state does not mark its email as verified
- WHEN Profile Tailors evaluates the linked identity's email
- THEN Profile Tailors MUST NOT synchronize the local email
- AND Profile Tailors MUST fail closed according to local eligibility

#### Scenario: Verified Keycloak email conflicts locally

- GIVEN a Keycloak identity is linked to a local principal by `(issuer, sub)`
- AND validated Keycloak state marks its email as verified
- AND the email conflicts with another local principal or local email
- WHEN Profile Tailors evaluates the linked identity's email
- THEN Profile Tailors MUST NOT synchronize the local email
- AND Profile Tailors MUST NOT replace the local email
- AND the email conflict alone MUST NOT deny login
- AND Profile Tailors MUST establish or continue a relying-party session only if the already-linked local principal passes existing local authentication and account eligibility
- AND local resource and platform authorization MUST be evaluated under local rules when the principal requests a protected resource
- AND the email MUST NOT change the selected local principal

#### Scenario: Verified email conflict does not bypass local authentication eligibility

- GIVEN a Keycloak identity is linked by `(issuer, sub)` to a local principal
- AND its verified email conflicts with another local principal or local email
- AND the linked local principal fails an existing local authentication or account eligibility rule
- WHEN Profile Tailors evaluates whether to establish or continue a relying-party session
- THEN Profile Tailors MUST NOT synchronize or replace the local email
- AND Profile Tailors MUST NOT establish or continue the session
- AND Profile Tailors MUST NOT select another principal based on email

#### Scenario: Local authorization remains enforced on protected resource requests

- GIVEN a human has a relying-party session for a locally eligible principal
- AND the verified Keycloak email conflicts with another local principal or local email
- AND the principal lacks authorization for a requested protected resource
- WHEN the human requests that resource
- THEN Profile Tailors MUST deny the request under the applicable local authorization rules
- AND the email conflict MUST NOT change the selected principal or local email
