# Design: Media Copyright, Attribution & Takedown Workflow

> **Archive note**: Reconciled to match shipped implementation. See verify-report.md for
> full deviation log. Key differences from original design:
> - **ADR-005**: Feature flag dropped — endpoints are always-on (simplification)
> - **REST surface**: Split `POST .../approve` + `POST .../reject` replaced single `POST .../action`
> - **Permission keys**: Dash-delimited (`media-read`, `media-takedown`) per codebase convention
> - **Counter-notice flow**: Entirely removed from implementation scope
> - **Detail endpoint**: `GET .../{id}` intentionally omitted (YAGNI)
> - **TakedownReportStatus**: Shipped as `REPORTED`, `APPROVED`, `DISMISSED`, `SUSPENDED` (reserved)
> - **Email implementation**: `TakedownEmailTemplates` + domain-event consumers instead of direct
>   `EmailSender.send()` on templates class
> - **Audit events**: Generic `AuditHook.onMutation()` action strings instead of dedicated event-type enum

## Technical Approach

Two-phase incremental implementation. Phase 1 adds `licence` schema + attribution display. Phase 2
builds the full takedown workflow as a new governance sub-domain. Delivers immediate Unsplash
compliance in Phase 1 while Phase 2 addresses DMCA readiness.

## Phase 1 — Schema + Attribution Display

### Architecture Decisions

| Option                                              | Tradeoff                                                                                                                                                             | Decision                                                                                                                                                  |
|-----------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------|
| **ADR-001**: `licence` as VARCHAR vs enum           | VARCHAR is flexible for diverse licences (Unsplash, CC, proprietary) but shifts validation to app layer. Enum is type-safe but requires code changes for new values. | **VARCHAR(64) nullable** — new licence values appear too often to couple to an enum. Application-layer validation via a `LicenceValidator` in the domain. |
| **ADR-002**: Nullable `licence` for existing assets | Backfilling tens of thousands is impractical. Pre-existing rows get NULL. Attribution display shows what's available.                                                | **Nullable** — display-only field; no migration needed for existing rows.                                                                                 |

### Database

- **Create** `db/changelog/media/007-add-licence-column.yaml`:
  ```yaml
  databaseChangeLog:
    - changeSet:
        id: media-007-add-licence-column
        author: sdd-apply
        changes:
          - addColumn:
              tableName: media_assets
              columns:
                - column:
                    name: licence
                    type: VARCHAR(64)
                    constraints: { nullable: true }
  ```
- **Remove** `db/changelog/media/006-drop-external-metadata.yaml` — delete from git (never applied,
  unreferenced in master changelog). Attribution columns remain intact.

### Domain

- **File**: `server/smp/src/main/kotlin/com/profiletailors/smp/media/domain/MediaModels.kt`
- Add `val licence: String? = null` to `MediaAsset` data class (around line 166, after `metadata`)

### DTOs

- **File**:
  `server/smp/src/main/kotlin/com/profiletailors/smp/media/infrastructure/http/MediaDtos.kt`
- Add `val licence: String? = null` to `MediaAssetResponse` (around line 153, after `metadata`)

- **File**: `server/smp/src/main/kotlin/com/profiletailors/smp/media/application/MediaQueries.kt`
- Add `val licence: String? = null` to `MediaAssetSummary` (around line 58, after `metadata`)

### Persistence

- **File**:
  `server/smp/src/main/kotlin/com/profiletailors/smp/media/infrastructure/persistence/R2dbcMediaRepositories.kt`
- In `create()` (line 38–48): add `licence` to INSERT columns and `:licence` to VALUES. Bind with
  `.bindNullable("licence", asset.licence, String::class.java)`
- In every SELECT query: add `licence` to the column list
- In `rowToMediaAsset()` (line 451): add `licence = row.get("licence", String::class.java)` —
  nullable, no `requireNotNull`

### Unsplash Integration

