# Delta: LinkedIn Media Upload

## Purpose

Enable LinkedIn media upload support so users can attach binary assets (images, videos) to LinkedIn posts. This delta adds the complete asset ingest → storage → registration → publish flow for `UPLOADED` and `EXTERNAL_URL` source types, and removes the hard asset block in `RealLinkedInPublisher`.

## ADDED Requirements

### Requirement: Asset Ingest API

The system MUST provide an API endpoint for workspace members to create `PublicationAsset` records.

A workspace member MUST be able to submit a `CreateAssetCommand` containing `workspaceId`, `storageKey` (for uploaded assets), `mediaType`, and optionally `externalUrl` (for external assets). The system MUST validate the media type against LinkedIn-supported types and enforce the 10MB per-asset size limit. Upon successful creation, the asset record MUST be persisted with status `READY` and a unique `assetId`.

#### Scenario: Workspace member uploads a media asset

- GIVEN an authenticated workspace member with a connected LinkedIn account
- WHEN the member submits a `CreateAssetCommand` with `storageKey` pointing to a valid uploaded file and `mediaType: IMAGE/JPEG`
- THEN the system MUST persist a `PublicationAsset` record with `sourceType: UPLOADED`, `mediaType: IMAGE/JPEG`, `storageKey`, and `status: READY`
- AND the asset MUST be assigned a unique `assetId`

#### Scenario: Workspace member registers an external URL asset

- GIVEN an authenticated workspace member with a connected LinkedIn account
- WHEN the member submits a `CreateAssetCommand` with `externalUrl` and `mediaType: IMAGE/PNG`
- THEN the system MUST persist a `PublicationAsset` record with `sourceType: EXTERNAL_URL`, `mediaType: IMAGE/PNG`, `externalUrl`, and `status: READY`

#### Scenario: Asset creation rejects unsupported media type

- GIVEN an authenticated workspace member submits a `CreateAssetCommand` with `mediaType: APPLICATION/PDF`
- WHEN the system validates the media type against LinkedIn-supported types
- THEN the system MUST reject the request with a validation error
- AND no `PublicationAsset` record MUST be created

#### Scenario: Asset creation rejects file exceeding size limit

- GIVEN an authenticated workspace member submits a `CreateAssetCommand` with a `storageKey` referencing a file larger than 10MB
- WHEN the system validates the file size
- THEN the system MUST reject the request with a validation error
- AND no `PublicationAsset` record MUST be created

### Requirement: AssetUploader Port

The system MUST provide an `AssetUploader` port interface that provider adapters implement to register and upload assets to their respective platforms.

The `AssetUploader` interface MUST declare: `suspend fun uploadAsset(asset: PublicationAsset, content: Flow<ByteArray>): ProviderAssetRef`. The port MUST be implemented by `RealLinkedInAssetUploader` for production and `FakeLinkedInAssetUploader` for testing.

#### Scenario: AssetUploader implementation returns provider asset reference

- GIVEN a `PublicationAsset` with `sourceType: UPLOADED` and a valid `storageKey`
- WHEN `AssetUploader.uploadAsset()` is called with the asset and its binary content flow
- THEN the uploader MUST return a `ProviderAssetRef` containing the provider-specific `providerAssetId` (LinkedIn URN), `mediaType`, and optionally `accessUrl`
- AND the LinkedIn URN format MUST be `urn:li:digitalmediaAsset:{asset-type}:{asset-id}`

### Requirement: RealLinkedInAssetUploader Two-Phase Upload

The `RealLinkedInAssetUploader` MUST implement the LinkedIn asset upload flow in two distinct phases.

**Phase 1 — Register**: The uploader MUST call `POST /assets` to register the asset and receive a `digitalmediaAsset` URN plus an upload URL. **Phase 2 — Stream**: The uploader MUST `PUT` the binary content directly to the upload URL obtained in Phase 1. The uploader MUST return a `ProviderAssetRef` with the URN as `providerAssetId`.

#### Scenario: LinkedIn asset uploader completes two-phase upload

