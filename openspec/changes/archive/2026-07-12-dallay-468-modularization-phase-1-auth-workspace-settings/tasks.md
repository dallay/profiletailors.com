# Tasks: DALLAY-468 Modularization Phase 1 — Auth, Workspace, Settings

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | 250-380 |
| 400-line budget risk | Medium |
| Chained PRs recommended | No |
| Suggested split | Single relocation PR |
| Delivery strategy | single-pr |
| Chain strategy | size-exception |

Decision needed before apply: No
Chained PRs recommended: No
Chain strategy: size-exception
400-line budget risk: Medium

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|------|------|-----------|-------|
| 1 | Move auth/workspace/settings files and rewrite imports | PR 1 | Include tests, lint, Linear evidence |

## Phase 1: Regression Guard / TDD Baseline

- [x] 1.1 Run `just frontend-test` before moving files; capture current failures/pass state for regression comparison.
- [x] 1.2 Run the optional `rg "@/(stores/(auth|workspace|settings)|lib/auth-api|views/(AuthView|SettingsView|LinkedInCallbackView)|components/workspace/WorkspaceIconModal)" apps/web/app/src` baseline and save matches to guide rewrites.

## Phase 2: File Relocation

- [x] 2.1 Move `src/views/AuthView.vue`, `LinkedInCallbackView.vue`, and their specs into `src/modules/auth/presentation/`.
- [x] 2.2 Move `src/stores/auth.ts` and auth store tests to `src/modules/auth/infrastructure/auth.store.ts`.
- [x] 2.3 Move `src/lib/auth-api.ts` and API tests to `src/modules/auth/infrastructure/auth-api.ts` unchanged.
- [x] 2.4 Move `src/stores/workspace.ts` to `src/modules/workspace/infrastructure/workspace.store.ts`.
- [x] 2.5 Move `src/components/workspace/WorkspaceIconModal.vue` to `src/modules/workspace/presentation/components/`.
- [x] 2.6 Move `src/views/SettingsView.vue`, settings view specs, and `src/stores/settings.ts` tests into `src/modules/settings/` layers.

## Phase 3: Import and Mock Rewrite

- [x] 3.1 Update `src/router/index.ts` and `src/main.ts` to import views/stores from `@modules/*`.
- [x] 3.2 Rewrite runtime consumers in `src/views`, `src/components`, `src/layouts`, `src/stores`, and `src/lib` from legacy auth/workspace/settings paths to `@modules/*`.
- [x] 3.3 Rewrite all `vi.mock()` and test imports from `@/stores/*` and `@/lib/auth-api` to the moved module paths.
- [x] 3.4 Keep all shadcn-vue imports as `@/components/ui/*`; do not edit `apps/web/app/components.json`.

## Phase 4: Verification

- [x] 4.1 Run `just frontend-test`; fix only module-resolution or mock-path regressions caused by the move.
- [x] 4.2 Run `just frontend-lint`; fix formatting/lint issues from relocated imports.
- [x] 4.3 Re-run the legacy-path `rg` check and confirm moved-path imports/mocks are gone.

## Phase 5: Linear / PR Readiness

- [x] 5.1 Confirm DALLAY-468 DoD: files moved, behavior unchanged, no shims, UI primitives unmoved.
- [x] 5.2 Prepare PR notes with test evidence, known `auth-api.ts` boundary exception, and rollback via git revert.
