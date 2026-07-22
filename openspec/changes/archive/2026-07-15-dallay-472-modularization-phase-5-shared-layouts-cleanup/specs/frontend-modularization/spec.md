# Delta for Frontend Modularization

## ADDED Requirements

### Requirement: Phase 5 shared, layout, and cleanup placement

The application MUST remove root-level leftovers by relocating files to the smallest correct owner
without changing runtime behavior.

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

The migration MUST preserve shadcn-vue compatibility by keeping generated primitives under
`src/components/ui` and protecting `@/lib/utils` consumers.

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

Legacy root folders MUST be removed once emptied, except folders retained for approved
compatibility.

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
