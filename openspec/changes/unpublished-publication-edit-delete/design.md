# Design: Unpublished Publication Edit/Delete

## Technical Approach

Wire frontend `deletePost()` and `updatePost()` in `publishing.ts` to the existing `PATCH /api/publishing/publications/{id}` and the new `DELETE /api/publishing/publications/{id}` endpoint. Add backend infrastructure (command, handler, repository method, policy gate, HTTP endpoint) consistent with the existing `CancelPublicationHandler` pattern. Use `Command` (no result) for delete since the caller discards the return value.

## Architecture Decisions

### Decision: Delete command uses `Command` (no result), not `CommandWithResult`

**Choice**: `DeletePublicationCommand : Command` — handler returns `Unit`
**Alternatives**: `CommandWithResult<Unit>` — requires a result wrapper even though the controller discards it
**Rationale**: The controller at line 160 has `mediator: Mediator` (no type param); it cannot call `mediator.send()` with a `CommandWithResult`. All existing void-style commands (cancel, retry) use `CommandWithResult<PublicationResult>` because they return the updated state. Delete returns nothing, so plain `Command` is cleaner and matches the existing `CommandHandler` pattern used by `delivery_attempts`.

### Decision: Cascade deletes handled in `R2dbcPublicationRepository`, not as a new handler

**Choice**: Add `deleteById(workspaceId, publicationId)` to `PublicationRepository`; implement with sequential explicit DELETEs
**Alternatives**: New `PublicationCascadeRepository`, transactional scope in `DeletePublicationHandler`, or migration adding `onDelete CASCADE`
**Rationale**: Explicit deletes are safer than relying on FK cascade defaults (Liquibase defines NO ACTION); the same adapter class already handles `replaceAssetLinks()` with manual DELETE, so the pattern is established. Adding it to `R2dbcPublicationRepository` keeps all publication persistence logic in one place.

### Decision: Job cancellation uses `PublicationJobRepository.replaceForPublication` with empty job, not a new `cancelJobForPublication` method

**Choice**: Call `publicationJobRepository.replaceForPublication(emptyJob)` (same as `EditPublicationHandler` and `ReschedulePublicationHandler`)
**Alternatives**: New `cancelJobForPublication(publicationId)` on `PublicationRepository`
**Rationale**: The existing `replaceForPublication` does `DELETE + INSERT` — same as `editPost()`. This matches the "replace job" pattern already used. The existing `R2dbcPublicationJobRepository.cancel()` has a pre-existing bug (line 711 uses `jobId` param for `publicationId` column), so avoiding it prevents replicating that bug.

### Decision: Optimistic UI in frontend with local-state-first approach

**Choice**: Remove from local state immediately, rollback on API error
**Alternatives**: Wait for API success before removing from state, disable button during request
**Rationale**: Matches the existing UX where posts disappear instantly on delete/cancel. The store already does this for local-only deletes. API errors revert by re-fetching from backend.

## Data Flow

```
Frontend (delete)
  └── deletePost(id)               [publishing.ts:757]
       ├── optimistic remove from publications.value + revoke blob URL
       ├── POST /api/publishing/publications/{id} DELETE   [fetchCalendar() restores on error]
       └── fetchCalendar()          [on error: re-hydrate local state]

Frontend (update/save)
  └── updatePost(id, updates)      [publishing.ts:776]
       ├── optimistic merge into publications.value
       ├── PATCH /api/publishing/publications/{id}   [existing endpoint]
       └── on error: restore original + throw

Backend (DELETE path)
  DELETE /api/publishing/publications/{id}
       └── PublishingPublicationController.deletePublication()
            └── mediator.send(DeletePublicationCommand(id))
                 └── DeletePublicationHandler
                      ├── requireWorkspaceContext() → workspaceId
                      ├── publicationRepository.findByWorkspaceAndId(ws, id)
                      │    ├── throw PublicationNotFoundException (→ 404)
                      │    └── PublicationLifecyclePolicy.requireDeletable(draft)
                      │         ├── throw PublicationDeletionNotAllowedException (→ 409)
                      │         └── return Unit
                      ├── publicationJobRepository.replaceForPublication(empty-job)
                      │    └── DELETE FROM publication_jobs WHERE publication_id = :id
                      └── publicationRepository.deleteById(ws, id)
                           ├── DELETE FROM delivery_attempts WHERE publication_id = :id
                           ├── DELETE FROM publication_jobs WHERE publication_id = :id
                           ├── DELETE FROM publication_asset_links WHERE publication_id = :id
                           └── DELETE FROM publications WHERE workspace_id = :ws AND id = :id
```

