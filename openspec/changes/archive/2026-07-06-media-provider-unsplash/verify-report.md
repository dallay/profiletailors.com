# Verification Report — `media-provider-unsplash`

> Re-verified 2026-07-06 after the runtime log-safety regression was added to
> `MediaProblemDetailsHandlerTest`.

## Verification Report

### Change and completeness

| Field | Result |
|---|---|
| Change | `media-provider-unsplash` |
| Persistence mode | OpenSpec |
| Verification mode | Standard behavioral verification. `openspec/config.yaml` sets `apply.tdd: true`, but no Strict TDD verifier module/runner was activated for this verify run. |
| Tasks | **33/33 complete**, 0 incomplete |
| Verdict | **PASS** |

### Real execution evidence

| Command | Result |
|---|---|
| `./gradlew :server:smp:test --tests "com.profiletailors.smp.media.application.MediaProviderHandlersTest" --tests "com.profiletailors.smp.media.application.ImportExternalAssetHandlerTest" --tests "com.profiletailors.smp.media.infrastructure.http.MediaProviderControllerWebFluxTest" --tests "com.profiletailors.smp.media.infrastructure.http.MediaProblemDetailsHandlerTest" --tests "com.profiletailors.smp.mediaprovider.unsplash.MediaProviderConfigTest" --tests "com.profiletailors.smp.mediaprovider.unsplash.UnsplashAdapterTest" --tests "com.profiletailors.smp.mediaprovider.unsplash.UnsplashWebClientTest" --tests "com.profiletailors.smp.mediaprovider.unsplash.UnsplashErrorMapperTest" --tests "com.profiletailors.smp.mediaprovider.unsplash.UnsplashWireMockIntegrationTest" -PexcludeTags=modularity,postgres --no-daemon` | ✅ `BUILD SUCCESSFUL` in 10s |
| `./gradlew :server:smp:test --tests "com.profiletailors.smp.media.infrastructure.http.MediaAssetResponseJsonTest" --tests "com.profiletailors.smp.media.infrastructure.http.MediaProviderControllerTest" -PexcludeTags=modularity,postgres --no-daemon` | ✅ `BUILD SUCCESSFUL` in 11s |
| `just backend-check` | ✅ `BUILD SUCCESSFUL` in 3s (`:server:smp:check`, including `detekt`, `spotless`, tests, and `koverVerify`) |
| `pnpm test:run src/features/media-composer/providers/MediaProviderPanel.test.ts src/components/composer/ComposerMediaPickerShell.test.ts src/components/CreatePostModal.test.ts src/views/MediaLibraryView.test.ts` | ✅ 90 tests passed |

### Spec compliance matrix

| Capability / scenario | Runtime evidence | Status |
|---|---|---|
| Port exposes provider-neutral `search` / `import` without leaking adapter types | `MediaProviderConfigTest`, `UnsplashAdapterTest` | ✅ COMPLIANT |
| Port is the public surface for future providers | `MediaProviderHandlersTest` proves selection by `providerId` across multiple adapters | ✅ COMPLIANT |
| New Unsplash photo creates an `EXTERNAL` asset with attribution + CAS blob | `ImportExternalAssetHandlerTest` | ✅ COMPLIANT |
| Re-import deduplicates to canonical existing asset | `ImportExternalAssetHandlerTest`, `UnsplashWireMockIntegrationTest` | ✅ COMPLIANT |
| Unverified email is rejected for import | `ImportExternalAssetHandlerTest`, `MediaProviderControllerWebFluxTest` | ✅ COMPLIANT |
| Concurrent upload slot is shared with imports | `ImportExternalAssetHandlerTest`, `MediaProviderControllerWebFluxTest` | ✅ COMPLIANT |
| Search returns paginated provider-neutral results | `MediaProviderControllerWebFluxTest`, `MediaProviderControllerTest` | ✅ COMPLIANT |
| Search forwards Unsplash 4xx as 502 `PROVIDER_ERROR` | `MediaProviderControllerWebFluxTest` | ✅ COMPLIANT |
| Search forwards Unsplash 429 with `Retry-After` | `MediaProviderControllerWebFluxTest` | ✅ COMPLIANT |
| Search times out as 504 `PROVIDER_UNREACHABLE` | `MediaProviderControllerWebFluxTest` | ✅ COMPLIANT |
| Import rejects invalid `externalId` with 400 `INVALID_EXTERNAL_ID` | `MediaProviderControllerWebFluxTest`, `MediaProviderControllerTest` | ✅ COMPLIANT |
| Import runs through guards | `MediaProviderControllerWebFluxTest`, `ImportExternalAssetHandlerTest` | ✅ COMPLIANT |
| Disabled feature flag returns 404 on search/import | `MediaProviderControllerWebFluxTest` | ✅ COMPLIANT |
| Unsupported MIME is rejected with 422 `IMPORT_REJECTED` | `UnsplashAdapterTest`, `MediaProviderControllerWebFluxTest` | ✅ COMPLIANT |
| Oversized binary is rejected with 422 `IMPORT_REJECTED` | `UnsplashAdapterTest`, `MediaProviderControllerWebFluxTest` | ✅ COMPLIANT |
| Unsplash adapter registers on startup when enabled | `MediaProviderConfigTest` | ✅ COMPLIANT |
| Adding a second provider requires no media-library source edit | `MediaProviderHandlersTest` | ✅ COMPLIANT |
| Public error bodies do not leak the access key | `UnsplashErrorMapperTest`, `MediaProviderControllerWebFluxTest`, `MediaProblemDetailsHandlerTest` | ✅ COMPLIANT |
| Provider exception logs do not leak the access key | `MediaProblemDetailsHandlerTest` captures the real Logback logger via `ListAppender` and asserts the sentinel key is absent | ✅ COMPLIANT |
| API responses expose attribution fields for `EXTERNAL` assets | `MediaAssetResponseJsonTest` | ✅ COMPLIANT |
| Provider tab is conditional | `ComposerMediaPickerShell.test.ts`, `CreatePostModal.test.ts` | ✅ COMPLIANT |
| Parent-owned panel performs search/import while picker shell stays data-free | `MediaProviderPanel.test.ts`, `ComposerMediaPickerShell.test.ts` | ✅ COMPLIANT |
| Importing a result emits provider-import / standard attach flow | `ComposerMediaPickerShell.test.ts`, `CreatePostModal.test.ts` | ✅ COMPLIANT |
| SPA does not render attribution metadata | `MediaLibraryView.test.ts` | ✅ COMPLIANT |
| WireMock proves local no-real-network import and canonical dedup reimport | `UnsplashWireMockIntegrationTest` | ✅ COMPLIANT |