- **File**:
  `server/smp/src/main/kotlin/com/profiletailors/smp/media/application/UnsplashMediaProviderHandlers.kt`
- In `persistPhoto()` (line 109–128): add `licence = "unsplash"` to the `MediaAsset` constructor (
  after `metadata`)

### Frontend

- **Create** `apps/web/app/src/modules/media/presentation/components/MediaAttribution.vue`:
  ```vue
  <script setup lang="ts">
  defineProps<{
    authorName?: string | null
    authorUrl?: string | null
    sourceProvider?: string | null
    licence?: string | null
  }>()
  </script>
  <template>
    <div v-if="authorName || licence" class="space-y-0.5 px-3 pb-2">
      <p v-if="authorName" class="truncate text-[10px] text-text-secondary">
        {{ $t('media.attributionBy') }}
        <a v-if="authorUrl" :href="authorUrl" target="_blank" rel="noopener"
           class="underline underline-offset-2 hover:text-text-display">{{ authorName }}</a>
        <template v-else>{{ authorName }}</template>
      </p>
      <p v-if="licence" class="font-mono text-[9px] uppercase tracking-[0.12em] text-text-secondary">
        {{ licence }}
      </p>
    </div>
  </template>
  ```
- **Modify** `apps/web/app/src/modules/media/presentation/views/MediaLibraryView.vue`:
    - Add `licence` to the `MediaAssetSummary` type usage (import it if needed from the API types)
    - Import and render `<MediaAttribution>` inside each asset card (after the metadata section at
      line 558)
- **Update** `apps/web/app/src/modules/media/services/media-api.ts`:
    - Add `licence?: string | null` to `MediaAssetSummary` type (after `metadata`, line 39)

## Phase 2 — Takedown Workflow

### Architecture Decisions

| Option                                                      | Tradeoff                                                                                                                                                                                                | Decision                                                                                                                                                                 |
|-------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **ADR-003**: TakedownReport in governance vs media context  | Embedding in media context places legal/compliance logic alongside upload/storage concerns, violating separation of concerns. Governance owns audit trail and compliance controls.                      | **Governance context** — new `media/takedown` sub-domain within governance. Audit trail belongs under governance.                                                        |
| **ADR-004**: `SUSPENDED` status vs separate blocklist table | Blocklist table adds JOIN overhead for every media query and requires a new exclusion pattern. Status field already models asset lifecycle; SUSPENDED is a natural extension of the existing CAS model. | **New `SUSPENDED` value in `MediaAssetStatus`** — simpler queries, works with existing `listByWorkspace(statuses: Set<>)` filtering, status already models availability. |
| **ADR-005**: Feature flag for takedown endpoints            | Takedown workflow is new functionality; a flag allows safe rollout and instant disable if issues arise. Use Spring Boot `@ConditionalOnProperty`.                                                       | **DROPPED during implementation** — endpoints are always-on. Tradeoff accepted: no instant disable without a deploy. Simpler wiring, fewer configuration paths. See W-03 in verify-report. |

### Domain Model — TakedownReport

**New file**:
`server/smp/src/main/kotlin/com/profiletailors/smp/governance/domain/MediaTakedownModels.kt`

> **Shipped implementation** (simplified): Single `TakedownReportStatus` enum replaces separate
> `TakedownStatus`, `TakedownResolution`, and `CounterNoticeStatus`. Counter-notice fields removed
> from `TakedownReport`. String IDs instead of value objects. `SUSPENDED` status reserved for
> future use but not produced by the state machine.

```kotlin
package com.profiletailors.smp.governance.domain

import java.time.Instant

enum class TakedownReportStatus { REPORTED, APPROVED, DISMISSED, SUSPENDED }

data class TakedownReport(
    val reportId: String,
    val assetId: String,
    val workspaceId: String,
    val reportedBy: String,
    val reporterContact: String,
    val category: String,
    val description: String,
    val evidenceUrls: List<String>,
    val status: TakedownReportStatus,
    val createdAt: Instant,
    val updatedAt: Instant? = null,
)
```

