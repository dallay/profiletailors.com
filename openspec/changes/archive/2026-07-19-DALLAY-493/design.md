# Design: Privacy DSAR — Access, Export, Correction, Deletion

## Technical Approach

New `privacy` bounded context under
`com.profiletailors.smp.privacy.{domain,application,infrastructure}`. A `DataSubjectRequest`
aggregate with typed status lifecycle handles all 4 DSAR types. Cross-workspace data aggregation via
`DataAggregationService` iterating workspace memberships. Deletion in 3 phases within
`AtomicTransactionRunner`. Audit via `PrivacyMutationAuditor` extending the TenancyMutationAuditor
pattern. Frontend as a `PrivacySection.vue` sub-view in `/settings`.

## Architecture Decisions

### Decision: Single aggregate for 4 DSAR types

| Option                   | Tradeoff                                            | Decision                                                                                      |
|--------------------------|-----------------------------------------------------|-----------------------------------------------------------------------------------------------|
| One aggregate per type   | Handlers cleaner but query complexity triples       | **Single `DataSubjectRequest` with `requestType` enum** — simpler persistence, easier list UI |
| Separate tables per type | No type-discriminated queries, but harder to extend | **One table**, one repository — follows existing patterns                                     |

### Decision: Synchronous export for MVP

| Option                  | Tradeoff                                         | Decision                                                                    |
|-------------------------|--------------------------------------------------|-----------------------------------------------------------------------------|
| Async with notification | Scalable but adds event bus + polling complexity | **Real-time** — user data is small (<100KB per user). Async is future work. |

### Decision: Cross-workspace audit approach

| Option                     | Tradeoff                                                          | Decision                                                                                                      |
|----------------------------|-------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------|
| Per-workspace audit events | Natural fit for `audit_events.workspace_id` but N writes per DSAR | **Single event with `workspace_id = '__DSAR__'` sentinel** — 1 write, clear in queries, avoids N audit events |
| Extend audit_events schema | Schema change with migration risk                                 | **Sentinel approach** — no schema change needed                                                               |

### Decision: Privacy section as sub-view in /settings

| Option                                  | Tradeoff                            | Decision                                                                                                           |
|-----------------------------------------|-------------------------------------|--------------------------------------------------------------------------------------------------------------------|
| New top-level route `/settings/privacy` | Cleaner separation but more routing | **Sub-view** via `PrivacySection.vue` rendered inside SettingsView — follows existing single-settings-page pattern |
| New route with tabs                     | More complex navigation             | Same page, scroll-to section                                                                                       |

## Package Layout

```
server/smp/src/main/kotlin/com/profiletailors/smp/privacy/
├── PrivacyBoundedContext.kt
├── domain/
│   ├── DataSubjectRequest.kt              — Aggregate (entity + status + request type)
│   ├── DataSubjectRequestStatus.kt        — Enum: PENDING, COMPLETED, REJECTED, FAILED
│   └── DataSubjectRequestRepository.kt    — Interface (save, findById, findByRequester, findByStatus, findExpired)
├── application/
│   ├── SubmitAccessRequestHandler.kt       — Command → aggregates user data
│   ├── SubmitExportRequestHandler.kt       — Command → JSON → presigned URL
│   ├── SubmitCorrectionRequestHandler.kt   — Command → update PII → propagate
│   ├── SubmitDeletionRequestHandler.kt     — Command → 3-phase orchestration
│   ├── CheckRequestStatusHandler.kt        — Query → current status
│   ├── ListRequestsHandler.kt              — Query → user's requests
│   ├── DataAggregationService.kt           — Collects user data from n contexts
│   └── PrivacyMutationAuditor.kt           — DSAR audit logging
└── infrastructure/
    ├── PrivacyBoundedContextConfiguration.kt — Spring config
    ├── http/
    │   ├── PrivacyController.kt             — REST endpoints
    │   └── PrivacyRequestDtos.kt            — Request/Response DTOs
    └── persistence/
        └── R2dbcDataSubjectRequestRepository.kt — R2DBC implementation
```

**Database changelog:**

