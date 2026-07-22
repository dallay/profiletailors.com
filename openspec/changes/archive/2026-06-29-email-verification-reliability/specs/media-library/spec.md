# Delta for Media Library

## ADDED Requirements

### Requirement: Email Verification Required for Media Upload

The system MUST require `emailStatus = VERIFIED` before an authenticated user can create or upload
media assets to the workspace media library.

This policy MUST align with other verification-gated product capabilities so unverified users
receive the same denial reason and no media asset is created or uploaded on their behalf.

#### Scenario: Unverified user cannot create uploadable asset

- GIVEN an authenticated user with `emailStatus = UNVERIFIED`
- WHEN the user requests media asset creation or upload
- THEN the system MUST deny the request
- AND the denial MUST indicate email verification is required

#### Scenario: Verified user can proceed with media upload flow

- GIVEN an authenticated user with `emailStatus = VERIFIED`
- WHEN the user requests media asset creation or upload with otherwise valid data
- THEN the system MUST evaluate the request under normal media-library rules
