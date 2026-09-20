# Frontend component sharing

## Goal
Reduce duplicated presentation code between `apps/web/app` and `apps/web/admin` without coupling their domain, authentication, navigation, or layout boundaries.

## Route
Delegated direct implementation in Plan Mode. This is not an OpenSpec cycle because the user authorized a focused refactor and did not request durable product specifications.

## Scope

1. Create a framework-specific shared Vue UI boundary under `shared/vue-ui`.
2. Extract stable visual tokens into `shared/assets/web/design-tokens.css`.
3. Consolidate `VersionBadge` into the shared Vue UI package.
4. Share stable presentational primitives needed by admin: button, input, card, badge, table, empty state, and pagination controls where the existing app primitives are suitable.
5. Migrate admin views away from duplicated `.admin-button-*`, `.admin-input`, `.admin-card`, and `.admin-table` implementations without moving admin business logic.
6. Consolidate admin pagination contracts and date formatting in admin-local modules.
7. Keep app feature modules, admin stores, auth flows, layouts, navigation, and `shared/web` framework-neutral contracts separate.

## Non-goals

- Do not merge `useAuthStore` and `useAdminAuthStore`.
- Do not make admin import from `apps/web/app`.
- Do not move Vue components into `shared/web`.
- Do not redesign either product surface.
- Do not add a dependency unless the existing workspace dependencies cannot support the extraction.

## Implementation order

1. Add shared package metadata and tests for shared `VersionBadge` and primitives.
2. Extract tokens and update both application stylesheets to consume them.
3. Migrate `VersionBadge` in app and admin, deleting the duplicated local components and tests after equivalent coverage exists.
4. Add admin-local `PagedResult<T>` and date formatter modules; update affected views.
5. Migrate the smallest admin view first, then the remaining table/form views using shared primitives.
6. Run focused tests and type checks before broader frontend checks.
7. Inspect the final diff for cross-app imports, new comments, suppressions, unsafe assertions, and unrelated changes.

## Verification

- Shared Vue UI unit tests.
- Admin unit tests.
- App unit tests covering the migrated `VersionBadge`.
- `pnpm --filter admin type-check` and `pnpm --filter app type-check`.
- `pnpm --filter admin lint` and `pnpm --filter app lint`.
- Relevant app/admin builds if the shared package or Vite resolution requires bundler verification.

## Risks

- Moving shadcn-vue primitives can expose path aliases or package dependency assumptions.
- Tailwind class generation must continue to see shared component classes in both apps.
- Shared primitives must remain presentation-only; domain-specific admin classes and permission logic stay local.
- Existing worktree changes must remain untouched.
