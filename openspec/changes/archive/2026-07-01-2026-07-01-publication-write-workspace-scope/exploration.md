## Exploration: Publication write workspace scope

### Current State

`R2dbcPublicationRepository.insertOrUpdate()` first runs
`UPDATE publications SET ... WHERE id = :id`, then falls back to `INSERT` if zero rows were updated.
The update payload explicitly writes `workspace_id = :workspaceId`, so the write path can change
tenancy metadata on an existing row. Reads and delete flow are workspace-scoped (
`findByWorkspaceAndId`, `findInDateRange`, `deleteUnpublished` all include `workspace_id`), but this
update path is not.

The reported issue needs one correction: the schema does **not** allow the same publication ID to
exist in multiple workspaces at the same time, because `publications.id` is the table primary key.
So the exact failure mode is **not** “two workspace rows share the same publication id and one gets
updated by mistake.” The real risk is that any internal caller that sends an existing publication
`id` with a mismatched `workspaceId` can silently reassign that single row to another workspace and
overwrite its content.

In the current public application flow, exploitability looks limited:

- `CreatePublicationHandler` generates fresh IDs with `pub-${UUID.randomUUID()}`, so normal creation
  cannot collide with an existing row.
- `EditPublicationHandler` first loads the publication with
  `findByWorkspaceAndId(workspaceId, publicationId)` and then copies that same aggregate forward, so
  the normal edit path preserves the original workspace.
- Because IDs are globally unique, the insert fallback does not create a second same-id row in
  another workspace.

So the tenancy bug is real at the repository boundary, but the issue statement overstates the “same
ID across workspaces” mechanism.

### Affected Areas

-
`server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/persistence/R2dbcPublishingRepositories.kt` —
`insertOrUpdate()` updates `publications` by `id` only, and `replaceAssetLinks()` depends on the
same publication identity.
- `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/application/PublishingHandlers.kt` —
  normal create/edit flows show current practical exposure and why accidental misuse is limited
  today.
- `server/smp/src/main/resources/db/changelog/publishing/004-create-publications.yaml` — confirms
  `publications.id` is the primary key and `workspace_id` is not part of the key.
- `server/smp/src/main/resources/db/changelog/publishing/005-create-publication-asset-links.yaml` —
  asset links hang off `publication_id`, so a wrongly reassigned publication carries its linked
  assets with it.
- `server/smp/src/main/resources/db/changelog/publishing/006-create-publication-jobs.yaml` — jobs
  reference `publication_id` and also store `workspace_id`, which can drift from the publication row
  if tenancy is rewritten incorrectly.
-
`server/smp/src/test/kotlin/com/profiletailors/smp/publishing/integration/PublishingWorkspaceIsolationIntegrationTest.kt` —
current tests verify read isolation only, not write-path tenancy safety.
-
`server/smp/src/test/kotlin/com/profiletailors/smp/publishing/infrastructure/persistence/R2dbcPublishingRepositoriesUnitTest.kt` —
existing repository tests cover happy-path updates but miss cross-workspace regression coverage.

### Approaches

1. **Scope the update by both `workspace_id` and `id`** — Change the repository update predicate to
   match the repository’s established tenancy contract.
    - Pros: Smallest safe fix; aligns writes with reads/deletes; preserves insert fallback semantics
      for new IDs.
    - Cons: Does not address other repository methods that also update by `id` only.
    - Effort: Low

2. **Redesign publication identity around composite tenancy keys** — Make workspace part of the
   relational identity and foreign keys.
    - Pros: Stronger structural tenancy guarantees.
    - Cons: Much larger schema/API migration; unnecessary for this issue; touches jobs, links, and
      downstream references.
    - Effort: High

### Recommendation

Use approach 1. Fix `insertOrUpdate()` so the update path matches on both `workspace_id` and `id`,
and add regression coverage that proves a save from one workspace cannot mutate a publication
belonging to another workspace. Also document the important nuance: the bug is a repository-level
tenancy drift risk, not a realistic “duplicate publication IDs across workspaces” scenario under the
current schema.

### Risks

- Existing tests can give a false sense of safety because they only prove workspace-scoped reads,
  not workspace-scoped writes.
- `publication_jobs` and `publication_asset_links` depend on `publication_id`; if a publication row
  is ever reassigned across workspaces, related data can become semantically inconsistent even
  without duplicate rows.
- Other publishing write methods (`markPublished`, `markFailed`, `markCancelled`, `markBlocked`, and
  `replaceForPublication`) also rely on `id`/`publication_id` without workspace predicates, though
  the strongest verified gap for this issue is `insertOrUpdate()`.

### Ready for Proposal

Yes — the orchestrator should treat issue #224 as a targeted tenancy-hardening change, but describe
the risk precisely: the real concern is unintended cross-workspace reassignment or overwrite of a
globally unique publication row, not coexistence of identical publication IDs in different
workspaces.
