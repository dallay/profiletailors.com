# Tasks: Composer Media Picker Shell

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~500–620 (additions-heavy, minimal deletions) |
| 400-line budget risk | Medium |
| Chained PRs recommended | Yes |
| Suggested split | PR 1 (shell + types + i18n + shell tests) → PR 2 (CreatePostModal wiring) |
| Delivery strategy | ask-on-risk |
| Chain strategy | pending (team decision) |

Decision needed before apply: Yes
Chained PRs recommended: Yes
Chain strategy: pending
400-line budget risk: Medium

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|------|------|-----------|-------|
| 1 | Standalone presentational shell: types, i18n, component, component tests | PR 1 | Base: main. ~440 lines; self-contained, no `CreatePostModal` changes |
| 2 | Wire trigger + parent-owned open state into `CreatePostModal` | PR 2 | Base: PR 1 branch (stacked) or main if PR 1 merged first; ~120 lines |

## Phase 1: Foundation — Types & i18n

- [x] 1.1 Create `apps/web/app/src/components/composer/composer-media-picker.types.ts` with `ComposerMediaPickerViewState`, `ComposerMediaPickerFilter`, `ComposerMediaPickerProps`, `ComposerMediaPickerSearchChange`, `ComposerMediaPickerFilterChange` (import `MediaAssetSummary` from `@/lib/media-api`; strict, no `any`).
- [x] 1.2 Add `composer.mediaPicker.*` keys (header, search, filter labels, close, loading, empty, error, ready, disabled, asset-grid region) to BOTH `en` and `es` objects in `apps/web/app/src/i18n/index.ts`.
- [x] 1.3 Run `just frontend-test` for `apps/web/app/src/i18n/i18n-keys.test.ts` to confirm en/es key parity.

## Phase 2: Shell Component (TDD)

- [x] 2.1 RED: Create `apps/web/app/src/components/composer/ComposerMediaPickerShell.test.ts` — failing tests for localized accessible names (en+es), loading/empty/error/ready/disabled rendering, disabled controls suppress emits, ready-state asset cards render, and typed `update:open`/`close`/`search-change`/`filter-change` emissions.
- [x] 2.2 GREEN: Create `apps/web/app/src/components/composer/ComposerMediaPickerShell.vue` using `@/components/ui/dialog` primitives + form controls; render header, close action, search/filter controls, asset-grid region, and state-specific body; emit typed payloads only when enabled.
- [x] 2.3 REFACTOR: Type props/emits from the types module; remove duplication; keep presentational — no store, `media-api`, fetch, mutation, or selection persistence.

## Phase 3: CreatePostModal Integration (TDD)

- [x] 3.1 RED: Extend `apps/web/app/src/components/CreatePostModal.test.ts` — failing assertions for trigger opens the shell, composer content preserved, keyboard/close dismissal returns focus to the trigger, and the parent observes shell emits.
- [x] 3.2 GREEN: Modify `apps/web/app/src/components/CreatePostModal.vue` — add the picker trigger, own `open` state, pass typed stub props, handle `close`/`search-change`/`filter-change` without store/API coupling.
- [x] 3.3 REFACTOR: Confirm no `mediaStore`/`media-api` import was added; keep wiring additive and scoped.

## Phase 4: Verification

- [x] 4.1 Run `just frontend-lint` and fix issues in changed files.
- [x] 4.2 Run `just frontend-test` for both new/modified test files; confirm every spec scenario asserts.
- [x] 4.3 Manually verify keyboard dismissal + focus return and composer-state preservation against the spec scenarios.