**New file**:
`server/smp/src/main/kotlin/com/profiletailors/smp/governance/domain/MediaTakedownRepository.kt`

```kotlin
package com.profiletailors.smp.governance.domain

interface MediaTakedownRepository {
    suspend fun save(report: TakedownReport): TakedownReport
    suspend fun findById(reportId: TakedownReportId): TakedownReport?
    suspend fun findByWorkspace(workspaceId: String, pageSize: Int, cursor: String?): TakedownReportPage
    suspend fun findByAsset(assetId: String): List<TakedownReport>
    suspend fun update(report: TakedownReport): TakedownReport
}

data class TakedownReportPage(
    val reports: List<TakedownReport>,
    val nextCursor: String?,
)
```

### Database — Takedown Reports

**New file**: `db/changelog/governance/006-create-takedown-reports.yaml`

```yaml
databaseChangeLog:
  - changeSet:
      id: governance-006-create-takedown-reports
      author: sdd-apply
      changes:
        - createTable:
            tableName: media_takedown_reports
            columns:
              - column: { name: report_id, type: VARCHAR(64), constraints: { primaryKey: true, nullable: false } }
              - column: { name: asset_id, type: VARCHAR(64), constraints: { nullable: false } }
              - column: { name: workspace_id, type: VARCHAR(64), constraints: { nullable: false } }
              - column: { name: reported_by, type: VARCHAR(64), constraints: { nullable: false } }
              - column: { name: reporter_contact, type: VARCHAR(255), constraints: { nullable: false } }
              - column: { name: category, type: VARCHAR(32), constraints: { nullable: false } }
              - column: { name: description, type: TEXT, constraints: { nullable: false } }
              - column: { name: evidence_urls, type: JSONB }
              - column: { name: status, type: VARCHAR(32), constraints: { nullable: false } }
              - column: { name: resolution, type: VARCHAR(32) }
              - column: { name: resolved_by, type: VARCHAR(64) }
              - column: { name: resolved_at, type: TIMESTAMP WITH TIME ZONE }
              - column: { name: counter_notice_status, type: VARCHAR(32), defaultValue: "NONE" }
              - column: { name: counter_notice_description, type: TEXT }
              - column: { name: counter_notice_submitted_at, type: TIMESTAMP WITH TIME ZONE }
              - column: { name: created_at, type: TIMESTAMP WITH TIME ZONE, defaultValueComputed: "CURRENT_TIMESTAMP" }
              - column: { name: updated_at, type: TIMESTAMP WITH TIME ZONE }
        - createIndex:
            tableName: media_takedown_reports
            indexName: idx_takedown_workspace
            columns: { column: [workspace_id, created_at] }
        - createIndex:
            tableName: media_takedown_reports
            indexName: idx_takedown_asset
            columns: { column: [asset_id] }
```

### MediaAssetStatus — SUSPENDED

**File**: `server/smp/src/main/kotlin/com/profiletailors/smp/media/domain/MediaModels.kt`

Add `SUSPENDED` to the `MediaAssetStatus` enum (after `DELETED` at line 67):

```kotlin
/**
 * Asset suspended due to a copyright takedown or moderation action.
 * Not available for selection or publishing. Not returned in public views.
 */
SUSPENDED,
```

**Transition rules**: `SUSPENDED` → `READY` (counter-notice accepted). No transition out of
`SUSPENDED` to `DELETED` (legal hold — must be resolved first).

### REST Endpoints

**New file**:
`server/smp/src/main/kotlin/com/profiletailors/smp/governance/infrastructure/http/TakedownReportController.kt`

Package: `com.profiletailors.smp.governance.infrastructure.http`

> **Shipped implementation** (4 endpoints, simplified): Detail endpoint `GET .../{reportId}` omitted
> (YAGNI). Counter-notice endpoints removed. Single `POST .../action` replaced by split `POST
> .../approve` and `POST .../reject`. Permission keys use dashes per codebase convention.

