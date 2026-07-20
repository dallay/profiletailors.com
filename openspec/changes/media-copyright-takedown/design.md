# Design: Media Copyright, Attribution & Takedown Workflow

## Technical Approach

Two-phase incremental implementation. Phase 1 adds `licence` schema + attribution display. Phase 2 builds the full takedown workflow as a new governance sub-domain. Delivers immediate Unsplash compliance in Phase 1 while Phase 2 addresses DMCA readiness.

## Phase 1 — Schema + Attribution Display

### Architecture Decisions

| Option | Tradeoff | Decision |
|--------|----------|----------|
| **ADR-001**: `licence` as VARCHAR vs enum | VARCHAR is flexible for diverse licences (Unsplash, CC, proprietary) but shifts validation to app layer. Enum is type-safe but requires code changes for new values. | **VARCHAR(64) nullable** — new licence values appear too often to couple to an enum. Application-layer validation via a `LicenceValidator` in the domain. |
| **ADR-002**: Nullable `licence` for existing assets | Backfilling tens of thousands is impractical. Pre-existing rows get NULL. Attribution display shows what's available. | **Nullable** — display-only field; no migration needed for existing rows. |

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
- **Remove** `db/changelog/media/006-drop-external-metadata.yaml` — delete from git (never applied, unreferenced in master changelog). Attribution columns remain intact.

### Domain

- **File**: `server/smp/src/main/kotlin/com/profiletailors/smp/media/domain/MediaModels.kt`
- Add `val licence: String? = null` to `MediaAsset` data class (around line 166, after `metadata`)

### DTOs

- **File**: `server/smp/src/main/kotlin/com/profiletailors/smp/media/infrastructure/http/MediaDtos.kt`
- Add `val licence: String? = null` to `MediaAssetResponse` (around line 153, after `metadata`)

- **File**: `server/smp/src/main/kotlin/com/profiletailors/smp/media/application/MediaQueries.kt`
- Add `val licence: String? = null` to `MediaAssetSummary` (around line 58, after `metadata`)

### Persistence

- **File**: `server/smp/src/main/kotlin/com/profiletailors/smp/media/infrastructure/persistence/R2dbcMediaRepositories.kt`
- In `create()` (line 38–48): add `licence` to INSERT columns and `:licence` to VALUES. Bind with `.bindNullable("licence", asset.licence, String::class.java)`
- In every SELECT query: add `licence` to the column list
- In `rowToMediaAsset()` (line 451): add `licence = row.get("licence", String::class.java)` — nullable, no `requireNotNull`

### Unsplash Integration

- **File**: `server/smp/src/main/kotlin/com/profiletailors/smp/media/application/UnsplashMediaProviderHandlers.kt`
- In `persistPhoto()` (line 109–128): add `licence = "unsplash"` to the `MediaAsset` constructor (after `metadata`)

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
  - Import and render `<MediaAttribution>` inside each asset card (after the metadata section at line 558)
- **Update** `apps/web/app/src/modules/media/services/media-api.ts`:
  - Add `licence?: string | null` to `MediaAssetSummary` type (after `metadata`, line 39)

## Phase 2 — Takedown Workflow

### Architecture Decisions

| Option | Tradeoff | Decision |
|--------|----------|----------|
| **ADR-003**: TakedownReport in governance vs media context | Embedding in media context places legal/compliance logic alongside upload/storage concerns, violating separation of concerns. Governance owns audit trail and compliance controls. | **Governance context** — new `media/takedown` sub-domain within governance. Audit trail belongs under governance. |
| **ADR-004**: `SUSPENDED` status vs separate blocklist table | Blocklist table adds JOIN overhead for every media query and requires a new exclusion pattern. Status field already models asset lifecycle; SUSPENDED is a natural extension of the existing CAS model. | **New `SUSPENDED` value in `MediaAssetStatus`** — simpler queries, works with existing `listByWorkspace(statuses: Set<>)` filtering, status already models availability. |
| **ADR-005**: Feature flag for takedown endpoints | Takedown workflow is new functionality; a flag allows safe rollout and instant disable if issues arise. Use Spring Boot `@ConditionalOnProperty`. | **Feature flag `media.takedown.enabled=true`** — flag-gate the controller bean and task scheduler. |

