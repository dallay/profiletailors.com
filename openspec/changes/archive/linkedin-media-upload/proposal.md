# Proposal: linkedin-media-upload

## Intent

Enable LinkedIn media upload support so users can attach binary assets (images, videos, documents) to LinkedIn posts. Currently `RealLinkedInPublisher` blocks all assets at publish time, preventing media-rich content. This change introduces a complete asset ingest → storage → registration → publish flow.

**Problem**: Users cannot attach media to LinkedIn posts. The publisher has a hard block (`require(command.assets.isEmpty())`) and no write path exists to create `PublicationAsset` records or register assets with LinkedIn's API.

## Scope

### In Scope
- `AssetUploader` port in `PublishingProviderPorts.kt` with `uploadAsset()` signature
- `RealLinkedInAssetUploader` implementing LinkedIn register → upload flow (URN returned)
- `FakeLinkedInAssetUploader` for testing without real credentials
- `PublicationAssetRepository.create()` write path (currently read-only)
- API endpoint for asset ingest (`CreateAssetCommand` → `PublishingApi.kt`)
- `providerAssetRef` field added to `PublicationAsset` to store LinkedIn URN
- Removal of asset block in `RealLinkedInPublisher`
- Presigned URL support via `@shared/storage` for external asset sources
- Binary upload to LinkedIn via their asset upload endpoint

### Out of Scope
- LinkedIn Pages support (separate capability)
- Other providers (LinkedIn-only MVP)
- Asset editing or deletion
- Asset metadata updates
- LinkedIn API credential management (reuse existing)

## Capabilities

### New Capabilities
- `linkedin-asset-upload`: Full flow from asset ingest to LinkedIn publication — register asset with LinkedIn API, upload binary data, return provider-specific URN, embed URN in post body

### Modified Capabilities
- `publication-asset`: Add `providerAssetRef` field and `create()` write path
- `linkedin-publishing`: Remove asset block, integrate `LinkedInAssetUploader`, embed URNs in `contentEntities`

## Approach

1. **Storage layer**: `@shared/storage` already provides `Storage.upload()` and `PresignableStorage.presignGet()`. No changes needed here — use presigned URLs as the asset source.

2. **Repository write path**: Add `create()` to `PublicationAssetRepository` following existing query patterns.

3. **Port interface**: Add `AssetUploader` to `PublishingProviderPorts.kt`:
   ```kotlin
   interface AssetUploader {
       suspend fun uploadAsset(asset: PublicationAsset, binarySource: Flow<ByteArray>): ProviderAssetRef
   }
   ```

4. **LinkedIn adapter** (`RealLinkedInAssetUploader`):
   - Step 1: `POST /assets` → register asset → get `digitalmediaAsset` URN
   - Step 2: `PUT /assets/{assetUrn}` with binary data
   - Step 3: Return `ProviderAssetRef(urn)` for embedding

5. **Publisher integration**: Modify `RealLinkedInPublisher`:
   - Remove `require(command.assets.isEmpty())`
   - For each asset, call `LinkedInAssetUploader.uploadAsset()`
   - Store returned URN in `providerAssetRef`
   - Add URNs to `contentEntities` in post body

6. **API endpoint**: `POST /assets` accepting `CreateAssetCommand` with `workspaceId`, `storageKey`, `mimeType`, `externalUrl`.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `PublishingProviderPorts.kt` | Modified | Add `AssetUploader` port interface |
| `PublicationAsset.kt` | Modified | Add `providerAssetRef` field |
| `PublicationAssetRepository.kt` | Modified | Add `create()` method |
| `PublishingApi.kt` | Modified | Add `CreateAssetCommand` endpoint |
| `RealLinkedInPublisher.kt` | Modified | Remove asset block, integrate uploader |
| `RealLinkedInAssetUploader.kt` | New | LinkedIn register → upload adapter |
| `FakeLinkedInAssetUploader.kt` | New | Test double for LinkedIn asset upload |
| `ProviderPublishCommand.kt` | Modified | Ensure `assets` list is passed through |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| LinkedIn API rate limits on asset endpoints | Medium | Add retry with backoff; consider batching |
| Large binary uploads timeout | Medium | Stream binary directly; increase timeout for video |
| Presigned URL expiry before upload completes | Low | Use short TTL; upload immediately after presign |
| Invalid mime type rejected by LinkedIn | Low | Validate mime types against LinkedIn supported list |

## Rollback Plan

1. Revert `RealLinkedInPublisher` to add back `require(command.assets.isEmpty())` block
2. Remove `providerAssetRef` migration (additive, nullable)
3. Remove `create()` from repository (no-op if not called)
4. Remove API endpoint
5. Remove `AssetUploader` port and implementations

## Dependencies

- `@shared/storage` — `Storage.upload()`, `PresignableStorage.presignGet()`
- Existing LinkedIn API credentials in environment
- `PublicationAsset` model already exists

## Success Criteria

- [x] `PublicationAssetRepository.create()` persists a new record with `providerAssetRef`
- [x] `RealLinkedInAssetUploader` completes register → upload flow and returns valid LinkedIn URN
- [x] `FakeLinkedInAssetUploader` can be used in tests without real credentials
- [x] `RealLinkedInPublisher.publish()` accepts non-empty assets list without throwing
- [x] LinkedIn URNs are embedded in `contentEntities` of the post body
- [x] Asset ingest API endpoint (`POST /assets`) creates a `PublicationAsset` record
- [x] Presigned URL flow works end-to-end for external asset sources