```
server/smp/src/main/resources/db/changelog/privacy/001-create-data-subject-requests.yaml
```

Add include to `db.changelog-master.yaml`.

## Aggregate Design

### DataSubjectRequest entity

```
DataSubjectRequest {
  id: String                   // "dsr-{uuid}"
  requesterPrincipalId: String // who submitted
  requestType: RequestType     // ACCESS | EXPORT | CORRECTION | DELETION
  status: DataSubjectRequestStatus
  detailsJson: String          // JSON — request-specific payload
  resultJson: String?          // JSON — result payload (null until COMPLETED)
  errorMessage: String?        // failure reason if FAILED
  expiresAt: Instant           // 30d from completion
  createdAt: Instant
  updatedAt: Instant
}
```

### Status Machine

```
PENDING ──→ COMPLETED
  │            │
  ├──→ REJECTED (validation fail)
  └──→ FAILED   (execution error, retryable)
```

### Invariants

- `email` must exist in `user_identities` for the requester (ACCESS/EXPORT/DELETION)
- `newEmail` must not conflict with existing `user_identities.email` (CORRECTION)
- Cannot DELETE if user is sole owner in ANY workspace
- Status transitions: only PENDING → {COMPLETED, REJECTED, FAILED}
- Expired requests are TTL-deleted by a scheduled job

### `requestType`-specific detailsJson payloads

| Type       | detailsJson fields                                    |
|------------|-------------------------------------------------------|
| ACCESS     | `{ }` — empty, just trigger                           |
| EXPORT     | `{ }` — empty                                         |
| CORRECTION | `{ "field": "email"\|"username", "newValue": "..." }` |
| DELETION   | `{ }` — empty                                         |

### `resultJson` payloads

| Type       | resultJson                                                                            |
|------------|---------------------------------------------------------------------------------------|
| ACCESS     | Full aggregated user data JSON                                                        |
| EXPORT     | `{ "downloadUrl": "https://...", "expiresAt": "..." }`                                |
| CORRECTION | `{ "updatedFields": ["email"] }`                                                      |
| DELETION   | `{ "affectedWorkspaces": [...], "anonymizedTables": [...], "revokedSessions": true }` |

## Repository Interface

```kotlin
interface DataSubjectRequestRepository {
    suspend fun save(request: DataSubjectRequest)
    suspend fun findById(id: String): DataSubjectRequest?
    suspend fun findByRequester(principalId: String): List<DataSubjectRequest>
    suspend fun findByStatus(status: DataSubjectRequestStatus): List<DataSubjectRequest>
    suspend fun findExpired(before: Instant): List<DataSubjectRequest>
}
```

## Data Aggregation

Cross-workspace iteration for ACCESS requests:

```
DataAggregationService.collectWorkspaceData(request.requesterPrincipalId):
  for each membership in tenancyService.getMemberships(principalId):
    workspaceId = membership.workspaceId
    data[workspaceId] = {
      workspace: tenancyService.getWorkspace(workspaceId)
      socialConnections: publishingService.getConnections(workspaceId, principalId)
      publications: publishingService.getPublications(workspaceId, principalId)
      mediaAssets: mediaService.getAssets(workspaceId, principalId)
      auditEvents: governanceService.getAuditEvents(workspaceId, principalId)
      consentRecords: governanceService.getConsentRecords(workspaceId, principalId)
    }
  data.identity = identityService.getUserProfile(principalId)
  data.waitlistEntries = leadCaptureService.findByEmail(email)
  return data
```

No transaction needed — this is a read-only aggregation. Runs sequentially within the handler. The
`principalId` context is resolved once and passed explicitly (cross-workspace has no X-Workspace-Id
header to use).

## Export Format (JSON Schema)