| Method | Path                                                    | Auth                                     | Handler                       |
|--------|---------------------------------------------------------|------------------------------------------|-------------------------------|
| `POST` | `/api/governance/media/takedown-reports`                | `media-read` (workspace membership)      | `ReportTakedownHandler`       |
| `GET`  | `/api/governance/media/takedown-reports`                | `media-read`                             | `ListTakedownReportsHandler`  |
| `POST` | `/api/governance/media/takedown-reports/{id}/approve`   | `media-takedown`                         | `ApproveTakedownHandler`      |
| `POST` | `/api/governance/media/takedown-reports/{id}/reject`    | `media-takedown`                         | `RejectTakedownHandler`       |

### Request/Response DTOs

**New file**:
`server/smp/src/main/kotlin/com/profiletailors/smp/governance/infrastructure/http/TakedownDtos.kt`

> **Shipped implementation** (simplified): No `TakedownActionRequest` — approve/reject are distinct
> POST endpoints with separate DTOs. Counter-notice DTOs removed.

```kotlin
data class ReportTakedownRequest(
    val assetId: String,
    val reason: String,
    val description: String,
    val referenceUrl: String? = null,
)

data class ReviewTakedownRequest(
    val notes: String? = null,
)

data class TakedownReportResponse(
    val reportId: String,
    val assetId: String,
    val workspaceId: String,
    val category: String,
    val description: String,
    val evidenceUrls: List<String>,
    val status: String,
    val createdAt: String,
)
```

### Application Handlers

**New files** in `server/smp/src/main/kotlin/com/profiletailors/smp/governance/application/`:

> **Shipped implementation** (4 handlers): No `GetTakedownReportQuery` (detail endpoint omitted).
> No counter-notice handlers. Split approve/reject replaces single `ReviewTakedownReportHandler`.

| File                                | Handler                        | Key behavior                                                                                                                                                    |
|-------------------------------------|--------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `TakedownHandlers.kt`              | `ReportTakedownHandler`        | Validates asset exists in workspace. Creates `TakedownReport` with status `REPORTED`. Persists. Records audit event `MEDIA_TAKEDOWN_REPORTED`. Sends domain event for email. Returns report response. |
| `TakedownHandlers.kt`              | `ApproveTakedownHandler`       | Validates report is reviewable. Sets asset status to `SUSPENDED`. Sets report status to `APPROVED`. Records `MEDIA_TAKEDOWN_APPROVED`. Sends domain event for email. |
| `TakedownHandlers.kt`              | `RejectTakedownHandler`        | Validates report is reviewable. Sets report status to `DISMISSED`. Asset untouched. Records `MEDIA_TAKEDOWN_REJECTED`. Sends domain event for email.             |
| `ListTakedownReportsQuery.kt`      | `ListTakedownReportsHandler`   | Lists reports for workspace. Returned by `GET /reports` endpoint.                                                                                               |

> **Shipped implementation**: Permission keys use dashes (`media-read`, `media-takedown`)
> matching codebase convention (see `workspace:consent:read`, `workspace:audit:read`).
> Authorization via `GovernanceAuthorizationService` (auth decider pattern) instead of direct
> `PermissionKey.of(...)` calls.

**Authorization pattern** — all handlers use:

```kotlin
governanceAuthorizationService.authorizeMediaRead(resourceContext)
governanceAuthorizationService.authorizeMediaTakedown(resourceContext)
```

The submit handler uses `authorizeMediaRead()` as a lighter membership gate. The approve/reject
handlers use `authorizeMediaTakedown()`.

### Authorization Permission Keys

**New file**: `db/changelog/authorization/011-seed-governance-media-permissions.yaml`

> **Shipped implementation**: Dash-delimited keys match existing codebase convention.

Seeds permissions:

- `workspace:governance:media-read` — workspace membership gate for submitting reports
- `workspace:governance:media-takedown` — review/action on reports

