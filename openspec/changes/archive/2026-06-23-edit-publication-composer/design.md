# Design: Edit Publication via Composer

## Context

Currently, `PostDetailModal` mixes the "view" and "edit" experience in one modal with inline editing (title input, textarea, datetime input). This is a limited UX: it doesn't support media attachment editing, doesn't show a social preview, and forces the user to work in a layout designed for reading.

The goal is to open the full `CreatePostModal` (the rich composer) when the user clicks "Edit" from `PostDetailModal`, with the publication data pre-filled.

---

## Current State

| Component | Role | Edits? |
|-----------|------|--------|
| `PostDetailModal.vue` | View detail + inline edit | Yes, but limited |
| `CreatePostModal.vue` | Rich composer for creation | No |
| `SchedulerView.vue` | Orchestrates both modals | Parent |

Backend `EditPublicationCommand` accepts: `title`, `bodyText`, `assetIds`, `scheduleMode`, `scheduledFor`, `priority`. `socialAccountId` is **not editable** — channel is fixed per the backend contract.

---

## Prerequisite: Close the `assetIds` Data Contract

Before the edit flow can pre-fill media, the `assetIds` field must travel end-to-end from the calendar API through to the frontend `Publication` type. There are two gaps currently.

### Gap 1: Backend — `CalendarPublicationResult` doesn't expose `assetIds`

The domain model (`PublicationDraft`) already holds `assetIds`, and the calendar handler already processes assets to compute `previewUrl`. However, the public DTO doesn't include the field, and the mapper doesn't pass it.

**Files to change:**

| File | Change |
|------|--------|
| `PublishingApi.kt` | Add `assetIds: List<String>` to `CalendarPublicationResult` |
| `PublishingHandlers.kt` | Pass `assetIds = assetIds` in `PublicationDraft.toCalendarResult()` |
| `PublishingApiTest.kt` | Add `assetIds` to the `CalendarPublicationResult` construction |
| `PublishingHandlersTest.kt` | Ensure existing `calendarPublication(..., assetIds = ...)` test fixture covers the field in the result |

> **Evidence:**
> - `PublicationDraft` already has `val assetIds: List<String> = emptyList()` (domain model)
> - Handler already calls `mediaAssetResolver.resolveReadyAssets(...)` and `resolvePreviewUrl(publication, assetsById)` — assets are already resolved, just not forwarded
> - Test fixture `calendarPublication(..., assetIds = listOf("asset-1"))` already exists; the mapper just needs to forward the field

### Gap 2: Frontend — `publicationMutationResultToPublication` doesn't copy `assetIds`

After a successful `updatePost` PATCH, the merged result is mapped back into the store. The mapper currently skips `assetIds`, which would cause the store's `Publication` record to lose media information after an edit save.

**Files to change:**

| File | Change |
|------|--------|
| `stores/publishing.ts` | Add `assetIds: result.assetIds` in `publicationMutationResultToPublication()` |
| `stores/publishing.ts` | Add `assetIds?: string[]` to `Publication` interface |

---

## Design

### 1. Extend `CreatePostModal` with edit mode

Add an optional prop:

```typescript
// CreatePostModal.vue
interface Props {
  isOpen: boolean
  initialDate?: string
  editingPublication?: Publication // NEW: pre-fill for editing
}
```

When `editingPublication` is provided, the modal is in **edit mode**.

#### Computed: edit mode detection

```typescript
const isEditMode = computed(() => !!props.editingPublication)
const isCreating = computed(() => !isEditMode.value)
```

#### Header text

- Create mode: `"composer.title"` (already there)
- Edit mode: new i18n key `"composer.editTitle"` ("Edit Post")

#### Channel selector

In edit mode, the channel selector is rendered **disabled/read-only** with the existing channel pre-selected. No channel switching is possible (backend contract).

```html
<button
  v-for="ch in publishingStore.channels.filter(ch => ch.status === 'ACTIVE')"
  :key="ch.id"
  @click="isEditMode ? undefined : selectChannel(ch.id)"
  :disabled="isEditMode"
  :class="isEditMode ? 'opacity-60 cursor-not-allowed' : '...'"
>
```

#### Pre-filling the form (watch on `isOpen`)

When `isOpen` becomes `true` in edit mode, pre-fill:

1. **`postText`** → `editingPublication.content`
2. **`selectedCalendarDate` + `scheduleTime`** → derived from `editingPublication.scheduledAt`
3. **`scheduleMode`** → derived from `editingPublication.scheduleMode` (`SCHEDULED_AT` → `'custom'`, `NOW` → `'now'`, `NEXT_SLOT` → `'next'`)
4. **`priorityMode`** → `editingPublication.priority`
5. **`selectedChannelId`** → channel from `editingPublication.channels[0]` (read-only)
6. **`mediaStore.selectedAssetIds`** → `editingPublication.assetIds` (requires the backend DTO gap to be closed first)

```typescript
// On open, in edit mode:
if (isEditMode.value && props.editingPublication?.assetIds?.length) {
  props.editingPublication.assetIds.forEach(id => {
    mediaStore.addToSelection(id)
  })
}
```

