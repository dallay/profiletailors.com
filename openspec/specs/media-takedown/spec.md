# Media Takedown Specification

> Reconciled to match shipped implementation (archive date: 2026-07-22).
> Counter-notice flow removed from scope. Detail endpoint intentionally omitted (YAGNI).
> Permission keys use dashes per codebase convention.

## Overview

Define the takedown report lifecycle and asset suspension workflow for copyright and content
moderation. Enables DMCA-compliant reporting, staff review, and audit trail with email
notifications at each state transition.

## Requirements

### Requirement: SUSPENDED Asset Status

`MediaAssetStatus` MUST include `SUSPENDED` as a valid state alongside `PROCESSING`,
`PENDING_UPLOAD`, `UPLOADING`, `READY`, `FAILED`, and `DELETED`. Assets in `SUSPENDED` state MUST be
excluded from media picker/composer views, publication previews, and all public API responses unless
the caller holds `workspace:governance:media-read`.

#### Scenario: Assets do not surface in picker

- GIVEN a media asset has status `SUSPENDED`
- WHEN the media library list query executes for a picker/composer session
- THEN the query SHALL exclude assets with status `SUSPENDED`
- AND the asset SHALL NOT appear in results

### Requirement: TakedownReport Domain

The system MUST model a `TakedownReport` with status enum `TakedownReportStatus` containing
`REPORTED`, `APPROVED`, `DISMISSED`, and `SUSPENDED` (reserved for future use — not produced by the
state machine). State transitions: `REPORTED → APPROVED` (on staff approve), `REPORTED → DISMISSED`
(on staff reject). Reports SHALL contain: report ID (String), asset ID, workspace ID, reporter
identity, category, description, evidence URLs, status, and timestamps.

#### Scenario: Submit valid report

- GIVEN an authenticated workspace member viewing a `READY` media asset
- WHEN the member submits via `POST /api/governance/media/takedown-reports` with `reason`,
  `description`, `assetId`
- THEN the system SHALL create a `TakedownReport` with status `REPORTED`
- AND the response SHALL return `201` with the report ID
- AND an audit event SHALL be recorded via `AuditHook.onMutation`

### Requirement: REST API

| Method | Path                                                  | Auth                                  | Description    |
|--------|-------------------------------------------------------|---------------------------------------|----------------|
| POST   | `/api/governance/media/takedown-reports`              | `workspace:governance:media-read`     | Submit report  |
| GET    | `/api/governance/media/takedown-reports`              | `workspace:governance:media-read`     | List reports   |
| POST   | `/api/governance/media/takedown-reports/{id}/approve` | `workspace:governance:media-takedown` | Approve report |
| POST   | `/api/governance/media/takedown-reports/{id}/reject`  | `workspace:governance:media-takedown` | Reject report  |

The detail endpoint `GET /api/governance/media/takedown-reports/{id}` was intentionally omitted as
YAGNI — the list endpoint plus the per-report approve/reject sub-paths provide sufficient
addressability. The counter-notice endpoint was removed from scope.

#### Scenario: Unauthorized user blocked

- GIVEN a workspace `MEMBER` WITHOUT `workspace:governance:media-takedown`
- WHEN accessing `POST .../approve` or `POST .../reject`
- THEN system SHALL return `403 Forbidden`
- AND no state change SHALL occur

### Requirement: Approval Flow

When staff approves a takedown, the asset MUST transition to `SUSPENDED`, the report status to
`APPROVED`, an audit event `MEDIA_TAKEDOWN_APPROVED` SHALL be recorded via `AuditHook.onMutation`,
and a `TakedownApproved` domain event SHALL be published for email dispatch. On rejection,
the report status becomes `DISMISSED`, asset remains `READY`, `MEDIA_TAKEDOWN_REJECTED` audit
recorded, and `TakedownRejected` domain event published.

#### Scenario: Approve → asset suspended

- GIVEN a `TakedownReport` with status `REPORTED`
- WHEN a user with `workspace:governance:media-takedown` calls `POST .../approve`
- THEN asset SHALL transition to `SUSPENDED`
- AND report SHALL transition to `APPROVED`
- AND workspace admins SHALL receive a notification email
- AND an audit event SHALL be recorded

#### Scenario: Reject → asset stays READY

- GIVEN a `TakedownReport` with status `REPORTED`
- WHEN authorized user calls `POST .../reject`
- THEN asset SHALL remain `READY`
- AND report SHALL transition to `DISMISSED`
- AND submitter SHALL receive a rejection email

### Requirement: SUSPENDED Badge

`MediaLibraryView.vue` SHALL render a warning-styled "Suspended" badge when assets with status
`SUSPENDED` appear in reviewer/admin contexts. The badge follows the existing status-badge patterns.

### Requirement: Email Notifications

Three email templates dispatched via domain-event consumers:

| Template              | Trigger          | Consumer                            |
|-----------------------|------------------|-------------------------------------|
| Takedown Confirmation | Report submitted | `SendTakedownReportedEmailConsumer` |
| Takedown Approved     | Staff approves   | `SendTakedownApprovedEmailConsumer` |
| Takedown Rejected     | Staff rejects    | `SendTakedownRejectedEmailConsumer` |

Templates live in `TakedownEmailTemplates.kt` under `governance/infrastructure/email/`. Consumers
resolve workspace admins via `WorkspaceOwnershipRepository` + `PrincipalIdentityLookup`. All emails
use idempotency keys.

### Requirement: Audit Trail

Every state transition (submission, approval, rejection) MUST be recorded via
`AuditHook.onMutation(action, ...)` with action strings `MEDIA_TAKEDOWN_REPORTED`,
`MEDIA_TAKEDOWN_APPROVED`, and `MEDIA_TAKEDOWN_REJECTED`. The audit mechanism uses the existing
generic `AuditHook` contract — no dedicated event-type enum was added.

### Out of Scope

The following capabilities were explicitly removed from scope:

- Counter-notice submit / review / accept / reject / restore workflow
- Detail endpoint `GET /reports/{id}`
- Feature flag for takedown endpoints (always-on)
- Public DMCA agent registration page
- Third-party reporting API integration (Lumen, etc.)

## Usage

The requirements in this document drive:

- Domain model: `TakedownReport`, `TakedownReportStatus`, `MediaAssetStatus.SUSPENDED`
- HTTP API: `TakedownController` (4 endpoints)
- Authorization: `GovernanceAuthorizationService` (`media-read`, `media-takedown`)
- Event publishing: `TakedownReported`, `TakedownApproved`, `TakedownRejected`
- Notification consumers: `SendTakedownReportedEmailConsumer`, `SendTakedownApprovedEmailConsumer`,
  `SendTakedownRejectedEmailConsumer`

## Troubleshooting

- **SUSPENDED assets not surfacing**: Confirm the media query is filtering by `setOf(READY)` (
  default) or includes `SUSPENDED` only for admin contexts.
- **Email not sent on approval**: Verify the workspace has owners resolvable via
  `WorkspaceOwnershipRepository` and `PrincipalIdentityLookup`.
- **Idempotent retries on `report` endpoint**: Duplicate `(workspaceId, assetId, reportedById)`
  tuples return the existing report without firing side effects.

## References

- Issue: `DALLAY-499`
- Change proposal: `../../changes/archive/2026-07-22-media-copyright-takedown/proposal.md`
- Design: `../../changes/archive/2026-07-22-media-copyright-takedown/design.md`
- Tasks: `../../changes/archive/2026-07-22-media-copyright-takedown/tasks.md`
