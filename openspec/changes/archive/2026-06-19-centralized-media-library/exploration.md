# Exploration: centralized-media-library

## Current State

### Backend publishing and asset domain

- The backend already has a workspace-scoped `PublicationAsset` model in
  `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/domain/PublishingModels.kt`.
- `PublicationAsset` already supports:
    - `workspaceId`
    - `sourceType` (`UPLOADED`, `EXTERNAL_URL`)
    - `mediaType`
    - `storageKey`
    - `externalUrl`
    - `originalFilename`
    - `fileSizeBytes`
    - `status`
    - `providerAssetRef`
- `CreateAssetHandler` already creates asset records and reserves deterministic storage keys under
  `assets/{workspaceId}/{assetId}` for uploaded assets.
- `PublicationAssetRepository` already persists asset metadata and updates upload lifecycle
  state/provider refs.
- Liquibase changelogs already create `publication_assets` and `publication_asset_links`, plus
  `file_size_bytes` and `provider_asset_ref` columns.

### Storage foundation

- `shared/storage` is already a real reusable storage module, not a stub.
- Existing capabilities include:
    - provider abstraction via `Storage` / `PresignableStorage`
    - provider registry via `BucketRegistry`
    - local filesystem, S3, and dedicated Cloudflare R2 adapters
    - upload/download/delete/list/exists/presign operations
    - storage metrics and domain events
- `StorageAutoConfiguration` already supports named providers and a default provider.
- This means the platform already has the low-level primitives needed for a media library.

### Publishing integration

- LinkedIn publishing already consumes `PublicationAsset` records.
- `RealLinkedInPublisher` resolves uploaded assets from storage via `attachmentsBucket` and
  `storageKey` before provider upload.
- `RealLinkedInAssetUploader` and `FakeLinkedInAssetUploader` already establish an adapter pattern
  for provider-specific media handling.
- Current publishing validation is provider-oriented, not library-oriented.

### Workspace-scoped HTTP conventions

- Workspace-scoped APIs rely on `X-Workspace-Id` through `WorkspaceContextWebFilter` and
  `resourceContextProvider.requireWorkspaceContext()`.
- Publishing endpoints already follow this pattern.
- Frontend authenticated requests can add workspace scope through `workspaceScoped: true` in
  `apps/web/app/src/lib/auth-api.ts`.

### Frontend state of media handling

- The dashboard SPA (`apps/web/app`) currently treats media as local composer state, not as reusable
  library records.
- `CreatePostModal.vue` only accepts a local `File[]`, limits selection to one file for the current
  LinkedIn-focused UX, generates blob previews, and never uploads the binary to backend storage.
- `apps/web/app/src/stores/publishing.ts` still sends `assetIds: []` when creating publications,
  even when local media files exist.
- Current thumbnails and previews are object URLs only; they are not backed by persisted media
  records.

## What Already Exists

1. **Workspace-aware asset metadata model** in the publishing domain.
2. **Persistent asset table and publication-to-asset links** in the database.
3. **Storage abstraction and provider adapters** in `shared/storage`.
4. **Presigned URL capability** through `PresignableStorage`.
5. **Provider-specific media publishing flow** for LinkedIn.
6. **Frontend workspace-scoped request pattern** already used by the SPA.

## What Is Missing

1. **A centralized media library read/write API**
    - No controller currently exposes create/list/get/delete media-library endpoints.
    - `CreateAssetCommand` exists, but there is no HTTP asset controller for it.

2. **Binary upload flow from browser to storage**
    - The system can reserve a `storageKey`, but the SPA has no upload endpoint or presigned-upload
      flow.
    - Existing presign support is GET-oriented; there is no explicit upload contract for browser
      ingestion yet.

3. **Library read model and browsing UX**
    - No backend query returns workspace assets for browsing/filtering/selecting.
    - No SPA media library view, picker, or asset management store exists.

4. **Asset reuse in publication creation/editing**
    - Publications support `assetIds`, but the current SPA never sends persisted asset ids.
    - Composer UX is still single-post local attachment handling, not workspace-level reusable media
      selection.

5. **Operational media metadata beyond current publishing needs**
    - No asset title/alt text/tags/folder semantics.
    - No deduplication, retention, archival, or ownership policies.
    - No explicit delete/replace lifecycle for stored binaries.

6. **Generic publishing abstraction beyond LinkedIn constraints**
    - Current UX and validation are biased toward LinkedIn MVP limits.
    - A centralized library should remain workspace-scoped and provider-neutral, even if issue #55
      ships only a narrower first slice.

## MVP Boundary for Issue #55

Recommend a **narrow MVP**: deliver a reusable, workspace-scoped media library that supports storing
and selecting uploaded assets for publications, without turning this change into a full DAM system.

### In scope

- Workspace-scoped asset creation for uploaded media.
- Binary ingest path from SPA to storage.
- Persistent asset metadata record tied to workspace.
- List/read APIs so the SPA can browse existing workspace media.
- Composer integration so publication requests send real `assetIds`.
- Reuse of existing storage/provider/publishing abstractions.
- Keep support focused on the currently accepted publishing media types already used by the product.