> **Note**: `Publication` type needs `assetIds?: string[]`. This is added in the prerequisite step above.

#### Submit behavior (handleSchedule)

When `isEditMode`, call `publishingStore.updatePost()` instead of `schedulePost()`:

```typescript
async function handleSchedule() {
  // ... validation and upload logic unchanged ...

  if (isEditMode.value) {
    await publishingStore.updatePost(props.editingPublication.id, {
      title: 'Post from App', // or keep existing title
      content: normalizedPostText,
      scheduledAt: finalScheduledDate?.toISOString(),
      assetIds: [...mediaStore.selectedAssetIds],
      priority: priorityMode.value,
    })
    emit('updated') // NEW: distinct from 'created'
  } else {
    await publishingStore.schedulePost({ ... })
    emit('created')
  }

  // ... close / reset logic unchanged ...
}
```

Add `updated` to emits:

```typescript
const emit = defineEmits<{
  (e: 'close'): void
  (e: 'created'): void
  (e: 'updated'): void // NEW
}>()
```

#### Submit button text

```html
<!-- In edit mode, change button text -->
{{ isEditMode ? $t('composer.saveChanges') : $t('composer.scheduleBtn') }}
```

#### "Create Another" toggle

Only shown in create mode:

```html
<label v-if="!isEditMode" class="flex items-center gap-1.5 ...">
  <input type="checkbox" v-model="createAnother" />
  <span>Create Another</span>
</label>
```

---

### 2. Simplify `PostDetailModal` — remove inline edit

Remove all inline editing state and behavior from `PostDetailModal`:
- Remove `editTitle`, `editContent`, `editScheduledAt` refs
- Remove `savePublication()` function
- Remove `isSaving`, `saveError` refs
- Remove `canEdit` computed (replace with a new `canEditPublication` that only enables the Edit button)
- Remove the inline title/body/schedule inputs (keep the read-only display versions)
- Remove the "Save" button from the footer

Add a new computed:

```typescript
const canEditPublication = computed(() =>
  props.publication ? publishingStore.isPublicationEditable(props.publication.status) : false
)
```

Add an **"Edit" button** in the footer (next to the "Delete" button):

```html
<button
  v-if="canEditPublication"
  @click="emit('edit')"
  class="flex items-center gap-1.5 px-3 py-2 rounded-xl bg-text-display text-bg-primary ..."
>
  <Pencil class="size-3.5" />
  {{ t('postDetail.edit') }}
</button>
```

Add to emits:

```typescript
const emit = defineEmits<{
  (e: 'close'): void
  (e: 'deleted', id: string): void
  (e: 'reschedule', payload: { id: string; scheduledAt: string }): void
  (e: 'edit', publication: Publication): void // NEW
}>()
```

Remove: `canEdit` prop-related code, `isReadOnly` display toggle (no longer needed — all non-PUBLISHED show read-only text view, no toggle), and the old `savePublication()`.

---

### 3. `SchedulerView` orchestrates the edit flow

The parent view currently has both modals. It needs to:

1. Accept the `'edit'` event from `PostDetailModal`
2. Close `PostDetailModal` and open `CreatePostModal` in edit mode

Current (simplified):

```vue
<PostDetailModal
  :is-open="showDetail"
  :publication="selectedPublication"
  @close="showDetail = false"
  @deleted="handleDeleted"
  @reschedule="handleReschedule"
/>

<CreatePostModal
  :is-open="showCreate"
  @close="showCreate = false"
  @created="handleCreated"
/>
```

New:

```vue
<PostDetailModal
  :is-open="showDetail"
  :publication="selectedPublication"
  @close="showDetail = false"
  @deleted="handleDeleted"
  @reschedule="handleReschedule"
  @edit="handleEditPublication"  <!-- NEW -->
/>

<CreatePostModal
  :is-open="showCreate"
  :initial-date="createInitialDate"
  :editing-publication="editingPublication"  <!-- NEW prop -->
  @close="showCreate = false"
  @created="handleCreated"
  @updated="handleUpdated"  <!-- NEW event -->
/>
```

New handler in `SchedulerView`:

```typescript
function handleEditPublication(publication: Publication) {
  showDetail.value = false
  editingPublication.value = publication
  showCreate.value = true
}

async function handleUpdated() {
  showCreate.value = false
  editingPublication.value = null
  // Refresh calendar data to reflect changes
  await fetchCalendar()
}
```

The `fetchCalendar()` call ensures the calendar reflects the updated publication.

---

## Files to Change

### Prerequisite (data contract — must do first)

| File | Change |
|------|--------|
| `server/smp/src/main/kotlin/.../PublishingApi.kt` | Add `assetIds: List<String>` to `CalendarPublicationResult` |
| `server/smp/src/main/kotlin/.../PublishingHandlers.kt` | Pass `assetIds = assetIds` in `toCalendarResult()` |
| `server/smp/src/test/kotlin/.../PublishingApiTest.kt` | Add `assetIds` to `CalendarPublicationResult` construction |
| `apps/web/app/src/stores/publishing.ts` | Add `assetIds?: string[]` to `Publication` interface |
| `apps/web/app/src/stores/publishing.ts` | Add `assetIds: result.assetIds` in `publicationMutationResultToPublication()` |

