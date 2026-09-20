# Tasks: version-badge-in-ui-frontends

## Overview

Track the build-time injection, UI components, frontend wiring, and verification required by the version badge proposal.

## Changes

### 1. Build-time injection

- [x] 1.1 Add `define` block in `apps/web/app/vite.config.ts` exposing `__APP_VERSION__`, `__GIT_SHA__`, `__BUILD_TIME__`.
- [x] 1.2 Add `define` block in `apps/web/admin/vite.config.ts` with the same globals.
- [x] 1.3 Add Vite `define` block in `apps/web/marketing/astro.config.mjs` with the same globals.
- [x] 1.4 Add TypeScript declaration files for the globals in each frontend (`src/types/build-info.d.ts`).
- [x] 1.5 Add `scripts/compute-build-info.mjs` that exports the short git SHA and ISO build time and is consumed by each Vite/Astro config.

### 2. Vue component (`apps/web/app` + `apps/web/admin`)

- [x] 2.1 Write `VersionBadge.spec.ts` first asserting it renders `v<version> · <sha>` and exposes build time as `title`.
- [x] 2.2 Implement `VersionBadge.vue` in `apps/web/app/src/shared/ui/`.
- [x] 2.3 Implement matching `VersionBadge.vue` in `apps/web/admin/src/shared/ui/` with the same contract.

### 3. Astro component (`apps/web/marketing`)

- [x] 3.1 Author `VersionBadge.astro` rendering the same shape.
- [x] 3.2 Add a Vitest assertion that confirms the structure and exposes the build time.

## Usage

### 4. Wiring

- [x] 4.1 Render `<VersionBadge />` inside the sidebar footer in `apps/web/app/src/layouts/AppShell.vue`.
- [x] 4.2 Render `<VersionBadge />` at the bottom of the admin sidebar in `apps/web/admin/src/layouts/AdminLayout.vue`.
- [x] 4.3 Render `<VersionBadge />` in `apps/web/marketing/src/layouts/Layout.astro`, conditionally hidden on the landing pages (`/` and `/es/`).

## Troubleshooting

### 5. Verification

- [x] 5.1 `pnpm --filter app test:run -- src/shared/ui/VersionBadge.spec.ts`
- [x] 5.2 `pnpm --filter admin test:run -- src/shared/ui/VersionBadge.spec.ts`
- [x] 5.3 `pnpm --filter marketing test --run src/components/VersionBadge.spec.ts`
- [x] 5.4 `pnpm --filter app type-check && pnpm --filter app lint`
- [x] 5.5 `pnpm --filter admin type-check && pnpm --filter admin lint`
- [x] 5.6 `pnpm --filter marketing check && pnpm --filter marketing lint`
- [x] 5.7 `pnpm --filter app build`, `pnpm --filter admin build`, `pnpm --filter marketing build`
- [x] 5.8 `git diff --check`

## References

- [Version badge proposal](./proposal.md)
