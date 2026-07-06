# Proposal: Media Provider — Unsplash

## Intent

Ship the first adapter on the `MediaProvider` port so workspace members can search and import
Unsplash photos into the media library. The `media-library` spec already defines
`MediaSourceType.EXTERNAL` and six attribution columns, and the CAS path is shared between
uploads and provider imports — but no adapter exists, so those fields are unused and the
composer lacks a stock-imagery shortcut.

## Scope

### In Scope

- New `mediaprovider` bounded context with `unsplash` as the only concrete adapter.
- `MediaProvider` port (`search`, `import`) under `media-library`.
- Server-mediated import: download → SHA-256 → CAS write → `media_assets` row with
  `source_type='EXTERNAL'`, `source_provider='unsplash'`, six attribution fields populated.
- API: `GET /api/workspaces/{ws}/media/providers/unsplash/search` and
  `POST /api/workspaces/{ws}/media/providers/unsplash/import`.
- SPA: provider tab inside the existing composer media picker (reuses the shell, no new design).
- Email verification, per-workspace rate limit, and concurrent upload slot enforced as for
  upload.
- `UNSPLASH_ACCESS_KEY` in backend `.env`.

### Out of Scope

- Other providers (Pexels, Giphy, AI image gen) — same port, separate change.
- Rendering `authorName` / `authorUrl` in the UI — owned by a follow-up UI change.
- Per-user/per-workspace Unsplash OAuth, search history, favorites, collections.
- Browser-direct CDN hotlinks — we always download to CAS to keep the key server-side and to
  enable dedup.
- LinkedIn publishing integration of imported assets — already covered by `linkedin-media-upload`.

## Capabilities

### New Capabilities

- `media-provider-unsplash`: First concrete adapter on the `MediaProvider` port. Search
  Unsplash, import an image with attribution preserved, attach to composer drafts.

### Modified Capabilities

- `media-library`: Add the `MediaProvider` port and an `ImportExternalAsset` flow that reuses
  the CAS pipeline. Schema/DTOs already support the data shape — only the import entry point
  is new.
- `composer-media-picker`: Add an optional `provider` prop and a `provider-import` emitted
  interaction. The shell stays shell-only; parent owns all data fetching.

## Approach

1. New `mediaprovider` bounded context at
   `com.profiletailors.smp.mediaprovider.{domain,application,infrastructure}`.
2. Reuse the CAS pipeline: import routes the downloaded bytes through the same
   `workspaceFileBlobRepository.upsertBlob()` + `createPendingAsset()` path used by uploads,
   with `source_type='EXTERNAL'` set in the same atomic insert. No new binary path.
3. Attribution mapping: `links.html` → `source_url`; `user.links.html` → `author_url`;
   `user.name` → `author_name`; `unsplash:{photo.id}` → `external_id`. `metadata` (JSONB) stores
   color, alt_description, tags subset for future display.
4. Download, never hotlink — gives a real `file_hash` for dedup and keeps the API key
   server-side.
5. Composer entry point: reuse `composer-media-picker` shell. Add `provider: 'unsplash' | null`
   and an emitted `provider-import`. Parent panel owns search and import calls.
6. Reuse `EmailVerifiedGuard` and per-workspace limits as-is.
7. Error mapping: Unsplash 4xx → 502 `PROVIDER_ERROR`; 429 → 429 + `Retry-After`;
   timeout/network → 504 `PROVIDER_UNREACHABLE`; unsupported MIME / >500 MB → 422
   `IMPORT_REJECTED`.

## Affected Areas

| Area                                                        | Impact   | Description                                          |
|-------------------------------------------------------------|----------|------------------------------------------------------|
| `server/smp/src/main/kotlin/.../mediaprovider/`             | New      | New bounded context                                  |
| `server/smp/src/main/kotlin/.../media/application/port/`    | Modified | `MediaProvider` port interface                       |
| `server/smp/src/main/kotlin/.../media/application/handler/` | Modified | Reuse CAS import for `EXTERNAL` rows                 |
| `server/smp/src/main/kotlin/.../media/api/`                 | Modified | Search + import endpoints                            |
| `server/smp/src/main/resources/application.yml`             | Modified | `mediaprovider.unsplash.*` config block              |
| `server/smp/src/test/kotlin/.../mediaprovider/`             | New      | Unit + WebFlux + WireMock tests                      |
| `apps/web/app/src/features/media/composer/`                 | Modified | Provider tab + search/import UI                      |
| `apps/web/app/src/api/media-providers.ts`                   | New      | Client types + fetchers                              |
| `apps/web/app/src/locales/{en,es}/media.json`               | Modified | Provider search, attribution, error strings          |
| `.env.example`                                              | Modified | `UNSPLASH_ACCESS_KEY` placeholder                    |

## Risks

| Risk                                                       | Likelihood | Mitigation                                                      |
|------------------------------------------------------------|------------|----------------------------------------------------------------|
| Unsplash API rate limit blocks import at scale             | Medium     | Surface 429 + `Retry-After`; cache popular searches             |
| Hotlink policy violation if we ever bypass download        | Low        | Always download; no CDN URL persisted; review at sdd-verify    |
| Attribution stored but not yet displayed in the UI         | Medium     | Acceptable — already in media-library spec; deferred UI change |
| Imports saturate the 5-slot concurrent upload limit        | Medium     | Imports count toward the same limit; failures map to 429       |
| Unsplash access key leaks in logs / error responses        | Low        | Mask in error mapper; never echo the raw key                   |
| Provider adapter is the only consumer of the new context   | Low        | Port is the public surface — adding Pexels is adapter-only     |

## Rollback Plan

1. Disable behind feature flag `mediaprovider.unsplash.enabled=false` (default `false` in all
   environments). Frontend hides the provider tab when off; backend returns 404.
2. Remove the `mediaprovider` package, search/import routes, SPA provider tab, and locale
   strings.
3. Leave `media-library` `EXTERNAL` schema and DTO changes intact — independent and not removed
   by this rollback.
4. Drop the `UNSPLASH_ACCESS_KEY` env entry. No data migration needed; existing `EXTERNAL` rows
   stay importable for any future provider.
5. Revert the `MediaProvider` port from `media-library` (additive removal; no callers left).

## Dependencies

- `UNSPLASH_ACCESS_KEY` in `.env` (Unsplash demo key acceptable for non-prod).
- Already-merged `media-library` external-source-type work and `media-asset-dedup` CAS path.
- Reused as-is: `EmailVerifiedGuard`, per-workspace rate limiter, concurrent upload slot.
- Frontend: existing `composer-media-picker` shell, shadcn-vue `Dialog`/`Tabs`/`Command`,
  i18n locale files.

## Success Criteria

- [ ] Search returns 200 with paginated results mapped to the shared provider search shape.
- [ ] Import persists a `media_assets` row with `source_type='EXTERNAL'`,
      `source_provider='unsplash'`, all six attribution fields, and bytes stored through CAS.
- [ ] Re-importing the same Unsplash photo into the same workspace yields a dedup hit
      (`deduped: true`) and returns the canonical existing asset row for that workspace instead of
      creating a duplicate asset row or blob.
- [ ] Email verification, rate limit, and concurrent slot are enforced identically to upload.
- [ ] SPA composer picker shows a provider tab; selecting a result creates an asset and
      triggers the standard "asset attached" flow.
- [ ] Attribution fields are present in API responses but NOT rendered anywhere in the UI.
- [ ] Feature flag off → provider tab hidden and backend search/import return 404.
- [ ] Unit + WebFlux + WireMock tests cover happy path, dedup hit, and Unsplash 4xx/5xx
      mappings.