Both keys are seeded for `WORKSPACE_OWNER` role via `011-seed-governance-permissions.yaml`.

### Email Notifications

> **Shipped implementation**: Separate `TakedownEmailTemplates` class under governance context.
> Event-driven via `TakedownEmailConsumers` instead of direct `EmailTemplates` methods.
> Counter-notice templates removed from scope.

**File**:
`server/smp/src/main/kotlin/com/profiletailors/smp/governance/infrastructure/email/TakedownEmailTemplates.kt`

Three template methods:

```kotlin
fun takedownReportedEmail(reportId: String): EmailMessage
fun takedownApprovedEmail(reportId: String, assetId: String): EmailMessage
fun takedownRejectedEmail(reportId: String, assetId: String): EmailMessage
```

**Consumers** in `TakedownEmailConsumers.kt`:

- `SendTakedownReportedEmailConsumer` — resolves workspace admins via `WorkspaceOwnershipRepository`
- `SendTakedownApprovedEmailConsumer` — dispatches approved notification
- `SendTakedownRejectedEmailConsumer` — dispatches rejected notification

Templates follow the existing HTML pattern (`Space Grotesk` body, `Space Mono` labels, dark theme,
monochrome palette). Idempotency keys follow `governance.takedown.{event}:{reportId}:{adminEmail}`.

### Audit Events

> **Shipped implementation**: Generic `AuditHook.onMutation()` action strings instead of dedicated
> event-type enum. Counter-notice audit events removed.

**File**: Audit events use the existing `AuditHook.onMutation(action, ...)` contract from the
governance module. No new event-type enum was added.

Action strings emitted:

- `MEDIA_TAKEDOWN_REPORTED` — on `ReportTakedownHandler`
- `MEDIA_TAKEDOWN_APPROVED` — on `ApproveTakedownHandler`
- `MEDIA_TAKEDOWN_REJECTED` — on `RejectTakedownHandler`

### R2DBC Persistence — Takedown Reports

**New file**:
`server/smp/src/main/kotlin/com/profiletailors/smp/governance/infrastructure/R2dbcTakedownReportRepository.kt`

Follows the `R2dbcConsentRepository.kt` pattern:

- `@Repository` class implementing `MediaTakedownRepository`
- `DatabaseClient` for SQL
- Row mapper `rowToTakedownReport(row)` using `Readable.get()`
- JSONB serialization for `evidenceUrls` via `ObjectMapper`

### Asset Status Transitions for Takedown

**File**: `server/smp/src/main/kotlin/com/profiletailors/smp/media/application/MediaRepositories.kt`

- Add
  `suspend fun updateStatus(assetId: String, workspaceId: String, status: MediaAssetStatus): MediaAsset?`
  to the interface

**File**:
`server/smp/src/main/kotlin/com/profiletailors/smp/media/infrastructure/persistence/R2dbcMediaRepositories.kt`

- Implement `updateStatus()` with a generic UPDATE SET status = :status, updated_at =
  CURRENT_TIMESTAMP

### Flow Sequences

**Takedown Report → Submit → Approve → Suspend → Notify** (shipped flow)

```text
 Reporter                     TakedownController         ReportTakedownHandler     TakedownReportRepo  MediaAssetRepo   EmailConsumer    AuditHook
    │                              │                              │                      │                  │               │              │
    │── POST /takedown-reports ───►│                              │                      │                  │               │              │
    │                              │── ReportTakedownCmd ────────►│                      │                  │               │              │
    │                              │                              │── save(report) ─────►│                  │               │              │
    │                              │                              │── AuditHook.onMutation(MEDIA_TAKEDOWN_REPORTED) ──────────────────►              │
    │                              │                              │── publish(TakedownReported) ──────────────────────────────►                      │
    │◄── 201 Created ──────────────┤                              │                      │                  │               │              │
    │                              │                              │                      │                  │               │              │
    │ Admin                        │                              │                      │                  │               │              │
    │── POST .../{id}/approve ────►│                              │                      │                  │               │              │
    │                              │── ApproveTakedownCmd ───────►│                      │                  │               │              │
    │                              │                              │── update(report) ───►│                  │               │              │
    │                              │                              │── updateStatus(SUSPENDED) ─────────────►                   │              │
    │                              │                              │── AuditHook.onMutation(MEDIA_TAKEDOWN_APPROVED) ───────────────────►      │
    │                              │                              │── publish(TakedownApproved) ───────────────────────────►                   │
    │◄── 200 OK ───────────────────┤                              │                      │                  │               │              │
```