### Feature implementation

| File | Change |
|------|--------|
| `components/CreatePostModal.vue` | Add `editingPublication` prop, edit mode logic, pre-fill, submit changes |
| `components/PostDetailModal.vue` | Add "Edit" button, remove inline edit state, emit `'edit'` event |
| `views/SchedulerView.vue` | Add `handleEditPublication`, `handleUpdated`, wire new props/events |
| `locales/*.json` | Add `composer.editTitle`, `postDetail.edit`, `composer.saveChanges` keys |

---

## i18n Keys Needed

```json
// composer — CreatePostModal
"editTitle": "Edit Post",
"saveChanges": "Save Changes",

// postDetail — PostDetailModal
"edit": "Edit"
```

---

## Test Coverage

### Backend Tests

| Test | What it covers |
|------|---------------|
| `CalendarPublicationResult` construction includes `assetIds` | DTO accepts and stores `assetIds` field |
| `toCalendarResult()` forwards `assetIds` | Mapper correctly maps `assetIds` from domain to DTO |

### Unit Tests: `CreatePostModal.test.ts`

| Test | What it covers |
|------|---------------|
| Opens in create mode with no pre-fill | Verify form is empty when no `editingPublication` |
| Opens in edit mode with pre-filled content | `editingPublication.content` populates textarea |
| Opens in edit mode with scheduled date pre-filled | Date + time picker pre-filled from `scheduledAt` |
| Opens in edit mode with `NOW` schedule pre-filled | `scheduleMode = 'now'` pre-selected |
| Opens in edit mode with `NEXT_SLOT` schedule pre-filled | `scheduleMode = 'next'` pre-selected |
| Channel selector is disabled in edit mode | All channel buttons have `disabled` attribute |
| Submit button text says "Save Changes" in edit mode | Button text is `composer.saveChanges` |
| Submit button calls `updatePost` in edit mode | `publishingStore.updatePost` is called, not `schedulePost` |
| `updated` event is emitted after successful edit | `emitted('updated')` has one entry |
| Error state is handled in edit mode | `submitError` is displayed on failure |
| Media assets are pre-loaded into mediaStore on open in edit mode | `mediaStore.selectedAssetIds` contains existing asset IDs |
| Form is cleared on close (edit mode) | After close, form resets to clean state |
| Priority toggle is pre-filled from existing publication | `priorityMode` matches `editingPublication.priority` |
| "Create Another" toggle is hidden in edit mode | Toggle not rendered when `isEditMode` is true |

### Unit Tests: `PostDetailModal.test.ts`

| Test | What it covers |
|------|---------------|
| "Edit" button is rendered when publication is editable | `isPublicationEditable` returns true |
| "Edit" button is NOT rendered when publication is PUBLISHED | Only view-only mode |
| "Edit" button is NOT rendered when publication is not editable | Status guard works |
| Clicking "Edit" emits `'edit'` event with the publication | `emitted('edit')` contains publication |
| Inline save button is removed | No `savePublication` call or `isSaving` state |
| Inline title/body inputs are removed | Read-only display only |

### Integration Tests (Playwright)

| Test | What it covers |
|------|---------------|
| Clicking "Edit" from PostDetailModal opens CreatePostModal in edit mode | E2E flow |
| Edit mode pre-fills content, scheduling, and media | E2E pre-fill |
| Saving edits updates the post and closes the modal | E2E save |
| Calendar reflects the updated publication after edit | E2E refresh |

---

## Open Questions

1. **Title field**: The backend accepts `title` in `EditPublicationCommand`, but the frontend `Publication` type maps to `title` (string). Should the composer also have a title input in edit mode? Currently `CreatePostModal` doesn't show a title field. For MVP, we keep the existing behavior (title is set server-side). This can be enhanced later.

2. **Asset preview**: When editing, existing assets are added to `mediaStore.selectedAssetIds`. The current `selectedAssetPreviewUrl` computed in `CreatePostModal` looks at `mediaStore.selectedAssets[0]` and falls back to the backend's `previewUrl`. If the publication has `thumbnail` or a `previewUrl`, this should render immediately. This should work without changes to the asset preview logic.

3. ~~**"Create Another" toggle**~~: **Resolved**. The toggle is shown only when `!isEditMode` — it is hidden in edit mode (see code snippet above).

---

## Scope for MVP

**In scope:**
- Edit content, scheduling, priority, and media via the full composer
- Pre-fill all fields correctly (including media, after DTO gap is closed)
- Update store and refresh calendar after save
- Channel read-only in edit mode (as per backend contract)
- Backend: add `assetIds` to `CalendarPublicationResult` and mapper
- Frontend: add `assetIds` to `Publication` type and mutation mapper
- All unit and E2E tests

**Out of scope for now:**
- Changing the channel (backend doesn't support it)
- Editing the title field (no UI for it currently in composer)
- Unlinking/removing individual assets (add/change supported, remove via clear)
