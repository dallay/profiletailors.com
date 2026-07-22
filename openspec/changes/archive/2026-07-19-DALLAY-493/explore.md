## Exploration: Privacy DSAR (Data Subject Access Request) — DALLAY-493

### Current State

Profile Tailors is a multi-tenant social media management platform. User data is distributed
across **12 database tables** in 6 bounded contexts, all workspace-scoped except for the
identity tables (principals + user_identities). There is **no existing DSAR pipeline**, no
account deletion flow, and no user-facing data export mechanism. The platform does, however,
have several foundations we can build on:

- **Existing audit infrastructure** (`audit_events` table + `R2dbcAuditHook`) for governance
- **Existing consent management** (`consent_records` table) with append-only semantics
- **Existing soft-delete pattern** in the media module (MediaAsset → DELETED, WorkspaceFileBlob →
  GARBAGE_COLLECTED)
- **Existing storage delete capability** in `AbstractS3CompatibleStorage.delete()`
- **Strict hexagonal architecture** with mediator-based CQRS

### 1. User Model — How Users Are Structured

**Two-table identity model:**

| Table             | Columns                                                                                               | Notes                                                           |
|-------------------|-------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------|
| `principals`      | `id`, `principal_type`(USER/SERVICE_ACCOUNT), `subject`, `provider`, `display_identity`, `created_at` | Every authenticated entity. `subject` is an email or OAuth sub. |
| `user_identities` | `principal_id`(PK→principals), `email`(unique), `username`, `email_status`, `created_at`              | PII lives here. 1:1 with principals for USER type.              |

**Primary user identifier:** `principal_id` (varchar(64), UUID-style like `user_abc123`).
**Natural lookup key for DSAR:** `email` (unique constraint on `user_identities.email`).

**Key files:**

-

`server/smp/src/main/kotlin/com/profiletailors/smp/identity/application/PrincipalIdentityLookup.kt` —
Interface to find identity by email, principalId, or subject

-

`server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/R2dbcPrincipalIdentityLookup.kt` —
R2DBC implementation

- `server/smp/src/main/kotlin/com/profiletailors/smp/identity/application/CurrentUserProfile.kt` —
  DTO with `principalId`, `email`, `username`, `displayIdentity`, `emailStatus`
-

`server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/http/CurrentUserProfileController.kt` —
`GET /api/auth/me` endpoint

### 2. Existing Data Export/Delete Patterns

**No export endpoint exists.** No data serialization or download mechanism is in place.

**Existing delete patterns (all workspace-scoped, not user-scoped):**

| Context    | Entity                 | Pattern                                                                                                                             |
|------------|------------------------|-------------------------------------------------------------------------------------------------------------------------------------|
| Media      | `MediaAsset`           | Soft-delete → DELETED status. Blobs GC'd after 7d orphaned. Row never physically deleted.                                           |
| Publishing | `Publication`          | `deleteUnpublished()` — only allowed in DRAFT/QUEUED/SCHEDULED status. Physically deletes row.                                      |
| Storage    | Object storage (S3/R2) | `StorageApplicationService.delete(bucket, key, deleterId)` — physically deletes from S3-compatible store. Emits `FileDeletedEvent`. |

**No user/anonymization deletion exists.** There is no service that walks all bounded contexts
and identifies all data belonging to a user for either access or deletion purposes.

### 3. Audit Trail — What Exists

**Infrastructure is in place and operational:**

| Component                         | File                                                                     | Details                                                                                  |
|-----------------------------------|--------------------------------------------------------------------------|------------------------------------------------------------------------------------------|
| `AuditHook` interface             | `server/smp/.../audit/domain/AuditHook.kt`                               | Three methods: `onRequestHandled`, `onAuthorizationDecision`, `onMutation`               |
| `R2dbcAuditHook`                  | `server/smp/.../audit/infrastructure/R2dbcAuditHook.kt`                  | Inserts into `audit_events` table                                                        |
| `TenancyMutationAuditor`          | `server/smp/.../tenancy/application/TenancyMutationAuditor.kt`           | **Reusable pattern** — wraps `AuditHook.onMutation()` for workspace-scoped mutations     |
| `AuthorizationAuditEventListener` | `server/smp/.../audit/infrastructure/AuthorizationAuditEventListener.kt` | Event-driven audit for authorization decisions                                           |
| `AuditEventController`            | `server/smp/.../governance/infrastructure/http/AuditEventController.kt`  | `GET /api/governance/audit-events` — lists workspace audit events with cursor pagination |
| `AuditHooksProperties`            | `server/smp/.../audit/infrastructure/AuditHooksProperties.kt`            | Gated by `platform.hooks.audit.enabled` (default: false)                                 |

