# Delta for Publishing

## ADDED Requirements

### Requirement: Publication Creation Uses Persisted Media Asset References

The publishing capability MUST allow workspace publications to reference persisted media-library
assets through `assetIds` instead of relying on local-only browser attachment state.

For this MVP, publication creation MUST accept previously uploaded workspace asset identifiers that
resolve to ready assets owned by the separate media-library bounded context. Publishing MUST consume
those identifiers through a provider-neutral media-library contract rather than by treating media as
an internal publishing concern. The system MUST continue using the current provider-neutral asset
reference pattern so downstream publishing integrations can consume stored assets through existing
storage-backed publishing flows. The client MUST submit real persisted `assetIds` when the user
attaches uploaded or previously stored media.

#### Scenario: Publication request attaches a previously uploaded workspace asset

- GIVEN a workspace has a persisted uploaded media asset that is `READY` for publication use
- WHEN the user creates a publication and selects that asset
- THEN the publication request MUST include the persisted asset identifier in `assetIds`
- AND the system MUST associate the publication with that asset through the
  `publication_asset_links` join record (publicationId, assetId). This join table already exists in
  the schema (see changelog `005-create-publication-asset-links.yaml`). Each publication may
  reference multiple assets. The assetId in the link record MUST resolve to a MediaAsset owned by
  the media bounded context.

#### Scenario: Publication created with no attached assets

- GIVEN a workspace member creates a publication with an empty `assetIds` list (text-only post)
- WHEN the system validates the publication
- THEN the system MUST accept the request without calling the media asset resolution port
- AND no media validation is performed for zero-asset publications
- AND the publishing handler MUST short-circuit before calling the media port when `assetIds` is
  empty, so zero-asset publications succeed even when the media context is unavailable.

#### Scenario: Local-only attachments are not the source of truth for publication submission

- GIVEN a user has selected media in the composer
- WHEN the user submits the publication
- THEN the client MUST use persisted media-library asset identifiers for attached media
- AND it MUST NOT submit the publication as if attachments were local-only temporary state

### Requirement: Publication Attachment Validation Uses Workspace Media Library State

The system MUST validate attached `assetIds` against workspace scope and media readiness through the
media-library bounded-context boundary before publication acceptance or execution.

Every attached asset MUST belong to the active workspace and MUST be in a state `READY` for
publishing consumption. The system MUST reject asset references that are missing, belong to another
workspace, or represent incomplete uploads. This validation MUST occur before downstream provider
publishing begins. The same validation rules MUST apply both when a publication is created and when
an existing publication is edited to change its attached assets. Publishing MUST treat media
readiness and asset existence as media-library concerns even if existing persistence structures are
reused behind the boundary.

#### Scenario: Publication rejects asset from another workspace

- GIVEN a publication request is made in workspace A
- AND one of the supplied `assetIds` belongs to workspace B
- WHEN the system validates the publication request
- THEN the system MUST reject the request before creating or executing the provider publication
- AND the response MUST identify that the asset is unavailable in the active workspace

#### Scenario: Publication rejects incomplete uploaded asset

- GIVEN a publication request includes an asset whose upload has not completed successfully
- WHEN the system validates the publication request
- THEN the system MUST reject or block the publication before provider execution
- AND the result MUST identify that the asset is not ready for publishing use

#### Scenario: Publication edit rejects incomplete uploaded asset

- GIVEN an existing publication is being edited in a workspace
- AND the updated request includes an asset whose upload has not completed successfully
- WHEN the system validates the edited publication request
- THEN the system MUST reject or block the update before provider execution
- AND the result MUST identify that the asset is not ready for publishing use

#### Scenario: Publication rejects missing asset identifier

- GIVEN a publication request includes an asset identifier that does not resolve within the active
  workspace
- WHEN the system validates the publication request
- THEN the system MUST reject the request before provider execution
- AND the result MUST identify that the asset is unavailable in the active workspace

