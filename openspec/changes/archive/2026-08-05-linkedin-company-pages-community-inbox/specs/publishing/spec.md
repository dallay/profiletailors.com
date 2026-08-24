# Delta for Publishing — LinkedIn Company Pages PR2

## ADDED Requirements

### Requirement: Community Read Wiring Does Not Change Personal Publishing

The PR2 Community Management adapter MUST remain behind provider-neutral ports and Spring/Mediator
application handlers. It MUST be used only for gated Company Page discovery and read import. It MUST
NOT become the credential, OAuth, or publisher path for personal LinkedIn profiles, and it MUST NOT
change the existing `/v2/userinfo`, signed OAuth state, `w_member_social`, or
`RealLinkedInPublisher` semantics.

#### Scenario: Personal OAuth regression remains absent

- GIVEN a member completes the existing personal LinkedIn OAuth flow with valid signed state
- WHEN the connection is finalized and `/v2/userinfo` is resolved
- THEN the existing personal connection and userinfo behavior MUST remain unchanged
- AND no Community Management operation MUST be required

#### Scenario: Page access is never inferred from personal publishing

- GIVEN a workspace has an active personal-profile LinkedIn account
- WHEN a request targets a Company Page
- THEN the system MUST require a separate organization-page account and all Page gates
- AND it MUST NOT call the personal publisher as a Page adapter

### Requirement: No Real Community Operation by Default

Spring wiring MAY register the provider adapter and deterministic fakes, but default configuration
MUST keep discovery, import, inbox, replies, and Page publishing disabled. Disabled operations MUST
fail closed before external HTTP or credential resolution; enabling them requires explicit approved
configuration and evidence.

#### Scenario: Disabled Page operation makes no external call

- GIVEN default Community Management configuration
- WHEN a Page sync is requested
- THEN the application MUST return a safe-off denial
- AND `RealLinkedInPublisher` and the Community Management HTTP transport MUST not be called