**`audit_events` table schema:**
`id`, `event_type`, `action`, `request_name`, `request_path`, `permission`, `actor_principal_id`,
`workspace_id`, `target_type`, `target_id`, `outcome`, `reason_code`, `role_keys_json`,
`details_json`, `created_at`

**Gap:** The audit hooks are designed for workspace-scoped operations. DSAR operations cross
workspace boundaries (a user may belong to multiple workspaces). We need to either:
(a) Extend the audit model to support cross-workspace events, or
(b) Fire audit events per-workspace for DSAR operations.

### 4. Multi-Tenant Structure & Authorization

**Workspace model:**

```
workspaces (id, name, status[ACTIVE|SUSPENDED|ARCHIVED], icon)
  ↑ 1:N
workspace_memberships (id, workspace_id, principal_id, principal_type, status[ACTIVE|SUSPENDED|REMOVED])
  ↑ M:N (via roles)
roles → permissions
```

**Key rules:**

- A workspace must have **at least one owner** (`WorkspaceOwnershipPolicy`)
- Memberships have statuses: ACTIVE, SUSPENDED, REMOVED
- Authorization is workspace-scoped via `ResourceContext` (X-Workspace-Id header)
- The `ResourceContext` is populated by `WorkspaceContextWebFilter` from the request header

**Key files:**

- `server/smp/.../tenancy/domain/Workspace.kt` — `Workspace` entity
- `server/smp/.../tenancy/domain/WorkspaceMembership.kt` — `WorkspaceMembership` entity
- `server/smp/.../tenancy/domain/WorkspaceOwnershipPolicy.kt` — Ownership invariants
- `server/smp/.../authorization/application/WorkspaceAuthorizationService.kt` — Authorization engine
- `server/smp/.../tenancy/infrastructure/http/WorkspaceContextWebFilter.kt` — Workspace context
  filter
- `server/smp/.../tenancy/infrastructure/http/WorkspaceMembershipController.kt` —
  `PATCH /api/tenancy/workspace-memberships/{principalId}/status`

**DSAR implication:** Deleting a user requires checking every workspace they belong to.
If they are the sole owner of any workspace, deletion must be blocked until ownership is
transferred or the workspace is deleted.

### 5. Object Storage — Media/Assets

**Two-layer storage model:**

| Layer          | Technology                                                                                  | Details                                    |
|----------------|---------------------------------------------------------------------------------------------|--------------------------------------------|
| Application    | `StorageApplicationService`                                                                 | Metrics, events, validation wrapper        |
| Domain         | `Storage` (interface) / `PresignableStorage` (interface)                                    | Upload/download/delete/presign/exists/copy |
| Infrastructure | `AbstractS3CompatibleStorage` / `S3Storage` / `R2StorageAdapter` / `LocalFilesystemStorage` | Implements both interfaces                 |

**Storage key pattern:** `assets/{workspaceId}/blobs/{fileHash}{ext}` or
`assets/{workspaceId}/temp/{assetId}{ext}`
**Storage supports:** `delete(bucket, key, deleterId)` — physically deletes from S3/R2/local
**Used by:** Media GC reconciler (`StaleAssetReconciler`) runs hourly

**Key files:**

- `shared/storage/src/main/kotlin/.../application/StorageApplicationService.kt`
- `shared/storage/src/main/kotlin/.../infrastructure/AbstractS3CompatibleStorage.kt`
- `server/smp/.../media/application/StaleAssetReconciler.kt` — GC job reference pattern

**DSAR implication:** When deleting a user, uploaded media assets in their workspace can be
soft-deleted (status → DELETED) and blobs marked READY_FOR_GC. The existing GC reconciler will
handle physical storage cleanup.

### 6. Cache Layer

**No application-level caching (Redis, etc.) found.** The only caches are:

- `CacheControl` HTTP headers on media preview endpoints (max-age headers)
- Browser-level caching

**No cache invalidation needed for DSAR operations.**

### 7. Current API Patterns

