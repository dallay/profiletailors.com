# Tasks: App Type-Check Remediation

## Review Workload Forecast

| Field                   | Value       |
|-------------------------|-------------|
| Estimated changed lines | 180–300     |
| 400-line budget risk    | Medium      |
| Chained PRs recommended | No          |
| Suggested split         | Single PR   |
| Delivery strategy       | ask-on-risk |
| Chain strategy          | pending     |

Decision needed before apply: No
Chained PRs recommended: No
Chain strategy: pending
400-line budget risk: Medium

### Suggested Work Units

| Unit | Goal                                   | Likely PR | Notes                                                                |
|------|----------------------------------------|-----------|----------------------------------------------------------------------|
| 1    | Restore strict app contracts and gates | PR 1      | Single remediation PR; focused tests and final verification included |

## Phase 1: Baseline and Strict Test Primitives

- [x] 1.1 Record `git status --short`; treat `Justfile`, `apps/web/app/package.json`, and
  `apps/web/app/e2e/**` as read-only/un-staged throughout remediation.
- [x] 1.2 Fix `apps/web/app/vite.config.ts` via `vitest/config`; verify config with
  `pnpm --filter app exec vitest run --config vite.config.ts src/App.test.ts`.
- [x] 1.3 Correct DOM stream, Vitest hook, and checked-capture contracts in `useFileHash.test.ts`,
  `media-api.test.ts`, and `i18n-keys.test.ts`; run
  `pnpm --filter app exec vitest run src/composables/useFileHash.test.ts src/lib/media-api.test.ts src/i18n/i18n-keys.test.ts`.

## Phase 2: Scheduler and Composer Contracts

- [x] 2.1 RED: add the day-route week-fallback assertion in `useCalendarUrl.test.ts`; run its
  focused test and confirm the new assertion fails.
- [x] 2.2 GREEN: normalize day to `calendar-week` in `useCalendarUrl.ts`; run
  `pnpm --filter app exec vitest run src/composables/useCalendarUrl.test.ts src/router/index.spec.ts src/router/index.guard.test.ts`.
- [x] 2.3 Align schedule-mode, `Date | undefined`, and guarded edit ID typing in
  `CreatePostModal.vue`; run
  `pnpm --filter app exec vitest run src/components/CreatePostModal.test.ts`.
- [x] 2.4 Strengthen `SchedulerView.test.ts` with `{date,density,count}` and rendered-meaning
  assertions; run `pnpm --filter app exec vitest run src/views/SchedulerView.test.ts`.

## Phase 3: Media TDD Slices

- [x] 3.1 RED: make the workspace mock mutable and add absent-workspace/no-`putAsset` regression in
  `stores/media.test.ts`; confirm the focused test fails.
- [x] 3.2 GREEN: guard workspace before upload state/API work in `stores/media.ts`; run
  `pnpm --filter app exec vitest run src/stores/media.test.ts`.
- [x] 3.3 RED: replace stale fixtures with `PENDING_UPLOAD` and `UPLOADING` in
  `MediaLibraryView.test.ts`; assert shared count/filter/style/selection behavior and confirm
  failure.
- [x] 3.4 GREEN/REFACTOR: add typed processing mapping and valid requested statuses in
  `MediaLibraryView.vue`; run
  `pnpm --filter app exec vitest run src/stores/media.test.ts src/views/MediaLibraryView.test.ts`.

## Phase 4: Timer TDD and Final Gates

- [x] 4.1 RED: add 3-second dismissal and unmount-cleanup fake-timer tests in
  `SettingsView.spec.ts`; confirm failure.
- [x] 4.2 GREEN: use `ReturnType<typeof setTimeout> | undefined` and `globalThis.clearTimeout` in
  `SettingsView.vue`; run
  `pnpm --filter app exec vitest run src/views/SettingsView.spec.ts src/views/SettingsView.validation.spec.ts`.
- [x] 4.3 Run `pnpm --filter app type-check && pnpm --filter app test:run`; then run
  `pnpm --filter app test:e2e:media:mocked` and final `pnpm --filter app type-check`.
- [x] 4.4 Prove isolation with `git diff -- Justfile apps/web/app/package.json apps/web/app/e2e` and
  `git status --short`; do not stage or alter CAS PR 1 files.
