# Proposal: Publication Write Workspace Scope

## Intent

Fix a verified tenancy gap in `R2dbcPublicationRepository`: `insertOrUpdate()` updates
`publications` by `id` only, while the repository contract is workspace-scoped everywhere else. A
draft save for one workspace can therefore mutate an existing row from another workspace before the
insert fallback runs.

## Scope

### In Scope

- Scope publication update writes by both `workspace_id` and `id`.
- Add regression coverage proving cross-workspace rows are not mutated by editable-draft saves.
- Verify unchanged behavior for same-workspace create and update flows.

### Out of Scope

- Broader publishing lifecycle changes or scheduler UX work.
- Schema redesign, ID strategy changes, or multi-tenant hardening outside publication writes.

## Capabilities

### New Capabilities

- None

### Modified Capabilities

- `publishing`: tighten pre-delivery publication persistence so workspace-scoped write operations
  MUST honor workspace tenancy boundaries.

## Approach

Update the repository write path so the `UPDATE publications ...` clause matches the established
workspace-scoped contract already used by reads and deletes. Add a repository/integration regression
test that seeds publications in different workspaces and proves an editable-draft save only affects
the matching workspace row.

## Affected Areas

| Area                                                                                                                             | Impact   | Description                                            |
|----------------------------------------------------------------------------------------------------------------------------------|----------|--------------------------------------------------------|
| `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/persistence/R2dbcPublishingRepositories.kt`         | Modified | Scope publication update writes by workspace           |
| `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/infrastructure/persistence/R2dbcPublishingRepositoriesUnitTest.kt` | Modified | Add repository regression coverage                     |
| `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/integration/PublishingWorkspaceIsolationIntegrationTest.kt`        | Modified | Verify tenancy behavior end-to-end                     |
| `openspec/specs/publishing/spec.md`                                                                                              | Modified | Clarify workspace-scoped publication write requirement |

## Risks

| Risk                                            | Likelihood | Mitigation                                                          |
|-------------------------------------------------|------------|---------------------------------------------------------------------|
| Existing tests miss tenancy regressions         | Med        | Add explicit cross-workspace update test                            |
| Fix changes create-vs-update fallback semantics | Low        | Keep insert fallback intact and verify same-workspace create/update |

## Rollback Plan

Revert the repository SQL change and the accompanying regression tests, restoring the previous write
behavior while preserving the investigation artifact.

## Dependencies

- Existing `publishing` spec tenancy expectations
- Publication repository tests with seeded multi-workspace data

## Success Criteria

- [ ] Editable draft saves no longer update a publication outside the caller workspace.
- [ ] Same-workspace draft create/update behavior still passes targeted repository tests.
- [ ] Spec and automated tests document the workspace-scoped write contract.