## File Changes

| File | Action | Description |
|------|--------|-------------|
| `server/smp/src/main/kotlin/…/publishing/domain/PublishingPolicies.kt` | Modify | Add `PublicationDeletionNotAllowedException` + `requireDeletable()` |
| `server/smp/src/main/kotlin/…/publishing/domain/PublishingRepositories.kt` | Modify | Add `deleteById(ws, id)` to `PublicationRepository` |
| `server/smp/src/main/kotlin/…/publishing/application/PublishingApi.kt` | Modify | Add `DeletePublicationCommand : Command` |
| `server/smp/src/main/kotlin/…/publishing/application/PublishingHandlers.kt` | Modify | Add `DeletePublicationHandler` + `PublicationNotFoundException` already exists |
| `server/smp/src/main/kotlin/…/publishing/infrastructure/persistence/R2dbcPublishingRepositories.kt` | Modify | Implement `deleteById()` with cascade deletes |
| `server/smp/src/main/kotlin/…/publishing/infrastructure/http/PublishingControllers.kt` | Modify | Add `DELETE /{publicationId}` endpoint |
| `server/smp/src/main/kotlin/…/publishing/infrastructure/http/PublishingProblemDetailsHandler.kt` | Modify | Add handlers for `PublicationDeletionNotAllowedException` (409) and `PublicationNotFoundException` (404) |
| `apps/web/app/src/stores/publishing.ts` | Modify | `deletePost()` calls API; `updatePost()` calls PATCH |
| `server/smp/src/test/kotlin/…/domain/PublicationLifecyclePolicyTest.kt` | Modify | Add tests for `requireDeletable()` |
| `server/smp/src/test/kotlin/…/application/PublishingHandlersTest.kt` | Modify | Add tests: success, not-found, deletion-not-allowed |
| `server/smp/src/test/kotlin/…/infrastructure/http/PublishingControllersTest.kt` | Modify | Add test for `DELETE /{publicationId}` dispatching `DeletePublicationCommand` |
| `apps/web/app/src/stores/publishing.test.ts` | Create | Add tests: delete calls API, rollback on error; update calls PATCH |

## Interfaces / Contracts

### Domain

```kotlin
// PublishingPolicies.kt — add after PublicationCancellationNotAllowedException
class PublicationDeletionNotAllowedException(
    publicationId: String,
    currentStatus: PublicationStatus,
) : PublicationStateTransitionException(
    "Publication '$publicationId' cannot be deleted in status $currentStatus. " +
        "Only DRAFT, QUEUED, and SCHEDULED publications may be deleted."
)

fun PublicationLifecyclePolicy.requireDeletable(draft: PublicationDraft) {
    val deletableStatuses = setOf(
        PublicationStatus.DRAFT,
        PublicationStatus.QUEUED,
        PublicationStatus.SCHEDULED,
    )
    if (draft.status !in deletableStatuses) {
        throw PublicationDeletionNotAllowedException(draft.id, draft.status)
    }
}
```

```kotlin
// PublishingRepositories.kt — add to PublicationRepository interface
suspend fun deleteById(workspaceId: String, publicationId: String)
```

### Application

```kotlin
// PublishingApi.kt — add after CancelPublicationCommand
data class DeletePublicationCommand(
    val publicationId: String,
) : Command  // from com.profiletailors.common.domain.bus.command.Command
```

