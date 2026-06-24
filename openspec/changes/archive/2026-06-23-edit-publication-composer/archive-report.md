# Archive Report: Edit Publication via Composer

**Change**: `edit-publication-composer`
**Archived**: 2026-06-23
**Mode**: openspec
**Status at archive**: PASS WITH WARNINGS

---

## Change Summary

Full composer edit flow for unpublished publications. The user can now edit content, scheduling,
priority, and media assets through the rich composer (`CreatePostModal`) instead of inline editing
in `PostDetailModal`. Channel is read-only in edit mode. Calendar refreshes after save.

---

## Spec Delta Merged

| Field | Value |
|-------|-------|
| Delta spec | `openspec/changes/edit-publication-composer/spec.md` |
| Main spec | `openspec/specs/publishing/spec.md` |
| Merge action | Added new `### Requirement: Composer-Based Publication Editing` section with 4 scenarios |

**Merged scenarios:**
1. User edits a scheduled publication from the scheduler (pre-fill content, schedule, priority, media, read-only channel)
2. Published publications remain read-only in the scheduler
3. Saving composer edits uses backend response and refreshes the scheduler
4. Edit mode keeps channel locked and hides create-only controls

---

## Verification Summary

| Metric | Result |
|--------|--------|
| Tasks total | 23 |
| Tasks complete | 23 |
| Verification verdict | PASS WITH WARNINGS |
| CRITICAL issues | 0 |
| Frontend tests (Vitest) | 584/584 passed |
| Backend tests | passed |
| Lint (Biome) | 0 issues |
| Detekt | 2 pre-existing violations (not introduced by this change) |

---

## Warnings on Record

| Warning | Severity | Notes |
|---------|----------|-------|
| Missing dedicated `CreatePostModal` unit tests for edit-mode branches | WARNING | Covered by `SchedulerView.test.ts` integration tests |
| Pre-existing Detekt violations in `R2dbcPublishingRepositories.kt` | WARNING | `LongMethod` in `insertOrUpdate` (103 lines, max 90), `LargeClass` in `R2dbcPublicationRepository` (506 lines, max 250) — pre-existing, not introduced by this change |
| `./gradlew build` fails due to pre-existing Detekt violations | WARNING | CI gate blocked by pre-existing code quality issues |

---

## Archive Contents

| Artifact | Path |
|----------|------|
| Proposal | `openspec/changes/archive/2026-06-23-edit-publication-composer/proposal.md` |
| Spec (delta) | `openspec/changes/archive/2026-06-23-edit-publication-composer/spec.md` |
| Design | `openspec/changes/archive/2026-06-23-edit-publication-composer/design.md` |
| Tasks | `openspec/changes/archive/2026-06-23-edit-publication-composer/tasks.md` |
| Verify Report | `openspec/changes/archive/2026-06-23-edit-publication-composer/verify-report.md` |
| State | `openspec/changes/archive/2026-06-23-edit-publication-composer/state.yaml` |

---

## Source of Truth Updated

- `openspec/specs/publishing/spec.md` — new `### Requirement: Composer-Based Publication Editing` section merged from delta spec

---

## Artifacts Generated During Change

### Backend
- `PublishingApi.kt` — `CalendarPublicationResult.assetIds: List<String>`
- `PublishingHandlers.kt` — `toCalendarResult()` forwards `assetIds`
- `PublishingApiTest.kt` — `CalendarPublicationResult.assetIds` test
- `PublishingHandlersTest.kt` — `assetIds` forwarding fixture + assertion

### Frontend
- `publishing.ts` — `Publication.assetIds?: string[]`, `publicationMutationResultToPublication` copies `assetIds`
- `CreatePostModal.vue` — edit mode with `editingPublication` prop, pre-fill, `updatePost` branch
- `PostDetailModal.vue` — removed inline edit, added "Edit" button with `emit('edit')`
- `SchedulerView.vue` — `handleEditPublication`, `handleUpdated`, calendar refresh
- `locales/*.json` — `composer.editTitle`, `composer.saveChanges`, `postDetail.edit`
- `CreatePostModal.test.ts`, `PostDetailModal.test.ts`, `SchedulerView.test.ts`

---

## SDD Cycle Complete

This change has been fully planned, implemented, verified, and archived.
Ready for the next change.
