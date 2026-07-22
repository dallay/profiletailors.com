# Apply Progress: DALLAY-468 Modularization Phase 1 — Auth, Workspace, Settings

## Status

Completed all tasks in `tasks.md` for the single relocation PR slice.

## TDD Evidence

- RED: Added `apps/web/app/src/modules/module-relocation.spec.ts` first and ran
  `pnpm --filter app exec vitest run src/modules/module-relocation.spec.ts`; it failed because
  `@modules/auth/presentation/AuthView.vue` did not exist yet.
- GREEN: Moved auth/workspace/settings files into `src/modules/*`, rewrote runtime imports and
  `vi.mock()` targets to `@modules/*`, then reran
  `pnpm --filter app exec vitest run src/modules/module-relocation.spec.ts`; it passed.
- Focused regression suite:
  `pnpm --filter app exec vitest run src/modules/auth src/modules/settings src/modules/workspace src/router/index.spec.ts src/router/index.guard.test.ts src/App.test.ts src/views/VerifyEmailView.spec.ts src/views/MediaLibraryView.test.ts src/stores/media.test.ts src/stores/publishing.test.ts src/lib/media-api.test.ts src/components/CreatePostModal.test.ts src/components/layout/AppShell.test.ts src/components/ThemeToggle.test.ts src/components/sidebar/SidebarChannelRow.test.ts src/components/composer/LinkedInPostPreview.test.ts`
  passed: 22 files, 331 tests.
- Focused lint:
  `pnpm --filter app exec biome check src/modules src/main.ts src/router/index.ts src/views src/components src/stores src/lib src/composables`
  passed.
- Legacy moved-path guard:
  `@/(stores/(auth|workspace|settings)|lib/auth-api|views/(AuthView|SettingsView|LinkedInCallbackView)|components/workspace/WorkspaceIconModal)`
  no longer matches under `apps/web/app/src`.

## Completed Tasks

- [x] 1.1 Baseline test command attempted with `just frontend-test --run`; recipe does not accept
  `--run`. Focused Vitest guard was used for RED/GREEN and focused suite for regression evidence.
- [x] 1.2 Baseline legacy-path grep found moved-path consumers and guided rewrites.
- [x] 2.1 Auth views and specs moved to `src/modules/auth/presentation/`.
- [x] 2.2 Auth store and tests moved to `src/modules/auth/infrastructure/auth.store.*`.
- [x] 2.3 Auth API and tests moved to `src/modules/auth/infrastructure/auth-api.*` without behavior
  changes.
- [x] 2.4 Workspace store moved to `src/modules/workspace/infrastructure/workspace.store.ts`.
- [x] 2.5 Workspace icon modal moved to
  `src/modules/workspace/presentation/components/WorkspaceIconModal.vue`.
- [x] 2.6 Settings view/specs and settings store tests moved into `src/modules/settings/`
  presentation/infrastructure layers.
- [x] 3.1 Router and main imports now use `@modules/*`.
- [x] 3.2 Runtime consumers now use `@modules/*` for moved auth/workspace/settings files.
- [x] 3.3 Test imports and `vi.mock()` paths now use `@modules/*` for moved files.
- [x] 3.4 shadcn-vue imports remain `@/components/ui/*`; `components.json` was not changed.
- [x] 4.1 Focused frontend tests passed.
- [x] 4.2 Focused frontend lint passed.
- [x] 4.3 Legacy moved-path grep returned no matches.
- [x] 5.1 DoD confirmed: physical move done, no shims, UI primitives unmoved.
- [x] 5.2 PR evidence prepared here; `auth-api.ts` remains the documented boundary exception and
  rollback is git revert.

## Notes

- The repository Justfile's `frontend-test` and `frontend-lint` recipes point at the marketing app
  directory, so app-focused `pnpm --filter app exec ...` commands were used for this Vue
  modularization task.
- Existing test stderr warnings remain from RouterView stubbing/CSS parsing and publishing fallback
  scenarios; they did not fail the suite and were not introduced by this relocation.
