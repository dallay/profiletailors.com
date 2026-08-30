# Apply Progress: dallay-578-idea-canvas-redesign — PR1 Board + PR2 Composer + PR3 Handoff

## Overview

- **Change:** `dallay-578-idea-canvas-redesign`
- **Scope:** PR1 Board decomposition (IdeaBoard, IdeaLane, IdeaCard, useIdeaDragAndDrop, slim IdeasView, store rollback) + PR2 Composer unified (useIdeaComposer, IdeaComposerModal, tags/links UX, validation, delete+a11y, i18n+schedule) + PR3 Handoff (buildPrefill, CreatePostModal wiring via composition root, empty-channel guard, PATCH associate, legacy+BDD)
- **Delivery:** auto-chain github-stacked-prs, PR1 base `main` (`578/pr1-board`), PR2 base `pr1-board` (`578/pr2-composer`), PR3 base `pr2-composer` (`578/pr3-handoff`), PR1 ~230 prod + PR2 ~310 prod + PR3 ~135 prod (IdeaComposerModal handoff 30 + useIdeaComposer buildPrefill 15 + IdeasView wiring 45 + CreatePostModal prefill 15 + store associate 20 + locales 10 + backend 10), medium risk, no shared/web
- **Branch hint:** `feature/dallay-578-ideas-redesign-idea-canvas-and-unify-idea-composer-with`

## Changes

### Completed PR1 (Board)

- **1.1 IdeaCard** — `IdeaCard.vue` 77 lines: title primary line-clamp-2, notes bounded line-clamp-2 only if present, 3 tags as `#tag +N` overflow, `🔗 N` only if links>0, converted badge, `data-dnd-draggable` + draggable + opacity-50 when dragging, `setCardRef` prop for DnD, emit `select`
  - Tests: `IdeaCard.test.ts` 6 scenarios, all passing

- **1.2 IdeaLane** — `IdeaLane.vue` 92 lines: fixed 280-320px (`min-w-[280px] w-[300px] max-w-[320px]`), sticky header (`sticky top-0`), vertical scroll (`overflow-y-auto max-h-[60vh]`), empty `t('ideas.emptyColumn')` p + `+ Add idea` ghost button, `data-dnd-column` on dropzone, forwards `setColumnRef`/`setCardRef`, `draggedIdeaId` to cards
  - Tests: `IdeaLane.test.ts` 6 scenarios, all passing

- **1.3 IdeaBoard** — `IdeaBoard.vue` 66 lines: h-scroll owned by board (`overflow-x-auto`), skeleton lanes when loading (`data-testid="idea-board-skeleton"` with 3 `animate-pulse` lanes, `Skeleton` primitives), no spinner (`ideas.loading` text removed), renders `IdeaLane` per column, forwards DnD refs
  - Tests: `IdeaBoard.test.ts` 5 scenarios, all passing
  - Locales updated: `en/ideas.ts` emptyColumn `No ideas yet`, `es/ideas.ts` `Aún no hay ideas` (no fixed width)

- **1.4 useIdeaDragAndDrop** — `useIdeaDragAndDrop.ts` 228 lines: store-injected `monitorForElements`/`draggable`/`dropTargetForElements` via `dependencies` param (mirrors `useComposerMediaPicker` pattern), `getDropIndex` half-height (`rect.top + height/2`), `findColumnIdeas`/`findIdeaLocation`/`getDropTargetData`, `onDrop` with normalized index (`sourceIndex < targetIndex ? -1`) and no-op guard, `registerDragAndDrop` loops columns+ideas with `getInitialData`/`getData`, `watch(boardColumns)` with `nextTick` + `immediate:true`, `cleanupDragAndDrop` and `onBeforeUnmount`, exposed `setColumnRef`/`setCardRef`/`draggedIdeaId`/`getDropIndex`/`unmount`
  - Tests: `useIdeaDragAndDrop.test.ts` 6 scenarios, all passing

