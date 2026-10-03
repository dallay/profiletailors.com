# Delta for admin-authorization

## ADDED Requirements

### Requirement: Bulk fail-fast permission check

The bulk endpoint MUST check `platform.waitlist.invite` once up front and throw
`PlatformAccessDeniedException` (HTTP 403) before touching any entry when the permission is missing.
No new permission is introduced; role mapping is unchanged.

#### Scenario: Missing permission fails fast

- GIVEN a principal with no `WAITLIST_INVITE` permission
- WHEN the principal calls the bulk endpoint
- THEN the response is 403 and no entry state, invitation, or audit row changes

#### Scenario: Read-only role denied

- GIVEN a principal with only `SUPPORT_AGENT` assignment
- WHEN the principal calls the bulk endpoint
- THEN the response is 403 under default-deny
