# Proposal: [Privacy] Implement DSAR workflows — Access, Export, Correction, Deletion

## Intent

Profile Tailors stores user data across 23 tables in 7 bounded contexts — with no pipeline to
access, export, correct, or delete it. GDPR/CCPA requires data controllers to honor DSAR requests
within regulatory timeframes. Without this, the platform cannot operate in regulated markets or
pass privacy audits.

## Scope

### In Scope

- New `privacy` bounded context with DSAR domain model (request aggregate, status lifecycle,
  repository)
- ACCESS request: aggregate all user data across contexts into a structured response
- EXPORT request: serialize user data as JSON, return via presigned download URL
- CORRECTION request: update PII (email, username) on `user_identities` and propagate to waitlist
  entries
- DELETION request: multi-phase orchestration — validate ownership, anonymize PII across all
  contexts (principals, audit, publications), revoke sessions, mark media for GC, remove memberships
- Cross-workspace execution mode — iterate all memberships where the user exists
- Privacy API endpoints under `/api/privacy/` following existing controller patterns
- Privacy section in frontend settings page (`/settings` or `/settings/privacy`)
- DSAR audit logging via `PrivacyMutationAuditor` following `TenancyMutationAuditor` pattern
- JSON export schema definition

### Out of Scope

- Automated DSAR status notifications (email/SMS trigger) — MVP relies on user polling
- Bulk DSAR processing or batching — single-request only
- Data portability to third-party APIs — download-only export
- Automated subject verification (prove identity by email ownership only)
- DSAR dashboard for admins (track all requests) — audit log is sufficient for MVP

## Capabilities

### New Capabilities

- `privacy-dsar`: DSAR lifecycle — submit, track status, retrieve results for
  access/export/correction/deletion requests
- `privacy-data-aggregation`: Cross-context user data collection and JSON export schema

### Modified Capabilities

- `iam`: User identity PII fields must support anonymization (`email`, `username` → `[REDACTED]`)
- `lead-capture-waitlist`: Entries by email must be retrievable for DSAR and anonymizable on
  correction/deletion

## Approach

Create a new `privacy` bounded context under `server/smp/` following hexagonal architecture and
CQRS/mediator patterns. Four request types share a common `DataSubjectRequest` aggregate with a
status lifecycle (PENDING → COMPLETED/REJECTED/FAILED). A `DataAggregationService` collects user
data across all bounded contexts by walking workspace memberships for the principal. Deletion runs
in three phases: (1) validate + anonymize PII, (2) revoke sessions + remove memberships, (3) mark
media for GC. The existing `TenancyMutationAuditor` pattern drives DSAR audit logging.

| Phase | Deliverable               | Pattern                                                                     |
|-------|---------------------------|-----------------------------------------------------------------------------|
| 1     | Domain model + repository | `DataSubjectRequest` aggregate, `R2dbcDataSubjectRequestRepository`         |
| 2     | Access handler            | `SubmitAccessRequestHandler` — aggregates data via `DataAggregationService` |
| 3     | Export handler            | JSON serialization + `StorageApplicationService.presignUrl()` for download  |
| 4     | Correction handler        | Updates `user_identities` PII, propagates to waitlist entries               |
| 5     | Deletion handler          | Multi-phase: ownership check → anonymize → revoke → remove → GC             |
| 6     | REST controller           | `PrivacyController` — CRUD for DSAR requests                                |
| 7     | Frontend UI               | Privacy section in settings, i18n locale files                              |

## Affected Areas

| Area                                 | Impact   | Description                                                     |
|--------------------------------------|----------|-----------------------------------------------------------------|
| `server/smp/src/.../privacy/`        | **New**  | New bounded context: domain, application, infrastructure layers |
| `server/smp/src/.../identity/`       | Modified | `user_identities` anonymization support, correction endpoint    |
| `server/smp/src/.../lead-capture/`   | Modified | Waitlist entry lookup by email, anonymization                   |
| `server/smp/src/.../tenancy/`        | Modified | Cross-workspace membership iteration for deletion               |
| `server/smp/src/.../credentials/`    | Modified | Session revocation for deleted user                             |
| `server/smp/src/.../media/`          | Modified | Soft-delete + GC marking for user's media                       |
| `server/smp/src/.../governance/`     | Modified | DSAR event audit logging (cross-workspace events)               |
| `apps/web/app/src/modules/settings/` | Modified | Privacy section UI, i18n locale entries                         |

## Risks

| Risk                                                                        | Likelihood | Mitigation                                                                          |
|-----------------------------------------------------------------------------|------------|-------------------------------------------------------------------------------------|
| User is sole owner of a workspace — deletion blocked                        | High       | Ownership validation upfront; clear error message directing to transfer ownership   |
| PII in `audit_events.details_json` needs redaction                          | Medium     | Scan & replace in `details_json`; `audit_events` rows are never deleted             |
| Waitlist entries use email, not principal_id — hard to find post-correction | Medium     | Normalize email lookup; propagate correction to waitlist entries                    |
| Pending async events process stale user state after deletion                | Low        | Drain relevant event consumers before completing deletion; use event barrier        |
| Workspace-scoped audit doesn't track cross-workspace DSAR                   | Medium     | `PrivacyMutationAuditor` writes events with a distinguished `workspace_id` sentinel |

## Rollback Plan

1. **Correction**: Revert `user_identities` update to old values via a snapshot stored before
   mutation
2. **Deletion (anonymization)**: No rollback from anonymization — enforce a manual approval step for
   full deletion in MVP. Anonymized data is recoverable from backup if caught within RPO window
3. **New tables**: `data_subject_requests` table is append-only; truncate if created in error
4. **API surface**: Remove `/api/privacy/` routes. Frontend privacy section is behind a feature flag

## Dependencies

- Existing `PrincipalIdentityLookup` interface for user resolution by email or principal ID
- Existing `WorkspaceOwnershipPolicy` for sole-owner validation
- Existing `StorageApplicationService` for presigned export URLs and blob deletion
- Existing `R2dbcAuditHook` pattern for DSAR audit events
- No new external dependencies required

## Success Criteria

- [ ] ACCESS request returns all user data across 7 bounded contexts in a single response
- [ ] EXPORT request generates a downloadable JSON file with complete user schema
- [ ] CORRECTION updates email/username and propagates to waitlist entries
- [ ] DELETION anonymizes PII across all tables, revokes sessions, removes memberships, marks media
  for GC
- [ ] DELETION blocked with clear error when user is sole workspace owner
- [ ] All DSAR lifecycle events are recorded in `audit_events` table
- [ ] Frontend privacy section renders and submits requests successfully
