# Media Attribution Specification

## Purpose

Define the attribution data model and display requirements for externally-sourced media assets.
Ensures compliance with provider license terms (e.g., Unsplash attribution requirement) by surfacing
author, source, and licence information in the media library UI.

## Requirements

### Requirement: Licence Schema

The `media_assets` table MUST include a nullable `licence VARCHAR(64)` column authored via Liquibase
changelog `007-add-licence-column.yaml`. Pre-existing assets SHALL have `NULL` until re-imported.
The dead file `006-drop-external-metadata.yaml` (never referenced in `db.changelog-master.yaml`)
SHOULD be removed from version control to prevent confusion.

#### Scenario: Licence column added via migration

- GIVEN a PostgreSQL `media_assets` table at the post-`005` Liquibase head
- WHEN the `007-add-licence-column` changeset runs
- THEN a nullable `licence VARCHAR(64)` column SHALL exist on `media_assets`
- AND existing rows SHALL have `licence = NULL`

### Requirement: Domain Model and DTOs

`MediaAsset` MUST include a nullable `licence: String?` field. `MediaAssetResponse` and
`MediaAssetSummary` MUST expose `licence` as a nullable string in JSON responses. The R2DBC mapping
MUST read and write the `licence` column.

#### Scenario: Model carries licence field

- GIVEN a `MediaAsset` with `licence = "unsplash"`
- WHEN the domain model is serialized
- THEN `MediaAssetResponse.licence` SHALL be `"unsplash"`
- AND `MediaAssetSummary.licence` SHALL be `"unsplash"`

#### Scenario: Legacy asset returns null licence

- GIVEN a `MediaAsset` persisted before the `licence` column existed
- WHEN returned via API
- THEN `licence` SHALL be `null`

### Requirement: Unsplash Licence Assignment

The Unsplash provider handler MUST set `licence = "unsplash"` when persisting a photo imported from
Unsplash, alongside the existing `sourceProvider = "unsplash"`, `authorName`, `authorUrl`, and
`sourceUrl` fields.

#### Scenario: Import sets licence automatically

- GIVEN an Unsplash photo is imported via
  `POST /api/media/providers/unsplash/photos/{externalId}/import`
- WHEN `UnsplashMediaProviderHandlers.persistPhoto()` executes
- THEN the persisted `MediaAsset` SHALL have `licence = "unsplash"`
- AND `sourceProvider` SHALL remain `"unsplash"`

### Requirement: Attribution Display in Media Library

The `MediaLibraryView.vue` component MUST display attribution information (author name, author URL,
source provider, source URL, licence) when available for an asset. Attribution display MUST NOT
require additional API calls — all fields are already present in the `MediaAssetSummary` DTO. When
attribution data is absent, the component SHALL gracefully hide attribution sections.

#### Scenario: Attribution renders without extra API call

- GIVEN `MediaLibraryView.vue` receives a `MediaAssetSummary` with `authorName = "John Doe"` and
  `sourceProvider = "unsplash"`
- WHEN the component renders
- THEN author and source SHALL be displayed in the asset card
- AND no additional HTTP request SHALL be made

#### Scenario: Asset without attribution hides section gracefully

- GIVEN `MediaLibraryView.vue` receives a `MediaAssetSummary` with all attribution fields null
- WHEN the component renders
- THEN the attribution section SHALL be hidden
- AND the asset card SHALL still render normally