- GIVEN a `PublicationAsset` with `mediaType: IMAGE/JPEG` and binary content available from storage
- WHEN `RealLinkedInAssetUploader.uploadAsset()` is invoked
- THEN the uploader MUST first call `POST /assets` to register the asset with LinkedIn
- AND receive a `digitalmediaAsset` URN and upload URL in the response
- AND then stream the binary content to the upload URL via `PUT /assets/{assetUrn}`
- AND return `ProviderAssetRef(providerAssetId: "urn:li:digitalmediaAsset:image:...", mediaType: IMAGE/JPEG)`

#### Scenario: LinkedIn asset uploader handles registration failure

- GIVEN a `PublicationAsset` with valid content
- WHEN `POST /assets` returns a failure response
- THEN the uploader MUST throw a `ProviderUploadException`
- AND the asset status MUST be transitioned to `FAILED`
- AND the publication MUST NOT be blocked (retryable error)

### Requirement: FakeLinkedInAssetUploader for Testing

The system MUST provide a `FakeLinkedInAssetUploader` test double that simulates the LinkedIn asset upload flow without requiring real credentials.

The fake MUST generate a deterministic fake URN in the correct format and MUST support configurable success/failure behavior for test scenarios.

#### Scenario: FakeLinkedInAssetUploader returns fake URN on success

- GIVEN a `FakeLinkedInAssetUploader` configured with `failOnNextCall = false`
- WHEN `uploadAsset()` is called with any valid asset
- THEN the fake MUST return a `ProviderAssetRef` with `providerAssetId` matching `urn:li:digitalmediaAsset:image:fake-asset-{uuid}`
- AND the operation MUST complete without throwing

#### Scenario: FakeLinkedInAssetUploader can be configured to fail

- GIVEN a `FakeLinkedInAssetUploader` configured with `failOnNextCall = true`
- WHEN `uploadAsset()` is called
- THEN the fake MUST throw `ProviderUploadException`
- AND the test can verify failure handling behavior

### Requirement: PublicationAsset Repository Write Path

The system MUST extend `PublicationAssetRepository` with a `create()` method that persists new asset records.

The `create()` method MUST accept the asset fields and persist a record with an assigned `assetId` and `status: READY`. The method MUST follow existing repository patterns and event publishing conventions.

#### Scenario: Repository creates new asset record

- GIVEN a valid `CreateAssetCommand` with all required fields
- WHEN `PublicationAssetRepository.create()` is called
- THEN a new `PublicationAsset` record MUST be persisted in the database
- AND a storage event MUST be published for audit trail
- AND the returned record MUST contain the assigned `assetId`

### Requirement: ProviderAssetRef Model

The system MUST define a `ProviderAssetRef` data class to hold provider-specific asset references returned after successful upload.