```kotlin
// PublishingHandlers.kt — add new handler
@Service
internal class DeletePublicationHandler(
    private val principalContextProvider: PrincipalContextProvider,
    private val resourceContextProvider: ResourceContextProvider,
    private val publicationRepository: PublicationRepository,
    private val publicationJobRepository: PublicationJobRepository,
    private val clock: Clock,
    private val principalIdentityLookup: PrincipalIdentityLookup = NoOpPrincipalIdentityLookup(),
    private val emailVerificationPolicy: EmailVerificationPolicy = permissiveEmailVerificationPolicy,
) : CommandHandler<DeletePublicationCommand> {
    override suspend fun handle(command: DeletePublicationCommand) {
        val principalCtx = principalContextProvider.require()
        requireEmailVerification(principalCtx, principalIdentityLookup, emailVerificationPolicy, AuthFeature.PUBLISH_CONTENT)
        val workspaceId = requireNotNull(resourceContextProvider.requireWorkspaceContext().workspaceId)
        val draft = publicationRepository.findByWorkspaceAndId(workspaceId, command.publicationId)
            ?: throw PublicationNotFoundException(command.publicationId)
        PublicationLifecyclePolicy.requireDeletable(draft)
        // Cancel and remove any pending job for this publication
        publicationJobRepository.replaceForPublication(
            PublicationJob(
                id = "pjob-${UUID.randomUUID()}",
                publicationId = draft.id,
                workspaceId = draft.workspaceId,
                status = com.profiletailors.smp.publishing.domain.JobStatus.CANCELLED,
                dueAt = clock.instant(),
                priorityRank = 0,
                attemptCount = 0,
                maxAttempts = 1,
            ),
        )
        publicationRepository.deleteById(workspaceId, command.publicationId)
    }
}
```

### Infrastructure — Persistence

```kotlin
// R2dbcPublishingRepositories.kt — add to R2dbcPublicationRepository
override suspend fun deleteById(workspaceId: String, publicationId: String) {
    // Delete delivery_attempts first (FK to both publications and publication_jobs)
    databaseClient.sql("DELETE FROM delivery_attempts WHERE publication_id = :id")
        .bind("id", publicationId)
        .fetch().rowsUpdated().awaitSingleOrNull() ?: 0

    // Delete publication_jobs (FK to publications)
    databaseClient.sql("DELETE FROM publication_jobs WHERE publication_id = :id")
        .bind("id", publicationId)
        .fetch().rowsUpdated().awaitSingleOrNull() ?: 0

    // Delete publication_asset_links (FK to publications)
    databaseClient.sql("DELETE FROM publication_asset_links WHERE publication_id = :id")
        .bind("id", publicationId)
        .fetch().rowsUpdated().awaitSingleOrNull() ?: 0

    // Delete the publication itself (workspace-scoped)
    databaseClient.sql("DELETE FROM publications WHERE workspace_id = :ws AND id = :id")
        .bind("ws", workspaceId)
        .bind("id", publicationId)
        .fetch().rowsUpdated().awaitSingle()
}
```

> **Migration note**: Liquibase FK definitions in `publishing/005` and `publishing/006` use `references: publications(id)` with no `onDelete` clause, so PostgreSQL defaults to `NO ACTION`. The explicit DELETEs above are required. A future migration can add `onDelete: CASCADE` to make FKs self-cleaning.

### Infrastructure — HTTP

```kotlin
// PublishingControllers.kt — add to PublishingPublicationController
@DeleteMapping("/{publicationId}", version = "1")
suspend fun deletePublication(@PathVariable publicationId: String): ResponseEntity<Unit> {
    commandHandler.handle(DeletePublicationCommand(publicationId))
    return ResponseEntity.noContent().build()
}
```

```kotlin
// PublishingProblemDetailsHandler.kt — add two new handlers
@ExceptionHandler(PublicationNotFoundException::class)
fun handle(exception: PublicationNotFoundException): ProblemDetail =
    ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.message ?: "Publication not found").apply {
        title = "Publication not found"
    }

@ExceptionHandler(PublicationDeletionNotAllowedException::class)
fun handle(exception: PublicationDeletionNotAllowedException): ProblemDetail =
    ProblemDetail.forStatusAndDetail(
        HttpStatus.CONFLICT,
        exception.message ?: "Publication cannot be deleted in current status",
    ).apply {
        title = "Publication deletion not allowed"
        setProperty("errorCode", "DELETION_NOT_ALLOWED")
        setProperty("publicationId", exception.publicationId)
        setProperty("currentStatus", exception.currentStatus.name)
    }
```

### Frontend — Store

