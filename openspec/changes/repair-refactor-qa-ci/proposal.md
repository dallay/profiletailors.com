# Proposal: Repair Refactor QA CI

## Intent

Restore PR #477 (`refactor-qa`) CI after composable extraction and backend port migration. Repairs must reinstate existing build, type-check, lint, test, and Kotlin compilation contracts without changing product behavior.

## Scope

### In Scope
- Remove duplicate legacy declarations in `SchedulerView.vue` and restore the scheduler parser/build boundary.
- Reconcile Vue `ref`/`value` usage and barrel/export contracts across extracted composables before updating test mocks.
- Restore Kotlin port references and audit metadata compatibility after the migration.
- Apply targeted accessibility and Biome lint corrections required by CI.
- Run focused then affected CI-equivalent frontend and backend checks.

### Out of Scope
- Product, route, API, state, or visual behavior changes.
- Refactor redesign, new abstractions, dependency changes, or broad formatting.
- Test changes that mask runtime contract failures.

## Capabilities

Existing specs (`frontend-modularization`, `app-typecheck-remediation`, `visual-calendar`) already require behavior-preserving modularization and clean app gates. This change repairs implementation compliance only.

### New Capabilities
None.

### Modified Capabilities
None.

## Approach

Repair failures at their production boundary: first eliminate SchedulerView legacy duplicates; then make extracted composables, imports, and barrels agree on reactive runtime contracts; only then align mocks with the restored contracts. Replace stale Kotlin port usages and make audit metadata conform to the current port types. Finish with narrowly scoped a11y/lint fixes; validate in dependency order so downstream failures are not obscured.

## Affected Areas

| Area | Impact | Description |
|---|---|---|
| `apps/web/app/src/modules/publishing/views/SchedulerView.vue` | Modified | Remove duplicate legacy declarations. |
| `apps/web/app/src/modules/{dashboard,governance,media,publishing,settings}` | Modified | Restore composable reactive/import/export contracts. |
| `apps/web/app/src/shared/composables` | Modified | Align barrels and public composable exports. |
| `apps/web/app/src/**/*.test.*` | Modified | Update mocks only after runtime contracts are correct. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/{media,governance,publishing}` | Modified | Repair ports and audit metadata compatibility. |

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Mock repair conceals a production mismatch | Medium | Validate build/type-check before test-mock edits. |
| Port correction changes semantics | Low | Preserve existing interfaces and use focused backend tests. |
| Broad CI remains slow/noisy | Medium | Run focused gates first, then affected CI lanes. |

## Rollback Plan

Revert the repair commit(s), restoring the PR's pre-repair source and test contracts. No schema, API, dependency, or data migration is involved.

## Dependencies

- Existing Node, pnpm, Gradle, Vitest, Biome, and backend test infrastructure; no new dependencies.

## Success Criteria

- [ ] App build, type-check, Biome lint, and affected unit tests pass.
- [ ] Backend Kotlin compilation and affected unit/BDD/Postgres lanes pass.
- [ ] PR #477 CI checks resolve without behavior-changing diffs.
