# Privacy DSAR Specification

## Purpose

Define the Data Subject Access Request (DSAR) aggregate, lifecycle state machine, request types, authorization model, rate limits, audit requirements, deletion workflow, and frontend UI for ACCESS, EXPORT, CORRECTION, and DELETION requests under GDPR/CCPA.

---

## Requirements

### Requirement: DSAR Request Types and State Machine

The system MUST support four DSAR types: `ACCESS`, `EXPORT`, `CORRECTION`, `DELETION`. Every request MUST follow the state machine: `PENDING → COMPLETED | REJECTED | FAILED`. Transitions from `COMPLETED`, `REJECTED`, or `FAILED` MUST be terminal. A `REJECTED` request MUST carry a `rejection_reason`.

| From | To | Trigger |
|------|----|---------|
| PENDING | COMPLETED | Handler finishes successfully |
| PENDING | REJECTED | Validation failure or manual rejection |
| PENDING | FAILED | Unrecoverable system error |
| Any terminal | — | No further transitions allowed |

Note: `IN_REVIEW` was considered during design but intentionally omitted from the initial implementation. The state machine transitions directly from `PENDING` to a terminal state. If a manual review step is needed in the future, `IN_REVIEW` can be reintroduced as a non-terminal intermediate state between `PENDING` and `COMPLETED | REJECTED`.

#### Scenario: Full lifecycle for a successful request

- GIVEN a DSAR of type `ACCESS` is submitted
- WHEN processing completes successfully
- THEN the status MUST transition `PENDING → COMPLETED`
- AND `completed_at` MUST be set to the completion timestamp

#### Scenario: Failed request reaches terminal state

- GIVEN a DSAR of type `EXPORT` is submitted
- AND the infrastructure fails unrecoverably
- WHEN the handler catches the exception
- THEN the status MUST be `FAILED`
- AND `completed_at` MUST NOT be set
- AND `rejection_reason` MUST be null

### Requirement: DataSubjectRequest Aggregate

`DataSubjectRequest` MUST be an aggregate root with the following fields:

| Field | Type | Notes |
|-------|------|-------|
| `id` | `DataSubjectRequestId` | Value type wrapping UUID |
| `type` | `DsarType` | `ACCESS`, `EXPORT`, `CORRECTION`, `DELETION` |
| `status` | `DsarStatus` | `PENDING`, `COMPLETED`, `REJECTED`, `FAILED` |
| `requested_by` | `PrincipalId` | Submitting principal |
| `requested_by_email` | String | Snapshot of email at time of request |
| `workspace_id` | `WorkspaceId?` | Null for global (e.g., deletion spans all workspaces) |
| `notes` | String? | Optional user-provided notes |
| `correction_data` | JSON? | Fields to correct (for `CORRECTION` type) |
| `result_ref` | String? | URL or path to result payload |
| `created_at` | Instant | |
| `updated_at` | Instant | |
| `completed_at` | Instant? | Set when terminal |
| `expires_at` | Instant? | Result download expiry |
| `rejection_reason` | String? | Required for `REJECTED` |

#### Scenario: Aggregate is created in PENDING state

- GIVEN a `DataSubjectRequest` is submitted
- WHEN the command handler processes it
- THEN the request MUST have status `PENDING`
- AND `created_at` MUST be set
- AND `expires_at` MUST be `created_at + 30 days` (regulatory retention)

### Requirement: Authorization

The system MUST allow only the following to submit a DSAR:
- **The data subject themselves** (authenticated as the `USER` principal matching `requested_by`)
- **Workspace admins** for workspace-scoped requests affecting workspace members

DELETION requests MUST require the subject's own identity. Admins MUST NOT submit deletion on behalf of others.

#### Scenario: User submits own DSAR

- GIVEN an authenticated `USER` principal
- WHEN they submit an ACCESS request
- THEN the request MUST be created with `requested_by` set to their principal ID

#### Scenario: Admin cannot delete on behalf of others

- GIVEN a workspace admin
- WHEN they attempt to submit a DELETION request for another user
- THEN the system MUST reject with `unauthorized`

### Requirement: Rate Limits

The system MUST enforce:

| Limit | Value |
|-------|-------|
| Max DSAR requests per user per day | 3 |
| Correction cooldown | 7 days between successful corrections of the same field |
| Max export payload size (inline) | 10 MB |
| Download URL TTL | 7 days |

#### Scenario: Rate limit blocks fourth request

- GIVEN a user submitted 3 DSARs today
- WHEN they submit a fourth
- THEN the system MUST reject with `rate_limit_exceeded`

### Requirement: Audit

Every DSAR lifecycle event MUST be logged to `audit_events` via a `PrivacyMutationAuditor`. The auditor SHALL follow the `TenancyMutationAuditor` pattern. Cross-workspace DSAR events MUST use a sentinel `workspace_id` value to distinguish them from workspace-scoped events.

| Event | `event_type` |
|-------|-------------|
| DSAR submitted | `dsar.submitted` |
| DSAR status change | `dsar.status_changed` |
| DSAR completed | `dsar.completed` |
| DSAR rejected | `dsar.rejected` |
| DSAR failed | `dsar.failed` |

