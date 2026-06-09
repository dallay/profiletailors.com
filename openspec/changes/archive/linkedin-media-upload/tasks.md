# Tasks: linkedin-media-upload

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~370 |
| 400-line budget risk | Medium |
| Chained PRs recommended | No |
| Suggested split | Single PR |
| Delivery strategy | ask-on-risk |
| Chain strategy | pending |

Decision needed before apply: No
Chained PRs recommended: No
Chain strategy: pending
400-line budget risk: Medium

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|------|------|-----------|-------|
| 1 | Full implementation | PR 1 | All phases in one PR |

## Phase 1: Model & Port Extensions

- [x] **Task 1**: Add `ProviderAssetRef` data class to `PublishingModels.kt`
  - **File**: `server/smp/src/main/kotlin/.../domain/PublishingModels.kt`
  - **What**: Add `ProviderAssetRef` data class with `providerAssetId`, `mediaType`, `accessUrl` fields
  - **Test first**: No
  - **Depends on**: —

- [x] **Task 2**: Add `providerAssetRef` field to `PublicationAsset`
  - **File**: `server/smp/src/main/kotlin/.../domain/PublishingModels.kt`
  - **What**: Add `providerAssetRef: ProviderAssetRef?` nullable field to `PublicationAsset` data class
  - **Test first**: No
  - **Depends on**: 1

- [x] **Task 3**: Add `AssetUploader` port to `PublishingProviderPorts.kt`
  - **File**: `server/smp/src/main/kotlin/.../domain/PublishingProviderPorts.kt`
  - **What**: Add `AssetUploader` interface with `uploadAsset()` method, `AssetUploadContext` data class, and `ProviderUploadException`
  - **Test first**: No
  - **Depends on**: 1

## Phase 2: Repository Write Path

- [x] **Task 4**: Add `create()` method to `PublicationAssetRepository`
  - **File**: `server/smp/src/main/kotlin/.../domain/PublishingRepositories.kt`
  - **What**: Add `create(asset: PublicationAsset): PublicationAsset` to the repository interface
  - **Test first**: No
  - **Depends on**: 2

- [x] **Task 5**: Implement `R2dbcPublicationAssetRepository.create()` INSERT mapping
  - **File**: `server/smp/src/main/kotlin/.../infrastructure/persistence/R2dbcPublishingRepositories.kt`
  - **What**: Add INSERT SQL mapping for `PublicationAsset` including `provider_asset_ref` JSON column
  - **Test first**: Yes
  - **Depends on**: 4

## Phase 3: LinkedIn Asset Uploader

- [x] **Task 6**: Implement `RealLinkedInAssetUploader` — 3-step flow
  - **File**: `server/smp/src/main/kotlin/.../infrastructure/linkedin/LinkedInAssetUploaderAdapters.kt`
  - **What**: Implement `registerAsset()` → `uploadBinary()` → `confirmAsset()`; inject `Storage` for download; use `LinkedInHttpTransport`
  - **Test first**: Yes
  - **Depends on**: 3, 5

- [x] **Task 7**: Implement `FakeLinkedInAssetUploader` for testing
  - **File**: `server/smp/src/main/kotlin/.../infrastructure/linkedin/LinkedInAssetUploaderAdapters.kt`
  - **What**: Implement test double returning deterministic fake URN; support `failOnNextCall` config for failure scenarios
  - **Test first**: Yes
  - **Depends on**: 6

- [x] **Task 8**: Register `RealLinkedInAssetUploader` and `FakeLinkedInAssetUploader` in `LinkedInPublishingConfiguration`
  - **File**: `server/smp/src/main/kotlin/.../infrastructure/linkedin/LinkedInPublishingAdapters.kt`
  - **What**: Add `@Bean` definitions for both uploaders; inject `LinkedInAssetUploadProperties` config
  - **Test first**: No
  - **Depends on**: 6, 7

## Phase 4: Storage Integration in Publisher

- [x] **Task 9**: Inject `Storage` into `RealLinkedInAssetUploader`
  - **File**: `server/smp/src/main/kotlin/.../infrastructure/linkedin/LinkedInAssetUploaderAdapters.kt`
  - **What**: Add `Storage` as constructor dependency; use `storage.download()` to stream binary for upload
  - **Test first**: No
  - **Depends on**: 6

- [x] **Task 10**: Wire storage download → LinkedIn upload streaming
  - **File**: `server/smp/src/main/kotlin/.../infrastructure/linkedin/LinkedInAssetUploaderAdapters.kt`
  - **What**: In `uploadBinary()`, call `storage.download(bucket, asset.storageKey)` and pipe `Flow<ByteArray>` to LinkedIn PUT endpoint
  - **Test first**: Yes
  - **Depends on**: 6, 9

## Phase 5: Publisher Integration

- [x] **Task 11**: Remove `require(command.assets.isEmpty())` from `RealLinkedInPublisher`
  - **File**: `server/smp/src/main/kotlin/.../infrastructure/linkedin/LinkedInPublishingAdapters.kt`
  - **What**: Remove the hard block on assets in `publish()` method
  - **Test first**: No
  - **Depends on**: 8

