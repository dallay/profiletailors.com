# Media Provider — Unsplash Specification

## Purpose

Define the first concrete adapter on the `MediaProvider` port so that workspace members can
search and import Unsplash photos into the media library with attribution preserved, the
capability hidden behind a feature flag, and the binary flow reused from the existing CAS
upload pipeline.

## Requirements

### Requirement: MediaProvider port under media-library

The system MUST define a `MediaProvider` port at `media-library` application boundaries
that exposes provider-neutral operations for searching and importing external imagery.
Implementations live in their own bounded contexts (the only one shipped in this change is
`mediaprovider.unsplash`).

#### Scenario: Port exposes search and import operations

- GIVEN the `MediaProvider` port in `media-library/application/port/`
- WHEN the application compiles
- THEN the port MUST declare exactly `search(query, page)` returning a paginated
  provider-neutral result and `import(workspaceId, externalId)` returning the new
  or deduped `media_assets` row id
- AND it MUST NOT expose provider-specific request or response types outside the
  adapter's bounded context

### Requirement: Provider import reuses the CAS binary path

`ImportExternalAsset` MUST route the downloaded bytes through the existing CAS pipeline used
by browser uploads. Provider imports MUST produce a `media_assets` row with
`source_type='EXTERNAL'`, `source_provider='unsplash'`, `external_id`, the six attribution
fields populated, and bytes stored through `workspaceFileBlobRepository.upsertBlob()` plus
`createPendingAsset()` in the same atomic insert used for uploads. The binary flow MUST be
identical to uploads — the only difference is metadata attached to the row.

#### Scenario: New Unsplash photo creates an EXTERNAL row

- GIVEN a workspace member with verified email
- WHEN they import an Unsplash photo that does not yet exist as a blob in the workspace
- THEN the system MUST insert a `media_assets` row with `source_type='EXTERNAL'`,
  `source_provider='unsplash'`, `external_id='unsplash:<photo_id>'`,
  `source_url=<links.html>`, `author_url=<user.links.html>`, `author_name=<user.name>`
- AND the row MUST reference a `workspace_file_blobs` entry written by the CAS pipeline

#### Scenario: Re-import hits dedup and returns the canonical existing asset

- GIVEN a workspace already stores the bytes of an Unsplash photo via CAS
- WHEN the same photo is re-imported by any member of the workspace
- THEN the response MUST be `{ assetId, deduped: true }` referencing the canonical existing
  active `media_assets` row for that workspace and hash
- AND it MUST NOT create a new blob or a duplicate asset row

#### Scenario: Unsplash photo exceeding 500 MB is rejected

- GIVEN an Unsplash result whose binary content exceeds 500 MB after download
- WHEN the import handler receives it
- THEN it MUST return 422 `IMPORT_REJECTED`
- AND it MUST NOT write a blob or a `media_assets` row

#### Scenario: Unsplash photo with unsupported MIME is rejected

- GIVEN an Unsplash result whose MIME is not in the supported allowlist
  (`image/jpeg`, `image/png`, `image/gif`, `image/webp`)
- WHEN the import handler receives it
- THEN it MUST return 422 `IMPORT_REJECTED`

### Requirement: Unsplash adapter is the only shipping implementation

The only concrete adapter that MUST ship in this change is `mediaprovider.unsplash`.
Adapter registration MUST happen at startup via a Spring configuration that wires the
Unsplash implementation to the `MediaProvider` port. Adding a new provider MUST require
only a new adapter class plus a property flag — no port edits.

#### Scenario: Unsplash adapter registers on startup

- GIVEN `mediaprovider.unsplash.enabled=true` and a valid `UNSPLASH_ACCESS_KEY`
- WHEN the backend starts
- THEN the `MediaProvider` port MUST be resolvable to the Unsplash adapter
- AND any provider without a registered implementation MUST cause the application to
  log and continue (no provider returns 404)

#### Scenario: Adding a second provider requires no port edit

- GIVEN a future Pexels adapter class implementing `MediaProvider`
- WHEN added to the project
- THEN the `media-library` package MUST NOT require source-code changes
- AND its `application.yaml` block alone MUST determine activation

### Requirement: Search and import HTTP endpoints

The system MUST expose two WebFlux endpoints under the media route group:

- `GET /api/workspaces/{ws}/media/providers/unsplash/search?query=<q>&page=<p>`
- `POST /api/workspaces/{ws}/media/providers/unsplash/import` with body
  `{ externalId: string }`. The frontend MUST always send the fully-qualified form
  `unsplash:<photoId>`, and the backend MUST validate the `unsplash:` prefix and reject any
  unqualified or wrong-provider value with 400 `INVALID_EXTERNAL_ID`.

Both endpoints MUST require workspace membership, a verified email, the per-workspace rate
limit, and the same concurrent-slot guard used by uploads. Requiring a verified email for
search (not only import) is a deliberate, consciously restrictive decision for this change.
Provider imports MUST count against the same concurrent-slot limit. Both endpoints MUST
return 404 when the feature flag is disabled.

#### Scenario: Search returns paginated results

- GIVEN a verified workspace member
- WHEN they call the search endpoint with `query=mountains&page=1`
- THEN the response MUST be 200 with `{ items: ProviderSearchItem[], page: { number, size,
  total } }`
- AND each `ProviderSearchItem` MUST contain `externalId`, `previewUrl`, `fullUrl`,
  `width`, `height`, `authorName`, `authorUrl`, `sourceUrl`

