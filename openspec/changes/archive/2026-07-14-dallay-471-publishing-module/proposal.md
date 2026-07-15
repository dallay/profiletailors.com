# Proposal: DALLAY-471 Publishing Module

## Intent

Complete frontend modularization phase 4 by relocating publishing-owned scheduler, composer, post detail/create post UI, publishing store, and related tests into `modules/publishing` without changing runtime behavior. Reconcile Linear shorthand "publishing + media stores" by keeping media state owned by `modules/media`, preserving DALLAY-469's media bounded-context decision.

## Scope

### In Scope
- Move scheduler/composer/post detail/create post publishing UI and tests under `apps/web/app/src/modules/publishing/`.
- Move `useComposerMediaPicker` under publishing application logic while keeping explicit media-store dependency injection.
- Move `publishing` Pinia store under publishing infrastructure and update all imports/mocks/routes.
- Move legacy `media` Pinia store under `modules/media/infrastructure`, not publishing.
- Extend relocation guard coverage for publishing module and media store paths.

### Out of Scope
- Product behavior, UI, API, route URL, Pinia state semantics, or scheduler/composer workflow changes.
- Moving shared layout/sidebar/app-shell, `SocialProviderIcon`, provider style utilities, or shadcn `components/ui/*`.
- Splitting stores, adding barrels/shims, changing media bounded-context ownership, or cleanup for DALLAY-472.

## Capabilities

### New Capabilities
None

### Modified Capabilities
- `frontend-modularization`: add phase 4 publishing/media-store relocation requirements.

## Approach

Use direct file relocation with import rewrites, no compatibility shims. Create `modules/publishing/views`, `presentation/components`, `presentation/components/composer`, `application`, and `infrastructure`. Route scheduler lazy imports to `@modules/publishing/views/SchedulerView.vue`. Keep media API/service and media store under `modules/media`; publishing media interaction must go through media domain/application-boundary communication and MUST NOT directly depend on another module's infrastructure internals beyond this behavior-preserving relocation step.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `apps/web/app/src/modules/publishing/` | New | Publishing view/components/composer/composable/store ownership |
| `apps/web/app/src/modules/media/infrastructure/media.store.ts` | New | Media store owner path, preserving DALLAY-469 |
| `apps/web/app/src/router/index.ts` | Modified | Scheduler route lazy imports |
| `apps/web/app/src/modules/{dashboard,settings,auth}/` | Modified | Cross-module publishing/media imports only |
| `apps/web/app/src/components/layout`, sidebar, queued-counts, toast | Modified | Import path updates only |
| `apps/web/app/src/modules/module-relocation.spec.ts` | Modified | Guard new module paths |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Moving media store to publishing would undo DALLAY-469 | Med | Keep media store in `modules/media/infrastructure` |
| Broad import/mock blast radius | High | Update source/tests/mocks together and run focused app checks |
| Dashboard depends on publishing composer | Med | Accept as existing product coupling; document dependency |
| Confusing publishing calendar components with shadcn calendar primitives | Med | Move only root publishing `CalendarCell/Header`, not `components/ui/calendar/*` |

## Rollback Plan

Revert the relocation commit to restore legacy file paths/imports. Because no behavior or schema changes are allowed, rollback is a git revert plus rerun focused app tests.

## Dependencies

- Existing DALLAY-469 media module ownership remains authoritative.
- Existing `@modules/*` alias and relocation guard infrastructure.

## Success Criteria

- [ ] Scheduler URLs, composer, post detail/create flows, media picker, and queued counts behave unchanged.
- [ ] No legacy imports remain for moved publishing store/view/components or moved media store.
- [ ] Relocation guard covers publishing and media store ownership.
- [ ] Focused Vue app Vitest, type-check, and Biome checks pass or unrelated failures are documented.