### Correctness

| Area | Result | Evidence |
|---|---|---|
| Backend quality gate | ✅ | `just backend-check` passed |
| Backend provider contract/extensibility | ✅ | `MediaProviderHandlersTest` passed |
| Backend import + dedup behavior | ✅ | `ImportExternalAssetHandlerTest`, `UnsplashWireMockIntegrationTest` passed |
| Backend HTTP boundary | ✅ | `MediaProviderControllerWebFluxTest`, `MediaProviderControllerTest` passed for `200/400/403/404/422/429/502/504` |
| Backend API response shape | ✅ | `MediaAssetResponseJsonTest` passed |
| Provider exception mapping and log safety | ✅ | `UnsplashErrorMapperTest`, `MediaProblemDetailsHandlerTest` passed |
| Frontend provider flow | ✅ | Focused Vitest suite passed (90 tests) |
| Coverage gate | ✅ | `:server:smp:koverVerify` passed via `just backend-check` |

### Design coherence

| Decision | Result |
|---|---|
| Port lives under `media-library`; Unsplash is downstream adapter | ✅ Followed |
| Import downloads server-side and reuses CAS | ✅ Followed |
| Feature flag defaults off | ✅ Followed |
| Error mapping goes through `UnsplashErrorMapper` | ✅ Followed |
| Picker stays shell-only and parent-owned | ✅ Followed |
| WireMock-backed integration strategy | ✅ Followed |
| Access key is not echoed through provider error logging | ✅ Followed |

### Findings

#### CRITICAL

None.

#### WARNING

None.

#### SUGGESTION

1. If the backend later introduces explicit correlation-id generation or MDC enrichment around
   provider failures, add a dedicated regression test for that path too. Current code inspection
   found no correlation-id/MDC path in this change area.

### Dual-judge verdict table

| Finding | Judge A | Judge B | Severity | Status |
|---|---|---|---|---|
| Second-provider extensibility has executable proof | ✅ | ✅ | INFO | Confirmed |
| WireMock-backed no-real-network + canonical dedup proof exists | ✅ | ✅ | INFO | Confirmed |
| Search/import HTTP boundary coverage is complete for required statuses | ✅ | ✅ | INFO | Confirmed |
| Runtime log-safety proof for noisy provider exceptions now exists | ✅ | ✅ | INFO | Confirmed |
| No blocking spec or design deviations remain | ✅ | ✅ | INFO | Confirmed |

## Final Verdict: PASS

The latest runtime regression closes the last blocking verification gap. The focused backend
suite now proves a noisy `ProviderErrorException` containing a fake access key does not leak that
key into the real `MediaProblemDetailsHandler` logger, while the existing targeted backend,
WireMock, HTTP-boundary, JSON-shape, and focused frontend suites remain green.

Archive is **allowed**. There are no remaining CRITICAL verification issues for
`media-provider-unsplash`.