### Domain Model — TakedownReport

**New file**: `server/smp/src/main/kotlin/com/profiletailors/smp/governance/domain/MediaTakedownModels.kt`

```kotlin
package com.profiletailors.smp.governance.domain

import java.time.Instant
import java.util.UUID

enum class TakedownCategory { COPYRIGHT, TRADEMARK, OTHER }
enum class TakedownStatus { SUBMITTED, UNDER_REVIEW, RESOLVED }
enum class TakedownResolution { TAKEDOWN_APPROVED, REPORT_REJECTED }
enum class CounterNoticeStatus { NONE, SUBMITTED, ACCEPTED, REJECTED }

data class TakedownReportId(val value: String) {
    companion object {
        fun generate(): TakedownReportId = TakedownReportId("tdr-${UUID.randomUUID()}")
    }
}

data class TakedownReport(
    val reportId: TakedownReportId,
    val assetId: String,
    val workspaceId: String,
    val reportedBy: String,
    val reporterContact: String,
    val category: TakedownCategory,
    val description: String,
    val evidenceUrls: List<String>,
    val status: TakedownStatus,
    val resolution: TakedownResolution? = null,
    val resolvedBy: String? = null,
    val resolvedAt: Instant? = null,
    val counterNoticeStatus: CounterNoticeStatus = CounterNoticeStatus.NONE,
    val counterNoticeDescription: String? = null,
    val counterNoticeSubmittedAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant? = null,
)
```

**New file**: `server/smp/src/main/kotlin/com/profiletailors/smp/governance/domain/MediaTakedownRepository.kt`

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

**Transition rules**: `SUSPENDED` → `READY` (counter-notice accepted). No transition out of `SUSPENDED` to `DELETED` (legal hold — must be resolved first).

### REST Endpoints

**New file**: `server/smp/src/main/kotlin/com/profiletailors/smp/governance/infrastructure/http/TakedownReportController.kt`

Package: `com.profiletailors.smp.governance.infrastructure.http`

| Method | Path | Auth | Handler |
|--------|------|------|---------|
| `POST` | `/api/governance/media/takedown-reports` | `governance:media:read` (workspace membership) | `SubmitTakedownReportHandler` |
| `GET` | `/api/governance/media/takedown-reports` | `governance:media:takedown` | `ListTakedownReportsHandler` |
| `GET` | `/api/governance/media/takedown-reports/{reportId}` | `governance:media:takedown` | `GetTakedownReportHandler` |
| `POST` | `/api/governance/media/takedown-reports/{reportId}/action` | `governance:media:takedown` | `ReviewTakedownReportHandler` |
| `POST` | `/api/governance/media/takedown-reports/{reportId}/counter-notice` | `governance:media:takedown` | `SubmitCounterNoticeHandler` |
| `POST` | `/api/governance/media/takedown-reports/{reportId}/counter-notice/action` | `governance:media:takedown` | `ReviewCounterNoticeHandler` |

### Request/Response DTOs

**New file**: `server/smp/src/main/kotlin/com/profiletailors/smp/governance/infrastructure/http/TakedownDtos.kt`

```kotlin
data class TakedownReportRequest(
    val assetId: String,
    val reason: String,              // COPYRIGHT | TRADEMARK | OTHER
    val description: String,
    val evidenceUrls: List<String> = emptyList(),
)

data class TakedownActionRequest(
    val action: String,              // APPROVE | REJECT
    val notes: String? = null,
)

data class CounterNoticeRequest(
    val description: String,
    val evidence: List<String> = emptyList(),
)

data class CounterNoticeActionRequest(
    val action: String,              // ACCEPT | REJECT
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
    val resolution: String?,
    val counterNoticeStatus: String,
    val createdAt: String,
)
```

### Application Handlers

**New files** in `server/smp/src/main/kotlin/com/profiletailors/smp/governance/application/`:

| File | Handler | Key behavior |
|------|---------|-------------|
| `SubmitTakedownReportCommand.kt` | `SubmitTakedownReportHandler` | Validates asset exists in workspace. Creates `TakedownReport` with status `SUBMITTED`. Persists via `MediaTakedownRepository`. Records audit event `MEDIA_TAKEDOWN_REPORTED`. Sends confirmation email. Returns report response. |
| `ListTakedownReportsQuery.kt` | — | Query object + handler. Lists reports for workspace. |
| `GetTakedownReportQuery.kt` | — | Fetch single report by ID + workspace. |
| `ReviewTakedownReportCommand.kt` | `ReviewTakedownReportHandler` | Validates report is reviewable. On `APPROVE`: sets asset status to `SUSPENDED` via `MediaAssetRepository.updateStatus()` (add new method), resolves report, records audit event `MEDIA_TAKEDOWN_APPROVED`, sends notification. On `REJECT`: resolves report, records `MEDIA_TAKEDOWN_REJECTED`, sends rejection email. |
| `SubmitCounterNoticeCommand.kt` | `SubmitCounterNoticeHandler` | Sets counter-notice status to `SUBMITTED`. Records `MEDIA_TAKEDOWN_COUNTER_NOTICE`. Sends `counterNoticeReceivedEmail` to admin. |
| `ReviewCounterNoticeCommand.kt` | `ReviewCounterNoticeHandler` | On `ACCEPT`: restores asset to `READY`, records `MEDIA_TAKEDOWN_RESTORED`, sends `assetRestoredNotificationEmail`. On `REJECT`: sets counter-notice status to `REJECTED`, updates report. |

**Authorization pattern** — all handlers use:

```kotlin
authorizationService.authorize(resourceContext, PermissionKey.of("workspace", "governance", "media:takedown"))
```

The submit handler uses `PermissionKey.of("workspace", "governance", "media:read")` as a lighter membership gate.

### Authorization Permission Keys

**New file**: `db/changelog/authorization/011-seed-governance-media-permissions.yaml`

Seeds permissions:
- `workspace:governance:media:read` — workspace membership gate for submitting reports
- `workspace:governance:media:takedown` — review/action on reports

Add both to `Role.kt` / seed data for `admin` and `owner` roles (consult existing role-permission seed patterns in `003-create-role-permissions.yaml` / `008-seed-default-permissions.yaml`).

### Email Notifications

**File**: `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/email/EmailTemplates.kt`

Add methods following the existing `verificationEmail()` pattern:

```kotlin
fun takedownReportConfirmationEmail(recipientEmail: String, reportId: String): EmailMessage
fun takedownApprovedNotificationEmail(recipientEmail: String, assetId: String, reportId: String): EmailMessage
fun takedownRejectedNotificationEmail(recipientEmail: String, assetId: String, reportId: String): EmailMessage
fun counterNoticeReceivedEmail(adminEmail: String, reportId: String): EmailMessage
fun assetRestoredNotificationEmail(recipientEmail: String, assetId: String, reportId: String): EmailMessage
```

Templates follow the existing HTML pattern (`Space Grotesk` body, `Space Mono` labels, dark theme, monochrome palette).

### Audit Events

**File**: `server/smp/src/main/kotlin/com/profiletailors/smp/governance/domain/AuditEventModels.kt` (applies to AuditEventItem)

New event types used in handler audit calls (use `auditEventWriter.write(...)` following existing pattern from governance handlers):

- `MEDIA_TAKEDOWN_REPORTED`
- `MEDIA_TAKEDOWN_APPROVED`
- `MEDIA_TAKEDOWN_REJECTED`
- `MEDIA_TAKEDOWN_COUNTER_NOTICE`
- `MEDIA_TAKEDOWN_RESTORED`

### R2DBC Persistence — Takedown Reports

**New file**: `server/smp/src/main/kotlin/com/profiletailors/smp/governance/infrastructure/R2dbcTakedownReportRepository.kt`

Follows the `R2dbcConsentRepository.kt` pattern:
- `@Repository` class implementing `MediaTakedownRepository`
- `DatabaseClient` for SQL
- Row mapper `rowToTakedownReport(row)` using `Readable.get()`
- JSONB serialization for `evidenceUrls` via `ObjectMapper`

### Asset Status Transitions for Takedown

**File**: `server/smp/src/main/kotlin/com/profiletailors/smp/media/application/MediaRepositories.kt`
- Add `suspend fun updateStatus(assetId: String, workspaceId: String, status: MediaAssetStatus): MediaAsset?` to the interface