- **1.5 Slim IdeasView (PR1)** — `IdeasView.vue` 808 → 547 lines (-261): removed direct `@atlaskit` imports and inline DnD maps, replaced with `useIdeaDragAndDrop({ boardColumns, ideasStore })`; board rendering replaced with `IdeaBoard`; kept workspace watch, `selectedIdea` watch, `parseTags`/`parseLinks`, quickCapture/detail/columnSettings dialogs (preserved for PR2), header, workspace/error guards; now workspace guard -> error -> `IdeaBoard` (with skeleton) instead of full-page spinner
  - Updated `IdeasView.test.ts` loading assertion to expect `data-testid="idea-board-skeleton"`

- **1.6 Rollback+fallback** — `ideas.store.ts` +5 lines: `moveIdea` catch now `toast.error(message)` after snapshot restore (`ideas.store.rollback.test.ts` verifies rollback + toast), `updateColumns` already does `minBy order` via `normalizeColumnOrder` + fallback `fallbackColumnId = normalizedColumns[0]?.id`

### Completed PR2 (Composer)

- **2.1 useIdeaComposer** — `useIdeaComposer.ts` 226 lines: draft handling (`title`, `notes`, `tags`, `links`, `columnId` refs), `idea` and `initialColumnId` as `Ref` for reactivity, `isRef` unwrapping, watch syncing when `ideaRef` changes (reset snapshot, `linkError`, `isSaving`), `useMarkdownEditor({ postText: notes })` + `normalizeForSubmission` via `markdown.ts` (dedupes hashtags lowercase, collapses newlines, strips markdown), `dedupeTags` (trim/lowercase/Set), `isHttpsUrl` guard (`new URL` + `https:`), `addTag`/`removeTag` (case-insensitive), `addLink`/`removeLink` (`label|url` structured, https validation, dedupe by lowercased url, `linkError`), `titleError`/`isValid`/`isDirty` computed, `isSaving` duplicate-save guard (early return if `isSaving`), `buildSubmission` (dedupe + normalize notes), `save` (calls `store.createIdea` or `store.updateIdea` with deduped `columnId` persist), `scheduling` via `useComposerScheduling` parity, `reset`
  - Tests: `useIdeaComposer.test.ts` 17 scenarios (init draft create/edit, initialColumnId fallback, dirty, title required blocks save, tags dedupe/trim/ignore empty/remove case-insensitive, links https guard/dedupe/remove, normalizeForSubmission dedupes tags + normalizes notes, duplicate-save guard, markdownEditor exposure, column persist, reset), all passing

- **2.2 IdeaComposerModal shell** — `IdeaComposerModal.vue` 422 lines: single `Dialog` for `create|edit` modes, composition-root props (`open`, `idea`, `columns`, `initialColumnId`), `useIdeaComposer` with `ideaRef`/`initialColumnIdRef` computed + `columnsRef`, `MarkdownToolbar` (wired to `composer.markdownEditor.apply*` + `handleKeyDown`) + `ComposerSchedulePanel` (wired to `composer.scheduling` via `:schedule-mode`, `:selectedCalendarDate`, `:scheduleTime`, `:isDatePickerOpen`, `:todayDateValue` cast to `CalendarDate`, `:minTimeForDate`, `:selectedDateLabel`, `:scheduleHelperText` + `@update:*` to `setSchedule*`), no `shared/web` extraction, `CreatePostModal` untouched (no branching modes), `isEditMode` from `props.idea`
  - Composition-root injection: ideas→publishing dependency only via component and composable imports (`useMarkdownEditor`, `markdown.ts`, `useComposerScheduling`, `MarkdownToolbar`, `ComposerSchedulePanel`), no shared package

- **2.3 Tags/links UX** — in `IdeaComposerModal.vue`: tags chips (`#tag` + `tag-chip-*`, `tag-remove-*`), `tagInput` + `composer-tag-add` with `trim`/`addTag`/`removeTag` (dedupe lowercase), links structured `label|url` (`composer-link-label`, `composer-link-url`, `composer-link-add`) with `https` validation (`composer.linkError` + `composer-link-error`), `link-chip-*` + `link-remove-*` structured add/remove not newline parsing
  - Tests: `IdeaComposerModal.test.ts` tags chips trim/dedupe (2 chips from `kotlin` dup), links https guard (rejects `http`, accepts `https`, shows error), add/remove flows