#### Scenario: DSAR lifecycle is fully audited

- GIVEN a DSAR is submitted, processed, and completed
- WHEN the audit trail is inspected
- THEN there MUST be `dsar.submitted`, `dsar.status_changed`, and `dsar.completed` events

### Requirement: Deletion Request Pre-Validation

Before any deletion phase, the system MUST validate:
1. The principal is not the sole owner of any workspace (`WorkspaceOwnershipPolicy`)
2. No publications are in `PENDING`/`PUBLISHING` state
3. No outstanding contractual obligations (active subscriptions)

If sole owner, the system MUST reject with `sole_workspace_owner` and a message directing to transfer ownership first.

#### Scenario: Deletion blocked for sole workspace owner

- GIVEN a principal who is the sole `OWNER` of workspace W
- WHEN a DELETION request is submitted
- THEN the system MUST reject with `sole_workspace_owner`
- AND the error MUST direct to transfer ownership first

### Requirement: Deletion Phase 1 — Anonymization

| Table | Action |
|-------|--------|
| `user_identities` | Replace `email`, `username` with `[REDACTED on {timestamp}]` |
| `principals.display_identity` | Set to `[REDACTED]` |
| `audit_events.details_json` | Redact PII via scan-and-replace |
| `waitlist_entries` | Anonymize email and clear metadata for matching entries |
| `email_verification_tokens` | Delete all tokens for the principal |

### Requirement: Deletion Phase 2 — Removal

| Entity | Action |
|--------|--------|
| `workspace_memberships` | Set status to `REMOVED` for all memberships |
| `refresh_sessions` | Delete all sessions |
| `api_key_credentials` | Delete all API keys |
| `social_connections` | Delete OAuth connections |
| `secure_credentials` | Delete encrypted tokens |

### Requirement: Deletion Phase 3 — Cleanup

| Entity | Action |
|--------|--------|
| `media_assets` | Set status to `DELETED` |
| `workspace_file_blobs` | Set status to `READY_FOR_GC` |
| `publications` | Cancel any `DRAFT`/`SCHEDULED`/`QUEUED` publications |

### Requirement: Legal Holds — What Cannot Be Deleted

The following tables MUST NOT have rows deleted or anonymized beyond PII redaction:

| Table | Reason |
|-------|--------|
| `audit_events` | Legal obligation (GDPR Art. 17.3.b) |
| `delivery_attempts` | Delivery proof |
| `consent_records` | Consent proof |
| `publications` (completed only) | Published content history |
| `local_password_credentials` | Auth audit (anonymize principal reference if needed) |

#### Scenario: Deletion completes all three phases

- GIVEN a DELETION request passes pre-validation
- WHEN the handler executes
- THEN Phase 1 MUST anonymize PII in `user_identities`, `principals`, `waitlist_entries`
- AND Phase 2 MUST remove memberships, revoke sessions, delete credentials
- AND Phase 3 MUST mark media assets as `DELETED`
- AND `audit_events` MUST remain intact (rows preserved, PII redacted)

### Requirement: Privacy Settings UI

The settings page MUST include a "Privacy" section with:

| Component | Behavior |
|-----------|----------|
| Request type selector | Dropdown: Access, Export, Correction, Deletion |
| Notes field | Optional textarea for user context |
| Submit button | Creates DSAR, shows confirmation |
| Status list | Table of past requests: type, status, submitted date, download link |
| Download link | Visible when EXPORT/ACCESS is COMPLETED with result URL |
| Correction form | Email and username fields with validation |
| Deletion warning | Confirmation dialog warning of irreversible action |

#### Scenario: User submits an export request from settings

- GIVEN the privacy settings page
- WHEN the user selects "Export", adds notes, and clicks Submit
- THEN a new `DataSubjectRequest` is created with status `PENDING`
- AND the status list updates to show the new request

#### Scenario: Deletion shows confirmation dialog

- GIVEN the privacy settings page
- WHEN the user selects "Deletion" and clicks Submit
- THEN a confirmation dialog MUST appear warning about irreversible action
- AND the request MUST NOT be submitted until confirmed

---

## Acceptance Criteria

- [ ] ACCESS request returns all user data across 7 bounded contexts in structured JSON
- [ ] EXPORT request generates a downloadable JSON file with presigned URL (TTL 7 days)
- [ ] CORRECTION updates email/username on `user_identities` and propagates to `waitlist_entries`
- [ ] DELETION: pre-validation blocks sole workspace owners
- [ ] DELETION: Phase 1 anonymizes PII across all tables
- [ ] DELETION: Phase 2 removes memberships, revokes sessions, deletes credentials
- [ ] DELETION: Phase 3 marks media DELETED and blobs READY_FOR_GC
- [ ] DELETION: audit_events, delivery_attempts, consent_records preserved
- [ ] Rate limit of 3 DSARs/user/day enforced
- [ ] 7-day correction cooldown enforced
- [ ] All lifecycle events logged to `audit_events`
- [ ] Frontend privacy section renders, submits requests, shows status history
- [ ] Cross-workspace deletion iterates all memberships
