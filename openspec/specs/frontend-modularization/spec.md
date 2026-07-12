# Delta for Frontend Modularization

## ADDED Requirements

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

### Requirement: Verification preserves behavior

The migration MUST be validated as a behavior-preserving file and import reorganization.

#### Scenario: Frontend verification passes

- GIVEN the modularization changes are complete
- WHEN `pnpm --filter app exec vitest run` and `pnpm --filter app exec biome check` run against the Vue SPA
- THEN both commands SHALL pass without module-resolution failures
- AND `just frontend-test` / `just frontend-lint` SHALL NOT be used as the sole verification target (those recipes target the marketing app)

#### Scenario: Known coupling is not redesigned

- GIVEN `auth-api.ts` still contains auth, workspace, fetch, and image proxy helpers
- WHEN it is moved for Phase 1
- THEN it SHALL be moved as-is into auth infrastructure
- AND API splitting or public barrel introduction MUST remain out of scope
