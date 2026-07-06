# Design: linkedin-media-upload

## Technical Approach

Enable LinkedIn media upload by introducing an `AssetUploader` port, a write path for
`PublicationAsset` records, and a `RealLinkedInAssetUploader` that executes LinkedIn's 3-step asset
registration flow. The change is additive — no existing behavior is broken, only the asset block in
`RealLinkedInPublisher` is removed once the uploader is wired.

## Architecture Decisions

### Decision: `AssetUploader` as a dedicated port (vs. extending `SocialPublisher`)

**Choice**: New `AssetUploader` interface in `PublishingProviderPorts.kt`, separate from
`SocialPublisher`.

**Alternatives considered**:

- Extend `SocialPublisher.publish()` to accept assets inline — violates Single Responsibility;
  publisher would need to know about storage, binary streaming, and LinkedIn-specific registration.
- Add `uploadAsset()` directly to `RealLinkedInPublisher` — creates a God class and makes testing
  harder.

**Rationale**: Asset upload is a distinct capability (register → stream binary → confirm) with
different failure semantics (retryable vs. fatal). A dedicated port enables
`FakeLinkedInAssetUploader` for testing and keeps `RealLinkedInPublisher` focused on post creation.

### Decision: Binary source via `Flow<ByteArray>` (vs. `ByteArray` or `InputStream`)

**Choice**: `Flow<ByteArray>` as the binary content type in `AssetUploader.uploadAsset()`.

**Alternatives considered**:

- `ByteArray` — loads entire asset into memory; unsuitable for video files.
- `InputStream` — blocking, not coroutine-friendly; awkward to adapt from `Flow`.

**Rationale**: `Storage.download()` already returns `Flow<ByteArray>`. This avoids eager loading and
allows streaming directly to LinkedIn's upload endpoint with minimal copying.

### Decision: Store `ProviderAssetRef` as nullable JSON in `PublicationAsset`

**Choice**: Add `providerAssetRef: ProviderAssetRef?` field to `PublicationAsset` data class;
serialize as nullable column.

**Alternatives considered**:

- Separate `provider_asset_refs` table — adds join complexity for a 1:1 relationship; overkill for
  MVP.
- Embed URN string directly in `storageKey` — conflation of concerns; storage key should not encode
  provider semantics.

**Rationale**: Additive nullable column is low-risk for migration. `ProviderAssetRef` is a simple
data class (URN + mediaType + optional accessUrl) that maps cleanly to a `TEXT` column via Jackson
serialization.

### Decision: Inject `Storage` into `RealLinkedInAssetUploader` (vs. passing binary directly)

**Choice**: `RealLinkedInAssetUploader` receives `Storage` and calls `storage.download(bucket, key)`
internally.

**Alternatives considered**:

- Accept `Flow<ByteArray>` in the command — would require the caller to know the bucket/key and
  resolve the flow before calling upload; creates a higher-waterfall dependency.
- Accept presigned URL and fetch inside uploader — adds HTTP round-trip; presigned URLs are for
  external clients, not internal upload.

**Rationale**: `PublicationAsset` already carries `storageKey`. The uploader can resolve the bucket
from config and call `storage.download()` directly, keeping the API surface minimal and the flow
implicit.

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                      Application Layer                          │
│  PublishingApi ──► CreateAssetHandler ──► PublicationAssetRepo   │
└─────────────────────────────────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│                       Domain Layer │
│  PublicationAsset  │  AssetUploader (port)  │  ProviderAssetRef│
└─────────────────────────────────────────────────────────────────┘
                               │
 ┌────────────────────┴────────────────────┐
          ▼ ▼
┌──────────────────────┐              ┌──────────────────────────┐
│ RealLinkedInAssetUplr │              │   FakeLinkedInAssetUplr  │
│ - registerAsset()    │              │   (test double)          │
│  - uploadBinary()     │              └──────────────────────────┘
│  - confirmAsset()     │
└──────────────────────┘
          │
          ▼
┌──────────────────────┐              ┌──────────────────────────┐
│   @shared/storage    │              │ LinkedIn REST API         │
│   Storage.download() │              │ /assets → binary → confirm│
└──────────────────────┘              └──────────────────────────┘
```

## Data Flow

### Asset Ingest Flow (create asset record)

```
Client ──POST /api/publishing/assets──►  PublishingApi
 │
                                              ▼
                                    CreateAssetHandler
 │
                              ┌───────────────┴───────────────┐
                              ▼                               ▼
              PublicationAssetRepository.create()    Storage.upload() (if UPLOADED)
 │
                              ▼
                      PublicationAsset (with providerAssetRef = null)
```

### LinkedIn Upload Flow (asset → LinkedIn URN)

```
PublishingJobExecutor
    │
    ▼
RealLinkedInPublisher.publish(command)
    │ command.assets: List<PublicationAsset>
    │
    ▼
