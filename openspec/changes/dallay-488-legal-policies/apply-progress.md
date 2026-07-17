# Apply Progress — PR 1 (Foundation)

**Change**: dallay-488-legal-policies
**Phase**: apply
**PR**: 1 — Foundation
**Date**: 2026-07-17

## Completed Tasks

### task-01 [x] — i18n: Add `legal` key skeleton and `footer.legalLinks` to both locales

- **Files modified**:
  - `apps/web/marketing/src/i18n/en.ts` — added `legal` key (privacy: 14 keys, terms: 13 keys, cookies: 9 keys, aup: 7 keys) with empty string values; added `footer.legalLinks` array with 4 EN entries
  - `apps/web/marketing/src/i18n/es.ts` — same structure mirrored with translated footer labels
  - `apps/web/marketing/src/i18n/utils.test.ts` — added 3 tests verifying legal structure and footer.legalLinks
- **Evidence**: All 9 i18n tests pass (3 new + 6 existing), TypeScript compiles, build succeeds

### task-02 [x] — Layout: Add `canonicalPath` prop and `sameAs` JSON-LD

- **File modified**: `apps/web/marketing/src/layouts/Layout.astro`
- **Changes**:
  - Added optional `canonicalPath` prop (string, default `'/'`) — replaced hardcoded root-URL canonical computation with dynamic path-based URLs for both EN and ES locales
  - Added optional `sameAs` prop (string[]) — appended to JSON-LD `provider.Organization` block when provided
  - Dynamic hreflang alternates now use `canonicalPath`-derived URLs
- **Note**: `jsonLdType` prop deferred to PR 2 (not in PR 1 scope per the implementation request)

### task-03 [x] — Footer: Add `legalLinks` prop and render legal `<nav>` group

- **File modified**: `apps/web/marketing/src/components/Footer.astro`
- **Changes**:
  - Added optional `legalLinks` prop (`Array<{ label: string; href: string }>`)
  - Renders `<nav aria-label="Legal">` with a `border-t` separator when prop is provided
  - Styled consistently with existing Nav link pattern (`font-mono text-xs text-text-muted hover:text-text-display`)
  - Backward compatible — footer renders without legal links when prop is omitted

## Verification

- **Tests**: 26/26 pass (`npx vitest run`)
- **TypeScript**: Compiles cleanly (pre-existing `baseUrl` deprecation only)
- **Build**: `astro build` completes — 2 pages built (index, /es/)

## Review Workload

- **Forecast**: ~100 lines (within 400-line budget)
- **Delivery strategy**: Chained PRs (PR 1 is the first autonomous slice)
- **Budget concern**: None — PR 1 is well under 400 changed lines

## Deviations from Design

- `jsonLdType` prop not added to Layout.astro — deferred to PR 2 where shared components (which need `jsonLdType="WebPage"`) are created. PR 1 scope only includes `sameAs` and `canonicalPath`.

## Remaining Tasks for PR 2+

- task-04: Draft EN Privacy Policy content
- task-05: Draft EN Terms of Service content
- task-06: Draft EN Cookie Policy + AUP content
- task-07: Draft ES translations for all four policies
- task-08 → task-11: Create shared page components
- task-12: Create 8 thin route wrappers
- task-13: Wire Footer links into _HomePage.astro
- task-14: Verify all 8 pages build and render
