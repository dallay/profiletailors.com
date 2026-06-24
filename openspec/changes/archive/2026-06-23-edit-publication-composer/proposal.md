# Proposal: Edit Publication via Composer

## Context

Currently, `PostDetailModal` mixes the "view" and "edit" experience in one modal with inline editing (title input, textarea, datetime input). This is a limited UX: it doesn't support media attachment editing, doesn't show a social preview, and forces the user to work in a layout designed for reading.

## Goal

Open the full `CreatePostModal` (the rich composer) when the user clicks "Edit" from `PostDetailModal`, with the publication data pre-filled.

## Scope

### In Scope
- Edit content, scheduling, priority, and media via the full composer
- Pre-fill all fields correctly (including media, after DTO gap is closed)
- Update store and refresh calendar after save
- Channel read-only in edit mode (as per backend contract)
- Backend: add `assetIds` to `CalendarPublicationResult` and mapper
- Frontend: add `assetIds` to `Publication` type and mutation mapper
- All unit and E2E tests

### Out of Scope
- Changing the channel (backend doesn't support it)
- Editing the title field (no UI for it currently in composer)
- Unlinking/removing individual assets (add/change supported, remove via clear)

## Technical Approach

1. **Prerequisite**: Close the `assetIds` data contract gap (backend DTO + frontend store)
2. **Extend `CreatePostModal`**: Add edit mode with `editingPublication` prop
3. **Simplify `PostDetailModal`**: Remove inline edit, add "Edit" button
4. **Wire `SchedulerView`**: Orchestrate the edit flow

## Files to Change

### Prerequisite (data contract)
- `server/smp/src/main/kotlin/.../PublishingApi.kt`
- `server/smp/src/main/kotlin/.../PublishingHandlers.kt`
- `server/smp/src/test/kotlin/.../PublishingApiTest.kt`
- `apps/web/app/src/stores/publishing.ts`

### Feature implementation
- `apps/web/app/src/components/CreatePostModal.vue`
- `apps/web/app/src/components/PostDetailModal.vue`
- `apps/web/app/src/views/SchedulerView.vue`
- `locales/*.json`
