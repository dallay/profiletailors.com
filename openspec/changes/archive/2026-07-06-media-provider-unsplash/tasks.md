# Tasks: Media Provider — Unsplash

## Review Workload Forecast

| Field                   | Value       |
|-------------------------|-------------|
| Estimated changed lines | 500–800     |
| 400-line budget risk    | Medium      |
| Chained PRs recommended | Yes         |
| Delivery strategy       | ask-on-risk |
| Chain strategy          | chained-prs |

Decision needed before apply: No — resolved during peer review.
Chained PRs recommended: Yes — backend provider adapter + API first, frontend provider tab second.
Chain strategy: chained PRs (`backend-foundation-import` → `frontend-provider-tab`).
400-line budget risk: Medium — cross-cutting backend + frontend + tests, mitigated by PR slicing.

### Suggested Work Units

| Unit | Goal                                      | Likely PR | Notes                                                                 |
|------|-------------------------------------------|-----------|-----------------------------------------------------------------------|
| 1    | Backend provider port + Unsplash adapter  | PR 1      | New bounded context, WebClient, feature flag, error mapping           |
| 2    | Import flow + HTTP endpoints + tests      | PR 1      | Reuse CAS path, guards, canonical dedup behavior, WireMock/WebFlux    |
| 3    | Frontend provider tab + client + tests    | PR 2      | Shell-only picker changes, parent-owned panel, i18n, feature flag     |

## Resolved Decisions (locked before apply)

| Topic | Decision |
|-------|----------|
| Dedup policy | Re-import returns the canonical existing active `media_assets` row for the workspace+hash; reuse the existing blob; never create a duplicate blob or asset row. |
| Feature flag default | `mediaprovider.unsplash.enabled=false` in ALL environments; enabled only via explicit ENV/config. |
| `externalId` wire contract | Frontend always sends `unsplash:<photoId>`; backend validates the `unsplash:` prefix and rejects unqualified/wrong-provider values with 400 `INVALID_EXTERNAL_ID`. |
| Verified email on search | Consciously restrictive: both search and import require a verified email. |
| PR strategy | Chained PRs — backend foundation + import first, frontend provider tab second. |

## Phase 1: Backend Foundation — Port + Unsplash Adapter + Configuration

- [x] 1.1 Create `server/smp/src/main/kotlin/com/profiletailors/smp/media/application/port/MediaProvider.kt`
  with the neutral port contract and contract types: `search(query, page)`,
  `import(workspaceId, externalId)`, `ProviderExternalId`, `ProviderSearchItem`,
  `ProviderSearchPage`, and `ImportResult`.
- [x] 1.2 Create `server/smp/src/main/kotlin/com/profiletailors/smp/mediaprovider/unsplash/UnsplashModels.kt`
  for Unsplash-specific internal types (`UnsplashPhoto`, search response DTOs, adapter-local
  errors). Keep these types internal to the adapter and never expose them across the port boundary.
- [x] 1.3 Create `server/smp/src/main/kotlin/com/profiletailors/smp/mediaprovider/unsplash/UnsplashClient.kt`
  using Spring `WebClient` (Reactor Netty) with explicit timeout configuration and no automatic
  retry on import; allow at most one bounded retry for idempotent search if implemented.
- [x] 1.4 Create `server/smp/src/main/kotlin/com/profiletailors/smp/mediaprovider/unsplash/UnsplashAdapter.kt`
  implementing the `MediaProvider` port. Map Unsplash search results into provider-neutral items and
  normalize `externalId` as `unsplash:<photoId>`.
- [x] 1.5 Create `server/smp/src/main/kotlin/com/profiletailors/smp/mediaprovider/unsplash/UnsplashProperties.kt`
  plus `MediaProviderConfig.kt` to bind `mediaprovider.unsplash.*` properties and register the
  adapter behind `mediaprovider.unsplash.enabled`.
- [x] 1.6 Create `server/smp/src/main/kotlin/com/profiletailors/smp/mediaprovider/unsplash/UnsplashErrorMapper.kt`
  so Unsplash 4xx map to 502 `PROVIDER_ERROR`, 429 forwards `Retry-After`, timeout/network map to
  504 `PROVIDER_UNREACHABLE`, and unsupported MIME or >500 MB maps to 422 `IMPORT_REJECTED`.
- [x] 1.7 Update `server/smp/src/main/resources/application.yml` with a new
  `mediaprovider.unsplash` block (`enabled`, `base-url`, `timeout`, `page-size`) and update
  `.env.example` with `UNSPLASH_ACCESS_KEY`.

## Phase 2: Media Import Flow — CAS Reuse + Guards + Endpoints

- [x] 2.1 Update `server/smp/src/main/kotlin/com/profiletailors/smp/media/application/MediaCommands.kt`
  to add `ImportExternalAssetCommand` and `ImportExternalAssetResult` (asset id + `deduped`).
- [x] 2.2 Update `server/smp/src/main/kotlin/com/profiletailors/smp/media/application/MediaHandlers.kt`
  to add `ImportExternalAssetHandler` that:
  - enforces the same email verification, per-workspace rate limit, and concurrent upload slot used
    by uploads,
  - downloads provider bytes through the adapter,
  - reuses the existing CAS path,
  - persists `source_type='EXTERNAL'`, `source_provider='unsplash'`,
    `external_id='unsplash:<photo_id>'`, `source_url`, `author_name`, `author_url`, and metadata,
  - returns the canonical existing active asset row on dedup rather than creating a duplicate blob or
    asset row.