```json
{
  "$schema": "https://profiletailors.com/privacy/export-schema-v1.json",
  "exportedAt": "2026-07-19T12:00:00Z",
  "requesterPrincipalId": "user_abc123",
  "identity": {
    "email": "user@example.com",
    "username": "johndoe",
    "displayIdentity": "user@example.com",
    "createdAt": "2025-01-01T00:00:00Z"
  },
  "workspaces": [
    {
      "workspaceId": "ws_xyz",
      "workspaceName": "My Workspace",
      "membershipStatus": "ACTIVE",
      "socialConnections": [ { "provider": "linkedin", "accountId": "...", "connectedAt": "..." } ],
      "publications": [ { "id": "...", "status": "PUBLISHED", "createdAt": "..." } ],
      "mediaAssets": [ { "id": "...", "filename": "photo.jpg", "status": "ACTIVE", "createdAt": "..." } ],
      "auditEvents": [ { "eventType": "...", "action": "...", "createdAt": "..." } ],
      "consentRecords": [ { "consentType": "...", "status": "...", "createdAt": "..." } ]
    }
  ],
  "waitlistEntries": [ { "email": "user@example.com", "waitlistName": "...", "createdAt": "..." } ]
}
```

## Deletion Flow (3-Phase)

```kotlin
// Phase 1: Validate + Anonymize PII (fail-fast, audited)
transactionRunner.runAtomically {
    validateNotSoleOwnerInAnyWorkspace(principalId)   // throws if blocked
    anonymizeUserIdentities(principalId)               // email→REDACTED, username→REDACTED
    anonymizeWaitlistEntries(email)                    // email→REDACTED per normalized email
    anonymizeAuditEvents(principalId)                  // scan+replace in details_json
    // Phase 1 complete — audit records phase success
}
// Phase 2: Revoke sessions + Remove memberships (best-effort)
credentialService.revokeAllSessions(principalId)       // delete from refresh_sessions
for each membership in tenancyService.getMemberships(principalId):
    tenancyService.removeMembership(membership.id)      // status→REMOVED
// Phase 3: Mark media for GC (async-safe)
for each membership in tenancyService.getMemberships(principalId):
    mediaService.markUserAssetsForGc(membership.workspaceId, principalId)
```

**Rollback:** Phase 1 is destructive (anonymization). Phase 2/3 are recoverable (session re-issue,
membership re-invite). A failed Phase 1 leaves the request in FAILED state for manual review.

**Error handling:** Each phase catches exceptions and records a FAILED audit event with
`errorMessage`. The request is moved to FAILED. `anonymizeAuditEvents` is best-effort — failure
there does not block the rest.

## Anonymization Strategy

| Table                       | What happens                                                                           |
|-----------------------------|----------------------------------------------------------------------------------------|
| `user_identities`           | `email → [REDACTED on 2026-07-19]`, `username → [REDACTED]`                            |
| `principals`                | `display_identity → [REDACTED]` (keeps `subject` for auth chain integrity)             |
| `audit_events.details_json` | Scan JSON keys for `principal_id`, `email` patterns → replace values with `[REDACTED]` |
| `waitlist_entries`          | `email → [REDACTED]`, `metadata → {}`                                                  |
| `email_verification_tokens` | **Physical delete** where `email` matches                                              |
| `refresh_sessions`          | **Physical delete** where `principal_id` matches                                       |
| `social_connections`        | **Physical delete** (cascades to `secure_credentials`)                                 |
| `workspace_memberships`     | Status → `REMOVED` (soft-delete, preserves workspace history)                          |
| `media_assets`              | Status → `DELETED`; blob → `READY_FOR_GC`                                              |

Anonymization replaces values; physical delete removes rows; soft-delete sets status flags.
`audit_events` rows are **never deleted** — only PII in `details_json` is redacted.

## REST API

| Method | Path                                  | Request DTO                                                               | Response                           | Status | Errors                                       |
|--------|---------------------------------------|---------------------------------------------------------------------------|------------------------------------|--------|----------------------------------------------|
| `POST` | `/api/privacy/requests`               | `SubmitPrivacyRequest { type: RequestType, details?: CorrectionDetails }` | `DataSubjectRequestResponse`       | 201    | 400 validation, 409 conflict, 403 sole-owner |
| `GET`  | `/api/privacy/requests`               | —                                                                         | `List<DataSubjectRequestResponse>` | 200    | —                                            |
| `GET`  | `/api/privacy/requests/{id}`          | —                                                                         | `DataSubjectRequestResponse`       | 200    | 404 not found                                |
| `GET`  | `/api/privacy/requests/{id}/download` | —                                                                         | presigned redirect (302)           | 302    | 404, 409 not EXPORT/not COMPLETED            |