```typescript
// publishing.ts — deletePost() → async with API call
async function deletePost(id: string) {
  const post = publications.value.find(p => p.id === id)
  if (!post) return
  const url = objectUrls.get(id)
  if (url) { URL.revokeObjectURL(url); objectUrls.delete(id) }
  publications.value = publications.value.filter(p => p.id !== id)
  saveToStorage()
  if (!auth.isAuthenticated) return
  try {
    await auth.apiFetch(`/api/publishing/publications/${id}`, {
      method: 'DELETE',
      workspaceScoped: true,
    })
  } catch (err) {
    await fetchCalendar()
    throw err
  }
}

// publishing.ts — updatePost() → async with API call
async function updatePost(id: string, updates: Partial<Publication>) {
  const idx = publications.value.findIndex(p => p.id === id)
  if (idx === -1) return
  const original = { ...publications.value[idx] }
  // Optimistic merge
  if (updates.thumbnail && original.thumbnail && objectUrls.has(id)) {
    URL.revokeObjectURL(objectUrls.get(id)!)
    objectUrls.delete(id)
  }
  if (typeof updates.thumbnail === 'string' && updates.thumbnail.startsWith('blob:')) {
    objectUrls.set(id, updates.thumbnail)
  }
  Object.assign(publications.value[idx], updates)
  saveToStorage()
  if (!auth.isAuthenticated) return
  try {
    const result = await auth.apiFetch<PublicationResult>(
      `/api/publishing/publications/${id}`,
      { method: 'PATCH', body: JSON.stringify(toBackendFormat(updates)), workspaceScoped: true },
    )
    // Merge server-authoritative fields back (id, status, scheduledFor, etc.)
    publications.value[idx] = { ...publications.value[idx], ...fromBackendFormat(result) }
    saveToStorage()
  } catch (err) {
    publications.value[idx] = original
    saveToStorage()
    throw err
  }
}
```

## Testing Strategy

| Layer | What | How |
|-------|------|-----|
| Domain | `requireDeletable()` throws for PROCESSING, PUBLISHED, BLOCKED, FAILED, CANCELLED; allows DRAFT, QUEUED, SCHEDULED | JUnit 5 `@Test` in `PublicationLifecyclePolicyTest.kt`, mirror pattern of `requireEditable()` tests |
| Handler | success (deletes), not-found (404), deletion-not-allowed (throws), email verification gate | JUnit 5 `@Test` in `PublishingHandlersTest.kt`, use existing `InMemoryPublicationRepository` and `InMemoryPublicationJobRepository` test doubles |
| Controller | `DELETE /{id}` dispatches `DeletePublicationCommand` | JUnit 5 `@Test` in `PublishingControllersTest.kt` with `CapturingMediator` |
| Handler | optimistic revert on API failure | Vitest `@testing-library/vue` or store unit test in `publishing.test.ts` |
| Handler | PATCH persists updated state on success | Vitest test in `publishing.test.ts` |

## Migration / Rollback

**No DB migration required.** The change adds only application-layer deletes (no schema changes). The `delivery_attempts` → `publication_jobs` → `publication_asset_links` → `publications` delete order handles all child rows. If a future migration adds `onDelete: CASCADE` to the FK definitions, the explicit deletes become redundant but harmless.

**Rollback**: Revert all file changes in one PR. Frontend reverts to local-only delete/update (the bug state, but non-breaking since the endpoint simply won't exist).

## Open Questions

- [ ] **`delivery_attempts` FK**: `delivery_attempts` has a direct FK to `publications` (`fk_delivery_attempts_publication`) AND to `publication_jobs`. Both lack `onDelete: CASCADE`. The `deleteById` implementation above deletes `delivery_attempts` first by `publication_id`. Confirm this is sufficient or if job ID must also be matched.
- [ ] **Feature gate for delete endpoint**: Should delete require a separate `AuthFeature.DELETE_PUBLICATION` or re-use `AuthFeature.PUBLISH_CONTENT` (same as cancel)? Recommend `PUBLISH_CONTENT` for consistency.
- [ ] **Frontend save UX**: The `updatePost()` PATCH returns a `PublicationResult`. The frontend `Publication` model fields (`content`, `title`, `channels`, etc.) differ from backend DTO fields (`bodyText`, `socialAccountId`, etc.). The `toBackendFormat()` / `fromBackendFormat()` helpers need to be implemented — verify the existing `schedulePost()` or `quickCreatePost()` has mapping helpers or inline conversion.
