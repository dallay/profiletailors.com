# Community Inbox Specification

## Purpose

Define read-only imported Company Page post data and the policy that keeps Community Management
safely disabled until verified evidence exists.

## Requirements

### Requirement: Read-Only Imported Page Content

The system MUST model imported posts with workspace, provider, organization-page actor, external
identity, publication timestamps, origin, lifecycle, expiry, and optional local-publication
reconciliation. Imported posts MAY be shown in calendar and detail reads. They MUST expose
`mutationAllowed = false`; this PR MUST NOT enable comments, replies, webhooks, publishing-as-page,
or other write operations.

#### Scenario: Imported post is visible but immutable

- GIVEN a permitted imported LinkedIn Page post exists in the active workspace
- WHEN calendar or post detail is requested
- THEN the response MUST include its external identity and lifecycle
- AND it MUST indicate that mutation is not allowed

### Requirement: All-Evidence Safe-Off Gate

Discovery and import MUST be denied unless every gate passes for the same workspace and social
account: Community Management approval, `ADMIN` role, required organization read scopes (
`r_organization_social` and `r_organization_social_feed`), a non-blank supported LinkedIn API
version, and a non-blank retention-policy version. Missing or mismatched evidence MUST produce a
typed denial before any provider call. Default discovery, import, inbox, and reply gates MUST be
disabled. Read-only discovery/import MAY be explicitly enabled after these gates pass; inbox,
replies, and Page publishing MUST remain disabled in PR2.

#### Scenario Outline: One missing gate denies before provider access

- GIVEN all evidence is valid except <missing gate>
- WHEN Page discovery or post sync is requested
- THEN the operation MUST be denied with the reason for <missing gate>
- AND the provider MUST receive no request

Examples:
| missing gate |
| approval |
| administrator role |
| organization read scope |
| API version |
| retention-policy version |

#### Scenario: Safe defaults remain off

- GIVEN no explicit feature configuration enables Community Management
- WHEN an authenticated workspace member requests discovery, import, inbox, or reply
- THEN the system MUST deny the operation
- AND it MUST NOT expose or resolve provider credentials

### Requirement: Personal OAuth and Publishing Are Separate

Company Page evidence and read adapters MUST use a distinct organization-page capability path. They
MUST NOT alter personal-profile OAuth state validation, `/v2/userinfo`, personal scopes, or
`RealLinkedInPublisher` behavior. A personal-profile connection MUST NOT be treated as a Company
Page actor.

#### Scenario: Personal profile cannot satisfy Page access

- GIVEN a workspace has only a valid personal LinkedIn connection
- WHEN Page discovery or Page import is requested
- THEN the system MUST deny it as the wrong account kind
- AND personal publishing behavior MUST remain available under its existing rules
