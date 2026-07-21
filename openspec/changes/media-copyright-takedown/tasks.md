# Tasks: Media Copyright, Attribution & Takedown Workflow

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | 900–1100 |
| 400-line budget risk | High |
| Chained PRs recommended | Yes |
| Suggested split | PR 1 (Phase 1: Attribution) → PR 2 (Phase 2: Takedown) |
| Delivery strategy | ask-on-risk |
| Chain strategy | pending |

Decision needed before apply: Yes
Chained PRs recommended: Yes
Chain strategy: pending
400-line budget risk: High

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|------|------|-----------|-------|
| 1 | Attribution schema + display (licence column, DTOs, UI component) | PR 1 | Base: main. Backward-compatible, nullable licence. |
| 2 | Takedown workflow (governance domain, auth, notifications, frontend) | PR 2 | Depends on PR 1 licence schema + SUSPENDED status. |

## Phase 1 — Schema & Attribution

- [x] 1.1 Delete dead `db/changelog/media/006-drop-external-metadata.yaml` from git
- [x] 1.2 Create `db/changelog/media/007-add-licence-column.yaml` — nullable `licence VARCHAR(64)` on `media_assets`
- [x] 1.3 Add `val licence: String? = null` to `MediaAsset` domain model
- [x] 1.4 Add `licence` to `MediaAssetResponse` DTO and `MediaAssetSummary` DTO
- [x] 1.5 Add `licence` to all SQL queries + `rowToMediaAsset()` in `R2dbcMediaRepositories.kt`
- [x] 1.6 Set `licence = "unsplash"` in `UnsplashMediaProviderHandlers.persistPhoto()`
- [x] 1.7 Add `licence?: string | null` to frontend `MediaAssetSummary` type in `media-api.ts`
- [x] 1.8 Create `MediaAttribution.vue` — displays author, source, licence when non-null
- [x] 1.9 Integrate `<MediaAttribution>` into `MediaLibraryView.vue` asset cards
- [x] 1.10 Test: licence maps correctly in R2DBC repository read/write
- [x] 1.11 Test: Unsplash import produces asset with `licence = "unsplash"`

## Phase 2 — Takedown Workflow

### Backend Core (Batch 1 — committed `ef472361`)
_Design was simplified during implementation: dropped counter-notice, resolution enum, and feature flag. No email wiring yet. Permission keys use dashes (e.g. `media-read`, `media-takedown`) instead of colons._

- [x] 2.1 Create authorization changelog `011-seed-governance-permissions.yaml` — seed `workspace:governance:media-read` and `workspace:governance:media-takedown` for WORKSPACE_OWNER
- [x] 2.2 Create governance changelog `006-create-takedown-reports.yaml` — `takedown_reports` table with workspace+status and workspace+asset indexes
- [x] 2.3 Add `SUSPENDED` to `MediaAssetStatus` enum (between FAILED and DELETED)
- [x] 2.4 Simplified: `TakedownReportStatus` enum (REPORTED, APPROVED, DISMISSED, SUSPENDED) replaces separate category/status/resolution enums
- [x] 2.5 Create `TakedownReport` domain model with approve/dismiss lifecycle + invariants. No separate value object — String IDs
- [x] 2.6 Create `TakedownReportRepository` interface without pagination (simplified)
- [x] 2.7 Add `updateStatus(assetId, workspaceId, status)` to `MediaAssetRepository`
- [x] 2.8 Implement `updateStatus()` in `R2dbcMediaRepositories.kt`
- [x] 2.9 Create `R2dbcTakedownReportRepository` with `DatabaseClient` + row mapper + upsert
- [x] 2.10 Create simplified commands/queries: `ReportTakedownCommand`, `ApproveTakedownCommand`, `RejectTakedownCommand`, `ListTakedownReportsQuery` (no counter-notice)
- [x] 2.11 Create `ReportTakedownHandler` — validates asset, saves, audits. Email not yet wired.
- [x] 2.12 Create `ApproveTakedownHandler` + `RejectTakedownHandler` — status transitions, audit. Email not yet wired. Asset status update not yet wired.
- [ ] 2.13 Counter-notice — removed from scope (simplified)
- [x] 2.14 Create DTOs: `ReportTakedownRequest`, `ReviewTakedownRequest`, `TakedownReportResponse`
- [x] 2.15 Create `TakedownController` with 4 endpoints: POST /reports, POST /reports/{id}/approve, POST /reports/{id}/reject, GET /reports. Feature flag not implemented.
- [ ] 2.16 Add audit event types — deferred. Handlers use generic audit wiring.
- [x] 2.17 Email templates — TakedownReported/Approved/Rejected templates with render() + idempotencyKey() + payload
- [x] 2.18 Email wiring — 3 event-driven consumers (SendTakedownReportedEmailConsumer, SendTakedownApprovedEmailConsumer, SendTakedownRejectedEmailConsumer) listen to domain events published by handlers; reported consumer resolves workspace admins via WorkspaceOwnershipRepository + PrincipalIdentityLookup
- [x] 2.19 Exclude `SUSPENDED` from default statuses — default already `setOf(MediaAssetStatus.READY)`, no change required
- [x] 2.20 Wire auth checks via `GovernanceAuthorizationService` with `media-read` and `media-takedown` permissions
- [ ] 2.21 Frontend: report form — not yet implemented
- [ ] 2.22 Frontend: review dashboard — not yet implemented
- [ ] 2.23 Frontend: SUSPENDED badge in MediaLibraryView — not yet implemented
- [x] 2.24 Unit tests: domain invariants and state machine — 11 test cases covering create, approve, dismiss, double-approve, double-dismiss, status transitions
- [x] 2.25 Unit tests: all command handlers with mocked repos + auth — 11 test cases (ReportTakedownHandler: 2, ApproveTakedownHandler: 3, RejectTakedownHandler: 3, ListTakedownReportsHandler: 3)
- [ ] 2.26 Integration tests (Postgres-backed) — not yet implemented
- [x] 2.27 WebFlux tests — TakedownControllerWebTest with 8 cases (report/approve/reject/list/validation)
- [ ] 2.28 E2E tests — not yet implemented
