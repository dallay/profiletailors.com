# Frontend Modularization Specification

## Overview

### ADDED Requirements

This specification records frontend modularization requirements across multiple phases:
- Phase 1: relocating auth, workspace, and settings code into the Phase 0 module structure
- Phase 5: removing root-level leftovers by relocating files to the smallest correct owner (shared, layouts, or owning modules)

## Changes

### Requirement: Phase 1 module placement

The application MUST relocate auth, workspace, and settings frontend code into the Phase 0 module structure without changing runtime behavior.

#### Scenario: Auth files are module-owned

- GIVEN the Phase 1 migration is applied
- WHEN a maintainer inspects auth views, store, API helper, and scoped tests
- THEN they SHALL reside under `apps/web/app/src/modules/auth/`
- AND views SHALL be under `presentation/` while Pinia/API files SHALL be under `infrastructure/`

#### Scenario: Workspace and settings files are module-owned

- GIVEN the Phase 1 migration is applied
- WHEN a maintainer inspects workspace and settings files
- THEN workspace store/modal SHALL reside under `modules/workspace/`
- AND settings view/store/tests SHALL reside under `modules/settings/`

### Requirement: Import and mock stability

All source, test, and Vitest mock references to moved files MUST use `@modules/*` paths, while existing product behavior SHALL remain unchanged.

#### Scenario: Legacy moved-path imports are removed

- GIVEN files have moved into auth, workspace, and settings modules
- WHEN source and tests import those moved files
- THEN they MUST NOT use legacy `@/stores/*`, `@/views/*`, `@/components/workspace/*`, or `@/lib/auth-api` paths
- AND they MUST resolve through the corresponding `@modules/<feature>/...` path

#### Scenario: Route and state behavior is preserved

- GIVEN routes and stores reference modularized files
- WHEN users authenticate, handle OAuth callback, select workspace state, or update settings
- THEN routes, rendered UI, Pinia state behavior, and API behavior SHALL match pre-migration behavior

#### Scenario: Test mocks follow moved paths

- GIVEN unit tests or component specs mock auth, workspace, settings, or auth API modules
- WHEN the test suite runs
- THEN mocks MUST target the new `@modules/*` import paths
- AND no test SHOULD depend on a legacy compatibility shim for moved files

### Requirement: shadcn-vue UI path exception

The migration MUST preserve the shadcn-vue managed `src/components/ui` location and imports.

#### Scenario: UI primitives stay at configured path

- GIVEN shadcn-vue primitives are used by moved module files
- WHEN those files are updated
- THEN imports for primitives SHALL remain `@/components/ui/*`
- AND `apps/web/app/components.json` MUST NOT be changed for this migration

#### Scenario: Non-shadcn feature UI moves only when scoped

- GIVEN feature-owned UI is part of auth, workspace, or settings scope
- WHEN it is moved
- THEN it SHALL live in that feature module's `presentation/` layer
- AND unrelated shared or shadcn-vue UI MUST NOT be moved

### Requirement: Dashboard module placement

Dashboard-owned frontend files MUST live under `apps/web/app/src/modules/dashboard/` using the existing module layering convention.

#### Scenario: Dashboard views are module views

- GIVEN the dashboard modularization is applied
- WHEN a maintainer inspects `HomeView.vue` and `AnalyticsView.vue`
- THEN both files SHALL reside under `modules/dashboard/presentation/views/`
- AND route behavior for `/` and `/analytics` SHALL remain unchanged

#### Scenario: Dashboard presentation components are module-local

- GIVEN dashboard components and dashboard shared atoms are moved
- WHEN a maintainer inspects dashboard UI files and their tests
- THEN they SHALL reside under `modules/dashboard/presentation/components/`
- AND shadcn-vue primitives, generic app components, and `CreatePostModal` MUST NOT be moved in this phase

#### Scenario: Dashboard state and domain files are module-owned

- GIVEN dashboard stores, types, and mock data are moved
- WHEN a maintainer inspects dashboard, analytics, insights, and content-pipeline state
- THEN the stores SHALL reside under `modules/dashboard/infrastructure/`
- AND dashboard types SHALL reside under `modules/dashboard/domain/`
- AND dashboard mock data SHALL be module-local under `modules/dashboard/infrastructure/mock-data/` or an equivalent dashboard-owned path

### Requirement: Dashboard imports and tests use module paths

Source files, tests, router entries, and Vitest mocks MUST reference moved dashboard files through `@modules/dashboard/...` or valid local relative imports.

#### Scenario: Legacy dashboard paths are removed

