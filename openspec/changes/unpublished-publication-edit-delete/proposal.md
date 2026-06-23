# Proposal: Unpublished Publication Edit/Delete

## Intent
Fix a broken publication-management flow in the scheduler. Today `apps/web/app/src/stores/publishing.ts` deletes and updates only local state, so removed or edited unpublished posts are not persisted and reappear after refresh. This change makes edit/delete real workspace-scoped publishing operations, enforced by backend lifecycle rules.

## Scope

### In Scope
- Wire frontend delete to `DELETE /api/publishing/publications/{publicationId}`
- Wire frontend edit/save to existing `PATCH /api/publishing/publications/{publicationId}`
- Add backend delete command, handler, repository method, policy gate, and HTTP endpoint
- Hard-delete related `publication_jobs` and `publication_asset_links`

### Out of Scope
- Multi-network fanout
- Soft delete or archival restore
- Any edits/deletes for `PROCESSING`, `PUBLISHED`, `BLOCKED`, `FAILED`, `CANCELLED`
- Changes to cancel/retry/reschedule behavior

## Capabilities

### New Capabilities
- None

### Modified Capabilities
- `publishing`: extend pre-delivery publication lifecycle to explicitly support deletion for unpublished publications

## Approach
- **Domain**: add `requireDeletable()` to `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/domain/PublishingPolicies.kt`. Allowed statuses: `DRAFT`, `QUEUED`, `SCHEDULED` only.
- **Application**: add `DeletePublicationCommand` in `PublishingApi.kt` and `DeletePublicationHandler` in `PublishingHandlers.kt`.
- **Repository**: add `deleteById(workspaceId, publicationId)` to `PublishingRepositories.kt`; implement in `R2dbcPublishingRepositories.kt` with child-row cleanup before parent delete if FK cascades are absent.
- **HTTP**: add `DELETE /api/publishing/publications/{publicationId}` in `PublishingControllers.kt`; success `204`, not found `404`, invalid status `409`.
- **Frontend**: in `apps/web/app/src/stores/publishing.ts`, call backend `DELETE` from `deletePost()` and existing `PATCH` from `updatePost()`; wire save UI in `apps/web/app/src/components/PostDetailModal.vue`.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `server/smp/.../PublishingPolicies.kt` | Modified | Add delete rule |
| `server/smp/.../PublishingApi.kt` | Modified | Add delete command |
| `server/smp/.../PublishingHandlers.kt` | Modified | Add delete handler |
| `server/smp/.../PublishingRepositories.kt` | Modified | Add delete contract |
| `server/smp/.../R2dbcPublishingRepositories.kt` | Modified | Hard delete + cascade cleanup |
| `server/smp/.../PublishingControllers.kt` | Modified | Add DELETE endpoint |
| `apps/web/app/src/stores/publishing.ts` | Modified | Persist delete/edit |
| `apps/web/app/src/components/PostDetailModal.vue` | Modified | Wire save action |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Missing FK cascades | Med | Inspect migrations; delete child rows explicitly |
| Job/publication delete race | Low | Cancel/delete in one handler flow |
| Existing job cancel bug nearby | Low | Avoid reusing wrong parameter pattern |

## Rollback Plan
Revert backend endpoint/handler/repository/policy changes and frontend store/UI wiring in one PR revert. No schema rollback is expected.

## Dependencies
- Confirm FK behavior in `server/smp/src/main/resources/db/migration/`

## Success Criteria
- [ ] Delete succeeds only for `DRAFT`, `QUEUED`, `SCHEDULED`
- [ ] Delete is rejected for `PROCESSING`, `PUBLISHED`, `BLOCKED`, `FAILED`, `CANCELLED`
- [ ] Edit/save uses existing PATCH endpoint
- [ ] Child rows are removed with the publication
- [ ] Scheduler no longer resurrects deleted unpublished posts after refresh

## Effort Estimate
Medium: ~1 day (backend 60%, frontend 30%, schema/risk verification 10%)
