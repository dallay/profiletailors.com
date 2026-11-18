# Delta for Password Recovery UI

## ADDED Requirements

### Requirement: Post-Cutover Password Recovery Uses Keycloak

After the approved human-authentication cutover is complete, a human password-recovery request MUST
use the Keycloak-owned recovery flow. Profile Tailors MUST NOT issue, validate, or consume local
human password-reset tokens. The existing Profile Tailors forgot/reset forms and capability behavior
apply only to pre-cutover operation and MUST NOT override this requirement.

#### Scenario: Guest requests password recovery after cutover

- GIVEN the approved human-authentication cutover is complete
- WHEN a guest requests password recovery
- THEN the user MUST be directed into the Keycloak-owned recovery flow
- AND Profile Tailors MUST NOT send a local password-recovery request or expose account existence

#### Scenario: Legacy reset token is presented after cutover

- GIVEN the approved human-authentication cutover is complete
- WHEN a client submits a legacy local password-reset token
- THEN Profile Tailors MUST NOT use the token to change a human credential