- GIVEN dashboard files have moved into the dashboard module
- WHEN source files, router entries, tests, or mocks import moved dashboard code
- THEN they MUST NOT use legacy `@/views/*`, `@/components/dashboard/*`, dashboard-owned `@/stores/*`, or dashboard-owned `@/lib/*` paths
- AND they SHALL resolve through the dashboard module or colocated relative paths

#### Scenario: Existing dashboard behavior is preserved

- GIVEN imports now resolve from the dashboard module
- WHEN users load the dashboard, analytics page, dashboard widgets, stores, and mock-backed data
- THEN rendered UI, Pinia behavior, route loading, and test-observable data behavior SHALL match the pre-migration behavior
- AND no compatibility shim SHOULD be required for moved dashboard files

### Requirement: Dashboard modularization verification

The migration MUST be verified as a behavior-preserving relocation with app-specific checks and relocation guards.

#### Scenario: App checks pass

- GIVEN the dashboard relocation is complete
- WHEN app-specific Vitest, lint, and type-check commands are run for `apps/web/app`
- THEN they SHALL pass without module-resolution, mock-path, lint, or type errors
- AND failures from unrelated app surfaces SHALL be reported separately, not hidden

#### Scenario: Relocation guard covers dashboard module

- GIVEN the project has a module relocation guard
- WHEN the guard is updated for dashboard modularization
- THEN it SHALL assert dashboard module paths resolve through `@modules/dashboard/...`
- AND it SHALL reject or expose reintroduction of legacy dashboard-owned root paths where applicable

### Requirement: Publishing module placement

Publishing-owned scheduler, composer, post detail/create-post UI, composable logic, Pinia store, and tests MUST live under `apps/web/app/src/modules/publishing/` using the existing module layering convention, without changing runtime behavior.

#### Scenario: Publishing-owned files are module-owned

- GIVEN the DALLAY-471 relocation is applied
- WHEN a maintainer inspects publishing scheduler, composer, post detail, create-post, store, composable, and scoped tests
- THEN they SHALL reside under `apps/web/app/src/modules/publishing/`
- AND view files SHALL be under `views/`, presentation files under `presentation/`, application logic under `application/`, and Pinia state under `infrastructure/`

#### Scenario: Legacy publishing roots are removed or guarded

- GIVEN publishing-owned files have moved into the publishing module
- WHEN source, tests, or relocation guards inspect legacy publishing-owned root paths
- THEN legacy `src/views/SchedulerView.vue`, publishing-owned `src/components/*`, `src/components/composer/*`, `src/composables/useComposerMediaPicker.ts`, and `src/stores/publishing.ts` paths MUST be absent or explicitly guarded
- AND no compatibility shim SHOULD be required for moved publishing files

### Requirement: Publishing behavior remains reachable

The relocation MUST preserve scheduler, composer, create-post, and post-detail user behavior.

#### Scenario: Scheduler route renders relocated view

- GIVEN the scheduler routes are loaded after relocation
- WHEN a user navigates to the existing scheduler URL
- THEN the route SHALL render the relocated `@modules/publishing/views/SchedulerView.vue`
- AND route URLs and rendered scheduler behavior SHALL match pre-relocation behavior

#### Scenario: Composer and post flows remain reachable

- GIVEN users can reach create-post, composer, and post-detail flows before relocation
- WHEN users open those flows from scheduler or dashboard entry points
- THEN the same dialogs, media picker behavior, store updates, and post-detail interactions SHALL remain reachable
- AND no route, UI, or Pinia state semantics MAY change as part of this relocation

### Requirement: Module imports and test mocks stay consistent

Source imports, router lazy imports, and Vitest mocks MUST reference moved publishing and media files through module paths or valid colocated relative paths.

#### Scenario: Publishing imports use module paths

- GIVEN publishing files have moved into `modules/publishing`
- WHEN source, router entries, tests, or mocks import scheduler, composer, publishing components, publishing store, or publishing composable code
- THEN they MUST use `@modules/publishing/...` or colocated relative paths
- AND they MUST NOT reference moved publishing files through legacy `@/views`, `@/components`, `@/components/composer`, `@/composables`, or `@/stores` paths

#### Scenario: Test mocks target relocated modules

- GIVEN tests mock publishing store, scheduler view, composer media picker, media store, or moved publishing components
- WHEN the test suite resolves those mocks
- THEN mock module identifiers SHALL match the relocated import paths
- AND tests SHALL NOT depend on stale legacy import strings

### Requirement: Media module ownership is preserved

DALLAY-471 MUST preserve DALLAY-469 media bounded-context ownership; media state SHALL remain under `modules/media`, not inside publishing, except for explicit publishing imports or adapters.

