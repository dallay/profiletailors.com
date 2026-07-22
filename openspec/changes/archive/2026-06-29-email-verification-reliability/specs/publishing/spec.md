# Delta for Publishing

## ADDED Requirements

### Requirement: Email Verification Required for Publishing and Social Connection

The system MUST require `emailStatus = VERIFIED` before a user can publish content or connect a
social account.

This verification gate MUST apply consistently across immediate publishing, scheduled publishing
requests, and social connection initiation or completion flows.

#### Scenario: Unverified user cannot publish

- GIVEN an authenticated user with `emailStatus = UNVERIFIED`
- WHEN the user attempts to create, queue, or publish content
- THEN the system MUST deny the request
- AND the denial MUST indicate email verification is required

#### Scenario: Unverified user cannot connect a social account

- GIVEN an authenticated user with `emailStatus = UNVERIFIED`
- WHEN the user attempts to initiate or complete a social connection flow
- THEN the system MUST deny the request
- AND the denial MUST indicate email verification is required

#### Scenario: Verified user can use gated publishing capabilities

- GIVEN an authenticated user with `emailStatus = VERIFIED`
- WHEN the user attempts to publish or connect a social account with otherwise valid input
- THEN the system MUST evaluate the request under normal publishing rules