**DTOs:**

```kotlin
data class SubmitPrivacyRequest(
    val type: RequestType,          // ACCESS | EXPORT | CORRECTION | DELETION
    val details: CorrectionDetails?,
)
data class CorrectionDetails(
    val field: String,              // "email" | "username"
    val newValue: String,
)
data class DataSubjectRequestResponse(
    val id: String,
    val type: String,
    val status: String,
    val details: Map<String, Any?>?,
    val result: Map<String, Any?>?,
    val errorMessage: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
```

## Audit Integration

```kotlin
@Service
class PrivacyMutationAuditor(
    private val principalContextProvider: PrincipalContextProvider,
    private val auditHook: AuditHook,
) {
    suspend fun recordSuccess(action: String, requestId: String, details: Map<String, String>) { ... }
    suspend fun recordRejected(action: String, requestId: String, reason: String, details: Map<String, String>) { ... }
}
```

Follows `TenancyMutationAuditor` contract exactly. The `workspaceId` param is set to `"__DSAR__"`
sentinel for cross-workspace operations. `targetType` is `"DATA_SUBJECT_REQUEST"`, `targetId` is the
request ID.

## Database Migrations

**`privacy/001-create-data-subject-requests.yaml`:**

```yaml
databaseChangeLog:
  - changeSet:
      id: privacy-001-create-data-subject-requests
      author: opencode
      changes:
        - createTable:
            tableName: data_subject_requests
            columns:
              - column: { name: id, type: varchar(64), constraints: { primaryKey: true, nullable: false } }
              - column: { name: requester_principal_id, type: varchar(64), constraints: { nullable: false } }
              - column: { name: request_type, type: varchar(16), constraints: { nullable: false } }
              - column: { name: status, type: varchar(16), constraints: { nullable: false } }
              - column: { name: details_json, type: text, constraints: { nullable: false } }
              - column: { name: result_json, type: text, constraints: { nullable: true } }
              - column: { name: error_message, type: text, constraints: { nullable: true } }
              - column: { name: expires_at, type: timestamp with time zone, constraints: { nullable: false } }
              - column: { name: created_at, type: timestamp with time zone, defaultValueComputed: CURRENT_TIMESTAMP, constraints: { nullable: false } }
              - column: { name: updated_at, type: timestamp with time zone, defaultValueComputed: CURRENT_TIMESTAMP, constraints: { nullable: false } }
        - createIndex: { tableName: data_subject_requests, indexName: idx_dsr_requester, columns: [{ name: requester_principal_id }] }
        - createIndex: { tableName: data_subject_requests, indexName: idx_dsr_status, columns: [{ name: status }] }
```

## Frontend Design

**Component tree:**

```
SettingsView.vue
  └── PrivacySection.vue          ← new, rendered below channels panel
        ├── DsarRequestForm.vue    ← type selector + submit
        ├── DsarRequestList.vue    ← user's past requests
        └── DsarStatusBadge.vue    ← status chip
```

**Pinia store** (`modules/settings/infrastructure/privacy.store.ts`):

- State: `requests`, `loading`, `error`
- Actions: `submitRequest(type, details?)`, `fetchRequests()`, `fetchDownloadUrl(id)`
- Calls `/api/privacy/requests` endpoints via `auth-api`

**Router:** No new route — `PrivacySection.vue` is imported and rendered inside the existing
`SettingsView.vue` as a new card section below the channels panel.

**i18n:** Add `privacy.*` keys to `shared/i18n/locales/{en,es}/settings.ts`.

## Open Questions

- [ ] Should the DSAR result (access/export) be stored inline in `result_json` or as a reference to
  a storage blob? Inline for MVP, blob if >1MB.
- [ ] Does the existign session revocation have an existing service or is it via direct
  `DatabaseClient`?
- [ ] Should `anonymizeAuditEvents` use a LIKE scan or structured JSON path? LIKE for MVP, jsonpath
  for scale.
