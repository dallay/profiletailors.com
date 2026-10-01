# DALLAY-617: composer library search

## Overview

Add client-side library search to the composer picker using the Media Library's filename, asset ID, and MIME-type matching semantics. The shell remains presentational, selection and reconciliation state remain independent of filtering, and the media API is unchanged.

**Route:** Delegated direct. No durable SDD cycle was requested.

## Changes

### Tasks

- [x] RPI-001 Add shell tests for the library search input, emitted query, filtered-empty state, source visibility, close reset, hidden selected IDs on apply, and visible keyboard focus styling.
- [x] RPI-002 Add composable tests for filename/asset ID/MIME-type matching, trimming/case-insensitivity, clearing, and preserved staged/pending IDs.
- [x] RPI-003 Implement the input event flow, composable filtered asset projection, close reset, and localized EN/ES labels and empty-state copy.
- [x] RPI-004 Extend the existing mocked composer E2E plan with named matching/empty/hidden-selection steps and accessible locators; verify search + apply in the mocked media lane.
- [x] RPI-005 Run focused Vitest tests, app lint/type-check/build, attempt the mocked E2E lane, inspect the final diff, and record exact evidence here.

### Acceptance criteria

- Search is available only on the library source with a localized label, placeholder, and visible token-based keyboard focus indicator.
- Search trims input and matches `originalFilename`, `assetId`, and `mediaType` case-insensitively; clearing restores all assets.
- A no-match query has a filtered-empty state distinct from a truly empty library; loading/error states remain intact.
- Closing the picker clears the query without changing staged selection, pending reconciliation assets, auto-staged IDs, apply behavior, or attachment limits.
- `MediaLibraryView` behavior and the media API contract remain unchanged; filtering stays client-side.

### Status

- RPI-001–RPI-005: complete; focused tests pass, the search-and-apply E2E scenario passes, and selection remains independent of the filtered projection.

## Usage

### Verification evidence

- Initial exploration: clean worktree on `main`; `just -l` confirmed app package commands and `app-test-e2e-media-mocked` are available. Created the suggested feature branch.
- Source review: `MediaLibraryView.vue` already matches trimmed lowercase queries against `assetId`, `originalFilename`, and `mediaType`; the composer composable creates `pickerAssets` independently of selection IDs.
- Test owners: `ComposerMediaPickerShell.test.ts`, `useComposerMediaPicker.test.ts`, `composer-media-attachments-mocked.spec.ts`, and `openspec/specs/e2e/composer-media-attachments-test-plan.md`.
- Shell tests: initial RED had 3 expected failures; search, visibility, filtered-empty, close-reset, hidden-selection apply, and keyboard-focus cases pass after implementation.
- Composable RED before implementation: the focused component/composable command exited 1 with 3 expected failures (2 missing composable search cases and the hidden-selection apply case); 54 other tests passed.
- Focused GREEN: the same component/composable Vitest command passed 2 files and 57/57 tests before review follow-up; after adding a keyboard-focus regression test, it passed 2 files and 58/58 tests.
- Full dashboard suite: `pnpm --filter app test:run` passed 164 test files and 1,873 tests. Vitest printed jsdom messages for canvas contexts/navigation, but no tests failed.
- Static checks: `pnpm --filter app type-check` passed. `pnpm --filter app lint` completed with one warning in unchanged `src/modules/publishing/presentation/components/mobile/SchedulerTimelineBody.vue:23` (`viewport` is unused); no diagnostics remained in changed files after formatting the two tests.
- Build: `just app-build` passed type-check and Vite production build after the focus-indicator change. Vite emitted dependency annotation warnings from Zod and a chunk-size warning (>500 kB).
- Playwright discovery: `pnpm --filter app exec playwright test -c e2e/playwright.media-mocked.config.ts --list --grep 'ML-COMPOSER-031'` found exactly one test. The targeted `pnpm --filter app exec playwright test -c e2e/playwright.media-mocked.config.ts --grep 'ML-COMPOSER-031'` run passed 1/1 after the runtime restart.
- Targeted Markdown lint passed for the task record and E2E plan; `git diff --check` passed.

## Troubleshooting

### Chromium launch failure during the initial full-lane attempt

The initial `just app-test-e2e-media-mocked` run could not launch Chromium because `libnspr4.so` was absent; it reported 38 browser-startup failures and 3 skipped tests. After the runtime restart, the targeted `ML-COMPOSER-031` run launched Chromium and passed 1/1. The full media lane was not rerun. If it again fails to launch Chromium, install the system dependency providing `libnspr4.so` and retry the lane.

An earlier default-config Markdown invocation expanded to repository globs and found 21 issues in two untouched files under `tmp/plans/`. The targeted check for the edited Markdown files passed; the unrelated files were left unchanged.

## References

- [GitHub issue #1268](https://github.com/dallay/profiletailors.com/issues/1268) — Linear issue DALLAY-617 mirror.
- [Pull request #1271](https://github.com/dallay/profiletailors.com/pull/1271) — implementation PR.
- `apps/web/app/src/modules/publishing/application/useComposerMediaPicker.ts` — picker filtering and selection state.
- `apps/web/app/src/modules/publishing/presentation/components/composer/ComposerMediaPickerShell.vue` — picker search UI.
- `apps/web/app/e2e/specs/composer-media-attachments-mocked.spec.ts` — mocked composer browser coverage.
- `openspec/specs/e2e/composer-media-attachments-test-plan.md` — composer media E2E contract.
