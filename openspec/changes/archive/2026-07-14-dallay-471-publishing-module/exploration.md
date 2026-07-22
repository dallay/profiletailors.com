## Exploration: DALLAY-471 modularization phase 4 publishing module

### Current State

The app is mid-modularization: `auth`, `workspace`, `settings`, `dashboard`, and `media` already
live under `apps/web/app/src/modules/*`, while publishing still straddles legacy roots. Scheduler
routes still lazy-load `../views/SchedulerView.vue`; the scheduler view imports publishing
components from `@/components/*` and the publishing store from `@/stores/publishing`. The composer
is rooted at `apps/web/app/src/components/CreatePostModal.vue`, with the picker composable in
`apps/web/app/src/composables/useComposerMediaPicker.ts` and composer presentation files under
`apps/web/app/src/components/composer/`. Media service code has already moved to
`@modules/media/services/media-api`; only the Pinia `media` store remains in the legacy `src/stores`
root and is consumed by both publishing UI and the media module view.

### Affected Areas

- `apps/web/app/src/views/SchedulerView.vue` — move to
  `apps/web/app/src/modules/publishing/views/SchedulerView.vue`; update imports for publishing
  components/store and preserve scheduler URL behavior.
- `apps/web/app/src/router/index.ts` — update all scheduler route lazy imports to
  `@modules/publishing/views/SchedulerView.vue`.
- `apps/web/app/src/components/CalendarCell.vue` — move into publishing presentation components;
  imports `ConflictBadge`, `SocialProviderIcon`, provider styles, and publishing types.
- `apps/web/app/src/components/CalendarHeader.vue` — move into publishing presentation components;
  imports `usePublishingStore` and shared `SocialProviderIcon`.
- `apps/web/app/src/components/ConflictBadge.vue` — move with publishing presentation components;
  used by scheduler/calendar UI.
- `apps/web/app/src/components/CreatePostModal.vue` — move with publishing composer UI; used by
  `SchedulerView` and dashboard `HomeView`.
- `apps/web/app/src/components/PostDetailModal.vue` — move with publishing presentation components;
  used by `SchedulerView` and imports publishing store/types.
- `apps/web/app/src/components/composer/` — move entire directory under publishing presentation
  composer; internal relative imports can mostly remain intact, but `PostPreviewPanel.vue` imports
  shared `SocialProviderIcon`.
- `apps/web/app/src/composables/useComposerMediaPicker.ts` and `.test.ts` — should move with
  publishing composer/application logic because only `CreatePostModal` uses it; keep its dependency
  on media store/types explicit.
- `apps/web/app/src/stores/publishing.ts` and `.test.ts` — move to
  `apps/web/app/src/modules/publishing/infrastructure/publishing.store.ts`; many app-shell,
  settings, auth callback, sidebar, queued-count, and scheduler tests/imports must be updated.
- `apps/web/app/src/stores/media.ts` and `.test.ts` — issue asks to move it, but media module is
  already modularized; safest destination is
  `apps/web/app/src/modules/media/infrastructure/media.store.ts`, not publishing, to avoid undoing
  DALLAY-469.
- `apps/web/app/src/modules/media/presentation/views/MediaLibraryView.vue` and `.test.ts` — update
  `useMediaStore` import from legacy `@/stores/media` to
  `@modules/media/infrastructure/media.store`.
- `apps/web/app/src/modules/dashboard/presentation/views/HomeView.vue` — update `CreatePostModal`
  import to the publishing module path.
- `apps/web/app/src/modules/settings/presentation/SettingsView.vue` and `.spec.ts` — update
  publishing store import.
- `apps/web/app/src/modules/auth/presentation/LinkedInCallbackView.spec.ts` and
  `apps/web/app/src/composables/useLinkedInCallback.ts` — update publishing store import while
  keeping auth module ownership.
- `apps/web/app/src/components/layout/AppShell.vue`, `AppShell.test.ts`, sidebar components/tests,
  `App.test.ts`, `useQueuedCounts.ts`/`.test.ts`, `UploadProgressToast.vue`/`.test.ts` — update
  imports/types to new publishing/media store module locations; do not move these shared/layout
  files in this phase.
- `apps/web/app/src/modules/module-relocation.spec.ts` — extend relocation guard with publishing
  view/store/components and media store exports.
- `apps/web/app/src/components/*.{test.ts}` and `apps/web/app/src/views/SchedulerView.test.ts` —
  move tests next to relocated files or update import/mock paths consistently.

### Approaches