#### Scenario: Media store remains media-owned

- GIVEN the media Pinia store is relocated
- WHEN a maintainer inspects its destination
- THEN it SHALL reside under `apps/web/app/src/modules/media/infrastructure/`
- AND it MUST NOT be moved under `modules/publishing/`

#### Scenario: Publishing depends explicitly on media

- GIVEN publishing composer needs media state or media types
- WHEN publishing code consumes media functionality
- THEN it SHALL use media application/domain ports or an explicit adapter relationship
- AND it SHALL NOT import another module's infrastructure internals directly
- AND the media module's service/store ownership SHALL remain independent of publishing

## Usage

### Requirement: Verification preserves behavior

The migration MUST be validated as a behavior-preserving file and import reorganization.

#### Scenario: Frontend verification passes

- GIVEN the modularization changes are complete
- WHEN `pnpm --filter app exec vitest run` and `pnpm --filter app exec biome check` run against the Vue SPA
- THEN both commands SHALL pass without module-resolution failures
- AND `just ci` SHALL pass as a repo-level safeguard
- AND `just frontend-format` SHALL NOT be treated as a Vue SPA verification gate while it targets `apps/web/marketing` and fails because that package's Biome format command processes no files
- AND `just frontend-test` / `just frontend-lint` SHALL NOT be used as the sole verification target (those recipes target the marketing app)

## Troubleshooting

### Requirement: Known coupling is not redesigned

#### Scenario: Known coupling is not redesigned

- GIVEN `auth-api.ts` still contains auth, workspace, fetch, and image proxy helpers
- WHEN it is moved for Phase 1
- THEN it SHALL be moved as-is into auth infrastructure
- AND API splitting or public barrel introduction MUST remain out of scope

### Requirement: Phase 5 shared, layout, and cleanup placement

The application MUST remove root-level leftovers by relocating files to the smallest correct owner without changing runtime behavior.

#### Scenario: Composable placement is evaluated by ownership

- GIVEN a root `src/composables/*` file remains
- WHEN Phase 5 placement is applied
- THEN generic UI/runtime composables SHALL move to `@shared/composables`
- AND domain-specific composables MUST move to auth, media, or publishing modules

#### Scenario: Shared utilities, types, and i18n move to shared

- GIVEN root utilities, cross-module types, or app i18n are used by multiple modules
- WHEN they are relocated
- THEN they SHALL live under `@shared` using stable subfolders
- AND imports, tests, and mocks MUST resolve through the new paths

### Requirement: shadcn-vue compatibility boundary

The migration MUST preserve shadcn-vue compatibility by keeping generated primitives under `src/components/ui` and protecting `@/lib/utils` consumers.

#### Scenario: UI primitives remain generated-compatible

- GIVEN shadcn-vue components import `@/components/ui/*`
- WHEN Phase 5 cleanup runs
- THEN `components/ui` SHALL remain in place
- AND `components.json` MUST NOT be changed

#### Scenario: `@/lib/utils` risk is handled deliberately

- GIVEN generated UI imports `cn` from `@/lib/utils`
- WHEN utility files are evaluated for relocation
- THEN `@/lib/utils` MUST remain compatible or all generated imports MUST be updated consistently
- AND no broken shadcn import MAY remain

### Requirement: Empty legacy folder cleanup

Legacy root folders MUST be removed once emptied, except folders retained for approved compatibility.

#### Scenario: Empty folders are removed or justified

- GIVEN moved files leave empty `components`, `composables`, `lib`, `views`, or `i18n` subfolders
- WHEN cleanup is complete
- THEN empty legacy folders SHALL be deleted
- AND retained folders MUST have an explicit compatibility reason such as `components/ui`

### Requirement: Verification and Linear DoD

The relocation MUST satisfy the Linear DoD as a behavior-preserving cleanup.

#### Scenario: App checks and guards pass

- GIVEN relocation is complete
- WHEN app Vitest, lint/type checks, and relocation guards run
- THEN they SHALL pass or document unrelated failures
- AND guards MUST prevent reintroducing cleaned legacy paths

#### Scenario: Linear acceptance criteria are verifiable

- GIVEN reviewers inspect the change
- WHEN they compare shell, route, auth, media, publishing, shared, and shadcn behavior
- THEN no visual, route, state, API, or design-system behavior change SHALL be present
- AND root leftovers SHALL be moved or explicitly justified

## References

- Change archive: `openspec/changes/archive/2026-07-12-dallay-468-modularization-phase-1-auth-workspace-settings/`
- Phase 0 module alias: `@modules/*`
- shadcn-vue managed UI path: `apps/web/app/src/components/ui`