**Standard controller pattern (used across all contexts):**

```kotlin
@Validated
@RestController
@RequestMapping(value = ["/api/{context}/{resource}"])
@Tag(name = "...", description = "...")
class XxxController(private val mediator: Mediator) {

    @GetMapping(version = "1")
    suspend fun list(...): ResponseEntity<...> = mediator.send(SomeQuery(...))

    @PostMapping(version = "1")
    suspend fun create(@Valid @RequestBody request: SomeRequest): ResponseEntity<...> =
        mediator.send(SomeCommand(...))
}
```

**Key conventions:**

- Media-type versioning via `@GetMapping(version = "1")`
- Workspace context via `resourceContextProvider.requireWorkspaceContext()`
- All handlers use `@Service` from `com.profiletailors.common.domain.Service` (not Spring's)
- `Mediator.send()` for both commands and queries
- Request/response DTOs use `@Schema` for OpenAPI docs
- Validation via `@Valid` + `jakarta.validation`

### 8. Frontend Patterns

**Settings page:** `apps/web/app/src/modules/settings/presentation/SettingsView.vue`

- Route: `/settings`
- Current sections: Workspace identity (name + icon), Connected channels (LinkedIn), Language/Theme
  toggle
- **No privacy/data section exists**
- Uses Pinia store pattern (`useSettingsStore`)
- i18n module-based locale files: `en/settings.ts`, `es/settings.ts`

**Router:** `apps/web/app/src/router/index.ts`

- All authenticated routes use `meta: { requiresAuth: true }`
- Settings route:
  `{ path: '/settings', name: 'settings', component: SettingsView, meta: { requiresAuth: true } }`

### Complete Data Inventory — All User Data Tables

| #  | Table                         | Bounded Context | Workspace-Scoped?  | PII?                      | Data Type          |
|----|-------------------------------|-----------------|--------------------|---------------------------|--------------------|
| 1  | `principals`                  | identity        | No                 | Maybe (display_identity)  | Auth identity      |
| 2  | `user_identities`             | identity        | No                 | **Yes** (email, username) | User profile       |
| 3  | `local_password_credentials`  | identity        | No                 | No (hash only)            | Auth credential    |
| 4  | `email_verification_tokens`   | identity        | No                 | **Yes** (email)           | Verification       |
| 5  | `refresh_sessions`            | credentials     | No                 | No                        | Auth session       |
| 6  | `api_key_credentials`         | credentials     | No                 | No                        | API credential     |
| 7  | `service_account_credentials` | credentials     | No                 | No                        | Service credential |
| 8  | `workspaces`                  | tenancy         | N/A                | No                        | Workspace entity   |
| 9  | `workspace_memberships`       | tenancy         | Yes                | No                        | Membership link    |
| 10 | `workspace_ownerships`        | tenancy         | Yes                | No                        | Ownership link     |
| 11 | `social_connections`          | publishing      | Yes                | No                        | OAuth connection   |
| 12 | `social_accounts`             | publishing      | Yes                | No                        | Social account ref |
| 13 | `publications`                | publishing      | Yes                | No (author_principal_id)  | Published content  |
| 14 | `publication_assets`          | publishing      | Yes                | No                        | Media refs         |
| 15 | `publication_jobs`            | publishing      | Yes                | No                        | Delivery state     |
| 16 | `delivery_attempts`           | publishing      | Yes                | No                        | Delivery logs      |
| 17 | `secure_credentials`          | publishing      | Yes                | No                        | Encrypted tokens   |
| 18 | `media_assets`                | media           | Yes                | No                        | Uploaded media     |
| 19 | `workspace_file_blobs`        | media           | Yes                | No                        | Deduped blobs      |
| 20 | `audit_events`                | governance      | Yes (workspace_id) | Maybe (details_json)      | Governance log     |
| 21 | `consent_records`             | governance      | Yes                | No (subject is opaque)    | Consent history    |
| 22 | `waitlist_entries`            | lead-capture    | No                 | **Yes** (email, metadata) | Pre-account leads  |
| 23 | `waitlists`                   | lead-capture    | No                 | No                        | Waitlist config    |

### Gaps Identified

1. **No DSAR/privacy bounded context** — Entire set of services needs to be created
2. **No user data aggregation** — No service today can collect all data for a given user across
   contexts
3. **No data export format** — Need to define JSON schema for user data export
4. **No PII anonymization** — No pattern for replacing PII fields with `[REDACTED]` / `[DELETED]`
5. **No account deletion orchestration** — Must handle: validate ownership, anonymize PII, mark
   memberships REMOVED, revoke sessions, delete storage objects
6. **No correction endpoint** — Users can't update email/username via API today
7. **No frontend privacy UI** — Settings page has no privacy/data section
8. **No cross-workspace audit** — `audit_events` is indexed by `workspace_id`. DSAR operations need
   event logging that spans workspaces.

### Risks & Constraints

1. **Workspace ownership constraint** — A user who is the sole OWNER of a workspace cannot be
   deleted without transferring ownership or deleting the workspace first. Must block deletion
   with a clear error message.
2. **Existing data must be preserved** — Publications, delivery attempts, and audit events
   reference `author_principal_id` and `actor_principal_id`. These must be **anonymized** not
   deleted (RGPD Art. 17.3.b — legal obligations).
3. **Waitlist entries** — May contain pre-account user data (email, metadata). These are not
   linked to a principal_id, so finding them requires matching by normalized email.
4. **Secure credentials** — Encrypted OAuth tokens linked to social connections. Must be deleted
   when the user revokes connections or deletes their account.
5. **Email is the cross-reference key** — waitlist_entries, consent_records (ANONYMOUS kind),
   and user_identities all use email. A change of email (correction request) must propagate.
6. **Reactive / async consumers** — Domain events like `UserRegistered` are consumed
   asynchronously. Deletion must ensure no pending events are processed after deletion.
7. **Audit must never be deleted** — `audit_events` must be preserved; only PII in `details_json`
   may need redaction.

### Recommended Approach

**Create a new `privacy` bounded context** following the existing hexagonal architecture:

```
smp/privacy/
├── domain/
│   ├── DataSubjectRequest.kt        — DSAR aggregate (ACCESS, EXPORT, CORRECT, DELETE, RESTRICT)
│   ├── DataSubjectRequestId.kt       — Value type
│   ├── DataSubjectRequestStatus.kt   — Enum: PENDING, COMPLETED, REJECTED, FAILED
│   └── DataSubjectRequestRepository.kt
├── application/
│   ├── SubmitAccessRequestHandler.kt     — Aggregates user data across all contexts
│   ├── SubmitExportRequestHandler.kt     — Generates JSON export, returns presigned URL
│   ├── SubmitCorrectionRequestHandler.kt — Updates PII fields
│   ├── SubmitDeletionRequestHandler.kt   — Orchestrates multi-phase deletion
│   ├── CheckRequestStatusHandler.kt      — Status query
│   └── DataAggregationService.kt         — Collects user data from all contexts
└── infrastructure/
    ├── http/
    │   └── PrivacyController.kt          — REST endpoints
    └── persistence/
        └── R2dbcDataSubjectRequestRepository.kt
```

**Key design decisions to make:**

1. **Synchronous vs. async export** — For MVP, export can be real-time (small user). For scale,
   use the Mediator pattern async with a notification when ready.
2. **Deletion phases** — Phase 1: validate + anonymize PII. Phase 2: remove memberships + revoke
   sessions. Phase 3: mark media for GC. Must be atomic within a transaction runner.
3. **Workspace scope** — Most deletion operations are workspace-scoped. We need a special
   "cross-workspace" execution mode that iterates all memberships for the user.
4. **Audit integration** — Log all DSAR lifecycle events to `audit_events` table. Create a
   `PrivacyMutationAuditor` following the `TenancyMutationAuditor` pattern.
5. **Anonymization strategy** — Replace PII with `[REDACTED on {timestamp}]` for audit/history
   preservation. Physical delete for ephemeral data (sessions, tokens).
6. **Frontend location** — Add a "Privacy" section to the existing `/settings` page, or create
   a new `/settings/privacy` sub-route.

**Order of implementation:**

1. Data subject request domain model + repository
2. Access request handler (data aggregation)
3. Export request handler (JSON serialization + download)
4. Correction request handler (PII update)
5. Deletion request handler (orchestration)
6. REST controller
7. Frontend privacy UI
8. Verification

### Ready for Proposal

**Yes** — the exploration is thorough enough to proceed to the proposal phase. All major
architectural concerns have been identified, and the gaps are well-understood.