**File**: `server/smp/src/main/kotlin/com/profiletailors/smp/media/infrastructure/persistence/R2dbcMediaRepositories.kt`
- Implement `updateStatus()` with a generic UPDATE SET status = :status, updated_at = CURRENT_TIMESTAMP

### Flow Sequences

**Takedown Report → Review → Approve → Suspend → Notify**

```
Reporter                    TakedownReportController          SubmitHandler       TakedownReportRepo  MediaAssetRepo    EmailSender    AuditEventWriter
   │                              │                              │                      │                  │                  │               │
   │── POST /takedown-reports ───►│                              │                      │                  │                  │               │
   │                              │── SubmitTakedownReportCmd ──►│                      │                  │                  │               │
   │                              │                              │── save(report) ─────►│                  │                  │               │
   │                              │                              │── audit(MEDIA_TAKEDOWN_REPORTED) ────────────────►─────│               │
   │                              │                              │── send(confirmationEmail) ────────────────────►          │               │
   │◄── 201 Created ──────────────┤                              │                      │                  │                  │               │
   │                              │                              │                      │                  │                  │               │
   │ Admin                        │                              │                      │                  │                  │               │
   │── POST .../reportId/action ──►│                              │                      │                  │                  │               │
   │                              │── ReviewTakedownReportCmd ──►│                      │                  │                  │               │
   │                              │                              │── update(report) ───►│                  │                  │               │
   │                              │                              │── updateStatus(assetId, SUSPENDED) ───────►                │               │
   │                              │                              │── audit(MEDIA_TAKEDOWN_APPROVED) ─────────────────►───────│               │
   │                              │                              │── send(approvedEmail) ───────────────────────────►        │               │
   │◄── 200 OK ───────────────────┤                              │                      │                  │                  │               │
```

**Counter-Notice → Review → Restore → Notify**

```
   Reporter                    TakedownReportController     SubmitCounterHandler    MediaTakedownRepo   MediaAssetRepo   EmailSender    AuditWriter
      │                              │                              │                     │                  │               │              │
      │── POST .../counter-notice ──►│                              │                     │                  │               │              │
      │                              │── SubmitCounterNoticeCmd ──►│                     │                  │               │              │
      │                              │                              │── update(report) ──►│                  │               │              │
      │                              │                              │── audit(COUNTER_NOTICE) ─────────────────────────►─────│              │
      │                              │                              │── send(adminEmail) ────────────────────────────►        │              │
      │◄── 200 OK ───────────────────┤                              │                     │                  │               │              │
      │                              │                              │                     │                  │               │              │
      │ Admin                        │                              │                     │                  │               │              │
      │── POST .../counter-notice/action ──►│                        │                     │                  │               │              │
      │                              │── ReviewCounterNoticeCmd ──►│                     │                  │               │              │
      │                              │                              │── update(report) ──►│                  │               │              │
      │                              │                              │── updateStatus(assetId, READY) ───────────────────►                  │
      │                              │                              │── audit(RESTORED) ───────────────────────────────►─────              │
      │                              │                              │── send(restoredEmail) ───────────────────────────►                  │
      │◄── 200 OK ───────────────────┤                              │                     │                  │               │              │
```

### Data Flow — Media Query Filtering

```
MediaAssetController.listAssets()
  └── ListWorkspaceAssetsQuery(statuses = defaultReadyStatuses)
       └── MediaAssetRepository.listByWorkspace(statuses)
            └── SQL: SELECT ... FROM media_assets
                 WHERE status IN (:statuses)
                 ORDER BY created_at DESC
```

Phase 2 change: the default statuses list in `MediaQueries.kt` / `MediaAssetController` excludes `SUSPENDED`. Callers that want all statuses (admin views) pass an explicit set including `SUSPENDED`.

### File Changes Summary

