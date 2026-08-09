---
name: frontend-architecture
description: Use when designing, reviewing, or validating boundaries across the Vue/Pinia app, the flatter admin SPA, Astro marketing, or shared web contracts.
allowed-tools: Read, Edit, Write, Glob, Grep, Bash
metadata:
  author: profiletailors
  version: "1.0"
---

# Frontend Architecture Skill

Define language-appropriate boundaries for the repository's frontend surfaces. This skill is
architecture guidance, not a new analyzer: current verification uses the existing Vitest,
TypeScript, Biome, and Astro checks until a separate frontend-enforcement proposal is approved.
Do not apply Kotlin DDD markers or backend package rules to TypeScript, Vue, or Astro code.

## When to Use

- Adding or relocating a feature in `apps/web/app`.
- Reviewing imports between Vue feature modules or Pinia stores.
- Working in the flatter `apps/web/admin` platform-admin SPA.
- Changing static marketing pages, Astro components, or shared consent contracts.
- Deciding whether a generated shadcn-vue or composition-root import is an approved exception.

## Surface Profiles

| Surface | Architectural profile | Primary boundaries | Verification |
|---|---|---|---|
| `apps/web/app` | Vue 3 + Pinia dashboard SPA | Feature modules expose public barrels; internals stay private; presentation consumes feature APIs rather than another feature's infrastructure. | Existing module relocation/import tests, Vitest, `pnpm --filter app type-check`, Biome. |
| `apps/web/admin` | Separate, flatter Vue + Pinia platform-admin SPA | `views`, `stores`, `layouts`, `router`, and shared UI are intentionally flatter. Do not require dashboard module folders or dashboard feature barrels. | `just admin-test`, `just admin-check`, Biome. |
| `apps/web/marketing` | Static-first Astro marketing surface | `pages`, `components`, `layouts`, `scripts`, `i18n`, `legal`, and static assets. Do not import dashboard modules or admin internals. | `just frontend-check`, `just frontend-lint`, marketing Vitest; use the existing Astro commands. |
| `shared/web` | Dependency-light cross-frontend contract package | Types, validation, storage, and privacy utilities may be consumed by app, admin, and marketing. It must not import from any application surface. | Shared package Vitest and the consuming surface checks. |

## Dashboard App: Feature Modules

The primary app feature boundary is `apps/web/app/src/modules/<feature>`. A feature MAY use the
folders that exist for its concern:

```text
src/modules/<feature>/
├── domain/          # feature types and domain rules when needed
├── application/     # composables/use-case orchestration when needed
├── infrastructure/  # API clients, adapters, Pinia stores
├── presentation/    # Vue views and components
└── index.ts         # public feature entry point when cross-feature use is needed
```

The folders are an available profile, not a requirement to create empty layers. Keep a small
feature small; do not add backend-style ceremony without a boundary or test that benefits from it.

### Import Rules

- A feature's public cross-feature surface is its `src/modules/<feature>/index.ts` barrel. Export
  only stable types, composables, views, or adapters that callers are meant to use.
- Code in one feature MUST NOT import another feature's `infrastructure/**` internals directly.
  Use the other feature's public barrel or an explicitly named application adapter.
- Feature presentation should consume its own application/domain/infrastructure code through the
  feature's normal composition, not expose raw API clients or stores as a cross-feature contract.
- A feature MAY import shared UI components and `@profiletailors/shared-web`. `shared/web` must not
  import the feature back.
- Pinia stores are infrastructure/state adapters. Do not use another feature's store as a hidden
  transport boundary when a typed public API or adapter is appropriate.
- Aliases such as `@/` and package exports are preferred to fragile relative traversal across
  feature roots.
- A composition root such as the router, app bootstrap, or a top-level view MAY assemble feature
  stores and public APIs. Keep that exception local and explicit; it does not make an internal
  feature path public.

### Public Barrel Example

```ts
// src/modules/media/index.ts
export {useMediaStore} from './infrastructure/media.store';
export {useUploadAsset} from './application/useUploadAsset';
export type {MediaAsset} from './domain/media';
```

A consumer imports from `@/modules/media` rather than
`@/modules/media/infrastructure/media.store`. A barrel must not export every implementation detail
just to bypass a boundary review.

## Admin SPA Profile

`apps/web/admin` is a separate platform-admin application with its own router, layouts, views,
stores, i18n, and UI. Its flatter structure is deliberate:

```text
src/
├── views/
├── stores/
├── layouts/
├── router/
├── i18n/
└── components/       # when present
```

