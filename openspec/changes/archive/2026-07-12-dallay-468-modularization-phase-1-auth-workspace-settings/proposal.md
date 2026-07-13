# Proposal: DALLAY-468 Modularization Phase 1 — Auth, Workspace, Settings

## Intent

Move auth, workspace, and settings frontend code into Phase 0 feature-module structure so the app tree reflects product capabilities without changing runtime behavior.

## Scope

### In Scope
- Move auth views, store, API helper, and related tests into `modules/auth`.
- Move workspace store, workspace icon modal, and related imports into `modules/workspace`.
- Move settings view/store/tests into `modules/settings`.
- Update application imports, test imports, and Vitest mocks to `@modules/*`.
- Preserve routes, UI behavior, state behavior, API behavior, and shadcn-vue paths.

### Out of Scope
- Splitting `auth-api.ts` into shared/auth/workspace API clients.
- Moving `src/components/ui` or changing `components.json`.
- Introducing module public barrels or architecture enforcement tooling.
- Behavior changes to auth, OAuth callback, workspace selection, or settings.

## Capabilities

### New Capabilities
None — this is physical modularization only.

### Modified Capabilities
None — requirements remain unchanged for existing specs such as `registration`, `iam`, `oauth-callback-ui`, `app-shell`, and related UI/API behavior.

## Approach

Use the exploration recommendation: direct module moves with complete import/mock rewrites. Place Vue views/components under `presentation/` and Pinia/API files under `infrastructure/`. Keep `auth-api.ts` moved as-is for Phase 1, documenting its cross-feature coupling for later cleanup.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `apps/web/app/src/modules/auth` | New/Modified | Auth views, store, API helper, tests |
| `apps/web/app/src/modules/workspace` | New/Modified | Workspace store and icon modal |
| `apps/web/app/src/modules/settings` | New/Modified | Settings view, store, tests |
| `apps/web/app/src/router/index.ts` | Modified | Route imports and auth store import |
| `apps/web/app/src/main.ts` | Modified | Store imports |
| `apps/web/app/src/**/__tests__`, `*.spec.ts`, `*.test.ts` | Modified | Mock/import path rewrites |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Missed mocks/imports after path changes | Med | Run `just frontend-test` and fix module resolution failures |
| `auth-api.ts` creates awkward cross-module dependency | Med | Move as-is now; defer API decomposition to later phase |
| Settings remains mixed across settings/workspace/auth/publishing | Med | Preserve behavior; defer behavioral decomposition |
| shadcn-vue path accidentally moved | Low | Keep all `@/components/ui/*` imports unchanged |

## Rollback Plan

Revert the file moves and import rewrites in the DALLAY-468 change set. Since behavior is unchanged and no data/schema changes are planned, rollback is a git revert plus rerunning `just frontend-test`.

## Dependencies

- Phase 0 aliases/directories from DALLAY-467.
- Existing shadcn-vue config must keep `@/components/ui`.

## Success Criteria

- [ ] Auth, workspace, and settings files live under their target modules.
- [ ] No scoped source/tests continue importing moved files via legacy `@/stores/*`, `@/views/*`, or `@/lib/auth-api` paths.
- [ ] `src/components/ui` remains unmoved.
- [ ] `just frontend-test` passes.
- [ ] `just frontend-lint` passes.
