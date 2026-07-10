# Tasks: Edit Publication via Composer

## Review Workload Forecast

| Field                   | Value                                                                                 |
|-------------------------|---------------------------------------------------------------------------------------|
| Estimated changed lines | 350-550                                                                               |
| 400-line budget risk    | Medium                                                                                |
| Chained PRs recommended | No                                                                                    |
| Suggested split         | Single feature branch with backend prerequisite first, then frontend flow, then tests |
| Delivery strategy       | single-pr                                                                             |
| Chain strategy          | not-needed                                                                            |

Decision needed before apply: No
Chained PRs recommended: No
Chain strategy: not-needed
400-line budget risk: Medium

## Phase 1: Close `assetIds` data contract

- [x] 1.1 Add `assetIds: List<String>` to `CalendarPublicationResult` in `PublishingApi.kt`
- [x] 1.2 Forward `assetIds = assetIds` in `PublicationDraft.toCalendarResult()` in
  `PublishingHandlers.kt`
- [x] 1.3 Update backend tests to cover `assetIds` in DTO construction and mapper forwarding
- [x] 1.4 Add `assetIds?: string[]` to frontend `Publication` interface in `stores/publishing.ts`
- [x] 1.5 Add `assetIds: result.assetIds` in `publicationMutationResultToPublication()`

## Phase 2: Add composer edit mode

- [x] 2.1 Extend `CreatePostModal.vue` props with `editingPublication?: Publication`
- [x] 2.2 Add edit mode computed state and mode-specific header/button text
- [x] 2.3 Pre-fill content, schedule, priority, channel, and media on open in edit mode
- [x] 2.4 Disable channel switching in edit mode
- [x] 2.5 Branch submit flow to `publishingStore.updatePost()` and emit `updated`
- [x] 2.6 Hide "Create Another" toggle in edit mode
- [x] 2.7 Add/adjust `CreatePostModal` tests for edit mode behavior

## Phase 3: Remove inline editing from detail modal and wire parent flow

- [x] 3.1 Remove inline edit state and save behavior from `PostDetailModal.vue`
- [x] 3.2 Add `canEditPublication` computed and footer "Edit" button that emits `edit`
- [x] 3.3 Add/adjust `PostDetailModal` tests for edit button visibility and emitted event
- [x] 3.4 Add `editingPublication` state to `SchedulerView.vue`
- [x] 3.5 Wire `@edit` to open `CreatePostModal` in edit mode and close detail modal
- [x] 3.6 Wire `@updated` to close composer, clear edit state, and refresh calendar
- [x] 3.7 Add i18n keys in locale files for edit mode labels

## Phase 4: Verification

- [x] 4.1 Run backend verification for publishing changes
- [x] 4.2 Run frontend unit tests covering composer/detail modal flow
- [x] 4.3 Run frontend linting for touched files
