# Delta for Email Verification UI

## ADDED Requirements

### Requirement: Post-Cutover Verification Uses Keycloak

After the approved human-authentication cutover is complete, verification links and verification
outcomes MUST be handled by Keycloak. The existing SPA token landing route and local backend
submission behavior apply only to pre-cutover operation. Profile Tailors MUST NOT treat a legacy
local verification token as proof of human authentication or create a relying-party session from it.

#### Scenario: User opens a Keycloak verification link

- GIVEN the approved human-authentication cutover is complete
- WHEN a user follows a Keycloak-owned email verification link
- THEN the verification flow MUST remain within the Keycloak-owned lifecycle
- AND the Profile Tailors SPA MUST NOT submit a local verification token

#### Scenario: Legacy local verification link is opened after cutover

- GIVEN the approved human-authentication cutover is complete
- WHEN a user opens a legacy Profile Tailors verification link
- THEN the SPA MUST NOT submit its token to the local verification endpoint
- AND the SPA MUST NOT imply that the token authenticated the user