| File | Action | Description |
|------|--------|-------------|
| `server/smp/src/main/resources/db/changelog/media/006-drop-external-metadata.yaml` | Delete | Dead changelog — never applied, unreferenced in master |
| `server/smp/src/main/resources/db/changelog/media/007-add-licence-column.yaml` | Create | Add `licence VARCHAR(64)` to `media_assets` |
| `server/smp/src/main/kotlin/.../media/domain/MediaModels.kt` | Modify | Add `licence` field + `SUSPENDED` enum value |
| `server/smp/src/main/kotlin/.../media/application/MediaQueries.kt` | Modify | Add `licence` to `MediaAssetSummary` |
| `server/smp/src/main/kotlin/.../media/application/MediaRepositories.kt` | Modify | Add `updateStatus()` to interface |
| `server/smp/src/main/kotlin/.../media/infrastructure/persistence/R2dbcMediaRepositories.kt` | Modify | `licence` in all SQL + row mapper; implement `updateStatus()` |
| `server/smp/src/main/kotlin/.../media/infrastructure/http/MediaDtos.kt` | Modify | Add `licence` to `MediaAssetResponse` |
| `server/smp/src/main/kotlin/.../media/application/UnsplashMediaProviderHandlers.kt` | Modify | Set `licence = "unsplash"` in `persistPhoto()` |
| `server/smp/src/main/kotlin/.../governance/domain/MediaTakedownModels.kt` | Create | `TakedownReport` value object + enums |
| `server/smp/src/main/kotlin/.../governance/domain/MediaTakedownRepository.kt` | Create | Repository interface |
| `server/smp/src/main/kotlin/.../governance/application/TakedownCommandModels.kt` | Create | All command/query data classes |
| `server/smp/src/main/kotlin/.../governance/application/TakedownHandlers.kt` | Create | All handler classes |
| `server/smp/src/main/kotlin/.../governance/infrastructure/http/TakedownDtos.kt` | Create | Request/response DTOs |
| `server/smp/src/main/kotlin/.../governance/infrastructure/http/TakedownReportController.kt` | Create | REST controller |
| `server/smp/src/main/kotlin/.../governance/infrastructure/R2dbcTakedownReportRepository.kt` | Create | R2DBC adapter |
| `server/smp/src/main/resources/db/changelog/governance/006-create-takedown-reports.yaml` | Create | Takedown reports table |
| `server/smp/src/main/resources/db/changelog/authorization/011-seed-governance-media-permissions.yaml` | Create | New permission keys |
| `server/smp/src/main/kotlin/.../identity/infrastructure/email/EmailTemplates.kt` | Modify | Add takedown email templates |
| `apps/web/app/src/modules/media/services/media-api.ts` | Modify | Add `licence` to `MediaAssetSummary` type |
| `apps/web/app/src/modules/media/presentation/components/MediaAttribution.vue` | Create | Attribution display component |
| `apps/web/app/src/modules/media/presentation/views/MediaLibraryView.vue` | Modify | Integrate `<MediaAttribution>` in asset cards |

### Testing Strategy

| Layer | What to Test | Approach |
|-------|-------------|----------|
| Unit — domain | TakedownReport invariants, status transitions, counter-notice state machine | Kotest table-driven tests |
| Unit — handlers | Submit/Review/CounterNotice flows with mocked repos | Handler unit tests with mock repository + mock auth |
| Integration — R2DBC | `R2dbcTakedownReportRepository` CRUD + pagination | `@DataR2dbcTest` with embedded Postgres/Testcontainers |
| Integration — REST | Controller endpoint contracts, auth enforcement, validation errors | `@WebFluxTest` with mock handlers |
| Integration — Email template rendering | Each template renders with expected variables | `EmailTemplatesTest` (follow existing pattern) |
| E2E — Frontend | Attribution display visibility for Unsplash vs uploaded assets | Playwright `MediaLibraryView` test |

### Migration / Rollout

- Phase 1: Deploy anytime (backward-compatible, `licence` is nullable)
- Phase 2: Feature flag `media.takedown.enabled=false` by default. Enable per-workspace after review.
- Rollback Phase 1: `liquibase rollback count 1` reverts licence column
- Rollback Phase 2: Disable flag. Bulk-update `SUSPENDED → READY`. Remove changelogs.

### Open Questions

- [ ] Should `SUSPENDED` assets be visible with a "restricted" indicator, or fully hidden in the library view?
- [ ] Rate-limit strategy for takedown submissions per IP/principal? (Proposal mentions rate-limiting but design doesn't specify)
- [ ] Confirm admin email address for counter-notice notifications — does the app store a tenant admin contact?
