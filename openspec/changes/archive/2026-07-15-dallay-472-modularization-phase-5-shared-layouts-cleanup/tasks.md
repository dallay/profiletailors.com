# Tasks: DALLAY-472 Modularization Phase 5 Shared Layouts Cleanup

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | 350-500 |
| 400-line budget risk | Medium |
| Chained PRs recommended | Yes |
| Suggested split | PR 1 guards/inventory → PR 2 layout/shared moves → PR 3 cleanup/verification |
| Delivery strategy | feature-branch-chain |
| Chain strategy | approved: feature-branch-chain |

Decision needed before apply: No
Chained PRs recommended: Yes
Chain strategy: feature-branch-chain
400-line budget risk: Medium

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|------|------|-----------|-------|
| 1 | Add relocation guards and placement inventory | PR 1 | Base main; test-only guard first. |
| 2 | Move layouts, shared code, module leftovers | PR 2 | Depends on PR 1; import-safe relocations. |
| 3 | Delete empty legacy folders and verify | PR 3 | Depends on PR 2; dev-server/manual checks. |

## Phase 1: TDD Guards and Inventory

- [x] 1.1 RED: extend `apps/web/app/src/modules/module-relocation.spec.ts` to fail on legacy `components/layout`, `components/sidebar`, root `composables`, `views`, `i18n`, and non-compat `lib` leftovers.
- [x] 1.2 RED: add guard exclusions for `apps/web/app/src/components/ui/**` and `apps/web/app/src/lib/utils.ts` shadcn compatibility.
- [x] 1.3 Create a composable placement inventory in the guard/test fixture or task notes classifying each root composable as shared, layout, auth, media, or publishing.

## Phase 2: Layout and Module Relocation

- [x] 2.1 Move `components/layout/{AppShell,AppHeader}.vue`, `components/sidebar/*`, `UploadProgressToast.vue`, and `useConnectMessage.ts` into `apps/web/app/src/layouts/`.
- [x] 2.2 Update `apps/web/app/src/App.vue`, layout tests, mocks, and relative imports to use `@layouts/*` while preserving shell composition order.
- [x] 2.3 Move `views/VerifyEmailView.vue` and `useLinkedInCallback.ts` to `src/modules/auth`, `useFileHash.ts` to `src/modules/media`, and `useCalendarUrl.ts`/`useQueuedCounts.ts` to `src/modules/publishing`.

## Phase 3: Shared Relocation and Import Updates

- [x] 3.1 Move `WorkspaceAvatar.vue`, `ThemeToggle.vue`, and `SocialProviderIcon.vue` to `apps/web/app/src/shared/components/` and update consumers.
- [x] 3.2 Move `useFocusTrap.ts` and `usePopoverDismissal.ts` to `apps/web/app/src/shared/composables/` and update imports/mocks.
- [x] 3.3 Move `formatters.ts`, `string-utils.ts`, `provider-styles.ts`, `sse.ts`, `validation/schemas.ts`, and `i18n/index.ts` under `@shared`, updating `main.ts`, tests, and mocks.
- [x] 3.4 Preserve `components.json`, `components/ui/**`, and `@/lib/utils` compatibility; do not change shadcn generated paths.

## Phase 4: Cleanup and Verification

- [x] 4.1 Delete emptied legacy folders except approved compatibility boundaries: `components/ui/**` and `lib/utils.ts`.
- [x] 4.2 Run `pnpm --filter app test:run`, `pnpm --filter app lint`, and app type-check/build if unresolved imports appear.
- [x] 4.3 Start the app dev server and manually verify shell navigation, settings, media, scheduler, verify-email, and LinkedIn callback routes show no missing-import warnings.