- [x] **Task 12**: For each asset in `ProviderPublishCommand`, call `AssetUploader`
  - **File**: `server/smp/src/main/kotlin/.../infrastructure/linkedin/LinkedInPublishingAdapters.kt`
  - **What**: Iterate `command.assets`; for `UPLOADED` type call `assetUploader.uploadAsset()`; for `EXTERNAL_URL` use direct URL
  - **Test first**: Yes
  - **Depends on**: 11

- [x] **Task 13**: Transform URNs to LinkedIn `contentEntities` in `buildPostBody()`
  - **File**: `server/smp/src/main/kotlin/.../infrastructure/linkedin/LinkedInPublishingAdapters.kt`
  - **What**: In `buildPostBody()`, add URNs from `ProviderAssetRef` into LinkedIn `contentEntities` array format
  - **Test first**: Yes
  - **Depends on**: 12

- [x] **Task 14**: Update `LinkedInCapabilityValidator` to validate media types and file sizes
  - **File**: `server/smp/src/main/kotlin/.../infrastructure/linkedin/LinkedInPublishingAdapters.kt`
  - **What**: Add validation for supported types (JPEG, PNG, GIF, WEBP, MP4) and 10MB size limit; reject with clear error message
  - **Test first**: Yes
  - **Depends on**: 11

## Phase 6: API Surface

- [x] **Task 15**: Add `CreateAssetCommand` and `CreateAssetHandler` to `PublishingApi.kt`
  - **File**: `server/smp/src/main/kotlin/.../application/PublishingApi.kt`
  - **What**: Add `CreateAssetCommand` data class and `CreateAssetHandler` class with `handle()` method
  - **Test first**: No
  - **Depends on**: 4

- [x] **Task 16**: Add `POST /api/publishing/assets` endpoint (multipart form)
  - **File**: `server/smp/src/main/kotlin/.../application/PublishingApi.kt`
  - **What**: Add route `POST /api/publishing/assets` accepting `multipart/form-data`; parse `mediaType`, `sourceType`, `externalUrl`, `file`; wire to `CreateAssetHandler`
  - **Test first**: Yes
  - **Depends on**: 15

- [x] **Task 17**: Wire `StorageApplicationService` in handler for actual file storage
  - **File**: `server/smp/src/main/kotlin/.../application/PublishingHandlers.kt`
  - **What**: In `CreateAssetHandler`, inject `StorageApplicationService`; for `UPLOADED` type call `storage.upload()` before persisting record
  - **Test first**: Yes
  - **Depends on**: 15

## Phase 7: Tests

- [x] **Task 18**: Add unit tests for `LinkedInCapabilityValidator` media type validation
  - **File**: `server/smp/src/test/kotlin/.../infrastructure/linkedin/LinkedInPublishingAdaptersTest.kt`
  - **What**: Test supported types accepted, unsupported types rejected, file size limit enforced
  - **Test first**: Yes
  - **Depends on**: 14

- [x] **Task 19**: Add unit tests for `RealLinkedInAssetUploader` register → upload → confirm flow
  - **File**: `server/smp/src/test/kotlin/.../infrastructure/linkedin/LinkedInPublishingAdaptersTest.kt`
  - **What**: Mock `LinkedInHttpTransport`; verify 3-step sequence called in order; URN extracted correctly from register response
  - **Test first**: Yes
  - **Depends on**: 6

- [x] **Task 20**: Add integration test for asset ingest flow (with fake storage)
  - **File**: `server/smp/src/test/kotlin/.../application/PublishingHandlersTest.kt`
  - **What**: Test `CreateAssetHandler` end-to-end: multipart upload → storage put → repository create → result returned
  - **Test first**: Yes
  - **Depends on**: 16, 17

- [x] **Task 21**: Update `PublishingHandlersTest` for LinkedIn connection with assets
  - **File**: `server/smp/src/test/kotlin/.../application/PublishingHandlersTest.kt`
  - **What**: Add test case for LinkedIn publish with `UPLOADED` asset; verify `AssetUploader` called, URN embedded in post body
  - **Test first**: Yes
  - **Depends on**: 12, 13

## Implementation Order Summary

| Phase | Tasks | Focus |
|-------|-------|-------|
| Phase 1 | 1–3 | Model & Port Extensions |
| Phase 2 | 4–5 | Repository Write Path |
| Phase 3 | 6–8 | LinkedIn Asset Uploader |
| Phase 4 | 9–10 | Storage Integration |
| Phase 5 | 11–14 | Publisher Integration |
| Phase 6 | 15–17 | API Surface |
| Phase 7 | 18–21 | Tests |
| **Total** | **21** | |

## Notes

- TDD approach: write failing test first for Tasks 5, 6, 7, 10, 12, 13, 14, 18, 19, 20, 21
- Use `FakeLinkedInAssetUploader` in integration tests to avoid real LinkedIn API calls
- Follow `Method_scenario_expectedOutcome` naming for all tests
- Nullable types, `orElse`, `takeIf` — no `!!` operator
- Package structure: `com.profiletailors.smp.publishing.domain`, `.application`, `.infrastructure.linkedin`