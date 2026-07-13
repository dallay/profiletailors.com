# Frontend Modularization Specification

## Overview

### ADDED Requirements

This specification records Phase 1 frontend modularization requirements for relocating auth, workspace, and settings code into the Phase 0 module structure without changing runtime behavior.

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
- THEN they MUST NOT use legacy `@/views/*`, `@/components/dashboard/*`, `@/stores/*`, or dashboard-owned `@/lib/*` paths
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

## References

- Change archive: `openspec/changes/archive/2026-07-12-dallay-468-modularization-phase-1-auth-workspace-settings/`
- Phase 0 module alias: `@modules/*`
- shadcn-vue managed UI path: `apps/web/app/src/components/ui`