The `ProviderAssetRef` data class MUST contain: `providerAssetId` (required — the provider's URN or ID), `mediaType` (required — the resolved media type), and `accessUrl` (optional — URL for accessing the uploaded asset).

#### Scenario: ProviderAssetRef captures LinkedIn URN

- GIVEN a successful LinkedIn asset upload
- WHEN `RealLinkedInAssetUploader` completes the upload
- THEN the returned `ProviderAssetRef` MUST contain `providerAssetId` as the full LinkedIn URN
- AND `mediaType` matching the resolved type
- AND `accessUrl` as `null` (LinkedIn URN is self-addressing)

## MODIFIED Requirements

### Requirement: RealLinkedInPublisher Integrates Asset Uploader

This requirement REPLACES the existing hard block on assets in `RealLinkedInPublisher`.

The `RealLinkedInPublisher` MUST process non-empty asset lists by calling the `AssetUploader` for each `UPLOADED` asset and transforming each result into a LinkedIn `contentEntities` entry. For `EXTERNAL_URL` assets, the publisher MUST use the presigned URL or direct URL in the `source` field. The publisher MUST embed all asset URNs or URLs in the post body using LinkedIn's `contentEntities` format.

#### Scenario: Publisher publishes post with uploaded image asset

- GIVEN a publication command with one `PublicationAsset` where `sourceType: UPLOADED`
- WHEN `RealLinkedInPublisher.publish()` is called
- THEN the publisher MUST call `AssetUploader.uploadAsset()` to register the asset with LinkedIn
- AND receive a `ProviderAssetRef` containing the LinkedIn URN
- AND embed the URN in `contentEntities` of the LinkedIn post body
- AND update the asset record with the `providerAssetRef`

#### Scenario: Publisher publishes post with external URL asset

- GIVEN a publication command with one `PublicationAsset` where `sourceType: EXTERNAL_URL` and a valid `externalUrl`
- WHEN `RealLinkedInPublisher.publish()` is called
- THEN the publisher MUST use the `externalUrl` directly in the LinkedIn `contentEntities` `source` field
- AND no `AssetUploader` call is required for this asset type

#### Scenario: Publisher publishes post with multiple assets

- GIVEN a publication command with up to 10 `PublicationAsset` records
- WHEN `RealLinkedInPublisher.publish()` is called
- THEN the publisher MUST process each asset in order
- AND call `AssetUploader` for each `UPLOADED` asset
- AND embed all resulting URNs in the LinkedIn `contentEntities` array
- AND the post body MUST contain all registered assets

#### Scenario: Publisher handles asset upload failure gracefully

- GIVEN a publication command with assets
- WHEN `AssetUploader.uploadAsset()` throws a `ProviderUploadException` for one asset
- THEN the system MUST mark that specific asset's status as `FAILED`
- AND the publication MUST NOT be blocked from publishing
- AND the remaining assets MUST continue processing
- AND the failed asset MUST be eligible for retry in a subsequent attempt

### Requirement: LinkedInCapabilityValidator Media Type and Size Validation

This requirement EXTENDS the existing `LinkedInCapabilityValidator` to add media type and file size validation.

The validator MUST reject publications where any asset has a `mediaType` not in the LinkedIn-supported set: `IMAGE/JPEG`, `IMAGE/PNG`, `IMAGE/GIF`, `IMAGE/WEBP`, `VIDEO/MP4`. The validator MUST also reject publications where any asset's file size exceeds 10MB.

#### Scenario: Validator rejects unsupported media type

- GIVEN a publication targets a LinkedIn account with an asset having `mediaType: APPLICATION/PDF`
- WHEN the publication is validated for queueing
- THEN the system MUST reject the publication as invalid
- AND the rejection MUST indicate the unsupported media type

#### Scenario: Validator rejects asset exceeding size limit

- GIVEN a publication targets a LinkedIn account with an asset larger than 10MB
- WHEN the publication is validated for queueing
- THEN the system MUST reject the publication as invalid
- AND the rejection MUST indicate the size limit exceeded

### Requirement: PublicationAsset Status Lifecycle

This requirement ADDS status transition semantics to `PublicationAsset`.

An asset MUST transition from `READY` to `PROCESSING` when `AssetUploader` begins the upload. Upon successful upload completion, the asset MUST retain or return to `READY` status with the `providerAssetRef` populated. Upon upload failure, the asset MUST transition to `FAILED` status.

#### Scenario: Asset transitions to PROCESSING during upload

- GIVEN a `PublicationAsset` with `status: READY`
- WHEN `AssetUploader.uploadAsset()` is invoked
- THEN the asset status MUST be transitioned to `PROCESSING`
- AND the status update MUST be persisted before the upload begins

#### Scenario: Asset returns to READY after successful upload

- GIVEN a `PublicationAsset` with `status: PROCESSING`
- WHEN `AssetUploader.uploadAsset()` completes successfully
- THEN the asset status MUST be transitioned to `READY`
- AND the `providerAssetRef` field MUST be populated with the returned reference

#### Scenario: Asset transitions to FAILED after upload failure

- GIVEN a `PublicationAsset` with `status: PROCESSING`
- WHEN `AssetUploader.uploadAsset()` throws a `ProviderUploadException`
- THEN the asset status MUST be transitioned to `FAILED`
- AND the `providerAssetRef` field MUST remain `null`
- AND the failure MUST be recorded for retry eligibility

## REMOVED Requirements

### Requirement: Hard Block on Assets in RealLinkedInPublisher

The existing requirement that blocked all asset usage in `RealLinkedInPublisher` is REMOVED.

Previously, `RealLinkedInPublisher` contained `require(command.assets.isEmpty())` which rejected any publication with assets. This hard block is now removed and replaced by the asset processing logic defined above.

(Reason: The block prevented any media-rich LinkedIn content. The new asset uploader and validation flow replaces this blanket rejection with proper capability-based validation and provider-specific upload handling.)
