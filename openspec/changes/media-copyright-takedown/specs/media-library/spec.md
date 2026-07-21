# Delta for Media Library

## ADDED Requirements

### Requirement: Licence Field on DTOs

`MediaAssetResponse` and `MediaAssetSummary` MUST include a nullable `licence: String?` field alongside the existing attribution fields (`authorName`, `authorUrl`, `sourceProvider`, `sourceUrl`). The field SHALL serialize as `null` for legacy assets.

#### Scenario: Licence field present in response
- GIVEN a `MediaAsset` with `licence = "unsplash"`
- WHEN serialized to `MediaAssetResponse`
- THEN the JSON SHALL include `"licence": "unsplash"`

### Requirement: Attribution Display in MediaLibraryView

`MediaLibraryView.vue` MUST render attribution information (author name, author URL, source provider, source URL, licence) when available. The component SHALL NOT make additional API calls — all fields are already present in `MediaAssetSummary`. When attribution fields are null, the component SHALL hide the attribution section gracefully.

#### Scenario: Attribution renders inline
- GIVEN `MediaLibraryView.vue` receives a `MediaAssetSummary` with `authorName = "John Doe"` and `licence = "unsplash"`
- WHEN the component renders
- THEN author name SHALL be displayed
- AND licence value SHALL be displayed
- AND no additional HTTP request SHALL be made

### Requirement: SUSPENDED Status in List Filtering

Media library list queries MUST exclude assets with `MediaAssetStatus.SUSPENDED` from all picker, composer, and public API responses. The default list filter (`status=READY`) SHALL remain unchanged. Explicit `status` query parameters SHALL also exclude `SUSPENDED` assets unless the caller holds `workspace:governance:media:read`.
(Previously: no moderation/exclusion status existed; all READY assets were returned.)

#### Scenario: Library list excludes SUSPENDED
- GIVEN a workspace contains both `READY` and `SUSPENDED` assets
- WHEN a member requests the media library list with default filters
- THEN the response SHALL include only non-`SUSPENDED` assets

## MODIFIED Requirements

### Requirement: Minimal Asset Lifecycle for MVP Reuse

The system MUST expose status information for the client to distinguish lifecycle states: `PROCESSING`, `PENDING_UPLOAD`, `UPLOADING`, `READY`, `FAILED`, `DELETED`, and `SUSPENDED`. Assets in `SUSPENDED` state are not retryable and SHALL NOT transition to `PROCESSING` without a counter-notice approval. The `FAILED → PROCESSING` retry path SHALL NOT apply to `SUSPENDED` assets.
(Previously: the lifecycle included `PROCESSING`, `READY`, `FAILED`, `DELETED` without `SUSPENDED`.)

Asset lifecycle state transitions (updated):

- `PROCESSING` → `READY` (upload completes successfully)
- `PROCESSING` → `FAILED` (upload fails or is interrupted)
- `FAILED` → `PROCESSING` (client retries)
- `READY` → `SUSPENDED` (takedown approved)
- `SUSPENDED` → `READY` (counter-notice approved)

#### Scenario: READY asset transitions to SUSPENDED on takedown
- GIVEN a READY media asset
- WHEN a takedown report against that asset is approved
- THEN the asset SHALL transition to `SUSPENDED`
- AND the asset SHALL NOT appear in standard library list results

#### Scenario: SUSPENDED asset restored via counter-notice
- GIVEN a `SUSPENDED` asset
- WHEN the counter-notice is approved
- THEN the asset SHALL transition to `READY`
- AND the asset SHALL reappear in standard library list results
