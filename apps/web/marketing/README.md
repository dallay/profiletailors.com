# Profile Tailors Marketing Site

[![Release](https://img.shields.io/github/v/release/dallay/profiletailors.com?filter=landing%40v*&display_name=tag&style=flat-square&color=2d3748&label=Release)](https://github.com/dallay/profiletailors.com/releases?q=landing%40v)
[![Deployed](https://img.shields.io/badge/dynamic/json?url=https%3A%2F%2Fprofiletailors.com%2Fversion.json&query=%24.version&prefix=v&style=flat-square&color=007ec6&label=Deployed)](https://profiletailors.com)
[![Astro](https://img.shields.io/badge/Astro-7.x-2d3748?style=flat-square&logo=astro&logoColor=ffffff)](https://astro.build)
[![TypeScript](https://img.shields.io/badge/TypeScript-6.x-2d3748?style=flat-square&logo=typescript&logoColor=3178c6)](https://www.typescriptlang.org)
[![Node.js](https://img.shields.io/badge/Node.js-24.x-2d3748?style=flat-square&logo=node.js&logoColor=5fa04e)](https://nodejs.org)
[![Tailwind CSS](https://img.shields.io/badge/Tailwind_CSS-4.x-2d3748?style=flat-square&logo=tailwindcss&logoColor=06b6d4)](https://tailwindcss.com)

Public-facing, static-first Astro 7 application serving product marketing content, bilingual landing pages, legal compliance policies, and the client-side waitlist acquisition flow for Profile Tailors.

## Role in the platform

Serves as the primary public entry point (`https://profiletailors.localhost`) for prospects and leads. It depends on `@profiletailors/shared-web` for GDPR consent contracts and validation, and sources shared branding assets from `shared/assets/web/`. It captures waitlist signups client-side and directs registered users to the dashboard application (`apps/web/app`).

## Tech stack

- **Runtime & Language**: Node.js (`>=24.19.0`), TypeScript 6.0
- **Framework**: Astro 7.2
- **Styling**: Tailwind CSS 4.3
- **Testing**: Vitest 3.2 (Unit), Playwright 1.62 (E2E)
- **Code Quality**: Biome 2.5 (Linting & Formatting)

## Getting started

### Prerequisites

- Node.js `>= 24.19.0`
- pnpm `>= 11.8.0`
- `just` task runner (`>= 1.30`)

### Installation

Install workspace dependencies from the monorepo root:

```bash
just setup
```

Or directly via pnpm:

```bash
pnpm install
```

### Running locally

Start development server along with Portless domain routing:

```bash
just dev-frontend
```

To run only the marketing site without other apps:

```bash
pnpm --filter marketing dev
```

Target URL: `https://profiletailors.localhost` (or `http://localhost:4321` fallback).

### Environment variables

| Variable | Required | Description | Default |
| --- | --- | --- | --- |
| `PUBLIC_SITE_URL` | No | Base site URL for canonical tags and OpenGraph meta | `https://profiletailors.com` |
| `PLAYWRIGHT` | No | Disables devtools overlay during Playwright test runs | `false` |

## Project structure

```text
apps/web/marketing/
├── src/
│   ├── components/  # Astro components and interactive islands
│   ├── i18n/        # English (en.ts) and Spanish (es.ts) copy dictionaries
│   ├── legal/       # Legal publication gate configurations
│   ├── pages/       # Localized Astro routes (English default, Spanish /es/)
│   └── styles/      # Tailwind styles and custom design tokens
├── public/          # Static favicons and public assets
├── tests/           # Vitest unit test suites
└── e2e/             # Playwright E2E browser test scenarios
```

## Testing

### Unit tests

Run Vitest unit tests:

```bash
just frontend-test
```

Run with coverage report:

```bash
pnpm --filter marketing test:coverage
```

### Type and content check

```bash
just frontend-check
```

### E2E tests

Run Playwright browser tests:

```bash
just frontend-test-e2e
```

### Linting

```bash
just frontend-lint
```

## API / Public interface

This subproject renders public web routes:

- `/` — English homepage & waitlist signup
- `/es/` — Spanish homepage & waitlist signup
- `/privacy/`, `/es/privacy/` — Privacy Policy
- `/terms/`, `/es/terms/` — Terms of Service
- `/cookies/`, `/es/cookies/` — Cookie Policy
- `/acceptable-use/`, `/es/acceptable-use/` — Acceptable Use Policy

## Configuration

- `astro.config.mjs`: Configures Tailwind CSS V4, Vite path aliases (`@shared/assets` pointing to `../../shared/assets`), and locale routing (`en` default, `es` prefixed).
- `src/legal/legal-publication.ts`: Controls publication gate status for legal pages.

## Contributing

Please review the [Root CONTRIBUTING.md](../../../CONTRIBUTING.md) for workflow rules, commit conventions, and pull request guidelines.

## License

This project is licensed under the GNU Affero General Public License v3.0 (AGPL-3.0). See the [Root LICENSE](../../../LICENSE) for details.

---
Back to [Root README](../../../README.md)