#### Scenario: Provider dispatch handles storage unavailability after validation

- GIVEN an asset passed `READY` validation before provider dispatch
- WHEN the storage binary is unavailable at dispatch time (e.g., transient storage error)
- THEN the provider adapter MUST treat this as a retriable infrastructure error
- AND MUST NOT mark the publication as successfully published
- AND MUST surface the failure through the existing publication failure mechanism

#### Scenario: Publication request with duplicate asset identifiers

- GIVEN a publication request includes the same `assetId` value more than once in the `assetIds`
  list
- WHEN the system processes the request
- THEN the system MUST silently deduplicate the list before validation and link creation
- AND the publication MUST be associated with each unique asset only once

### Requirement: Composer Media Selection Uses Reusable Workspace Assets

The SPA composer MUST support selecting media from persisted workspace assets, including newly
uploaded assets and previously uploaded assets from the same workspace.

The MVP composer flow MUST support this persisted sequence: create asset, complete upload, retain
asset identifier, optionally browse existing workspace assets, and submit the publication with the
chosen `assetIds`. The composer MAY continue offering immediate previews for usability, but preview
state MUST NOT replace persisted asset selection as the canonical publishing input.

#### Scenario: User uploads media once and publishes with persisted asset id

- GIVEN a workspace member selects a supported media file in the composer
- WHEN the client completes the MVP persisted media flow
- THEN the composer MUST retain the created asset identifier
- AND the publication submission MUST reference that persisted asset identifier

#### Scenario: User reuses existing workspace asset in a new post

- GIVEN a workspace contains a previously uploaded media asset that is `READY` for use
- WHEN the user browses the media library from the composer and selects that asset
- THEN the composer MUST attach the existing persisted asset
- AND the subsequent publication submission MUST reuse that asset without requiring a new upload

### Requirement: Existing Publishing Consumers Continue Using Storage-Backed Assets

Existing publishing integrations that already consume storage-backed asset references MUST remain
compatible with media-library-backed `assetIds`.

This change SHALL preserve the existing publishing path in which provider adapters resolve uploaded
assets from platform-managed storage using persisted asset metadata. The media-library MVP MUST NOT
require a broader publishing-domain refactor outside the current asset reference flow, but it MUST
keep media ownership in the separate media-library bounded context. For the current product MVP,
LinkedIn is the required downstream consumer that MUST remain compatible. If other providers are
active in the codebase, they MUST either continue working with the same provider-neutral asset
reference contract or remain explicitly out of scope for this change.

#### Scenario: LinkedIn publishing consumes media-library-backed asset reference

- GIVEN a publication references a persisted uploaded asset through `assetIds`
- WHEN the existing LinkedIn publishing flow resolves publication assets for provider upload
- THEN it MUST consume the stored asset through the existing storage-backed asset path
- AND the media library MUST behave as the source of truth for that asset reference rather than an
  internal publishing-owned media store

#### Scenario: End-to-end persisted media flow reaches provider publishing through existing asset path

- GIVEN a workspace member creates a supported media asset, completes upload successfully, browses
  or reuses that `READY` asset, and submits a publication with its persisted `assetId`
- WHEN the publication proceeds through the existing provider publishing flow
- THEN the system MUST validate that the asset is `READY` and workspace-scoped before execution
- AND the downstream provider integration MUST resolve the stored binary through the existing
  storage-backed asset path

## Note on Legacy Publishing Asset Records

Existing `publication_assets` rows created before this change (legacy rows) may lack the
media-bounded-context ownership semantics introduced here. For the MVP transition:

1. Legacy rows that have status `READY` and a valid `storageKey` MUST continue to be accepted by the
   publishing validation flow.
2. Legacy rows that are missing required fields or have non-`READY` status MUST be treated as
   invalid by publication validation.
3. No backfill migration is required for MVP; legacy rows are treated as-is at the publishing
   boundary.
