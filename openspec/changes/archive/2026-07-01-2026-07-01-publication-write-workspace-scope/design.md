# Design: Publication Write Workspace Scope

## Technical Approach

Tighten the publication write path inside `R2dbcPublicationRepository` so editable draft persistence
remains workspace-scoped end to end. The change keeps the existing update-then-insert strategy, but
the `UPDATE publications` statement will match on both `id` and `workspace_id`. Because publication
IDs are globally unique, this is not about supporting duplicate IDs across workspaces; it prevents a
mismatched workspace write from overwriting or reassigning the single authoritative row. This aligns
write semantics with existing `findByWorkspaceAndId` and `deleteUnpublished` behavior and preserves
same-workspace create/update flows.

## Architecture Decisions

| Decision                            | Options                                                                                                | Choice / Rationale                                                                                                                                                                                                                                                                                      |
|-------------------------------------|--------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Insert vs update discrimination     | 1) `UPDATE ... WHERE id` 2) `UPDATE ... WHERE id AND workspace_id` then `INSERT` 3) DB-native upsert   | **2**. It preserves current repository behavior while enforcing tenancy at the repository boundary. Option 1 causes tenancy drift. Option 3 is unnecessary for this targeted fix and would complicate R2DBC SQL plus asset-link sequencing.                                                             |
| Mismatched workspace on existing id | 1) Silent insert fallback after scoped update misses 2) Explicit existence check by `id` and fail fast | **2**. If `UPDATE` affects 0 rows and a row with the same global `id` exists in another workspace, repository code should raise a workspace-scoping/persistence exception instead of attempting `INSERT` and depending on PK failure. This makes the contract explicit and prevents ambiguous behavior. |
| Schema change                       | 1) Composite key `(workspace_id,id)` 2) Keep current PK on `id`                                        | **2**. Current issue is repository tenancy enforcement, not identity redesign. Composite keys would cascade into jobs, asset links, and handlers with disproportionate scope.                                                                                                                           |

## Data Flow

```text
EditPublicationHandler
  → findByWorkspaceAndId(workspaceId, publicationId)
  → build updated draft from loaded aggregate
  → transactionRunner.runAtomically {
       UPDATE publications
       WHERE id = :id AND workspace_id = :workspaceId
       if 0 rows:
         SELECT workspace_id FROM publications WHERE id = :id
         if found in other workspace → fail
         else INSERT publications
       replaceAssetLinks(publicationId)
       replaceForPublication(publicationId)
     }
```

Normal create still uses a fresh UUID-backed `pub-*` id, so `INSERT` remains the expected path.
Normal edit still loads by workspace first, so same-workspace updates continue to hit the scoped
`UPDATE` path.

## File Changes

| File                                                                                                                             | Action | Description                                                                                                            |
|----------------------------------------------------------------------------------------------------------------------------------|--------|------------------------------------------------------------------------------------------------------------------------|
| `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/persistence/R2dbcPublishingRepositories.kt`         | Modify | Scope publication update SQL by `workspace_id`; add explicit cross-workspace existing-id guard before insert fallback. |
| `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/infrastructure/persistence/R2dbcPublishingRepositoriesUnitTest.kt` | Modify | Add repository regression tests for same-workspace update, new insert, and cross-workspace overwrite prevention.       |
| `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/integration/PublishingWorkspaceIsolationIntegrationTest.kt`        | Modify | Add integration coverage proving one workspace cannot mutate another workspace publication through the update path.    |

## Interfaces / Contracts

```kotlin
interface PublicationRepository {
    suspend fun createDraft(draft: PublicationDraft): PublicationDraft
    suspend fun updateEditableDraft(draft: PublicationDraft): PublicationDraft
}
```

Contract clarification:

- `createDraft` / `updateEditableDraft` MUST treat `(workspaceId, id)` as the write scope.
- If `id` exists under a different workspace, the repository MUST fail rather than overwrite,
  reassign tenancy, or silently create another row.
- `updateEditableDraft` continues delegating to the shared persistence path; safety lives in
  repository SQL/guard logic, not in handlers alone.

## Testing Strategy

| Layer       | What to Test                                                                                                             | Approach                                                                                                                              |
|-------------|--------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------|
| Unit        | Scoped update succeeds for same workspace; new id inserts; existing id in other workspace fails and leaves row unchanged | Extend `R2dbcPublishingRepositoriesUnitTest` with seeded publications across workspaces and row-level assertions.                     |
| Integration | Edit flow cannot drift tenancy when caller workspace differs from stored row                                             | Extend `PublishingWorkspaceIsolationIntegrationTest` or equivalent handler-level flow using real repositories and transaction runner. |
| E2E         | None                                                                                                                     | Not needed; bug is repository-boundary persistence logic.                                                                             |

## Migration / Rollout

No migration required. SQL shape changes only in repository code; schema and IDs remain unchanged.

## Open Questions

- [ ] Should the repository throw a dedicated domain exception for cross-workspace existing-id
  collisions, or reuse the current persistence exception style?
