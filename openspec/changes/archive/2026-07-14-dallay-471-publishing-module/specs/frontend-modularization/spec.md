# Delta for Frontend Modularization

## ADDED Requirements

### Requirement: Publishing module placement

Publishing-owned scheduler, composer, post detail/create-post UI, composable logic, Pinia store, and
tests MUST live under `apps/web/app/src/modules/publishing/` using the existing module layering
convention, without changing runtime behavior.

#### Scenario: Publishing-owned files are module-owned

- GIVEN the DALLAY-471 relocation is applied
- WHEN a maintainer inspects publishing scheduler, composer, post detail, create-post, store,
  composable, and scoped tests
- THEN they SHALL reside under `apps/web/app/src/modules/publishing/`
- AND view files SHALL be under `views/`, presentation files under `presentation/`, application
  logic under `application/`, and Pinia state under `infrastructure/`

#### Scenario: Legacy publishing roots are removed or guarded

- GIVEN publishing-owned files have moved into the publishing module
- WHEN source, tests, or relocation guards inspect legacy publishing-owned root paths
- THEN legacy `src/views/SchedulerView.vue`, publishing-owned `src/components/*`,
  `src/components/composer/*`, `src/composables/useComposerMediaPicker.ts`, and
  `src/stores/publishing.ts` paths MUST be absent or explicitly guarded
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
- THEN the same dialogs, media picker behavior, store updates, and post-detail interactions SHALL
  remain reachable
- AND no route, UI, or Pinia state semantics MAY change as part of this relocation

### Requirement: Module imports and test mocks stay consistent

Source imports, router lazy imports, and Vitest mocks MUST reference moved publishing and media
files through module paths or valid colocated relative paths.

#### Scenario: Publishing imports use module paths

- GIVEN publishing files have moved into `modules/publishing`
- WHEN source, router entries, tests, or mocks import scheduler, composer, publishing components,
  publishing store, or publishing composable code
- THEN they MUST use `@modules/publishing/...` or colocated relative paths
- AND they MUST NOT reference moved publishing files through legacy `@/views`, `@/components`,
  `@/components/composer`, `@/composables`, or `@/stores` paths

#### Scenario: Test mocks target relocated modules

- GIVEN tests mock publishing store, scheduler view, composer media picker, media store, or moved
  publishing components
- WHEN the test suite resolves those mocks
- THEN mock module identifiers SHALL match the relocated import paths
- AND tests SHALL NOT depend on stale legacy import strings

### Requirement: Media module ownership is preserved

DALLAY-471 MUST preserve DALLAY-469 media bounded-context ownership; media state SHALL remain under
`modules/media`, not inside publishing, except for explicit publishing imports or adapters.

#### Scenario: Media store remains media-owned

- GIVEN the media Pinia store is relocated
- WHEN a maintainer inspects its destination
- THEN it SHALL reside under `apps/web/app/src/modules/media/infrastructure/`
- AND it MUST NOT be moved under `modules/publishing/`

#### Scenario: Publishing depends explicitly on media

- GIVEN publishing composer needs media state or media types
- WHEN publishing code consumes media functionality
- THEN it SHALL import from `@modules/media/...` or use an explicit adapter relationship
- AND the media module's service/store ownership SHALL remain independent of publishing
