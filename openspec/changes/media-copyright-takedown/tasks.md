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

- [ ] 1.1 Delete dead `db/changelog/media/006-drop-external-metadata.yaml` from git
- [ ] 1.2 Create `db/changelog/media/007-add-licence-column.yaml` — nullable `licence VARCHAR(64)` on `media_assets`
- [ ] 1.3 Add `val licence: String? = null` to `MediaAsset` domain model
- [ ] 1.4 Add `licence` to `MediaAssetResponse` DTO and `MediaAssetSummary` DTO
- [ ] 1.5 Add `licence` to all SQL queries + `rowToMediaAsset()` in `R2dbcMediaRepositories.kt`
- [ ] 1.6 Set `licence = "unsplash"` in `UnsplashMediaProviderHandlers.persistPhoto()`
- [ ] 1.7 Add `licence?: string | null` to frontend `MediaAssetSummary` type in `media-api.ts`
- [ ] 1.8 Create `MediaAttribution.vue` — displays author, source, licence when non-null
- [ ] 1.9 Integrate `<MediaAttribution>` into `MediaLibraryView.vue` asset cards
- [ ] 1.10 Test: licence maps correctly in R2DBC repository read/write
- [ ] 1.11 Test: Unsplash import produces asset with `licence = "unsplash"`

## Phase 2 — Takedown Workflow

- [ ] 2.1 Create authorization changelog `011-seed-governance-media-permissions.yaml` — seed `workspace:governance:media:read` and `workspace:governance:media:takedown` for admin/owner roles
- [ ] 2.2 Create governance changelog `006-create-takedown-reports.yaml` — `media_takedown_reports` table with indexes
- [ ] 2.3 Add `SUSPENDED` to `MediaAssetStatus` enum after `DELETED`
- [ ] 2.4 Create enums: `TakedownCategory`, `TakedownStatus`, `TakedownResolution`, `CounterNoticeStatus` in `governance/domain/MediaTakedownModels.kt`
- [ ] 2.5 Create `TakedownReport` domain model + `TakedownReportId` value object
- [ ] 2.6 Create `MediaTakedownRepository` interface with CRUD + pagination
- [ ] 2.7 Add `updateStatus(assetId, workspaceId, status)` to `MediaAssetRepository` interface
- [ ] 2.8 Implement `updateStatus()` in `R2dbcMediaRepositories.kt`
- [ ] 2.9 Create `R2dbcTakedownReportRepository` with `DatabaseClient` + row mapper
- [ ] 2.10 Create command/query classes: `SubmitTakedownReportCommand`, `ReviewTakedownReportCommand`, `SubmitCounterNoticeCommand`, `ReviewCounterNoticeCommand`, list/detail query objects
- [ ] 2.11 Create handlers: `SubmitTakedownReportHandler` (validate asset, save, audit `MEDIA_TAKEDOWN_REPORTED`, send email)
- [ ] 2.12 Create `ReviewTakedownReportHandler` — approve → asset `SUSPENDED` + audit + email; reject → audit + email
- [ ] 2.13 Create `SubmitCounterNoticeHandler` + `ReviewCounterNoticeHandler` — accept restores `READY`, rejects escalates
- [ ] 2.14 Create DTOs: `TakedownReportRequest`, `TakedownActionRequest`, `CounterNoticeRequest`, `CounterNoticeActionRequest`, `TakedownReportResponse`
- [ ] 2.15 Create `TakedownReportController` with 6 endpoints behind feature flag `media.takedown.enabled`
- [ ] 2.16 Add audit event types: `MEDIA_TAKEDOWN_REPORTED`, `APPROVED`, `REJECTED`, `COUNTER_NOTICE`, `RESTORED`
- [ ] 2.17 Add 5 email template methods to `EmailTemplates.kt` following existing pattern
- [ ] 2.18 Wire email sending in all takedown handlers
- [ ] 2.19 Exclude `SUSPENDED` from default statuses in `MediaQueries.kt` / `MediaAssetController`
- [ ] 2.20 Wire auth checks in controller (`governance:media:read` for submit, `governance:media:takedown` for actions)
- [ ] 2.21 Create frontend takedown report submission form in new `governance/` module
- [ ] 2.22 Create frontend takedown review dashboard for authorized staff
- [ ] 2.23 Show `SUSPENDED` status badge in `MediaLibraryView.vue`
- [ ] 2.24 Unit tests: domain invariants and state machine for all enum + model transitions
- [ ] 2.25 Unit tests: all command handlers with mocked repos + auth
- [ ] 2.26 Integration tests: `R2dbcTakedownReportRepository` CRUD + pagination
- [ ] 2.27 WebFlux tests: controller endpoint contracts, validation, auth enforcement
- [ ] 2.28 E2E test: attribution display visibility for Unsplash vs uploaded assets