### Out of scope

- Folder hierarchies, albums, tagging, search ranking, or smart organization.
- Rich asset editing (crop, transform, annotate, alt-text workflows).
- Cross-workspace sharing.
- Provider-specific optimization pipelines.
- Advanced moderation, virus scanning, deduplication, quotas, or lifecycle retention rules.
- Full multi-provider media capability normalization.
- Reworking the entire publishing domain out of `publishing` into a new bounded context.

## Recommended Shape for Proposal Phase

1. **Treat the first media library as a workspace asset catalog over existing `PublicationAsset`
   records** rather than inventing a second asset model.
2. **Add a dedicated HTTP/controller layer** for asset creation and listing under the existing
   workspace-scoped publishing/dashboard conventions.
3. **Add browser upload support** by extending storage-facing capabilities for ingest, likely via a
   backend upload endpoint or presigned-upload contract.
4. **Update the SPA composer and publishing store** so local file selection becomes:
    - create/reserve asset
    - upload binary
    - persist/select asset id
    - submit publication with `assetIds`
5. **Keep the first UX simple**:
    - upload media
    - browse workspace media
    - attach existing asset to a post
    - optional remove/delete later if already cheap to add

## Affected Areas

### Backend

- `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/application/PublishingApi.kt`
    - existing asset DTOs/commands; likely place for new asset queries/results
- `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/application/PublishingHandlers.kt`
    - existing `CreateAssetHandler`; likely add list/get/delete handlers
- `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/domain/PublishingModels.kt`
    - current asset model
- `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/domain/PublishingRepositories.kt`
    - repository/query expansion
-

`server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/persistence/R2dbcPublishingRepositories.kt`

- asset persistence and new reads

-

`server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/http/PublishingControllers.kt`

- current publishing controllers; likely place or reference pattern for a new asset controller

-

`server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/linkedin/LinkedInPublishingAdapters.kt`

- current downstream consumer of uploaded assets

-

`server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/linkedin/LinkedInAssetUploaderAdapters.kt`

- current attachments bucket configuration

- `server/smp/src/main/resources/db/changelog/publishing/003-create-publication-assets.yaml`
- `server/smp/src/main/resources/db/changelog/publishing/005-create-publication-asset-links.yaml`
- `server/smp/src/main/resources/db/changelog/publishing/009-add-provider-asset-ref.yaml`
-

`server/smp/src/main/resources/db/changelog/publishing/010-add-file-size-to-publication-assets.yaml`

### Shared storage

- `shared/storage/src/main/kotlin/com/profiletailors/storage/domain/Storage.kt`
- `shared/storage/src/main/kotlin/com/profiletailors/storage/domain/PresignableStorage.kt`
-

`shared/storage/src/main/kotlin/com/profiletailors/storage/application/StorageApplicationService.kt`
-
`shared/storage/src/main/kotlin/com/profiletailors/storage/infrastructure/StorageAutoConfiguration.kt`

- any new upload/presign contract if browser ingest is added

### Frontend

- `apps/web/app/src/components/CreatePostModal.vue`
    - current local-only file handling
- `apps/web/app/src/stores/publishing.ts`
    - currently sends `assetIds: []`
- `apps/web/app/src/lib/auth-api.ts`
    - workspace-scoped request helper already available
- likely new media-library UI under `apps/web/app/src/components/` or `src/views/`
- likely new Pinia store/composable for workspace media assets

## Archived Change Patterns to Reuse

- `openspec/changes/archive/2026-06-13-connect-spa-channels-to-linkedin/exploration.md`
    - good pattern for distinguishing backend reality, frontend gaps, workspace-scoped conventions,
      and proposal shape
- `openspec/changes/archive/2026-05-26-pluggable-storage-providers/exploration.md`
    - good reference for storage capability inventory
- `openspec/changes/archive/linkedin-media-upload/specs/publishing-media-upload.md`
    - important requirement precedent showing the repository already adopted `PublicationAsset`,
      asset uploader, and storage-backed upload semantics
- `openspec/changes/archive/2026-06-11-r2-storage-dedicated-adapter/spec.md`
    - useful storage-specific precedent when proposal/design need concrete R2/browser-upload
      decisions

## Risks / Decisions to Clarify in Proposal

1. **Upload architecture choice**
    - direct backend multipart ingest vs presigned browser upload
2. **Bounded-context choice**
    - extend current publishing asset model vs introduce a separate media domain too early
3. **Delete semantics**
    - deleting metadata only vs deleting underlying object storage immediately
4. **Provider-neutrality vs LinkedIn-first constraints**
    - issue #55 should not overfit the library model to one provider, even if the first consumer is
      LinkedIn
5. **Read-model scope**
    - simple newest-first list is enough for MVP; avoid over-designing search/folders now

## Ready for Proposal

Yes.

The codebase already contains the core asset and storage primitives needed for a centralized
workspace-scoped media library. The main gap is not storage capability; it is the missing product
flow between SPA file selection, backend asset APIs, binary ingest, and publication reuse of
persisted `assetIds`.