- Keep admin views and stores within the admin application unless a contract is intentionally moved
  to `shared/web` or a shared UI package.
- Do not move admin code into the dashboard's `src/modules/**` profile solely for symmetry.
- Admin composition roots may coordinate multiple admin stores and views.
- Admin may consume `@profiletailors/shared-web` and shared UI; `shared/web` must remain independent
  of admin implementation details.
- A dashboard feature rule must not reject valid admin imports, and an admin exception must not
  silently weaken dashboard feature boundaries.

## Marketing Astro Profile

`apps/web/marketing` is static-first and remains distinct from both Vue SPAs:

- `src/pages/` owns routes; localized pages use the existing `src/pages/es/` structure.
- `src/components/` owns reusable Astro components and islands.
- `src/layouts/` owns page shells and document structure.
- `src/scripts/` owns browser-side progressive enhancement and animation helpers.
- `src/i18n/` owns locale data and translation utilities; `src/legal/` owns publication metadata.
- Marketing components may consume `@profiletailors/shared-web` contracts, but must not import
  `apps/web/app` modules, admin views/stores, or backend Kotlin code.
- Keep static-first behavior: do not introduce a client island or runtime dependency when an Astro
  component or server-rendered/static path is sufficient.
- Localized content can be longer than English; do not introduce fixed-width architectural
  containers to compensate for copy differences.

The Astro profile is not a Vue feature profile. Do not require Pinia, `domain/application/
 infrastructure/presentation` folders, or Vue module barrels in marketing code.

## Shared Web Contract Package

`shared/web` is the narrow dependency-light package for contracts shared by app, admin, and
marketing. Its public exports are package exports and the root `index.ts`; current areas include
`types`, `validation`, and `utils`.

- Keep shared code framework-neutral and browser-safe for all consuming surfaces.
- Shared code MAY depend on its deliberately declared validation dependency, but MUST NOT import
  from `apps/web/app`, `apps/web/admin`, or `apps/web/marketing`.
- Put reusable consent types, schemas, storage, and privacy-signal helpers here when all relevant
  consumers need the same contract.
- Surface-specific UI, Pinia state, Astro components, and route behavior do not belong in
  `shared/web`.
- Preserve package exports when adding a public module; do not require consumers to reach through
  private file paths.

## Approved Exceptions

The following are approved composition or compatibility exceptions, not general bypasses:

1. **Composition roots:** router/bootstrap/top-level views may assemble multiple feature APIs and
   stores to render a screen or register routes.
2. **Shared UI:** generated or hand-maintained shadcn-vue primitives may be imported by any Vue
   surface according to the package's public exports. They are UI dependencies, not feature
   infrastructure.
3. **Generated shadcn files:** generated components may retain the structure produced by the
   generator. Do not hand-edit generated implementation merely to fit a feature-layer profile;
   wrap it at the feature boundary when needed.
4. **Shared contracts:** app, admin, and marketing may consume `@profiletailors/shared-web`.
   The reverse dependency is prohibited.
5. **Admin and marketing profiles:** flatter admin paths and Astro paths are governed by their own
   profiles above, not by dashboard rules.

When an exception is needed outside this list, document the composition root, dependency direction,
and rollback path in the change proposal before adding it.

## Anti-Patterns

- Importing `@/modules/other/infrastructure/...` from a feature or shared presentation component.
- Treating a Pinia store as a public cross-feature contract without a typed adapter or barrel.
- Putting app/admin implementation in `shared/web` because two consumers happen to need it.
- Importing Vue dashboard or admin code into Astro marketing.
- Requiring backend DDD annotations, Kotlin folder names, or Spring checks in frontend code.
- Creating an `index.ts` barrel that exports all internals with no stable API intent.
- Adding a new architecture dependency or unified gate without an approved proposal and clean
  baseline.

## Verification

Run the existing repository recipes for the affected surface:

```bash
just frontend-lint
just frontend-check
just frontend-test
just admin-test
just admin-check
pnpm --filter app type-check
```

For `shared/web`-only changes, run its package test through the repository's existing frontend
verification flow or the package's declared Vitest runner. Do not invent a frontend architecture
analyzer in this guidance change. A future checker must report its surface and contract owner and
must not scan Astro or `shared/web` with dashboard rules.

## References

- `.agents/skills/architecture-governance/SKILL.md`
- `.agents/skills/frontend-platform/vue/SKILL.md`
- `.agents/skills/frontend-platform/astrolicious-astro/SKILL.md`
- `apps/web/app/src/modules/`
- `apps/web/admin/src/`
- `apps/web/marketing/src/`
- `shared/web/`