**Reject flow** (shipped, no counter-notice):

```text
    │ Admin                        │                              │                      │                  │               │              │
    │── POST .../{id}/reject ─────►│                              │                      │                  │               │              │
    │                              │── RejectTakedownCmd ────────►│                      │                  │               │              │
    │                              │                              │── update(report) ───►│                  │               │              │
    │                              │                              │── AuditHook.onMutation(MEDIA_TAKEDOWN_REJECTED) ───────────────────►     │
    │                              │                              │── publish(TakedownRejected) ──────────────────────────►                  │
    │◄── 200 OK ───────────────────┤                              │                      │                  │               │              │
```

### Data Flow — Media Query Filtering

```text
MediaAssetController.listAssets()
  └── ListWorkspaceAssetsQuery(statuses = defaultReadyStatuses)
       └── MediaAssetRepository.listByWorkspace(statuses)
            └── SQL: SELECT ... FROM media_assets
                 WHERE status IN (:statuses)
                 ORDER BY created_at DESC
```

Phase 2 change: the default statuses list in `MediaQueries.kt` / `MediaAssetController` excludes
`SUSPENDED`. Callers that want all statuses (admin views) pass an explicit set including
`SUSPENDED`.

### File Changes Summary

| File                                                                                                  | Action | Description                                                   |
|-------------------------------------------------------------------------------------------------------|--------|---------------------------------------------------------------|
| `server/smp/src/main/resources/db/changelog/media/006-drop-external-metadata.yaml`                    | Delete | Dead changelog — never applied, unreferenced in master        |
| `server/smp/src/main/resources/db/changelog/media/007-add-licence-column.yaml`                        | Create | Add `licence VARCHAR(64)` to `media_assets`                   |
| `server/smp/src/main/kotlin/.../media/domain/MediaModels.kt`                                          | Modify | Add `licence` field + `SUSPENDED` enum value                  |
| `server/smp/src/main/kotlin/.../media/application/MediaQueries.kt`                                    | Modify | Add `licence` to `MediaAssetSummary`                          |
| `server/smp/src/main/kotlin/.../media/application/MediaRepositories.kt`                               | Modify | Add `updateStatus()` to interface                             |
| `server/smp/src/main/kotlin/.../media/infrastructure/persistence/R2dbcMediaRepositories.kt`           | Modify | `licence` in all SQL + row mapper; implement `updateStatus()` |
| `server/smp/src/main/kotlin/.../media/infrastructure/http/MediaDtos.kt`                               | Modify | Add `licence` to `MediaAssetResponse`                         |
| `server/smp/src/main/kotlin/.../media/application/UnsplashMediaProviderHandlers.kt`                   | Modify | Set `licence = "unsplash"` in `persistPhoto()`                |
| `server/smp/src/main/kotlin/.../governance/domain/MediaTakedownModels.kt`                             | Create | `TakedownReport` value object + enums                         |
| `server/smp/src/main/kotlin/.../governance/domain/MediaTakedownRepository.kt`                         | Create | Repository interface                                          |
| `server/smp/src/main/kotlin/.../governance/application/TakedownCommandModels.kt`                      | Create | All command/query data classes                                |
| `server/smp/src/main/kotlin/.../governance/application/TakedownHandlers.kt`                           | Create | All handler classes                                           |
| `server/smp/src/main/kotlin/.../governance/infrastructure/http/TakedownDtos.kt`                       | Create | Request/response DTOs                                         |
| `server/smp/src/main/kotlin/.../governance/infrastructure/http/TakedownReportController.kt`           | Create | REST controller                                               |
| `server/smp/src/main/kotlin/.../governance/infrastructure/R2dbcTakedownReportRepository.kt`           | Create | R2DBC adapter                                                 |
| `server/smp/src/main/resources/db/changelog/governance/006-create-takedown-reports.yaml`              | Create | Takedown reports table                                        |
| `server/smp/src/main/resources/db/changelog/authorization/011-seed-governance-media-permissions.yaml` | Create | New permission keys                                           |
| `server/smp/src/main/kotlin/.../governance/infrastructure/email/TakedownEmailTemplates.kt`              | Create | Takedown email templates (separate from `EmailTemplates`)     |
| `server/smp/src/main/kotlin/.../governance/infrastructure/email/TakedownEmailConsumers.kt`             | Create | Domain-event consumers for email dispatch                     |
| `apps/web/app/src/modules/media/services/media-api.ts`                                                | Modify | Add `licence` to `MediaAssetSummary` type                     |
| `apps/web/app/src/modules/media/presentation/components/MediaAttribution.vue`                         | Create | Attribution display component                                 |
| `apps/web/app/src/modules/media/presentation/views/MediaLibraryView.vue`                              | Modify | Integrate `<MediaAttribution>` + SUSPENDED badge              |
| `apps/web/app/src/modules/governance/components/TakedownReportDialog.vue`                              | Create | Takedown report submission form                               |
| `apps/web/app/src/modules/governance/views/GovernanceTakedownView.vue`                                 | Create | Governance takedown review dashboard                          |

