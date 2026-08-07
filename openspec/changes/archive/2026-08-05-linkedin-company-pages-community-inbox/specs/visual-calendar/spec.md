# Delta for Visual Calendar — LinkedIn Company Pages PR2

## ADDED Requirements

### Requirement: Imported Page Posts Are Calendar Read Models

The visual calendar backend MAY surface imported Company Page posts through `GET /api/publishing/social-content/calendar`, using the existing workspace, version, range, lifecycle, actor, cursor, and limit contract. Imported items MUST remain distinguishable from Profile Tailors publications by provider, actor, origin, and lifecycle. Calendar responses MUST preserve `nextCursor` and MUST NOT expose credentials.

#### Scenario: Calendar displays an imported Page post
- GIVEN an active workspace contains an imported LinkedIn Company Page post
- WHEN the member requests a valid calendar range with required authentication and workspace headers
- THEN the response MUST include the post when it falls in range
- AND the item MUST identify its Page actor and imported origin

### Requirement: Imported Page Posts Cannot Enter Publication Writes

Imported Company Page posts MUST be informational calendar/detail records only. They MUST NOT be draggable, rescheduled, edited, cancelled, deleted, quick-created from, or submitted to personal-profile publishing endpoints. Existing personal-profile calendar and publication behavior MUST remain unchanged.

#### Scenario: Read-only Page item is not reschedulable
- GIVEN the calendar contains an imported Page post with `mutationAllowed = false`
- WHEN a client attempts to reschedule or edit it using a publication write contract
- THEN the system MUST reject the operation
- AND the imported post MUST remain unchanged

#### Scenario: Personal publication remains separate
- GIVEN the same calendar range contains a personal-profile publication and an imported Page post
- WHEN the calendar is requested
- THEN both records MUST retain distinct account/actor identity and origin
- AND only the personal publication MAY participate in publication lifecycle operations
