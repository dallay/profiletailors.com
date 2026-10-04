# Design: Threads Provider Integration

## Technical Approach

Extend the publishing bounded context with provider-keyed registries and routers. LinkedIn remains the compatibility alias; Threads reuses connection, publication, scheduler, worker, media-library, delivery-attempt, and notification paths. Adapters remain behind domain/application ports per ADR-0002 and ADR-0015/0016.

## Architecture Decisions

| Decision | Choice | Rejected alternative and rationale |
|---|---|---|
| Provider routing | `ProviderConnectionRegistry`, `ProviderPublishingRegistry`, and capability registry keyed by `SocialProvider`; generic controller routes plus LinkedIn aliases | Provider-specific handlers duplicate workflow and make future providers costly |
| OAuth state | Generalize signed HMAC state to provider/workspace/principal/redirect/nonce/issued/expiry; use only Meta-supported parameters, with no invented PKCE | Client-held or unsigned state permits CSRF, replay, or workspace confusion |
| Credentials | Provider-neutral extraction port; AES-GCM encrypted JSON in existing `secure_credentials`, with expiry/scopes and refresh-ahead metadata | Tokens in `SocialConnection`, logs, evidence, or APIs violate the secret boundary |
| Threads publishing | Dedicated HTTP adapter with typed DTOs; bounded container create → poll → finalize; capability validator rejects unsupported media before worker I/O | Treating Threads as a generic REST post cannot model its container lifecycle |
| Ambiguity | Record `IN_PROGRESS`/`AMBIGUOUS` phase and remote identifiers before retry; retry only when provider lookup proves no final object | Blind retry risks duplicate posts; assuming timeout means failure loses delivery truth |
| Enablement | Configuration binding validates enabled Threads credentials, redirect URI, API base/version, encryption key, and TTL budgets at startup; disabled configuration is `HIDDEN` | Runtime discovery of missing secrets creates partial, unsafe availability |

## Data Flow

```text
SPA catalog → generic initiate → signed state → Meta OAuth → generic complete
     │             │                               │
     └── composer ─┴── worker → credential resolver → Threads adapter
                                      │                 │
media library → TTL-checked fetch URL ─┴→ containers → poll → publish
                                                     ↓
                              delivery_attempt + publication external ID/status
```

Completion extracts only provider identity, display name, kind, avatar URL, and encrypted credential reference; raw payloads are discarded. Publishing resolves a credential, validates capability, obtains a provider-fetchable HTTPS URL, creates child containers, polls with bounded backoff, then finalizes the parent. Errors map to retryable, non-retryable, reconnect, or ambiguous categories.

The media URL must satisfy `expiresAt >= now + createBudget + pollBudget + finalizeBudget + safetySkew`; otherwise the resolver reissues or rejects it. The media library remains authoritative for readiness and no public bucket is introduced.

## File Changes

| File/module | Action | Description |
|---|---|---|
| `publishing/domain/{PublishingModels,PublishingProviderContracts,OAuthConnectionContracts}.kt` | Modify | Add `THREADS`, provider-neutral OAuth/credential/registry contracts, capability/media lifecycle types; preserve LinkedIn compatibility. |
| `publishing/application/{PublishingConnectionHandlers,PublishingPublicationHandlers,PublishingProviderCatalogHandlers}.kt` | Modify | Route by provider, re-evaluate catalog policy at initiation, map provider errors, and keep handlers framework-free. |
| `publishing/infrastructure/{http,credentials,media,persistence}` | Modify | Generic connection endpoints/DTOs, credential extraction/refresh wiring, TTL resolver, repository evidence mapping. |
| `publishing/infrastructure/threads/{ThreadsPublishingWiring,ThreadsHttpTransport,ThreadsDtos}.kt` | Create | Meta OAuth/token/profile/container adapter, typed serialization, capability validation, and error mapping. |
| `db/changelog/publishing/023-threads-delivery-evidence.yaml` and master | Create/modify | Add nullable container ID and provider status to `delivery_attempts`; rollback drops only these columns. No OAuth-state migration. |
| `apps/web/app/src/{router,index;modules/auth;modules/publishing;modules/settings;shared/lib/provider-presentation.ts;shared/i18n/locales/*}` | Modify/create | Generic provider callback route, catalog/action mapping, reconnect/disconnect, Threads catalog labels, and composer channel capability filtering; no second composer. |

## Interfaces / Contracts

`POST /api/publishing/{provider}/connections/{initiate|complete}` uses existing v1 headers, workspace authorization, redirect allow-list, problem-details errors, and redacted responses. `/linkedin/...` remains an alias. Threads account kind is `PERSONAL_PROFILE`; one provider account maps to one workspace `SocialAccount`.

`ThreadsPublisher.publish(command)` returns final remote ID/public URL only after finalization. Internal evidence stores operation key, claim version, container ID, final ID, provider status, phase, and sanitized error code. No token, authorization code, signed media URL, or raw payload is serialized or logged.

## Testing Strategy

- **Unit:** provider registries/router, state expiry/tamper/replay, capability matrix (text, one image/video, 2–20 mixed), TTL inequality, token refresh, error classification, and ambiguity transitions.
- **Integration:** WebTestClient contract/alias/auth tests; WireMock or equivalent typed Meta HTTP tests; encrypted credential round-trip; R2DBC/Liquibase evidence and idempotency tests; worker tests for bounded polling and claim fencing.
- **BDD/E2E:** add `publishing-threads.feature` and glue for catalog, OAuth completion, reconnect, media constraints, and no-secret responses; Playwright covers settings connect/callback, channel selection, and scheduled Threads publication with mocked provider HTTP. Real Meta smoke/App Review evidence is a release gate, not a local test claim.

## Migration / Rollout

Deploy schema and code with Threads disabled. Enable only after secrets, redirect allow-list, Meta app roles/review evidence, metrics, and smoke verification. Roll back by disabling Threads, preserving LinkedIn routes/data; remove nullable evidence columns only after Threads jobs end. Never retry ambiguity without remote lookup.

## Security / Operations / Open Questions

Encrypt credentials with the existing key; redact provider exceptions and audit metadata; revoke or invalidate credentials on disconnect. Emit metrics for OAuth failures, refresh/reconnect, poll timeout, ambiguity, and rate limits without identifiers or URLs. Meta endpoint/version and production TTL budgets remain pending provider validation.