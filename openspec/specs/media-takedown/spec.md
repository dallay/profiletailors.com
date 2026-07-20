# Media Takedown Specification

## Purpose

Define the takedown report lifecycle and asset suspension workflow for copyright and content moderation. Enables DMCA-compliant reporting, staff review, counter-notice process, and audit trail with email notifications at each state transition.

## Requirements

### Requirement: SUSPENDED Asset Status

`MediaAssetStatus` MUST include `SUSPENDED` as a valid state alongside `PROCESSING`, `PENDING_UPLOAD`, `UPLOADING`, `READY`, `FAILED`, and `DELETED`. Assets in `SUSPENDED` state MUST be excluded from media picker/composer views, publication previews, and all public API responses unless the caller holds `workspace:governance:media:read`.

#### Scenario: Assets do not surface in picker
- GIVEN a media asset has status `SUSPENDED`
- WHEN the media library list query executes for a picker/composer session
- THEN the query SHALL exclude assets with status `SUSPENDED`
- AND the asset SHALL NOT appear in results

### Requirement: TakedownReport Domain

The system MUST model a `TakedownReport` with the following state machine: `SUBMITTED → UNDER_REVIEW → RESOLVED` (resolution: `TAKEDOWN_APPROVED | REPORT_REJECTED`). Counter-notice path: `COUNTER_NOTICE_SUBMITTED → RESTORED | ESCALATED`. Reports SHALL contain: report ID, asset ID, workspace ID, reporter identity, reason/category, evidence/description, status, resolution, and timestamps.

### Requirement: REST API

| Endpoint | Auth | Description |
|----------|------|-------------|
| `POST /api/governance/media/takedown-reports` | Any workspace member | Submit report |
| `GET /api/governance/media/takedown-reports` | `workspace:governance:media:read` | List reports |
| `GET /api/governance/media/takedown-reports/{id}` | `workspace:governance:media:read` | Report detail |
| `POST /api/governance/media/takedown-reports/{id}/action` | `workspace:governance:media:takedown` | Approve/reject |
| `POST /api/governance/media/takedown-reports/{id}/counter-notice` | Asset owner/workspace member | Submit counter-notice |

#### Scenario: Submit valid report
- GIVEN an authenticated workspace member viewing a `READY` media asset
- WHEN the member submits via `POST /api/governance/media/takedown-reports` with `reason`, `description`, `assetId`
- THEN the system SHALL create a `TakedownReport` with status `SUBMITTED`
- AND the response SHALL return `201` with the report ID
- AND an audit event SHALL be recorded

#### Scenario: Unauthorized user blocked
- GIVEN a workspace `MEMBER` WITHOUT `workspace:governance:media:takedown`
- WHEN accessing `POST .../action`
- THEN system SHALL return `403 Forbidden`
- AND no state change SHALL occur

### Requirement: Approval Flow

When staff approves a takedown, the asset MUST transition to `SUSPENDED`, the report to `RESOLVED`/`TAKEDOWN_APPROVED`, an audit event `MEDIA_TAKEDOWN_APPROVED` SHALL be recorded, and an email SHALL be sent to the submitter. On rejection, the asset remains `READY`, report → `RESOLVED`/`REPORT_REJECTED`, audit event `MEDIA_TAKEDOWN_REJECTED`, email sent.

#### Scenario: Approve → asset suspended
- GIVEN a `TakedownReport` with status `UNDER_REVIEW`
- WHEN a user with `workspace:governance:media:takedown` calls `POST .../action` with `action = "APPROVE"`
- THEN asset SHALL transition to `SUSPENDED`
- AND report SHALL transition to `RESOLVED` with resolution `TAKEDOWN_APPROVED`
- AND submitter SHALL receive a resolution email
- AND an audit event SHALL be recorded

#### Scenario: Reject → asset stays READY
- GIVEN a `TakedownReport` with status `UNDER_REVIEW`
- WHEN authorized user calls `POST .../action` with `action = "REJECT"`
- THEN asset SHALL remain `READY`
- AND report SHALL transition to `RESOLVED`/`REPORT_REJECTED`
- AND submitter SHALL receive a rejection email

### Requirement: Counter-Notice Flow

The asset owner or workspace member MAY submit a counter-notice on a `SUSPENDED` asset. If staff accepts the counter-notice, asset → `READY`, report → `RESTORED`, submitter emailed. If rejected, asset stays `SUSPENDED`, report → `ESCALATED`.

#### Scenario: Counter-notice accepted → restored
- GIVEN a `TakedownReport` with resolution `TAKEDOWN_APPROVED` and asset `SUSPENDED`
- WHEN the owner submits a counter-notice via `POST .../counter-notice`
- AND staff reviews and approves the counter-notice
- THEN asset SHALL transition to `READY`
- AND report SHALL transition to `RESTORED`
- AND submitter SHALL receive a restoration email

#### Scenario: Counter-notice rejected → stays suspended
- GIVEN a counter-notice has been submitted on a `SUSPENDED` asset
- WHEN staff reviews and rejects the counter-notice
- THEN asset SHALL remain `SUSPENDED`
- AND report SHALL transition to `ESCALATED`

### Requirement: Audit Trail

Every state transition (submission, approval, rejection, counter-notice, restoration, escalation) MUST be recorded in the `audit_events` table with: actor ID, action type (`MEDIA_TAKEDOWN_SUBMITTED`, `MEDIA_TAKEDOWN_APPROVED`, `MEDIA_TAKEDOWN_REJECTED`, `COUNTER_NOTICE_SUBMITTED`, `MEDIA_RESTORED`, `MEDIA_ESCALATED`), target type (`TAKEDOWN_REPORT`), target ID, and metadata JSON containing report ID and reason.
