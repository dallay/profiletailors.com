# Media Library — Delta for Media Provider (Unsplash)

## Purpose

Add the `MediaProvider` port and the `ImportExternalAsset` flow that reuses the existing
CAS pipeline for provider imports.

## Added Requirements

### Requirement: MediaProvider port

The `media-library` bounded context MUST expose a `MediaProvider` port with `search` and
`import` operations, both returning provider-neutral types. Implementations live in their
own bounded context (the first shipped adapter is `mediaprovider.unsplash`).

#### Scenario: Port is the public surface for providers

- GIVEN a future provider adapter (Pexels, Giphy, etc.)
- WHEN adding it to the system
- THEN `media-library` MUST NOT require source edits beyond application configuration

### Requirement: Provider imports share the CAS binary path

The import flow MUST reuse `workspaceFileBlobRepository.upsertBlob()` and
`createPendingAsset()` to persist provider-imported binaries. Provider imports MUST
populate `source_type='EXTERNAL'`, `source_provider`, `external_id`, and the six
attribution columns atomically with the row insert. The binary pipeline itself MUST be
unchanged from the upload path.

#### Scenario: Re-import deduplicates to the canonical asset

- GIVEN a workspace already stores the bytes of a provider photo
- WHEN the same photo is re-imported
- THEN the response MUST return `deduped: true`
- AND it MUST reference the canonical existing active `media_assets` row for that workspace
- AND it MUST reuse the existing blob rather than creating a duplicate blob or asset row

### Requirement: Provider import requires verified email and rate limits

`ImportExternalAsset` MUST be guarded by `EmailVerifiedGuard`, the per-workspace rate
limiter, and the same concurrent-slot guard used by uploads. Provider imports MUST count
against the concurrent-slot limit.

#### Scenario: Unverified email is rejected

- GIVEN a workspace member without verified email
- WHEN they request an import
- THEN the response MUST be the same status that an unverified upload receives

#### Scenario: Concurrent upload slot shared

- GIVEN the workspace has five in-flight uploads
- WHEN a member requests an import
- THEN the import MUST be rejected with 429 until a slot frees up
