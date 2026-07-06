# Design: Media Provider — Unsplash

## Technical Approach
Add a new `mediaprovider` bounded context under `server/smp` that ships the first concrete
`MediaProvider` adapter (Unsplash). The `media-library` bounded context gains a
`MediaProvider` port and an `ImportExternalAsset` flow that reuses the existing CAS binary
pipeline from the upload path. The composer media picker gains a parent-owned provider tab;
the picker remains shell-only. Frontend data fetches live in a parent panel.

This change ships behind a feature flag (`mediaprovider.unsplash.enabled=false` by default in
all environments). The provider is enabled only through explicit environment/configuration.
A second adapter (Pexels, Giphy, AI gen) is out of scope and is additive: a new adapter class
plus a property flag only.

## Architecture Decisions

| Decision | Alternatives | Rationale |
|---|---|---|
| Define `MediaProvider` port under `media-library/application/port/` with provider-neutral types | Keep port in `mediaprovider`; define a generic `ProviderAdapter` interface in `shared/` | Port lives next to the consumers (`ImportExternalAsset`, media controllers); the provider is a downstream adapter, not a generic kernel. Sharing types across bounded contexts would couple unrelated contexts. |
| Reuse the existing CAS pipeline (`workspaceFileBlobRepository.upsertBlob()` + `createPendingAsset()`) for provider imports | Build a separate import path; hotlink to the source URL; persist bytes outside the workspace | The binary pipeline already handles dedup, validation, and atomic row creation. Reuse gives free dedup and aligns with the existing CHECK-constraint rules. Hotlinking violates attribution policy and breaks dedup. |
| Provider imports download to the server, never hotlink | Pass provider URL through to the browser; lazy fetch on attach | Keeps the `UNSPLASH_ACCESS_KEY` server-side, gives a stable `file_hash` for dedup, and prevents the API key from leaking via CORS or referrer. |
| Frontend never fetches provider data inside the picker shell | Inline a `useProviderSearch` composable in the picker; read Pinia media provider store | The picker spec (and prior change #2) require the shell to remain data-free. The parent panel owns all network calls. |
| Provider tab is driven by a typed `provider: "unsplash" \| null` prop | Hardcode the tab on; let the shell read config directly | Keeps the picker deterministic and easy to test; the wrapper at `CreatePostModal` reads app/bootstrap capability config sourced from the backend flag and decides the prop value, while the shell remains a pure presentational boundary. |
| Use `@/components/ui/command` + `@/components/ui/tabs` from shadcn-vue for the provider search panel | Custom ComboBox; `cmdk` directly | The repo already uses these primitives (see media library shell). Reuse keeps the design system coherent. |
| WireMock stubs for Unsplash fixtures in tests, no real network | VCR-style cassettes; `nock` | The repo already standardizes on WireMock (per Gradle deps and infra conventions). Adopting it here keeps the test stack uniform. |
| Spring `WebClient` (Reactor Netty) with explicit connect/read timeout; no automatic retry on `import`, at most one bounded retry for idempotent `search` on 5xx | Resilience4j wrappers; Vert.x WebClient | `WebClient` is the standard reactive client in this Spring Boot + WebFlux backend. Import triggers a download + CAS write, so a blind retry risks partial-effect churn; keep retry to the read-only `search` path. |

## Data Flow

```text
CreatePostModal
  ├─ reads bootstrap capability config sourced from backend feature flags → passes provider="unsplash" to picker
  └─ owns: search input, results, in-flight import
          ↓
ComposerMediaPickerShell (provider tab)
  ├─ emits provider-search / provider-import
  └─ renders shell-only UI

When the author picks a result:
  parent → POST /api/workspaces/{ws}/media/providers/unsplash/import
  → controller guards: EmailVerifiedGuard, per-workspace rate limiter, concurrent-slot
  → MediaProvider.import(workspaceId, externalId) [port]
  → UnsplashAdapter.download() + parse attribution
  → workspaceFileBlobRepository.upsertBlob() (CAS, SHA-256)
  → mediaAssetsRepository.createPendingAsset(... source_type='EXTERNAL', source_provider='unsplash', attribution ...)
  → response { assetId, deduped }
```

Attribute mapping:

| Unsplash field | `media_assets` column |
|---|---|
| `links.html` | `source_url` |
| `user.links.html` | `author_url` |
| `user.name` | `author_name` |
| `unsplash:<photo.id>` | `external_id` |
| `color`, `alt_description`, `tags[]` (subset) | `metadata` JSONB |
| (full image bytes) | `workspace_file_blobs` (CAS) |

## File Changes

### Backend (new)

| File | Description |
|---|---|
| `server/smp/src/main/kotlin/com/profiletailors/smp/mediaprovider/unsplash/UnsplashModels.kt` | Unsplash-specific domain types (`UnsplashPhoto`, `UnsplashSearchPage`, `UnsplashError`) — internal to the adapter, never crossing the port boundary. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/mediaprovider/unsplash/UnsplashAdapter.kt` | Adapter implementing the `media-library` `MediaProvider` port for Unsplash; maps `search` and `import` calls into Unsplash HTTP calls and returns provider-neutral port types. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/mediaprovider/unsplash/UnsplashClient.kt` | Thin Reactor WebClient wrapper around `api.unsplash.com`. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/mediaprovider/unsplash/MediaProviderConfig.kt` | Spring configuration wiring the Unsplash adapter to the port under the `mediaprovider.unsplash.enabled` flag. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/mediaprovider/unsplash/UnsplashErrorMapper.kt` | Maps Unsplash 4xx → 502 `PROVIDER_ERROR`; 429 → 429 + `Retry-After`; timeout → 504 `PROVIDER_UNREACHABLE`; rejects unsupported MIME / >500 MB with 422 `IMPORT_REJECTED`. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/mediaprovider/unsplash/UnsplashProperties.kt` | `@ConfigurationProperties` reading `mediaprovider.unsplash.*`. |

### Backend (modified)

| File | Description |
|---|---|
| `server/smp/src/main/kotlin/com/profiletailors/smp/media/application/port/MediaProvider.kt` | New port with `search` and `import` operations, plus the provider-neutral types (`ProviderSearchItem`, `ProviderSearchPage`, `ImportResult`, `ProviderExternalId`). This is the single home of the port and its contract types. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/media/application/handler/ImportExternalAssetHandler.kt` | New handler that calls the port and returns the new/deduped asset id. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/media/api/MediaProviderController.kt` | New `@RestController` for search and import endpoints. |
| `server/smp/src/main/resources/application.yml` | New `mediaprovider.unsplash.*` block (default `enabled: false`, `base-url`, `timeout`). |
| `.env.example` | New `UNSPLASH_ACCESS_KEY` placeholder. |
| `server/smp/src/test/kotlin/com/profiletailors/smp/mediaprovider/` | Tests (WebFlux + WireMock + unit). |

### Frontend (new)

| File | Description |
|---|---|
| `apps/web/app/src/features/media-composer/providers/MediaProviderPanel.vue` | Parent panel that owns search/import UI (uses `@/components/ui/command` + `@/components/ui/tabs`). |
| `apps/web/app/src/api/media-providers.ts` | Typed client for `search` and `import` HTTP calls. |
| `apps/web/app/src/features/media-composer/providers/MediaProviderPanel.test.ts` | Component tests with mocked `media-providers` client. |

### Frontend (modified)

| File | Description |
|---|---|
| `apps/web/app/src/components/composer/ComposerMediaPickerShell.vue` | Adds `provider` prop, renders provider tab, emits `provider-search` and `provider-import`. |
| `apps/web/app/src/components/composer/composer-media-picker.types.ts` | Adds `provider` prop type and `provider-search`/`provider-import` event payloads. |
| `apps/web/app/src/components/composer/ComposerMediaPickerShell.test.ts` | Adds scenarios for provider tab visibility, parent-owned interactions. |
| `apps/web/app/src/components/CreatePostModal.vue` | Reads feature flag, passes `provider` prop, owns the parent panel and feature flag wiring. |
| `apps/web/app/src/components/CreatePostModal.test.ts` | Adds scenarios for provider tab feature flag on/off. |
| `apps/web/app/src/locales/en/media.json` + `apps/web/app/src/locales/es/media.json` | New locale keys: `composer.mediaPicker.provider.*`, error mappings, attribution placeholders. |

## Interfaces / Contracts

```ts
// Domain port (Kotlin)
interface MediaProvider {
    suspend fun search(query: String, page: Int): ProviderSearchPage
    suspend fun import(workspaceId: WorkspaceId, externalId: ProviderExternalId): ImportResult
}

data class ProviderExternalId(val value: String)              // "unsplash:<photoId>" convention
data class ProviderSearchPage(val items: List<ProviderSearchItem>, val page: PageMeta)
data class ProviderSearchItem(
    val externalId: ProviderExternalId,
    val previewUrl: String,
    val fullUrl: String,
    val width: Int,
    val height: Int,
    val authorName: String,
    val authorUrl: String,
    val sourceUrl: String,
)
data class ImportResult(val assetId: MediaAssetId, val deduped: Boolean)
```

```ts
// Frontend types
export interface MediaProviderSearchItem {
  externalId: string
  previewUrl: string
  fullUrl: string
  width: number
  height: number
  authorName: string
  authorUrl: string
  sourceUrl: string
}

export interface MediaProviderSearchResponse {
  items: MediaProviderSearchItem[]
  page: { number: number; size: number; total: number }
}

export interface MediaProviderImportResponse {
  assetId: string
  deduped: boolean
}
```

```ts
// Picker prop and event additions
export interface MediaProviderTabProps {
  provider: 'unsplash' | null
}

export type MediaProviderSearchEvent = { query: string; page: number }
export type MediaProviderImportEvent = { externalId: string }
```

```yaml
# application.yml (new block)
mediaprovider:
  unsplash:
    enabled: false               # default off in ALL environments; enable explicitly via ENV/config
    base-url: https://api.unsplash.com
    timeout: 5s
    page-size: 20
```

## Bounded-Context Placement

```text
media (existing)
├─ domain: MediaAsset, MediaSourceType enum, attribution fields
├─ application: ImportExternalAssetHandler (calls MediaProvider.import)
├─ application/port: MediaProvider (NEW) ←── adapter-facing seam
└─ infrastructure: media_assets R2DBC, CAS, controllers

mediaprovider (NEW)
└─ unsplash
   ├─ domain: UnsplashPhoto, UnsplashSearchPage, UnsplashError
   ├─ application: SearchPhotosHandler, ImportPhotoHandler
   ├─ infrastructure: UnsplashClient (WebClient), UnsplashAdapter (port impl)
   └─ config: MediaProviderConfig (wires adapter under flag)
```

## Risk Mitigations

| Risk | Mitigation |
|---|---|
| Unsplash 429 at scale | Forward `Retry-After`; future-proof by caching popular queries in a later change. |
| Access key leaks in logs/errors | `UnsplashErrorMapper` masks the key; structured logging grep-tested in CI. |
| Attribution displayed prematurely | Component/DOM tests assert `authorName`/`authorUrl` never appear in the rendered output for `EXTERNAL` assets in the composer and library surfaces. |
| Imports bypass concurrent-slot guard | `ImportExternalAssetHandler` shares the same guard used by uploads. |
| Frontend fetches inside the picker shell | Shell tests mock the API client and assert the shell performs no network call — only event emissions; the parent panel owns all calls. |

## Open Questions / Out of Scope
- Pagination cursors vs page numbers — defer, ship page-number first.
- Provider-side dedup across workspaces — defer; workspace-scoped CAS dedup is sufficient.
- Caching of common searches — defer; document as follow-up.