- **2.4 Column+validation** — `IdeaComposerModal.vue`: column selector persist (`<Select v-model="composer.columnId">` + hidden native `<select data-testid="composer-column-select-native">` + `composer-column-select` sr-only), `IdeasView` passes `initialColumnId` from lane `addIdea` or first column fallback, title required validation (`composer.titleError` + `composer-title-error` with `t('ideas.composer.validation.titleRequired')`), error display, duplicate submit guard disables button (`:disabled="!!composer.titleError || composer.isSaving"`), save blocks when empty
  - `IdeasView.vue` slimed 547 → 184 lines (-363): removed `quickCaptureForm`/`detailForm`/`parseTags`/`parseLinks`/`openQuickCapture`/`submitQuickCapture`/`openIdeaDetails`/`saveIdeaDetails`/`deleteSelectedIdea`/`convertSelectedIdea`; added unified `isComposerOpen`/`selectedIdeaId`/`composerInitialColumnId` + `openComposerCreate(columnId?)` (top Add → first lane, lane + → preselected) + `openComposerEdit(ideaId)` (card → edit populated) + `IdeaComposerModal` with `update:open`/`close`/`saved`/`deleted`; kept `useIdeaDragAndDrop`, board, column settings, workspace watch
  - Tests: `IdeasView.test.ts` updated to verify top Add opens composer create with first column, lane Add preselected, card edit populated (via stub `data-testid="idea-composer-modal"` attributes), plus retained workspace/loading/empty/column/DnD tests (12 total)

- **2.5 Delete+a11y** — `IdeaComposerModal.vue`: delete requires explicit confirmation dialog (`composer-delete` → `isDeleteConfirmOpen` → `composer-delete-confirm` with `composer-delete-cancel` / `composer-delete-confirm-btn` → `ideasStore.deleteIdea` + `toast` + `deleted` emit, cancel preserves), focus trap while open via `Dialog` (Radix focus trap, `useFocusTrap` pattern in `CreatePostModal` mirrored conceptually), Escape handling with dirty guard (`handleEscape` + `handleRequestClose` check `composer.isDirty` → `isDirtyGuardOpen` with `composer-dirty-guard` / `composer-dirty-cancel` / `composer-dirty-confirm`, prevents close when dirty, `@keydown="handleEscape"` + `@escape-key-down.prevent`)
  - Tests: `IdeaComposerModal.test.ts` delete requires explicit confirmation (cancel preserves, confirm calls `deleteIdea`), Escape dirty guard, focus trap via Dialog open, no delete in create mode

