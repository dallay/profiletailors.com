# Proposal: Centralized Media Library

## Intent

The product already has durable storage primitives and LinkedIn publishing that can consume stored
assets, but the SPA still treats media as ephemeral local state and the platform has no explicit
media domain. This change introduces a narrow MVP media library as a separate workspace-scoped media
bounded context so users can upload media once, browse previously uploaded assets, and attach
persisted media references to publications while establishing a reusable foundation for other apps.

## Problem

- **No media-library API**: The backend can create asset records internally, but no workspace-scoped
  HTTP endpoints expose create, list, read, or delete operations for media assets.
- **No browser ingest path**: The platform can reserve deterministic storage keys, but the SPA has
  no supported binary upload flow into backend-managed storage.
- **No reusable read model**: Users cannot browse workspace media because there is no list/read
  query over existing `PublicationAsset` records.
- **Composer remains local-only**: `CreatePostModal.vue` keeps media in local `File[]` state and
  `publishing.ts` still submits `assetIds: []`.
- **Current UX is not library-oriented**: Previews exist only as blob URLs, so uploaded media cannot
  be reused across publication attempts or later posts.

## Scope

### In Scope

- A separate workspace-scoped media bounded context with its own API and application flow
- Asset creation/reservation flow for uploaded media within that media context
- Browser-to-storage ingest flow for uploaded binaries
- Persistent media metadata and identifiers for uploaded workspace media
- Workspace-scoped list and read APIs for browsing existing media assets
- Integration from publishing so publications submit real persisted media references from the media
  context
- Reuse of existing `shared/storage` abstractions and selective reuse of existing publishing asset
  primitives where practical
- Media support limited to the currently accepted publishing media types already supported by the
  product MVP
- Context boundaries and contracts designed so other apps can later consume the media library
  without re-homing it
    - Delete semantics are deferred to a post-MVP phase and are not part of this change

### Out of Scope

- Delete semantics for media assets (deferred to a post-MVP phase)
- Keeping the MVP embedded as only an internal publishing concern
- Folder hierarchies, albums, tagging, advanced search, or smart organization
- Rich asset editing such as crop, transform, annotation, or alt-text workflows
- Cross-workspace sharing or public asset libraries
- Deduplication, virus scanning, moderation, quotas, retention, archival, or advanced lifecycle
  policies
- Provider-specific optimization pipelines or generalized multi-provider normalization beyond
  current needs
- Broad cross-domain refactors beyond the minimum seams needed to establish the media bounded
  context
- Immediate adoption by every other app or workflow beyond publishing MVP needs

## Capabilities

### New Capabilities

- `workspace-media-library-api`: Workspace-scoped API owned by the media bounded context for
  creating uploaded media records and listing or reading existing workspace assets
- `workspace-media-upload`: Browser ingest path that uploads selected binaries into backend-managed
  storage for reserved media records
- `workspace-media-picker`: SPA workflow for browsing existing workspace media and attaching
  persisted media to a publication

### Modified Capabilities

- `publishing`: Publication creation uses persisted media references from the media library instead
  of local-only attachment state
- `shared-storage`: Existing storage abstractions may gain the minimum contract needed to support
  browser upload for this flow
- `publication-asset`: Existing publishing asset primitives may be reused or adapted at an
  integration seam, but they are no longer the architectural home of the media library

## Approach

1. **Establish media as its own bounded context**: Introduce the first centralized media library as
   a separate workspace-scoped domain rather than embedding it inside publishing, while still
   keeping the MVP narrow.
2. **Add a dedicated HTTP layer**: Expose workspace-scoped media create/list/read endpoints
   following current workspace-context conventions (`X-Workspace-Id`).
3. **Add a narrow upload flow**: Introduce the smallest viable browser ingest path, either through
   backend-managed upload or a presigned-upload contract, while reusing existing storage primitives
   and deterministic storage keys.
4. **Integrate publishing as a consumer**: Replace local-only composer attachments with a persisted
   flow where publishing reserves/selects media from the media context, uploads binary content, and
   submits publication requests with persisted media references.
5. **Keep browsing simple**: Limit the first read model to straightforward workspace browsing, such
   as newest-first listing, without search, folders, or richer management semantics.
6. **Design for later reuse without expanding scope now**: Define seams and naming so the same media
   context can later serve other apps, but do not broaden MVP features beyond the current publishing
   need.

## Boundaries

- The media library is a **separate bounded context** with publishing as its first consumer, not its
  owning domain.
- The library is **workspace-scoped only**.
- The library is **provider-neutral in model**, even if LinkedIn is the first consuming publication
  path.
- The MVP is a **reusable upload-and-select catalog**, not a full digital asset management system.
- Metadata remains limited to what serves upload, retrieval, and current publishing needs; no title,
  tags, ownership model, or editorial metadata is introduced now.
- Cross-context integration with publishing must stay narrow and explicit so other apps can later
  consume the same media context.
- Delete semantics are deferred to a post-MVP phase and are not part of this change.

## Rationale

This proposal stays narrow in product scope because the immediate user problem is still the same:
upload once, browse later, and publish with real persisted media references. The approved
architectural decision changes where that capability should live. Instead of treating media as an
internal extension of publishing, this proposal establishes media as its own bounded context from
the start so the first MVP can serve publishing now without needing to be extracted later for reuse
by other apps. Existing storage primitives, workspace-aware persistence patterns, and any useful
publishing asset concepts can still be reused tactically, but the architectural ownership, API
surface, and future evolution point to a dedicated media domain.

