# Delta for Frontend Modularization

## ADDED Requirements

### Requirement: Dashboard module placement

Dashboard-owned frontend files MUST live under `apps/web/app/src/modules/dashboard/` using the
existing module layering convention.

#### Scenario: Dashboard views are module views

- GIVEN the dashboard modularization is applied
- WHEN a maintainer inspects `HomeView.vue` and `AnalyticsView.vue`
- THEN both files SHALL reside under `modules/dashboard/presentation/views/`
- AND route behavior for `/` and `/analytics` SHALL remain unchanged

#### Scenario: Dashboard presentation components are module-local

- GIVEN dashboard components and dashboard shared atoms are moved
- WHEN a maintainer inspects dashboard UI files and their tests
- THEN they SHALL reside under `modules/dashboard/presentation/components/`
- AND shadcn-vue primitives, generic app components, and `CreatePostModal` MUST NOT be moved in this
  phase

#### Scenario: Dashboard state and domain files are module-owned

- GIVEN dashboard stores, types, and mock data are moved
- WHEN a maintainer inspects dashboard, analytics, insights, and content-pipeline state
- THEN the stores SHALL reside under `modules/dashboard/infrastructure/`
- AND dashboard types SHALL reside under `modules/dashboard/domain/`
- AND dashboard mock data SHALL be module-local under `modules/dashboard/infrastructure/mock-data/`
  or an equivalent dashboard-owned path

### Requirement: Dashboard imports and tests use module paths

Source files, tests, router entries, and Vitest mocks MUST reference moved dashboard files through
`@modules/dashboard/...` or valid local relative imports.

#### Scenario: Legacy dashboard paths are removed

- GIVEN dashboard files have moved into the dashboard module
- WHEN source files, router entries, tests, or mocks import moved dashboard code
- THEN they MUST NOT use legacy `@/views/*`, `@/components/dashboard/*`, `@/stores/*`, or
  dashboard-owned `@/lib/*` paths
- AND they SHALL resolve through the dashboard module or colocated relative paths

#### Scenario: Existing dashboard behavior is preserved

- GIVEN imports now resolve from the dashboard module
- WHEN users load the dashboard, analytics page, dashboard widgets, stores, and mock-backed data
- THEN rendered UI, Pinia behavior, route loading, and test-observable data behavior SHALL match the
  pre-migration behavior
- AND no compatibility shim SHOULD be required for moved dashboard files

### Requirement: Dashboard modularization verification

The migration MUST be verified as a behavior-preserving relocation with app-specific checks and
relocation guards.

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