1. **Move publishing UI/store into `modules/publishing`, media store
   into `modules/media/infrastructure`** — publishing owns scheduler/composer/publishing Pinia
   store; media keeps its already-modularized service and gains its store.
    - Pros: Matches existing module pattern, respects DALLAY-469 media ownership, minimizes
      cross-module confusion, and creates clear `@modules/publishing` import boundaries.
    - Cons: Touches many imports and tests; `CreatePostModal` remains used by dashboard, creating a
      legitimate cross-module dependency from dashboard to publishing.
    - Effort: Medium

2. **Move both publishing and media stores into `modules/publishing/infrastructure`** — follow the
   issue wording literally by grouping stores with composer.
    - Pros: Fewer publishing composer import hops in the short term.
    - Cons: Reverses media modularization intent, forces `MediaLibraryView` to depend on publishing,
      and blurs domain boundaries.
    - Effort: Medium

3. **Add compatibility re-export shims at legacy paths while moving implementation files** — move
   files but leave `src/stores/publishing.ts`, `src/stores/media.ts`, and selected component
   wrappers temporarily.
    - Pros: Lowest breakage risk; can migrate imports incrementally.
    - Cons: Leaves old roots alive, weakens the modularization guard, and makes DALLAY-472 cleanup
      larger.
    - Effort: Low/Medium

### Recommendation

Use Approach 1. Create `apps/web/app/src/modules/publishing/` with `views/SchedulerView.vue`,
`presentation/components/{CalendarCell,CalendarHeader,ConflictBadge,CreatePostModal,PostDetailModal}.vue`,
`presentation/components/composer/*`, `application/useComposerMediaPicker.ts`, and
`infrastructure/publishing.store.ts`. Move `media.ts` to
`apps/web/app/src/modules/media/infrastructure/media.store.ts` so the media module remains owner of
media state. Update imports directly to `@modules/...` rather than leaving compatibility shims, then
extend `module-relocation.spec.ts` to guard publishing and media store resolution. Keep
`SocialProviderIcon`, sidebar/layout shell, `UploadProgressToast`, and provider style utilities in
their current shared/root locations for this phase unless DALLAY-472 later moves shared/layout
cleanup.

Recommended move map:

- `src/views/SchedulerView.vue` → `src/modules/publishing/views/SchedulerView.vue`
- `src/components/CalendarCell.vue` →
  `src/modules/publishing/presentation/components/CalendarCell.vue`
- `src/components/CalendarHeader.vue` →
  `src/modules/publishing/presentation/components/CalendarHeader.vue`
- `src/components/ConflictBadge.vue` →
  `src/modules/publishing/presentation/components/ConflictBadge.vue`
- `src/components/CreatePostModal.vue` →
  `src/modules/publishing/presentation/components/CreatePostModal.vue`
- `src/components/PostDetailModal.vue` →
  `src/modules/publishing/presentation/components/PostDetailModal.vue`
- `src/components/composer/*` → `src/modules/publishing/presentation/components/composer/*`
- `src/composables/useComposerMediaPicker.ts` →
  `src/modules/publishing/application/useComposerMediaPicker.ts`
- `src/stores/publishing.ts` → `src/modules/publishing/infrastructure/publishing.store.ts`
- `src/stores/media.ts` → `src/modules/media/infrastructure/media.store.ts`

### Risks

- Media ownership risk: moving `media` store under publishing would undo part of DALLAY-469; keep
  media state inside `modules/media`.
- Import blast radius is broad: store imports are used by app shell, settings, auth callback,
  sidebar, queued counts, scheduler tests, media library tests, and composer tests.
- `CreatePostModal` is used by both scheduler and dashboard; dashboard will intentionally depend on
  publishing after the move.
- Test mocks in `SchedulerView.test.ts`, `PostDetailModal.test.ts`, `AppShell.test.ts`, and
  component tests use exact legacy import strings; missed mock-path updates can fail before runtime.
- There are two `CalendarCell.vue`/`CalendarHeader.vue` concepts: shadcn/reka UI files under
  `components/ui/calendar/` must not be moved or confused with publishing calendar cells.
- Existing comments in moved files may violate the repo's current strict code-comments policy, but
  this phase should avoid unrelated behavior/comment churn unless lint requires it.

### Ready for Proposal

Yes — propose a focused relocation change with no behavior changes: move publishing-owned
scheduler/composer files to `modules/publishing`, move media store to
`modules/media/infrastructure`, update imports/tests/router, and add module relocation guard
coverage. Tell the user the main design decision is to keep media state in the media module despite
the issue's shorthand "publishing + media stores" wording.