## Affected Areas

| Area                                                            | Impact       | Description                                                                                                                                                                                                                     |
|-----------------------------------------------------------------|--------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `server/smp/src/main/kotlin/com/profiletailors/smp/media/`      | New          | Introduce the dedicated media bounded context with its own application, domain, persistence, and HTTP layers                                                                                                                    |
| `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/` | Modified     | Consume persisted media from the media context instead of remaining the architectural home of the library                                                                                                                       |
| `shared/storage/src/main/kotlin/com/profiletailors/storage/`    | Modified     | Add only the minimum storage/upload contract needed for browser ingest, if existing APIs are insufficient                                                                                                                       |
| `apps/web/app/src/components/CreatePostModal.vue`               | Modified     | Replace local-only media handling with persisted media-library upload/select flow                                                                                                                                               |
| `apps/web/app/src/stores/publishing.ts`                         | Modified     | Load/manage media assets and submit real persisted media references in publication flows                                                                                                                                        |
| `apps/web/app/src/lib/media-api.ts`                             | New          | Create dedicated media-library API helpers (reserve, upload, list, get) that reuse authenticated workspace-scoped request patterns from `auth-api.ts` without co-locating media domain concerns inside auth infrastructure code |
| `apps/web/app/src/components/` or `apps/web/app/src/views/`     | New/Modified | Add simple media-library browsing/picking UI for the dashboard SPA                                                                                                                                                              |
| Persistence schema for media records and integration seams      | Modified     | Add the minimum persistence and linkage needed to let the media context own media records while publishing references them                                                                                                      |

## Modules / Packages Affected

- Backend media module: `com.profiletailors.smp.media`
- Backend publishing module: `com.profiletailors.smp.publishing`
- Shared storage module: `shared/storage`
- Frontend dashboard SPA: `apps/web/app`

## Risks

| Risk                                                                            | Likelihood | Mitigation                                                                                                                             |
|---------------------------------------------------------------------------------|------------|----------------------------------------------------------------------------------------------------------------------------------------|
| New bounded-context seams add more implementation overhead than an embedded MVP | Med        | Keep the media context intentionally thin and defer any generalized platform abstractions beyond the minimum needed for publishing MVP |
| Upload architecture choice expands scope unexpectedly                           | High       | Keep the proposal neutral but require the later design phase to choose the smallest viable ingest path                                 |
| Media-library MVP drifts into DAM features                                      | High       | Explicitly hold search, folders, tags, lifecycle policy, and rich editing out of scope                                                 |
| Publishing and media models become too tightly coupled despite the new boundary | Med        | Define explicit integration contracts so publishing consumes media without reclaiming ownership of the media model                     |
| LinkedIn-first UX leaks into the core library model                             | Med        | Keep the library workspace-scoped and provider-neutral while limiting supported media types to the current product envelope            |
| Delete semantics deferred — no delete API in MVP                                | Low        | Delete is explicitly out of scope; no storage/data inconsistency risk from this change                                                 |
| Frontend state migration leaves mixed local/persisted asset behavior            | Med        | Make persisted media references the canonical publication attachment flow for the composer MVP                                         |

## Rollback Plan

0. Toggle the `media.context.integration.enabled` feature flag to `false` to immediately revert
   publishing's asset resolution to the legacy path without a full redeployment (applicable only in
   the immediate post-Phase-3 period; see design for limitations). The flag is implemented as a
   runtime configuration value (e.g., Spring Boot property or environment variable); toggling
   requires updating the configuration source and may require a service restart depending on the
   platform's config refresh capability. ⚠️ WARNING: this flag is safe to toggle only before any
   workspace member has created a media-context-owned asset post-deployment. Toggling it after
   go-live will silently cause any publication referencing a new media-context asset to fail
   validation (not-found). Treat this as an immediate post-deployment emergency stop, not a
   long-running operational toggle.
1. Remove or disable the new media-context API routes while leaving existing publishing endpoints
   intact.
2. Revert the SPA composer and publishing store to local-only file selection and non-library
   submission behavior.
3. Leave any newly created media records in place if schema additions are purely additive; unused
   records can remain inert until the context is revisited.
4. Revert any new publishing-to-media integration seam so publishing no longer depends on the media
   context.
5. Revert any new upload contract added in `shared/storage` if it is not reused elsewhere.

## Dependencies

- Existing workspace context resolution via `X-Workspace-Id`
- Existing `shared/storage` provider abstraction and bucket configuration
- Existing publication creation flow that already accepts persisted media references
- Reusable persistence and storage patterns from current publishing asset handling where they reduce
  implementation cost without collapsing the new boundary

## Success Criteria

- [ ] Users can create workspace-scoped uploaded media records through a supported backend API owned
  by the media context
- [ ] The browser can upload selected media into platform-managed storage for those records
- [ ] The SPA can browse existing workspace media assets from persisted backend state
- [ ] Publishing can attach an uploaded or previously stored media item and submit real persisted
  media references
- [ ] LinkedIn publishing continues consuming uploaded assets through a narrow integration with the
  media context
- [ ] The resulting architecture leaves media positioned for reuse by other apps without first
  extracting it from publishing
- [ ] MVP scope remains limited to reusable upload/select behavior without folders, tags, rich
  editing, or advanced asset governance