RealLinkedInAssetUploader.uploadAsset(asset, storage, context)
    │
    ├─► LinkedInCredentialGateway.resolveCredential()
    │       │
    │       ▼
    │   accessToken
    │
    ├─► POST /assets  (register asset, owner + serviceRelationship)
    │       │
    │       ▼
    │   { asset: "urn:li:digitalmediaAsset:...", uploadUrl: "https://..." }
    │
    ├─► Storage.download(bucket, asset.storageKey)  → Flow<ByteArray>
    │       │
    │       ▼
    │   PUT {uploadUrl}  (stream binary, Content-Type: application/octet-stream)
    │
    └─► POST /assets/{assetURN}/?action=checkStatus  (confirm)
 │
            ▼
        ProviderAssetRef(providerAssetId = "urn:li:digitalmediaAsset:...")
```

### Publish with Assets (post creation with embedded media)

```
PublishingJobExecutor
    │
    ▼
RealLinkedInPublisher.publish(command)
    │
    ▼
For each asset:
 RealLinkedInAssetUploader.uploadAsset() → ProviderAssetRef
    │
    ▼
buildPostBody() embeds URNs in contentEntities:
{
  "author": "urn:li:person:...",
  "commentary": "Check out this image!",
  "content": {
    "entities": [
      { "entity": "urn:li:digitalmediaAsset:12345678" }
    ]
  }
}
    │
    ▼
POST /rest/posts  → LinkedIn post with attached media
```

## File Changes

| File                                                                                       | Action | Lines | Description                                                                                                                                                                       |
|--------------------------------------------------------------------------------------------|--------|-------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `server/smp/src/main/kotlin/.../domain/PublishingModels.kt`                                | Modify | +12   | Add `ProviderAssetRef` data class; add `providerAssetRef: ProviderAssetRef?` to `PublicationAsset`                                                                                |
| `server/smp/src/main/kotlin/.../domain/PublishingProviderPorts.kt`                         | Modify | +18   | Add `AssetUploader` interface, `AssetUploadContext`, `ProviderAssetRef` (or import from Models)                                                                                   |
| `server/smp/src/main/kotlin/.../domain/PublishingRepositories.kt`                          | Modify | +4    | Add `create(asset: PublicationAsset)` to `PublicationAssetRepository`                                                                                                             |
| `server/smp/src/main/kotlin/.../infrastructure/persistence/R2dbcPublishingRepositories.kt` | Modify | +30   | Implement `R2dbcPublicationAssetRepository.create()` INSERT                                                                                                                       |
| `server/smp/src/main/kotlin/.../infrastructure/linkedin/LinkedInPublishingAdapters.kt`     | Modify | +120  | Add `RealLinkedInAssetUploader`, `FakeLinkedInAssetUploader`; update `LinkedInPublishingConfiguration` beans; update `RealLinkedInPublisher` to remove asset block and embed URNs |
| `server/smp/src/main/kotlin/.../application/PublishingApi.kt`                              | Modify | +20   | Add `CreateAssetCommand`, `CreateAssetResult`                                                                                                                                     |
| `server/smp/src/main/kotlin/.../application/PublishingHandlers.kt`                         | Modify | +50   | Add `CreateAssetHandler`                                                                                                                                                          |
| `server/smp/src/main/resources/application.yaml`                                           | Modify | +4    | Add `platform.storage.providers.attachments.bucket` config                                                                                                                        |
| `server/smp/src/main/resources/db/changelog/publishing/003-create-publication-assets.yaml` | Modify | +8    | Add `provider_asset_ref` TEXT column (nullable)                                                                                                                                   |
| `server/smp/src/test/kotlin/.../infrastructure/linkedin/LinkedInPublishingAdaptersTest.kt` | Modify | +80   | Add tests for `RealLinkedInAssetUploader` (register, upload, confirm); add tests for `FakeLinkedInAssetUploader`                                                                  |
| `server/smp/src/test/kotlin/.../application/PublishingHandlersTest.kt`                     | Modify | +40   | Add test for `CreateAssetHandler`                                                                                                                                                 |

**Total estimated lines**: ~370 new lines across ~10 files.

## Interfaces / Contracts

### New port: `AssetUploader`

```kotlin
package com.profiletailors.smp.publishing.domain

interface AssetUploader {
    suspend fun uploadAsset(
        asset: PublicationAsset,
        storage: com.profiletailors.storage.domain.Storage,
        context: AssetUploadContext,
    ): ProviderAssetRef
}

data class AssetUploadContext(
    val socialAccount: SocialAccount,
    val accessToken: String,
    val apiBaseUrl: String,
    val apiVersion: String,
)

data class ProviderAssetRef(
    val providerAssetId: String, // LinkedIn URN: "urn:li:digitalmediaAsset:..."
    val mediaType: String,
    val accessUrl: String? = null,
)
```

### Extended: `PublicationAssetRepository`

```kotlin
interface PublicationAssetRepository {
    suspend fun findByWorkspaceAndIds(workspaceId: String, assetIds: Collection<String>): List<PublicationAsset>
    suspend fun create(asset: PublicationAsset): PublicationAsset  // NEW
}
```

### New command: `CreateAssetCommand`

```kotlin
data class CreateAssetCommand(
    val storageKey: String?, // required if sourceType = UPLOADED
    val externalUrl: String?,       // required if sourceType = EXTERNAL_URL
    val routeType: AssetSourceType,
    val mediaType: String,
    val originalFilename: String?,
) : CommandWithResult<CreateAssetResult>

