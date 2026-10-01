# DALLAY-617: composer library search

## Route

Delegated direct. Add client-side library search to the composer picker using the Media Library's current filename, asset ID, and MIME-type matching semantics. Keep the shell presentational, preserve all picker selection/reconciliation state, and make no backend/API changes. No durable SDD cycle is requested.

## Tasks

- [x] RPI-001 Add shell tests for the library search input, emitted query, filtered-empty state, source visibility, reset on close, and applying selection IDs hidden by a filter.
- [x] RPI-002 Add composable tests for filename/asset ID/MIME-type matching, trimming/case-insensitivity, clearing, and preserved staged/pending IDs.
- [x] RPI-003 Implement the input event flow, composable filtered asset projection, close reset, and localized EN/ES labels and empty-state copy.
- [ ] RPI-004 Extend the existing mocked composer E2E plan and verify search + apply in the mocked media lane. The plan and scenario are updated and the test lists correctly; browser execution is blocked by missing `libnspr4.so`.
- [x] RPI-005 Run focused Vitest tests, app lint/type-check/build, attempt the mocked E2E lane, inspect the final diff, and record exact evidence here.

## Acceptance criteria

- Search is available only on the library source and is accessible with localized label and placeholder.
- Search trims input and matches `originalFilename`, `assetId`, and `mediaType` case-insensitively; clearing restores all assets.
- A no-match query has a filtered-empty state distinct from a truly empty library; loading/error states remain intact.
- Closing the picker clears the query without changing staged selection, pending reconciliation assets, auto-staged IDs, apply behavior, or attachment limits.
- `MediaLibraryView` behavior and the media API contract remain unchanged; filtering stays client-side.

## Evidence

- Initial exploration: clean worktree on `main`; `just -l` confirms app package commands and `app-test-e2e-media-mocked` are available. Created the suggested feature branch.
- Source review: `MediaLibraryView.vue` already matches trimmed lowercase query against `assetId`, `originalFilename`, and `mediaType`; the composer composable owns picker state and creates `pickerAssets` independently of selection IDs.
- Test/source owners: `ComposerMediaPickerShell.test.ts`, `useComposerMediaPicker.test.ts`, `composer-media-attachments-mocked.spec.ts`, and `openspec/specs/e2e/composer-media-attachments-test-plan.md`.
- Shell tests: initial RED had 3 expected failures; after the shell search UI and localization changes, search, visibility, filtered-empty, and close-reset cases pass. The newly added hidden-selection apply case exposes that deriving selected IDs from filtered assets would drop hidden staged IDs.
- Composable RED (before implementation): `pnpm --filter app exec vitest run src/modules/publishing/presentation/components/composer/ComposerMediaPickerShell.test.ts src/modules/publishing/application/useComposerMediaPicker.test.ts` exited 1 with exactly 3 expected failures (2 absent composable search API cases and the hidden-selection apply case); 54 other tests passed.
- Composable/shell GREEN: the same focused Vitest command now passes with 2 files and 57/57 tests.
- Full dashboard suite: `pnpm --filter app test:run` passed; 164 test files and 1,873 tests. Vitest printed jsdom messages for canvas contexts/navigation, but no tests failed.
- Static checks: `pnpm --filter app type-check` passed. `pnpm --filter app lint` completed with one warning in the unchanged `src/modules/publishing/presentation/components/mobile/SchedulerTimelineBody.vue:23` (`viewport` is unused); no diagnostics remained in changed files after running Biome formatting on the two tests.
- Build: `just app-build` passed type-check and Vite production build. Vite emitted dependency annotation warnings from Zod and a chunk-size warning (>500 kB).
- E2E: added `ML-COMPOSER-031` covering filename, asset ID, MIME type, filtered-empty, clearing, hidden staged selection, and apply; updated `openspec/specs/e2e/composer-media-attachments-test-plan.md`. Playwright `--list --grep 'ML-COMPOSER-031'` found exactly one test. `just app-test-e2e-media-mocked` could not launch Chromium because `libnspr4.so` is absent; 38 tests failed at browser startup and 3 were skipped, so the new scenario did not execute.
- Markdown/diff: the exact two edited Markdown files passed `pnpm exec markdownlint-cli2 --no-globs :plan/tasks/dallay-617-composer-library-search.md :openspec/specs/e2e/composer-media-attachments-test-plan.md` with 0 issues; `git diff --check` passed. An earlier default-config Markdown invocation expanded to repository globs and found 21 issues in two untouched `tmp/plans/` files; those were left unchanged.

## Status

- RPI-001–RPI-003: complete; focused tests pass and selection remains independent of the filtered projection.
- RPI-004: blocked at browser execution by missing `libnspr4.so`; the test plan and scenario are present and the scenario is discoverable.
- RPI-005: complete; focused/full unit tests, type-check, lint, build, Markdown, and diff checks are recorded. The E2E environment blocker remains visible.

## Next step

Install the Chromium system dependency providing `libnspr4.so` on the test host, then rerun `just app-test-e2e-media-mocked` to execute `ML-COMPOSER-031` before merge.