- [x] 2.3 Verify and reuse the existing media domain invariants in
  `server/smp/src/main/kotlin/com/profiletailors/smp/media/domain/MediaModels.kt` for `EXTERNAL`
  assets (`sourceProvider` required, attribution fields allowed) without widening contracts
  unnecessarily.
- [x] 2.4 Create `server/smp/src/main/kotlin/com/profiletailors/smp/media/infrastructure/http/MediaProviderController.kt`
  with:
  - `GET /api/workspaces/{ws}/media/providers/unsplash/search?query=<q>&page=<p>`
  - `POST /api/workspaces/{ws}/media/providers/unsplash/import`
  returning `{ externalId }` input and `{ assetId, deduped }` output. The import endpoint MUST
  validate the `unsplash:` prefix on `externalId` and reject unqualified/wrong-provider values with
  400 `INVALID_EXTERNAL_ID`.
- [x] 2.5 Update HTTP DTOs as needed in `server/smp/src/main/kotlin/com/profiletailors/smp/media/infrastructure/http/MediaDtos.kt`
  so provider search/import responses have explicit schemas and preserve attribution fields on media
  responses.
- [x] 2.6 Ensure feature-flag-off behavior is consistent: adapter absent or disabled must yield 404
  on both search and import endpoints without leaking access-key or provider internals.
- [x] 2.7 Ensure API responses for `EXTERNAL` assets continue to expose `sourceProvider`,
  `externalId`, `sourceUrl`, `authorName`, `authorUrl`, and `metadata` through
  `MediaAssetSummary` / list/get endpoints.

## Phase 3: Backend Testing — Unit + WebFlux + WireMock

- [x] 3.1 Add unit tests under `server/smp/src/test/kotlin/com/profiletailors/smp/mediaprovider/unsplash/`
  for search mapping, attribution mapping, `externalId` normalization to `unsplash:<photoId>`,
  unsupported MIME rejection, and oversized binary rejection.
- [x] 3.2 Add WebFlux/controller tests for provider search and import covering happy path, disabled
  feature flag, invalid `externalId` prefix, unverified email, rate limit, concurrent-slot
  rejection, Unsplash 4xx→502, 429 with forwarded `Retry-After`, and timeout→504.
- [x] 3.3 Add WireMock-backed integration tests proving no real network call occurs and that
  re-importing identical bytes returns the canonical existing asset id with `deduped: true`.
- [x] 3.4 Add/adjust regression tests around access-key safety so response bodies, exception
  messages, and logs do not leak `UNSPLASH_ACCESS_KEY`.

## Phase 4: Frontend Integration — Provider Tab + Parent-Owned Panel + i18n

- [x] 4.1 Create `apps/web/app/src/api/media-providers.ts` with typed search/import client helpers
  for Unsplash provider endpoints.
- [x] 4.2 Create `apps/web/app/src/features/media-composer/providers/MediaProviderPanel.vue`
  as the parent-owned data component using shadcn-vue `Command` and `Tabs` primitives. It owns
  search state, results, in-flight import state, and calls the typed API client.
- [x] 4.3 Update `apps/web/app/src/components/composer/ComposerMediaPickerShell.vue` to accept
  `provider: 'unsplash' | null`, render the provider tab only when configured, and emit
  `provider-search` / `provider-import` interactions without making network calls.
- [x] 4.4 Update `apps/web/app/src/components/composer/composer-media-picker.types.ts` with the new
  prop/event payload types.
- [x] 4.5 Update `apps/web/app/src/components/CreatePostModal.vue` to read bootstrap capability
  config sourced from the backend feature flag, pass the `provider` prop, host the parent provider
  panel, and integrate successful imports into the existing “asset attached” flow.
- [x] 4.6 Update locale files `apps/web/app/src/locales/en/media.json` and
  `apps/web/app/src/locales/es/media.json` with provider tab labels, empty states, loading states,
  import CTA text, and provider-specific error strings.
- [x] 4.7 Preserve the intentional UX constraint from the spec: do not render `authorName` or
  `authorUrl` anywhere in this change even though the API returns them.

## Phase 5: Frontend Testing + Verification

- [x] 5.1 Add `apps/web/app/src/features/media-composer/providers/MediaProviderPanel.test.ts`
  with mocked `media-providers` client coverage for search, loading, empty state, error handling,
  and import success/failure.
- [x] 5.2 Update `apps/web/app/src/components/composer/ComposerMediaPickerShell.test.ts` to verify:
  provider tab visibility is conditional, provider interactions emit events, and the shell performs
  no direct network call.
- [x] 5.3 Update `apps/web/app/src/components/CreatePostModal.test.ts` to verify feature-flag-driven
  visibility, parent-owned orchestration, and the standard asset-attached flow after import.
- [x] 5.4 Run focused backend and frontend verification for this change (targeted unit/WebFlux/
  WireMock tests plus frontend component/unit tests). Record any gaps that still require a later
  `sdd-verify` pass.

## Exit Criteria for Apply Readiness

- [x] Proposal, design, spec, and tasks remain aligned on two critical invariants:
  `externalId = unsplash:<photoId>` and dedup returns the canonical existing active asset row.
- [x] Feature flag behavior is consistent across backend and frontend.
- [x] The picker remains shell-only; all provider networking stays in parent-owned components.
- [x] Attribution is preserved in persistence and API responses but not rendered in the UI.
