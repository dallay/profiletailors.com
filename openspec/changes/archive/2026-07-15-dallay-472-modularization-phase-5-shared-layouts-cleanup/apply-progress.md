# Apply Progress: DALLAY-472 Modularization Phase 5 Shared Layouts Cleanup

## Slice

PR 1 guards/inventory completed previously. PR 2 layout/shared/module relocation completed
previously. PR 3 cleanup/verification for approved `feature-branch-chain` delivery strategy
completed in this batch.

## Completed Tasks

- [x] 1.1 RED: extended `apps/web/app/src/modules/module-relocation.spec.ts` with a Phase 5
  legacy-root guard that fails while non-compat leftovers remain in `components/layout`,
  `components/sidebar`, root `composables`, `views`, `i18n`, and non-compat `lib` paths.
- [x] 1.2 RED: added explicit shadcn-vue compatibility guard exclusions for
  `apps/web/app/src/components/ui/**` and `apps/web/app/src/lib/utils.ts`, including `@/lib/utils`
  import compatibility.
- [x] 1.3 Added a composable placement inventory in the relocation guard for shared, layout, auth,
  media, and publishing ownership targets.
- [x] 2.1 Moved `AppShell.vue`, `AppHeader.vue`, sidebar sections/tests, `UploadProgressToast.vue`,
  and `useConnectMessage.ts` into `apps/web/app/src/layouts/`.
- [x] 2.2 Updated `App.vue`, layout tests, mocks, and imports to use `@layouts/*` or colocated
  layout-relative paths while preserving shell composition order.
- [x] 2.3 Moved `VerifyEmailView.vue` and `useLinkedInCallback.ts` to auth, `useFileHash.ts` to
  media, and `useCalendarUrl.ts`/`useQueuedCounts.ts` to publishing application ownership.
- [x] 3.1 Moved `WorkspaceAvatar.vue`, `ThemeToggle.vue`, and `SocialProviderIcon.vue` to
  `apps/web/app/src/shared/components/` and updated consumers/mocks.
- [x] 3.2 Moved `useFocusTrap.ts` and `usePopoverDismissal.ts` to
  `apps/web/app/src/shared/composables/` and updated imports/mocks.
- [x] 3.3 Moved `formatters.ts`, `string-utils.ts`, `provider-styles.ts`, `sse.ts`,
  `validation/schemas.ts`, and `i18n/index.ts` under `apps/web/app/src/shared/`, updating `main.ts`,
  tests, and mocks.
- [x] 3.4 Preserved `components.json`, `components/ui/**`, and `@/lib/utils` compatibility; no
  shadcn generated paths changed.
- [x] 4.1 Deleted emptied/non-compat legacy leftovers except approved compatibility boundaries:
  retained `apps/web/app/src/components/ui/**` and `apps/web/app/src/lib/utils.ts`; removed
  `src/lib/mockData/index.ts` because it was an empty non-shadcn stub, relocated root calendar test
  to publishing ownership, relocated `lib/utils.test.ts` to shared test ownership, and removed stray
  `components/.DS_Store` plus empty `lib/mockData` and `lib/validation` directories.
- [x] 4.2 Ran focused app verification and lint.
- [x] 4.3 Started the Vite dev server and smoke-checked
  shell/settings/media/scheduler/verify-email/LinkedIn callback routes for missing-import warnings.

## Verification

- `pnpm --filter app test:run src/modules/module-relocation.spec.ts` before PR 1 changes: PASS — 6
  tests passed. Vitest emitted existing stderr `Could not parse CSS stylesheet` in dashboard import
  guard.
- `pnpm --filter app test:run src/modules/module-relocation.spec.ts` after PR 1 guard changes: RED
  as required — 8 passed, 1 failed. The failing assertion listed Phase 5 legacy leftovers, proving
  the relocation guard was active before production relocation.
- `pnpm --filter app test:run src/modules/module-relocation.spec.ts` before PR 2 relocation: RED as
  required — 8 passed, 1 failed. The failing assertion listed all Phase 5 legacy leftovers:
  layout/sidebar roots, root composables, verify-email view, i18n, and non-compat lib files.
- `pnpm --filter app test:run src/modules/module-relocation.spec.ts` after relocation/import
  updates: PASS — 9 tests passed. Vitest still emitted existing stderr
  `Could not parse CSS stylesheet` in dashboard import guard.
-

`pnpm --filter app test:run src/layouts/AppShell.test.ts src/layouts/AppHeader.test.ts src/layouts/sidebar/SidebarHeaderSection.test.ts src/layouts/sidebar/SidebarChannelsSection.test.ts src/layouts/sidebar/SidebarChannelRow.test.ts src/layouts/sidebar/SidebarAccountSection.test.ts src/layouts/sidebar/SidebarConnectSection.test.ts src/modules/publishing/views/SchedulerView.test.ts src/modules/media/services/media-api.test.ts src/shared/lib/validation/schemas.test.ts`:
PASS — 10 files, 147 tests passed. Existing expected stderr from SchedulerView delete-error test
logged `Delete failed Error: Network error`.

- `pnpm --filter app test:run src/modules/module-relocation.spec.ts` after PR 3 guard expansion: RED
  as required — 8 passed, 1 failed. The failing assertion listed `components/calendar.test.ts`,
  `components/.DS_Store`, `lib/mockData/index.ts`, and `lib/utils.test.ts`, proving cleanup coverage
  before deletion/relocation.
-

`pnpm --filter app test:run src/modules/module-relocation.spec.ts src/modules/publishing/presentation/components/calendar.test.ts src/shared/lib/utils.test.ts`
after PR 3 cleanup: PASS — 3 files, 29 tests passed. Vitest still emitted existing stderr
`Could not parse CSS stylesheet` in dashboard import guard.

- `pnpm --filter app lint` before lint cleanup: FAIL — Biome reported unused `CalendarIcon`/
  `ImageIcon` imports in `CreatePostModal.vue` and formatting in `AppShell.test.ts`.
-

`pnpm --filter app test:run src/modules/module-relocation.spec.ts src/modules/publishing/presentation/components/calendar.test.ts src/shared/lib/utils.test.ts && pnpm --filter app lint`
after lint cleanup: PASS — 29 focused tests passed; Biome checked 632 files with no errors.

- `pnpm --filter app exec vite --host 127.0.0.1 --port 4174 --strictPort` plus route smoke for `/`,
  `/settings`, `/media`, `/scheduler/calendar/month`, `/verify-email`, and
  `/auth/linkedin/callback`: PASS — all returned HTTP 200 and Vite output had no missing-import
  warnings.

## Review Workload

- Forecast from `tasks.md`: Medium, 350-500 estimated changed lines.
- Delivery strategy: `feature-branch-chain`.
- Implemented slice: PR 3 cleanup/verification.
- Budget concern: None for this slice.

## Deviations

None. Implementation follows the design: shell ownership under `@layouts`, generic reusable code
under `@shared`, domain-owned code under auth/media/publishing modules, and shadcn-vue compatibility
retained at `components/ui/**` plus `@/lib/utils`.

## Remaining Tasks

None in apply. Ready for SDD verify.