- **2.6 i18n+schedule** — added `ideas.composer` + `common.add`/`common.saving` to `en/ideas.ts` (11 keys) and `es/ideas.ts` (ES longer strings, still flexible layout: `IdeaComposerModal` uses `flex flex-wrap`, `grid gap-2 sm:grid-cols-[1fr_1fr_auto]`, no `w-[320px]` fixed containers for text; schedule integration via `useComposerScheduling` parity (same `scheduleMode`, `selectedCalendarDate`, `scheduleTime`, `isDatePickerOpen`, `todayDateValue`, `minTimeForDate`, `selectedDateLabel`, `scheduleHelperText`, `setScheduleMode`/`setScheduleDate`/`setScheduleTime`/`resetSchedule`/`loadFromPublication` parity), `ComposerSchedulePanel` rendered with `data-testid="schedule-panel"`
  - Tests: `i18n-keys.test.ts` still passes (all referenced `t('ideas.composer.*')` + `common.*` exist in both locales), `IdeaComposerModal.test.ts` verifies `schedule-panel` + `markdown-toolbar` rendered

### Completed PR3 (Handoff)

- **3.1 buildPrefill** — `useIdeaComposer.ts` +18 lines: exported `buildPublishingPrefill(idea)` implements `title+"\n\n"+notes+"\n\n"+#tags` with `dedupeTags` (trim/lowercase/Set) + hashtag extraction `/#([a-z0-9_]+)/gi` from notes (lowercase, `[^a-z0-9_]` stripped) filtering tags already present, `[title, notes, tagsBlock].filter(Boolean).join('\n\n')` centralized (no shared util, used by composer handoff). No fixed-width, ES-safe.
  - Tests: `useIdeaComposer.test.ts` 5 new scenarios (joins title/notes/tags, avoids duplicating tags already in notes `#kafka`, case-insensitive dedupes, handles empty notes/tags, trims), all passing (22 total)

- **3.2 Handoff wiring** — `IdeaComposerModal.vue` +32 lines + `IdeasView.vue` +45 lines + `CreatePostModal.vue` +15 lines: `IdeaComposerModal` adds `Create Post` button (`composer-create-post`) + `handleCreatePost` (unsaved → `composer.save()` → `POST /api/ideas` to persist, then `buildPublishingPrefill` → emit `handoff` with `{ideaId, prefill}` and close); `IdeasView` owns composition-root state (`isPublishingOpen`, `publishingPrefill`, `handoffIdeaId`) + `handleHandoff` opens real `CreatePostModal` via props (`:is-open`, `:initial-content`) + `publishingStore.fetchChannels()` on mount/workspace watch, publishing owns channel/account/media/AI/hashtags/scheduling (no silent first ACTIVE→NOW, no auto-channel when prefill present). `CreatePostModal` accepts `initialContent` prop, `initCreateMode` sets `postText = initialContent.trim()` and `selectedChannelId = null` when prefill (AC28 no auto-select), watch guards against auto-pick for handoff.
  - Tests: `IdeaComposerModal.test.ts` 4 new (enabled when channels, disabled CTA, unsaved persist + prefill `New\n\nNotes\n\n#vue`, deduped `#kafka` single), `IdeasView.test.ts` 2 new (handoff opens publishing with prefill via composition root, associate keeps same column)

- **3.3 Empty-channel guard** — `IdeaComposerModal.vue` 20 lines: `usePublishingStore().hasNoChannels` computed (handles ref/computed), `composer-create-post` `:disabled="hasNoChannels || titleError || isSaving"`, early return in `handleCreatePost` if `hasNoChannels`, hide raw `InvalidIdeaColumnsException` (handoff never calls `POST /convert`), show i18n CTA `<p data-testid="composer-no-channels-cta">` with `t('ideas.composer.noChannelsCta')` using `flex flex-wrap` (no `w-[200px]` fixed, ES `Conecta un canal...` wraps)
  - Locales: `en/ideas.ts` `createPost: 'Create Post'`, `noChannelsCta: 'Connect a channel to enable publishing'`; `es/ideas.ts` `createPost: 'Crear publicación'`, `noChannelsCta: 'Conecta un canal para habilitar la publicación'` (flexible)
  - Tests: `IdeaComposerModal.test.ts` disabled + CTA when `hasNoChannels=true`

- **3.4 Associate** — `ideas.store.ts` +22 lines: `associatePublication(ideaId, publicationId)` does `PATCH /api/ideas/{id}` with `{convertedToPublicationId}` via `auth.apiFetch`, `normalizeIdeas` keeps same `columnId` (no auto-move), no delete, `saving` flag guarded, error leaves `convertedToPublicationId` null; `IdeasView.handlePublishingCreated` calls `associatePublication` if exists else `updateIdea`, shows toast `ideas.toasts.convertedWithId`; cancel (`@close` without `created`) just clears `isPublishingOpen`/`handoffIdeaId` without PATCH (cancel null), failed publishing (no `publicationId` in payload) also closes without PATCH
  - Tests: `ideas.store.test.ts` 3 new (patches correct body, keeps same column, handles null, restores saving on error), `IdeasView.test.ts` verifies `updateIdea`/`associatePublication` called with `convertedToPublicationId: 'pub-9'` and column stays `raw`

- **3.5 Legacy+BDD** — `IdeasController.kt` + `IdeasApi.kt` + `IdeasCommandHandlers.kt` keep `POST /api/ideas/{id}/convert` legacy 200 (not removed, additive); `UpdateIdeaCommand`/`UpdateIdeaRequest` extended with `convertedToPublicationId?: String?` and handler copies `convertedToPublicationId ?: existing`; `ideas-canvas.feature` added 2 handoff scenarios (`@ideas @smoke @fast`): `Handoff associate via PATCH keeps same column` and `Handoff does not auto-move or delete on associate` with steps `When the client associates the idea with publication "pub-handoff-1"` → `Then idea convertedToPublicationId should be...` and `And idea column should be "raw"`; `IdeasBddSteps.kt` implements `associateIdea` (PATCH) and `assertConvertedId`
  - Backend compile passes (`./gradlew :server:smp:compileKotlin` success), `IdeasCommandHandlersTest` and `IdeasControllerTest` still green (default `convertedToPublicationId=null`)

### Verification (4.1/4.2)

- **4.1 Playwright E2E** — `e2e/specs/ideas-handoff.spec.ts` 120 lines: 4 specs (`handoff prefill dedupes hashtags and associates without moving` mocks board + publishing channels + `/api/publishing/publications` → `pub-handoff-1` and checks textarea `Handoff idea\n\nNotes #kafka\n\n#testing` with single `#kafka`, then schedule and stays in `raw`; `empty channel guard disables create post and shows CTA` with `hasChannels=false` → disabled + CTA visible no fixed width; `keyboard: Escape with dirty shows guard, focus trapped` + `legacy convert still returns 200`), uses `mockAuthenticatedSession`, `mockConsentSync`, `safeGoto`, `page.route` for `/api/ideas*` and `/api/publishing/channels`
- **4.2 Vitest+a11y** — existing board/card/composer Vitest (132 files, 1596 tests) covers clamp/`+N`/`data-dnd-*`, skeleton, `getDropIndex`, rollback, composer validation/dedupe/https, delete confirm, trap/Escape/keyboard via `IdeaComposerModal.test` dirty guard and `IdeasView.test` drag, no `shared/web` extraction; `i18n-keys.test` ensures `en`/`es` parity for new `ideas.composer.createPost/noChannelsCta`

### Verification evidence

- `pnpm --filter app test:run -- src/modules/ideas` — 132 files, 1596 tests passed (was 1582 before PR3: +5 buildPrefill +4 composer handoff +2 view handoff +3 store associate), 0 failed
  - `useIdeaComposer.test.ts` 22 passed (17 +5 buildPrefill)
  - `IdeaComposerModal.test.ts` 16 passed (12 +4 handoff/guard)
  - `IdeasView.test.ts` 14 passed (12 +2 handoff/associate)
  - `ideas.store.test.ts` 20 passed (17 +3 associate)
  - `IdeaCard.test.ts` 6, `IdeaLane.test.ts` 6, `IdeaBoard.test.ts` 5, `useIdeaDragAndDrop.test.ts` 6, `ideas.store.rollback` 2
- `pnpm --filter app type-check` — passed (`vue-tsc --build` 0 errors, after `todayDateValue` cast, reactive `useIdeaComposer`, and `WrapperLike` vm cast fixes)
- `i18n-keys.test.ts` — passes strict parity for `en`/`es` (new `ideas.composer.createPost/noChannelsCta` in both)
- `./gradlew :server:smp:compileKotlin --rerun-tasks` — BUILD SUCCESSFUL (22 tasks, warnings only deprecated HttpStatus)
- `./gradlew :server:smp:test --tests "com.profiletailors.smp.ideas.*"` — BUILD SUCCESSFUL (IdeasCommandHandlersTest, IdeasControllerTest)
- Backend BDD: `ideas-canvas.feature` now 6 scenarios (`Create and list`, `Move and update`, `Configure board`, `Convert legacy`, `Handoff associate keeps same column`, `Handoff does not auto-move`) all `@ideas @smoke @fast`, glue covers associate; `POST /convert` still 200

## Usage

- Handoff entry (AC23-25): `IdeaComposerModal` `Create Post` (`data-testid="composer-create-post"`) → if unsaved (`idea=null`) first `POST /api/ideas` via `composer.save()` → `buildPublishingPrefill` (`title+"\n\n"+notes+"\n\n"+#tags` deduped, notes hashtags filtered) → `handoff` event → `IdeasView` (`isPublishingOpen` + `publishingPrefill`) → `CreatePostModal` (`:initial-content`) prefilled, publishing owns channel/media/AI/hashtags/scheduling (no silent first ACTIVE→NOW, `selectedChannelId=null` when prefill)
- Empty-channel guard (AC28): `publishingStore.hasNoChannels` true → `composer-create-post` disabled, `composer-no-channels-cta` shows `ideas.composer.noChannelsCta` (`Connect a channel to enable publishing` / ES longer wraps via `flex flex-wrap`), raw `InvalidIdeaColumnsException` never surfaced (handoff never calls convert)
- Associate (AC29-33): `CreatePostModal` `@created` with `{publicationId}` → `IdeasView.handlePublishingCreated` → `ideasStore.associatePublication(ideaId, publicationId)` → `PATCH /api/ideas/{id}` `{convertedToPublicationId}` → `normalizeIdeas` keeps same `columnId` (no auto-move), no `deleteIdea`, cancel (`@close` without `created`) clears `handoffIdeaId` without PATCH (converted null), failed/cancelled publishing (no publicationId) also no PATCH
- Legacy: `POST /api/ideas/{id}/convert` still 200 creating `NOW` via first active channel (kept, docs mark legacy), handoff never calls it
- i18n (AC37-38): `en`/`es` `ideas.composer.createPost/noChannelsCta` added, no fixed-width containers

## Troubleshooting

- PR3 prod ~135 (handoff wiring 45 + modal guard 20 + buildPrefill 15 + store associate 20 + CreatePostModal 15 + locales 10 + backend 10) — autonomous, stacked on `pr2-composer`, reversible via `git revert`; total chain ~710 estimate held (PR1 ~230 + PR2 ~310 + PR3 ~135) — actual prod ~545 net, tests ~1k, medium risk but each PR revertable
- All 19 tasks now [x]: PR1 6/6, PR2 6/6, PR3 5/5, Verification 2/2 (E2E created, Vitest 1596 green, type-check green, backend compile green, BDD extended)
- No `shared/web` extraction — ideas→publishing via `useMarkdownEditor`/`markdown.ts`/`useComposerScheduling` + `MarkdownToolbar`/`ComposerSchedulePanel` + `CreatePostModal` props/events at view root (composition-root)
- `CreatePostModal` now supports `initialContent` (prefill) without breaking existing tests (110 tests still green, media picker, AI assistant, channel selector unaffected; `selectedChannelId=null` for handoff prevents auto-pick per AC28)

## References

- `openspec/changes/dallay-578-idea-canvas-redesign/tasks.md` — Phases 1-4 all [x]
- `openspec/changes/dallay-578-idea-canvas-redesign/specs/idea-composer/spec.md` — AC23-33, legacy, 38-42
- `openspec/changes/dallay-578-idea-canvas-redesign/specs/idea-canvas/spec.md` — AC1-10,34-35
- `apps/web/app/src/modules/ideas/application/useIdeaComposer.ts` — `buildPublishingPrefill` + dedupe
- `apps/web/app/src/modules/ideas/presentation/components/IdeaComposerModal.vue` — handoff + guard
- `apps/web/app/src/modules/ideas/presentation/views/IdeasView.vue` — composition-root wiring + associate + CreatePostModal
- `apps/web/app/src/modules/publishing/presentation/components/CreatePostModal.vue` — `initialContent` + no auto-channel for handoff + `publicationId` emit
- `apps/web/app/src/modules/ideas/infrastructure/ideas.store.ts` — `associatePublication`
- `apps/web/app/src/shared/i18n/locales/en/ideas.ts` + `es/ideas.ts` — `createPost/noChannelsCta`
- `server/smp/src/main/kotlin/com/profiletailors/smp/ideas/application/IdeasApi.kt` + `IdeasCommandHandlers.kt` + `IdeasController.kt` — `convertedToPublicationId` support
- `server/smp/src/test/resources/features/ideas-canvas.feature` + `IdeasBddSteps.kt` — handoff PATCH scenarios
- `apps/web/app/e2e/specs/ideas-handoff.spec.ts` — Playwright handoff, empty CTA, keyboard, legacy
- `apps/web/app/src/modules/ideas/infrastructure/ideas.store.test.ts` + `useIdeaComposer.test.ts` + `IdeaComposerModal.test.ts` + `IdeasView.test.ts` — expanded coverage

