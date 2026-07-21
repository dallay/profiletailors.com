# Delta: Media Copyright, Attribution & Takedown

> **Incremental**: Phase 1 (Attribution) and Phase 2 (Takedown) independently shippable.

## Phase 1 — Media Attribution (New Capability)

| ID | Req | Strength |
|----|-----|----------|
| AT-1 | `media_assets` MUST add nullable `licence VARCHAR(64)` via Liquibase `007` | MUST |
| AT-2 | Dead `006-drop-external-metadata.yaml` SHOULD be removed from VCS | SHOULD |
| AT-3 | `MediaAsset` model MUST include nullable `licence: String?` | MUST |
| AT-4 | `MediaAssetResponse`/`MediaAssetSummary` MUST expose `licence` | MUST |
| AT-5 | R2DBC mapping MUST read/write `licence` column | MUST |
| AT-6 | MediaLibrary MUST display authorName, authorUrl, sourceProvider, sourceUrl, licence | MUST |
| AT-7 | Unsplash imports MUST set `licence = "unsplash"` at persist time | MUST |
| AT-8 | Legacy assets keep NULL `licence` until re-imported | SHOULD |
| AT-9 | Attribution MUST NOT need extra API calls (fields in DTOs) | MUST |

### Scenarios

**Unsplash import sets licence**: GIVEN an Unsplash photo imported via `POST /api/media/providers/unsplash/photos/{id}/import` WHEN `persistPhoto()` runs THEN asset SHALL have `licence = "unsplash"`.

**Legacy null licence**: GIVEN a pre-`licence` asset WHEN returned via API THEN `licence` SHALL be `null` AND non-null attribution fields SHALL render.

**No extra API call**: GIVEN `MediaLibraryView.vue` receives `authorName = "John"` and `sourceProvider = "unsplash"` WHEN it renders THEN attribution SHALL display AND no extra HTTP request SHALL fire.

## Phase 2 — Media Takedown (New Capability)

| ID | Req | Strength |
|----|-----|----------|
| TD-1 | `MediaAssetStatus` MUST include `SUSPENDED` | MUST |
| TD-2 | `TakedownReport` states: `SUBMITTED → UNDER_REVIEW → RESOLVED`; counter: `COUNTER_NOTICE_SUBMITTED → RESTORED/ESCALATED` | MUST |
| TD-3 | `POST /api/governance/media/takedown-reports` — submit | MUST |
| TD-4 | `GET .../takedown-reports` — list (`workspace:governance:media:read`) | MUST |
| TD-5 | `GET .../takedown-reports/{id}` — detail (`workspace:governance:media:read`) | MUST |
| TD-6 | `POST .../takedown-reports/{id}/action` — review (`workspace:governance:media:takedown`) | MUST |
| TD-7 | `POST .../takedown-reports/{id}/counter-notice` — submit | MUST |
| TD-8 | Approve → asset `SUSPENDED` + email + audit `MEDIA_TAKEDOWN_APPROVED` | MUST |
| TD-9 | Reject → asset stays `READY` + email + audit `MEDIA_TAKEDOWN_REJECTED` | MUST |
| TD-10 | Counter-notice accepted → asset `READY` + email | MUST |
| TD-11 | Counter-notice rejected → asset stays `SUSPENDED`, escalate | MUST |
| TD-12 | `SUSPENDED` excluded from picker/composer/previews/public API | MUST |
| TD-13 | Every transition SHALL record in `audit_events` | MUST |

### Scenarios

**Submit report**: GIVEN workspace member viewing a `READY` asset WHEN they POST with `reason`, `description`, `assetId` THEN `201` + `TakedownReport` with status `SUBMITTED` + audit event.

**Approve → suspended**: GIVEN report `UNDER_REVIEW` WHEN staff with `workspace:governance:media:takedown` calls `POST .../action` with `APPROVE` THEN asset → `SUSPENDED`, report → `RESOLVED`/`TAKEDOWN_APPROVED`, email sent.

**Reject → stays READY**: GIVEN report `UNDER_REVIEW` WHEN authorized user calls `.../action` with `REJECT` THEN asset stays `READY`, report → `RESOLVED`/`REPORT_REJECTED`.

**Counter-notice accepted**: GIVEN resolved `TAKEDOWN_APPROVED` + asset `SUSPENDED` WHEN owner counters AND staff approves THEN asset → `READY`, report → `RESTORED`.

**Counter-notice rejected**: GIVEN counter-notice on `SUSPENDED` asset WHEN staff rejects THEN asset stays `SUSPENDED`, report → `ESCALATED`.

**Unauthorized blocked**: GIVEN `MEMBER` without `workspace:governance:media:takedown` WHEN calling `POST .../action` THEN `403`.

**Suspended excluded**: GIVEN `SUSPENDED` asset WHEN picker/composer list queries run THEN asset SHALL NOT appear.

## Modified — Deltas

### Media Library
- **`SUSPENDED` in `MediaAssetStatus`**: excluded from list/query results unless caller holds `workspace:governance:media:read`. Default `status=READY` filter unchanged. (Previously: no suspension state.)
- **`licence` on DTOs**: `MediaAssetResponse`/`MediaAssetSummary` MUST expose nullable `licence: String?`. (Previously: absent.)
- **Lifecycle extended**: `READY → SUSPENDED` (takedown) and `SUSPENDED → READY` (counter-notice). No `SUSPENDED → PROCESSING` retry path.

### IAM
- **Two permissions**: `workspace:governance:media:read` (view reports) and `workspace:governance:media:takedown` (take action). `takedown` only for `OWNER`/`ADMIN`. (Previously: none.)

### Email Notifications
- **Three templates**: Takedown Confirmation (on submit), Takedown Resolution (on approve/reject), Counter-Notice Update (on restore/escalate). Plain-text + HTML via `EmailTemplates`. (Previously: verification only.)