### Testing Strategy

| Layer                                  | What to Test                                                                | Approach                                                |
|----------------------------------------|-----------------------------------------------------------------------------|---------------------------------------------------------|
| Unit — domain                          | TakedownReport state machine (approve, dismiss, invariants)                 | Kotest table-driven (11 test cases)                    |
| Unit — handlers                        | Report/Approve/Reject/List flows with mocked repos + auth                   | Handler unit tests (11 test cases)                     |
| Integration — R2DBC                    | `R2dbcTakedownReportRepository` CRUD + workspace isolation + filtering      | `@DataR2dbcTest` with Postgres Testcontainers (3 cases)|
| Integration — REST                     | Controller endpoint contracts, auth enforcement, validation errors          | `@WebFluxTest` with mock handlers (8 test cases)       |
| Integration — Email consumers          | TakedownEmailConsumers dispatch with idempotency keys                      | Event consumer tests (15 cases)                        |
| Unit — Frontend (Vitest)               | TakedownReportDialog, GovernanceTakedownView, governance-api service        | Vitest (25 passed, 1 todo)                             |
| E2E — Frontend                         | Mocked authenticated review-and-approve flow                                | Playwright (3 browsers, 1 spec)                        |

### Migration / Rollout

- Phase 1: Deploy anytime (backward-compatible, `licence` is nullable)
- Phase 2: Always-on (feature flag dropped during simplification per ADR-005). Takedown endpoints
  are unconditionally registered.
- Rollback Phase 1: `liquibase rollback count 1` reverts licence column
- Rollback Phase 2: Remove takedown controller + changelogs. Bulk-update `SUSPENDED → READY`.

### Open Questions (Resolved)

- [x] **SUSPENDED visibility**: Hidden from default list (status=READY). Visible with a warning-styled
  "Suspended" badge when reviewer opts into `SUSPENDED` status filter. Shipped implementation.
- [x] **Rate-limiting**: Not implemented. Deferred — proposal mention was aspirational, no concrete
  requirement in shipped scope.
- [x] **Admin email**: Resolved via `WorkspaceOwnershipRepository` + `PrincipalIdentityLookup`. All
  workspace owners receive notifications. Counter-notice notification not applicable (removed).
