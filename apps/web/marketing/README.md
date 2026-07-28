# Profile Tailors Marketing Site

## Overview

The marketing site is the static-first Astro 6 application at `apps/web/marketing/`. It serves the
public Profile Tailors website, including English and Spanish locale routes, legal pages, consent
UI, and the client-side waitlist flow.

The app is part of the workspace. Use the root `just` command hub for normal development and
validation.

## Usage

### Start development

```bash
just dev-frontend
```

This starts the marketing site at `http://localhost:4321` and the dashboard through Portless. To
start only the marketing site from the repository root:

```bash
pnpm --filter marketing dev
```

### Build and check

```bash
just frontend-check
just frontend-build
just frontend-lint
just frontend-test
```

Preview the production build with:

```bash
just frontend-preview
```

### End-to-end tests

```bash
just frontend-test-e2e
```

Marketing Playwright specs are under `e2e/`. Consent behavior is covered by
`e2e/consent.spec.ts`.

## Structure

- `src/pages/` — localized routes and public pages.
- `src/components/` — Astro components and interactive islands.
- `src/i18n/` — English and Spanish marketing/legal copy.
- `src/styles/` — site-level styling and design tokens.
- `e2e/` — Playwright configuration and browser scenarios.

Shared web assets and consent primitives live under `shared/` and are consumed through workspace
aliases. See [consent-management.md](../../../docs/consent-management.md) for the cross-surface
consent contract.

## Troubleshooting

- Spanish copy is often longer than English; avoid fixed-width containers when changing layouts.
- If the named dashboard URL is unavailable while running both apps, install/start Portless; the
  marketing server itself remains available on port 4321.
- If dependencies are missing, run `just install` from the repository root.

## References

- [Repository onboarding](../../../docs/getting-started.md)
- [Astro/Vue split decision](../../../docs/architecture/adr/0007-astro-and-vue-frontend-split.md)
- [Consent management](../../../docs/consent-management.md)
- [Root documentation index](../../../docs/README.md)
