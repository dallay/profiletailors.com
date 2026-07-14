# Design: DALLAY-471 Publishing Module

## Technical Approach

Relocate publishing-owned Vue SPA files into `apps/web/app/src/modules/publishing/` using the same direct-move pattern as DALLAY-468/470. This is a behavior-preserving physical move plus import/mock rewrite only: scheduler URLs, Pinia store IDs/state, composer workflow, media picker behavior, and component props/events stay unchanged. Media state remains owned by `modules/media` from DALLAY-469; publishing may import media only through stable `@modules/media/...` paths.

## Architecture Decisions

| Decision | Choice | Alternatives considered | Rationale |
|---|---|---|---|
| Publishing layout | `views`, `presentation/components`, `presentation/components/composer`, `application`, `infrastructure` | Flat module folder; leave legacy roots | Matches existing module convention while separating UI, composable logic, and Pinia infrastructure. |
| Media boundary | Move `media.ts` to `modules/media/infrastructure/media.store.ts` while requiring future cross-module media interaction to go through media application/domain ports | Put media store under publishing; direct cross-module infrastructure imports | Preserves DALLAY-469 ownership and prevents publishing from depending on media internals long-term. |
| Import strategy | Rewrite directly to `@modules/publishing/...` and `@modules/media/...`; no shims/barrels | Compatibility re-exports at legacy paths | Relocation guards should prove old roots are gone; shims hide boundary violations. |
| Mock strategy | Update `vi.mock()` strings to exact new module paths with tests moved beside targets | Keep mocks on old aliases | Vitest mocks match import specifiers; stale mocks silently stop intercepting. |

## Data Flow

```text
router ──→ @modules/publishing/views/SchedulerView
              ├──→ publishing presentation components/composer
              ├──→ publishing infrastructure store
              └──→ media application/domain port for composer media needs

media route ──→ @modules/media/presentation ──→ media application/domain port ──→ media infrastructure adapter
```

## Target Directory Layout

```text
apps/web/app/src/modules/publishing/
├── application/useComposerMediaPicker.ts
├── infrastructure/publishing.store.ts
├── presentation/components/{CalendarCell,CalendarHeader,ConflictBadge,CreatePostModal,PostDetailModal}.vue
├── presentation/components/composer/*
└── views/SchedulerView.vue
```

## File Changes

| File | Action | Description |
|---|---|---|
| `src/views/SchedulerView.vue` + `.test.ts` | Move | To `modules/publishing/views/`; update local component/store imports and mocks. |
| `src/components/CalendarCell.vue` + `.test.ts` | Move | To publishing components; do not touch `components/ui/calendar/*`. |
| `src/components/CalendarHeader.vue` + `.test.ts` | Move | To publishing components; update publishing store import. |
| `src/components/ConflictBadge.vue` | Move | To publishing components; update consumers/mocks. |
| `src/components/CreatePostModal.vue` + `.test.ts` | Move | To publishing components; composer/media imports become module paths. |
| `src/components/PostDetailModal.vue` + `.test.ts` | Move | To publishing components; update publishing store/type imports. |
| `src/components/composer/**` | Move | To `presentation/components/composer/**`; keep internal relatives, update shared imports. |
| `src/composables/useComposerMediaPicker.ts` + `.test.ts` | Move | To publishing `application/`; depends on publishing and media contracts via module paths. |
| `src/stores/publishing.ts` + `.test.ts` | Move | To `modules/publishing/infrastructure/publishing.store.ts`; preserve `defineStore` ID/exports. |
| `src/stores/media.ts` + `.test.ts` | Move | To `modules/media/infrastructure/media.store.ts`; preserve media API/service imports. |
| `src/router/index.ts` | Modify | Scheduler lazy imports use `@modules/publishing/views/SchedulerView.vue`. |
| `src/modules/{dashboard,settings,auth,media}/**`, layout/sidebar/toast/queued-counts/app tests | Modify | Import path and mock path rewrites only. |
| `src/modules/module-relocation.spec.ts` | Modify | Guard publishing view/store/components/composable and media store resolution. |

## Interfaces / Contracts

Stable imports after relocation:

```ts
@modules/publishing/infrastructure/publishing.store
@modules/publishing/views/SchedulerView.vue
@modules/publishing/presentation/components/CreatePostModal.vue
@modules/publishing/application/useComposerMediaPicker
@modules/media/infrastructure/media.store
```

## Testing Strategy

| Layer | What to Test | Approach |
|---|---|---|
| Unit | Moved stores/composable/components | Move tests with files; update imports and `vi.mock()` paths first, then run focused Vitest. |
| Integration | Router/module resolution | Extend `module-relocation.spec.ts` and run router/scheduler-related tests. |
| E2E | Scheduler/composer route behavior | No new E2E required; run scheduler E2E only if focused tests expose routing risk. |

## TDD / Relocation Guard Strategy

Start by extending `module-relocation.spec.ts` so new `@modules/publishing` and media-store imports fail before files move. Then move files, rewrite imports/mocks, and make existing tests pass without changing assertions.

## Verification Commands

```bash
pnpm --filter app exec vitest run src/modules/module-relocation.spec.ts
pnpm --filter app exec vitest run src/modules/publishing src/modules/media src/router/index.ts src/App.test.ts
pnpm --filter app exec biome check src/modules src/router/index.ts src/components src/composables src/stores
pnpm --filter app type-check
```

Avoid broad `just ci` during relocation unless focused checks reveal cross-app risk or before final push/PR.

## Migration / Rollout

No migration required. Roll out as one relocation PR. Rollback is a git revert of file moves/import rewrites, then rerun the focused Vitest and type-check commands.

## Open Questions

None.