#### Scenario: Search forwards Unsplash 4xx as 502 PROVIDER_ERROR

- GIVEN the Unsplash API returns 4xx
- WHEN the search handler receives it
- THEN the response MUST be 502 with code `PROVIDER_ERROR`
- AND the response body MUST NOT include the Unsplash access key

#### Scenario: Search forwards Unsplash 429 with Retry-After

- GIVEN the Unsplash API returns 429 with a `Retry-After` header
- WHEN the search handler receives it
- THEN the response MUST be 429 with the same `Retry-After` header

#### Scenario: Search times out as 504 PROVIDER_UNREACHABLE

- GIVEN the Unsplash API does not respond before the configured timeout
- WHEN the search handler awaits the response
- THEN the response MUST be 504 with code `PROVIDER_UNREACHABLE`

#### Scenario: Import rejects an unqualified externalId

- GIVEN the Unsplash import endpoint
- WHEN the body `externalId` is missing the `unsplash:` prefix or uses a different provider prefix
- THEN the response MUST be 400 with code `INVALID_EXTERNAL_ID`
- AND it MUST NOT download bytes, call Unsplash, or write any row

#### Scenario: Import runs through guards

- GIVEN the Unsplash import endpoint
- WHEN invoked
- THEN it MUST enforce `EmailVerifiedGuard`, the per-workspace rate limiter, and the
  concurrent upload slot
- AND it MUST return the same status codes as an upload guard trip

#### Scenario: Disabled feature flag returns 404

- GIVEN `mediaprovider.unsplash.enabled=false`
- WHEN any client calls either search or import
- THEN the response MUST be 404

### Requirement: Access-key safety

The `UNSPLASH_ACCESS_KEY` value MUST be loaded from environment only and MUST NEVER appear
in HTTP responses, structured logs, exception messages, or correlation ids.

#### Scenario: Key does not leak through errors

- GIVEN a working Unsplash adapter
- WHEN a 4xx/5xx/timeout occurs
- THEN log lines, exception messages, and response bodies MUST NOT contain the access key

### Requirement: Composer picker exposes a provider tab

The composer media picker MUST render a provider tab when the parent supplies a configured
provider, and MUST remain shell-only: no fetching, no persistence, no asset mutation. All
provider-specific data calls MUST happen in a parent-owned panel that reads from a typed
client and emits a `provider-import` interaction.

#### Scenario: Provider tab appears only when enabled

- GIVEN a parent component passes `provider="unsplash"` to the picker
- WHEN the picker renders
- THEN a "Provider" tab MUST be visible alongside the existing tab(s)
- AND when `provider` is `null` or omitted the tab MUST NOT render

#### Scenario: Parent panel owns search and import

- GIVEN a provider tab is rendered
- WHEN the author types a query and presses Enter
- THEN the picker MUST emit `provider-search` with the typed payload
- AND the picker MUST NOT call any HTTP endpoint directly

#### Scenario: Importing a result emits provider-import

- GIVEN a provider-search result is displayed by the parent panel
- WHEN the author clicks "Import"
- THEN the picker MUST emit `provider-import` with `{ externalId }`
- AND the picker MUST NOT directly call the import API

### Requirement: Attribution present in API responses, not rendered

The API MUST return `authorName`, `authorUrl`, and `sourceUrl` for `EXTERNAL` assets. The
SPA MUST NOT render any of them in this change. A follow-up UI change is required to
surface attribution.

#### Scenario: API exposes attribution

- GIVEN a request for an `EXTERNAL` asset
- WHEN the response is built
- THEN it MUST include `authorName`, `authorUrl`, and `sourceUrl` fields with the stored
  values

#### Scenario: SPA does not render attribution

- GIVEN the composer media picker and any media library surface
- WHEN a reviewer inspects the rendered DOM for an `EXTERNAL` asset
- THEN no element MUST display `authorName` or `authorUrl`

### Requirement: Feature flag determines availability

The capability MUST be feature-flagged through `mediaprovider.unsplash.enabled` (default
`false` in all environments). It is enabled only through explicit environment/configuration.
When `false`, the backend MUST return 404 on every search and import, and the SPA MUST hide
the provider tab.

#### Scenario: Backend off

- GIVEN `mediaprovider.unsplash.enabled=false`
- WHEN a request reaches the backend
- THEN it MUST respond 404

#### Scenario: Frontend off

- GIVEN the SPA loads with `mediaprovider.unsplash.enabled=false`
- WHEN the composer media picker renders
- THEN the provider tab MUST NOT render

### Requirement: Test coverage

The change MUST add:

- Hexagonal unit tests for the Unsplash adapter (search mapping, attribution mapping,
  unsupported MIME detection).
- WebFlux slice tests for the search and import endpoints (happy path, dedup hit, 4xx, 5xx,
  timeout, rate limit, disabled flag, unverified email).
- WireMock integration tests proving no real network call and that re-imports of the same
  bytes dedup correctly.

#### Scenario: WireMock proves no real network call

- GIVEN the Unsplash test fixtures are loaded into WireMock
- WHEN all webflux tests run
- THEN the Unsplash base URL MUST resolve to WireMock stubs and no real network call MUST
  occur

#### Scenario: Dedup is verified end-to-end

- GIVEN a workspace containing an Unsplash photo already imported
- WHEN the same photo is imported again
- THEN the second response MUST have `deduped: true` and reference the same
  `media_assets` id
