# Profile Tailors Dashboard

## Overview

The dashboard is the Profile Tailors Vue 3 single-page application. It uses Vue Router for
navigation, Pinia for client state, shadcn-vue/Reka UI for primitives, Vue I18n for English and
Spanish copy, and Vitest plus Playwright for verification.

The app is part of the workspace at `apps/web/app/`. Run commands from the repository root through
the `just` command hub rather than invoking package-manager or Gradle commands manually.

## Changes

- **2026-08-03**: Corrected AGENTS.md reference path from `../../../AGENTS.md` to `../../../.agents/AGENTS.md` (dead-reference-cleanup automation).

## Usage

### Start development

```bash
just app
```

The app is served through Portless at `https://pt-app.localhost`. To start both web surfaces:

```bash
just dev-frontend
```

### Build and check

```bash
just app-build
just frontend-lint
just frontend-test
```

For the local CI subset, use:

```bash
just ci-local
```

### End-to-end tests

Available app lanes are documented in [e2e/README.md](e2e/README.md). The command hub exposes the
main lanes:

```bash
just app-test-e2e-media-mocked
just app-test-e2e-media-real
```

Additional Playwright projects and fixtures live under `e2e/`.

## Structure

- `src/modules/` — feature slices and presentation components.
- `src/router/` — route definitions, metadata, and guards.
- `src/shared/` — shared UI, validation, i18n, and utilities.
- `e2e/` — Playwright configurations, fixtures, page objects, and specs.

Follow the repository architecture rules in [AGENTS.md](../../../.agents/AGENTS.md) and the relevant
frontend skills under `.agents/skills/`.

## Troubleshooting

- If the `.localhost` URL does not resolve, install/start Portless and run `portless proxy start`.
- If Node 22 tests fail because `localStorage.clear` is unavailable, use the repository's documented
  Node 22 local-storage file workaround before `just ci-local`.
- If an E2E lane needs backend data, start the required services with `just infra-up` and
  `just backend-run`.

## References

- [Repository onboarding](../../../docs/getting-started.md)
- [Frontend architecture decision](../../../docs/architecture/adr/0007-astro-and-vue-frontend-split.md)
- [E2E guide](e2e/README.md)
- [Root documentation index](../../../docs/README.md)
