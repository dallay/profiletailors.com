# Proposal: Unpublished Publication Edit/Delete

## Intent

Close the mismatch between scheduler UI and publishing backend for unpublished posts. Users must be able to truly delete unpublished publications and edit them through the existing backend contract instead of seeing local-only success that disappears from server truth.

## Scope

### In Scope
- Add backend delete support for unpublished publications in `DRAFT`, `QUEUED`, or `SCHEDULED`.
- Wire frontend delete and edit flows to backend APIs, with edit UI available only for editable statuses.
- Add backend and frontend automated coverage for delete/edit behavior and status gating.

### Out of Scope
- SQL-level hardening inside `updateEditableDraft()` unless design proves a cheap, low-risk path.
- Changes to published/publication-processing behavior, audit policy, or broader composer redesign.

## Capabilities

### New Capabilities
- None

### Modified Capabilities
- `publishing`: extend pre-delivery lifecycle behavior to support true deletion of unpublished publications and backend-backed editing from the scheduler UI.

## Approach

Add a dedicated backend delete command, repository path, and HTTP endpoint that enforce the same editable-state policy already used for edit/cancel flows and clean up related queued job state. In the SPA, replace local-only `deletePost()` and `updatePost()` behavior with API-backed actions plus rollback/error handling, and expose edit actions only when publication status is `DRAFT`, `QUEUED`, or `SCHEDULED`.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `server/smp/.../publishing/application/PublishingApi.kt` | Modified | Add delete command/result contract |
| `server/smp/.../publishing/application/PublishingHandlers.kt` | Modified | Implement delete workflow and reuse lifecycle checks |
| `server/smp/.../publishing/domain/PublishingRepositories.kt` | Modified | Add publication delete persistence contract |
| `server/smp/.../publishing/infrastructure/http/PublishingControllers.kt` | Modified | Expose delete endpoint |
| `server/smp/.../publishing/infrastructure/persistence/R2dbcPublishingRepositories.kt` | Modified | Persist delete and related cleanup |
| `apps/web/app/src/stores/publishing.ts` | Modified | Replace local-only edit/delete with backend integration |
| `apps/web/app/src/components/PostDetailModal.vue` | Modified | Add edit affordance and correct status gating |
| `apps/web/app/src/views/SchedulerView.vue` | Modified | Route card actions through integrated store flow |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Delete leaves orphaned jobs/links | Med | Define cleanup path and cover with repository/handler tests |
| Mixed local/backend behavior remains | Med | Centralize store actions and update all delete entry points |
| Edit UI scope grows into composer rewrite | Low | Keep edits inside existing modal/store contract |

## Rollback Plan

Revert the new delete endpoint/store wiring and restore current UI actions to read-only delete/edit disablement while keeping existing reschedule behavior intact.

## Dependencies

- Existing `PATCH /api/publishing/publications/{publicationId}` contract
- Publishing lifecycle policy for editable statuses

## Success Criteria

- [ ] Unpublished delete removes the publication through backend-backed flow and no longer reports false local success.
- [ ] Scheduler edit updates unpublished publications through the existing PATCH endpoint only for `DRAFT`/`QUEUED`/`SCHEDULED`.
- [ ] Backend and frontend tests cover allowed/rejected edit/delete paths.