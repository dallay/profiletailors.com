# Delta: Media Copyright, Attribution & Takedown

> **Incremental**: Phase 1 (Attribution) and Phase 2 (Takedown) independently shippable.
> 
> **Archive note**: Delta reconciled to match shipped implementation. See verify-report.md for
> the full deviation log (W-01 through W-05). Counter-notice flow removed from scope by
> recorded simplification.

## Phase 1 — Media Attribution (New Capability)

| ID   | Req                                                                                 | Strength |
|------|-------------------------------------------------------------------------------------|----------|
| AT-1 | `media_assets` MUST add nullable `licence VARCHAR(64)` via Liquibase `007`          | MUST     |
| AT-2 | Dead `006-drop-external-metadata.yaml` SHOULD be removed from VCS                   | SHOULD   |
| AT-3 | `MediaAsset` model MUST include nullable `licence: String?`                         | MUST     |
| AT-4 | `MediaAssetResponse`/`MediaAssetSummary` MUST expose `licence`                      | MUST     |
| AT-5 | R2DBC mapping MUST read/write `licence` column                                      | MUST     |
| AT-6 | MediaLibrary MUST display authorName, authorUrl, sourceProvider, sourceUrl, licence | MUST     |
| AT-7 | Unsplash imports MUST set `licence = "unsplash"` at persist time                    | MUST     |
| AT-8 | Legacy assets keep NULL `licence` until re-imported                                 | SHOULD   |
| AT-9 | Attribution MUST NOT need extra API calls (fields in DTOs)                          | MUST     |

### Scenarios

**Unsplash import sets licence**: GIVEN an Unsplash photo imported via
`POST /api/media/providers/unsplash/photos/{id}/import` WHEN `persistPhoto()` runs THEN asset SHALL
have `licence = "unsplash"`.

**Legacy null licence**: GIVEN a pre-`licence` asset WHEN returned via API THEN `licence` SHALL be
`null` AND non-null attribution fields SHALL render.

**No extra API call**: GIVEN `MediaLibraryView.vue` receives `authorName = "John"` and
`sourceProvider = "unsplash"` WHEN it renders THEN attribution SHALL display AND no extra HTTP
request SHALL fire.

## Phase 2 — Media Takedown (New Capability)

### Shipped Implementation

The following reflects the actual shipped implementation after the recorded simplifications
during apply. Counter-notice flow was removed from scope. The REST surface uses split approve/
reject endpoints instead of a single action body. Permission keys use dashes per codebase
convention. The detail endpoint (TD-5) was intentionally omitted as YAGNI. The
`TakedownReportStatus.SUSPENDED` value is reserved for future use but not produced by the
state machine.

| ID    | Req                                                                                                                      | Strength |
|-------|--------------------------------------------------------------------------------------------------------------------------|----------|
| TD-1  | `MediaAssetStatus` MUST include `SUSPENDED`                                                                              | MUST     |
| TD-2a | `TakedownReportStatus`: `REPORTED`, `APPROVED`, `DISMISSED`, `SUSPENDED` (reserved, future use)                         | MUST     |
| TD-2b | `POST .../approve` → status `APPROVED`; `POST .../reject` → status `DISMISSED`                                          | MUST     |
| TD-3  | `POST /api/governance/media/takedown-reports` — submit                                                                   | MUST     |
| TD-4  | `GET .../takedown-reports` — list (`workspace:governance:media-read`)                                                    | MUST     |
| TD-5  | Detail endpoint `GET .../{id}` — **intentionally omitted** (YAGNI; list + action sub-paths suffice)                     | WONT     |
| TD-6  | `POST .../takedown-reports/{id}/approve` — approve (`workspace:governance:media-takedown`)                              | MUST     |
| TD-6b | `POST .../takedown-reports/{id}/reject` — reject (`workspace:governance:media-takedown`)                                | MUST     |
| TD-7  | `POST .../counter-notice` — **removed from scope** (simplification)                                                      | WONT     |
| TD-8  | Approve → asset `SUSPENDED` + email + audit `MEDIA_TAKEDOWN_APPROVED`                                                    | MUST     |
| TD-9  | Reject → asset stays `READY` + email + audit `MEDIA_TAKEDOWN_REJECTED`                                                   | MUST     |
| TD-10 | Counter-notice accepted → **removed from scope**                                                                         | WONT     |
| TD-11 | Counter-notice rejected → **removed from scope**                                                                         | WONT     |
| TD-12 | `SUSPENDED` excluded from picker/composer/previews/public API                                                            | MUST     |
| TD-13 | Every transition SHALL record via `AuditHook.onMutation` with string actions (`MEDIA_TAKEDOWN_*`)                       | MUST     |
| TD-14 | No feature flag — endpoints are always-on per simplification                                                             | MUST     |
| TD-15 | Permission keys use dashes: `workspace:governance:media-read`, `workspace:governance:media-takedown`                    | MUST     |

