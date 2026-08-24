# Design: LinkedIn Company Pages Community Inbox — PR2 Foundation

## Technical Approach

Keep Company Page reads provider-neutral and CQRS/Mediator-driven. The HTTP controller creates version-1 commands/queries; application handlers obtain `WorkspaceScope` from `ResourceContextProvider`, resolve a workspace-owned organization-page account, pass through one all-evidence gate, and then call a read-only LinkedIn adapter. Imported posts are persisted in the existing `social_content_*` schema and exposed only as immutable read models. Personal OAuth and `RealLinkedInPublisher` remain untouched.

```text
HTTP + v1 headers
  -> Mediator
  -> workspace handler -> access/evidence gate -> SocialContentProvider
                                      |                 |
                                      +-> R2DBC actor/post/checkpoint stores
```

## Architecture Decisions

| Decision | Alternatives considered | Rationale |
|---|---|---|
| Make all three handlers Spring-discoverable Mediator handlers | Controller-direct services; reflection-only registration | `RegistryImpl` indexes `CommandWithResultHandler`/`QueryHandler` interfaces. Explicit interfaces and the project `common.domain.Service` annotation prevent runtime handler-not-found failures. |
| Centralize approval in `SocialContentAccessGate`, with adapter defense-in-depth | Per-handler checks; adapter-only checks | One policy validates the same workspace/account, approval, `ADMIN`, both organization scopes, supported nonblank API version, and nonblank retention-policy version. Adapter checks before token resolution/external HTTP protect future callers. |
| Persist bounded batches transactionally and checkpoint after persistence | Save each row and checkpoint separately; add a new migration | Existing migration `publishing-016` already provides tables and identity constraints. A batch-writer port gives atomic upserts/tombstones/checkpoint behavior without schema changes; retries remain idempotent. |
| Keep Community Management read-only in PR2 | Reuse personal publisher; expose comment/reply methods | Page posts must never enter publication writes. The adapter stays separate from `RealLinkedInPublisher`; write-like provider methods remain unbound/typed-denied until a later capability. |
| Use opaque stable keyset cursors | Expose LinkedIn offsets; offset pagination | Cursor tokens hide provider offsets and avoid calendar duplication. Decode/validation happens before any provider call. |

## Data Flow

1. Security authenticates the bearer token; workspace context is populated from `X-Workspace-Id`.
2. Controller dispatches `SocialContentSyncCommand`, `WorkspaceSocialContentCalendarQuery`, or `SocialContentPostQuery` through `Mediator` with `version = "1"` mappings.
3. The gate resolves the account/connection and approval evidence by workspace and social-account identity. Disabled flags (`discovery`, `import`, and `sync`) fail before credential resolution.
4. Sync loads the workspace/actor checkpoint, requests at most `polling.maxPages`, retries 429s using bounded `Retry-After` delays, deduplicates overlap by external identity, and writes posts plus checkpoint in one batch. A non-null provider continuation cursor is saved; tombstones are calculated only for a complete full sync.
5. Calendar reads workspace-qualified posts using a stable `(publishedAt, externalPostId)` cursor. Detail reads the same scope-qualified store and returns `mutationAllowed = false`.

## File Changes

| File | Action | Description |
|---|---|---|
| `publishing/domain/SocialContentModels.kt` | Modify | Add account identity to actor/evidence context, retention-policy configuration, opaque cursor codec, and bounded-sync status/continuation types. |
| `publishing/domain/SocialContentPorts.kt` | Modify | Add access-gate and atomic batch-persistence contracts; keep provider contracts read-only at the application boundary. |
| `publishing/application/SocialContentApplicationHandlers.kt` | Modify | Implement Mediator handler interfaces for sync and post detail; retain workspace derivation and typed isolation failures. |
| `publishing/application/{SocialContentAccessGate,SocialContentSyncHandler,SocialContentCalendarQueryHandler}.kt` | Modify/Create | Reuse one evidence policy, persist continuation checkpoints, and map read-only calendar/detail models. |
| `publishing/infrastructure/socialcontent/SocialContentConfiguration.kt` | Modify | Bind properties, gate, fakes/test profile, R2DBC repositories, batch writer, and adapter conditionally; no external operation is enabled by default. |
| `publishing/infrastructure/socialcontent/*Repository.kt` | Create | Workspace-qualified R2DBC actor, post, reader, checkpoint, evidence, and transactional batch adapters over migration 016. |
| `publishing/infrastructure/linkedin/LinkedInCommunityManagementAdapter.kt` | Modify | Inject the gate, validate organization actors, use configured API version, preserve opaque cursor translation, and fail closed for writes. |
| `publishing/infrastructure/http/PublishingControllers.kt` | Modify | Add explicit v1 mappings and request/cursor validation without changing personal routes. |
| `server/smp/src/test/resources/features/{social-content-sync,community-inbox}.feature` | Create | Tagged `@social-content @smoke @fast` scenarios for headers, isolation, paging, retry/checkpoint, tombstones, gates, safe defaults, and read-only behavior. |
| `server/smp/src/test/kotlin/.../bdd/{SocialContentBddSteps,PublishingBddSteps}.kt` | Modify/Create | Wire deterministic provider/evidence fakes and WebTestClient assertions. |

## Interfaces / Contracts

```kotlin
interface SocialContentAccessGate {
    suspend fun authorize(scope: WorkspaceScope, actorId: String, operation: CapabilityOperation)
}

interface SocialContentBatchWriter {
    suspend fun persist(
        posts: Collection<SocialPost>,
        tombstoneIds: Set<ExternalPostId>,
        checkpoint: SyncCheckpoint,
    )
}
```

The HTTP contract remains the specified v1 endpoints and headers; responses contain no credentials and use existing problem-details mappings.

## Testing Strategy

| Layer | What to Test | Approach |
|---|---|---|
| Unit | Gate matrix, cursor validation, overlap deduplication, retry timing, checkpoint ordering, tombstones, `mutationAllowed` | JUnit/Kotlin coroutine tests with fakes. |
| Integration | Mediator registration, WebFlux headers/versioning/security, R2DBC workspace filters and atomic batch writes | Spring/WebFlux tests and database fixtures. |
| E2E/BDD | Every active Given/When/Then scenario, including personal OAuth/publisher regressions | Tagged Cucumber fast suite with `BddDatabaseSupport` and zero-call provider assertions. |

## Migration / Rollout

No migration required: use `publishing-016`. Keep all social-content flags false in every environment. Rollback removes the new bindings/configuration and leaves personal publishing unchanged; no schema rollback is needed.

## Open Questions

- [ ] Confirm the production allowlist/source for supported LinkedIn API versions before enabling Page reads; this does not block safe-off implementation.