data class CreateAssetResult(
    val assetId: String,
    val workspaceId: String,
    val routeType: AssetSourceType,
    val mediaType: String,
    val status: PublicationAssetStatus,
)
```

### API endpoint

```
POST /api/publishing/assets
Content-Type: multipart/form-data
Body:
  - file: binary (optional; present if sourceType = UPLOADED)
  - mediaType: string (e.g. "image/jpeg", "video/mp4")
  - originalFilename: string (optional)
  - sourceType: "UPLOADED" | "EXTERNAL_URL"
  - externalUrl: string (required if sourceType = EXTERNAL_URL)

Response: CreateAssetResult (201 Created)
```

## Testing Strategy

| Layer       | What to Test                                                            | Approach                                                                                       |
|-------------|-------------------------------------------------------------------------|------------------------------------------------------------------------------------------------|
| Unit        | `RealLinkedInAssetUploader` —3-step flow with mocked HTTP               | Mock `LinkedInHttpTransport`; verify register → upload → confirm sequence and URN extraction   |
| Unit        | `FakeLinkedInAssetUploader` — returns stable fake URN                   | Assert URN format `urn:li:digitalmediaAsset:fake-...`                                          |
| Unit        | `CreateAssetHandler` — persists record, returns result                  | Mock `PublicationAssetRepository`; verify `create()` call and result mapping                   |
| Unit        | `RealLinkedInPublisher` — assets embedded in post body                  | Mock `AssetUploader`; verify `contentEntities` structure                                       |
| Integration | `R2dbcPublicationAssetRepository.create()` — round-trip INSERT + SELECT | Integration test with embedded R2dbcDatabase; verify all fields including `provider_asset_ref` |
| Integration | LinkedIn asset upload E2E (real API, fake mode)                         | Use `fake` LinkedIn mode with a stub server; verify upload URL is called with binary           |
| Contract    | `Storage` download integration                                          | Leverage existing `StorageContractTest`; `download()` is unchanged                             |

### LinkedIn Media Type Validation

`LinkedInCapabilityValidator` already validates asset count and media type presence. No new
validator needed — LinkedIn's supported types are wide (JPEG, PNG, GIF, MP4, PDF) and their API
returns informative errors for unsupported types.

## Configuration Additions

### application.yaml

```yaml
platform:
  storage:
    providers:
      attachments:
        bucket: ${SMP_STORAGE_ATTACHMENTS_BUCKET:profiletailors-attachments}
```

The bucket name is injected into `RealLinkedInAssetUploader` via a new
`LinkedInAssetUploadProperties` config object:

```kotlin
data class LinkedInAssetUploadProperties(
    val attachmentsBucket: String,
)
```

## Migration / Rollback

### Database Migration

A new Liquibase changeset adds a nullable `provider_asset_ref TEXT` column to `publication_assets`.
No data migration needed — existing records have `null` for this field.

```yaml
- changeSet:
    id: publishing-006-add-provider-asset-ref
    author: opencode
    changes:
      - addColumn:
          tableName: publication_assets
          columns:
            - column:
                name: provider_asset_ref
                type: TEXT
                constraints:
                  nullable: true
```

### Rollback Plan

1. **Revert `RealLinkedInPublisher`**: Restore `require(command.assets.isEmpty())` block.
2. **Remove `provider_asset_ref` column**: Additive migration — safe to revert.
3. **Remove `create()` from repository**: No-op if not called; handler removed.
4. **Remove API endpoint**: Remove `CreateAssetHandler` and route.
5. **Remove `AssetUploader` port and implementations**: Delete `RealLinkedInAssetUploader`,
   `FakeLinkedInAssetUploader`.

All reversions are additive-safe since the new code paths are only exercised when assets are
present.

## Open Questions

- [ ] **Chunked upload for large videos**: LinkedIn requires multipart for files > 10 MB. Should
  `RealLinkedInAssetUploader` handle chunking internally, or reject large files at validation
  time? (MVP: reject > 10 MB at `LinkedInCapabilityValidator`.)
- [ ] **Asset cleanup on publish failure**: If `publish()` succeeds but the post creation fails, the
  uploaded asset is orphaned on LinkedIn. Should we call LinkedIn's delete endpoint in a
  compensation handler? (MVP: ignore; assets are low-volume.)
- [ ] **Expiration of LinkedIn URNs**: LinkedIn asset URNs are permanent. Is there a need for a
  cleanup job to delete stale (never-used) assets? (MVP: no.)
- [ ] **Presigned URL flow for external sources**: `PublicationAsset` with
  `sourceType = EXTERNAL_URL` stores an `externalUrl`. Should `RealLinkedInAssetUploader` fetch from
  that URL and stream to LinkedIn, or is presigned-URL flow the intended path? (Clarification needed
  from product before implementing.)