### Endpoint Surface (shipped)

| Method | Path                                                    | Auth                                       | Notes                          |
|--------|---------------------------------------------------------|--------------------------------------------|--------------------------------|
| POST   | `/api/governance/media/takedown-reports`                | `workspace:governance:media-read`          | Submit report                  |
| GET    | `/api/governance/media/takedown-reports`                | `workspace:governance:media-read`          | List reports                   |
| POST   | `/api/governance/media/takedown-reports/{id}/approve`   | `workspace:governance:media-takedown`      | Approve takedown               |
| POST   | `/api/governance/media/takedown-reports/{id}/reject`    | `workspace:governance:media-takedown`      | Reject takedown                |

### Scenarios

**Submit report**: GIVEN workspace member viewing a `READY` asset WHEN they POST with `reason`,
`description`, `assetId` THEN `201` + `TakedownReport` with status `REPORTED` + audit event.

**Approve → suspended**: GIVEN report `REPORTED` WHEN staff with
`workspace:governance:media-takedown` calls `POST .../approve` THEN asset → `SUSPENDED`,
report → `APPROVED` + `MEDIA_TAKEDOWN_APPROVED` audit + email.

**Reject → stays READY**: GIVEN report `REPORTED` WHEN authorized user calls `POST .../reject` THEN
asset stays `READY`, report → `DISMISSED` + audit + email.

**Unauthorized blocked**: GIVEN `MEMBER` without `workspace:governance:media-takedown` WHEN calling
`POST .../approve` THEN `403`.

**Suspended excluded**: GIVEN `SUSPENDED` asset WHEN picker/composer list queries run THEN asset
SHALL NOT appear.

## Modified — Deltas

### Media Library

- **`SUSPENDED` in `MediaAssetStatus`**: excluded from list/query results unless caller holds
  `workspace:governance:media-read`. Default `status=READY` filter unchanged. (Previously: no
  suspension state.)
- **`licence` on DTOs**: `MediaAssetResponse`/`MediaAssetSummary` MUST expose nullable
  `licence: String?`. (Previously: absent.)
- **Lifecycle extended**: `READY → SUSPENDED` (takedown). Counter-notice restoration removed.
  No `SUSPENDED → PROCESSING` retry path.
- **`SUSPENDED` badge in UI**: MediaLibraryView shows a warning-styled badge for `SUSPENDED` assets.

### IAM

- **Two permissions**: `workspace:governance:media-read` (view reports) and
  `workspace:governance:media-takedown` (take action). Dash-delimited per codebase convention.
  `takedown` only for `OWNER`/`ADMIN`. (Previously: none.)

### Email Notifications

- **Three templates** (shipped): Takedown Confirmation (on submit), Takedown Approved (on approve),
  Takedown Rejected (on reject). Dispatched via domain-event consumers:
  `TakedownEmailTemplates` + `SendTakedownReportedEmailConsumer`,
  `SendTakedownApprovedEmailConsumer`, `SendTakedownRejectedEmailConsumer`.
  Counter-notice templates removed from scope.
