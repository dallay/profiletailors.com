# Profile Tailors Dashboard App

Authenticated Vue 3 single-page application providing the main social media management dashboard for Profile Tailors, enabling post scheduling, multi-account publishing, media management, analytics, and workspace settings.

## Role in the platform

Acts as the core user application surface (`https://pt-app.localhost`). It consumes REST APIs exposed by the Spring Boot backend (`server/smp`), relies on `@profiletailors/shared-web` for GDPR consent contracts, and uses shared web assets from `shared/assets/web/`. It serves authenticated team members and social media managers.

## Tech stack

- **Runtime & Language**: Node.js (`>=24.19.0`), TypeScript 6.0
- **Framework & State**: Vue 3.5, Pinia 4.0, Vue Router 5.2
- **UI & Styling**: Tailwind CSS 4.3, Reka UI (Radix Vue), shadcn-vue, Lucide Vue
- **Internationalization**: Vue I18n 11.4 (English & Spanish)
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

To run only the dashboard app:

```bash
just app
```

Target URL: `https://pt-app.localhost` (or `http://localhost:5173` fallback).

### Environment variables

| Variable | Required | Description | Default |
| --- | --- | --- | --- |
| `VITE_API_BASE_URL` | No | Target URL for Spring Boot backend API | `http://localhost:7638` |
| `PLAYWRIGHT` | No | Disables Vue DevTools overlay during Playwright test runs | `false` |
| `E2E_TEST_USER_PASSWORD` | No | Test user password for Playwright E2E HAR replay | `S3cr3tP@ssw0rd*123` |

## Project structure

```text
apps/web/app/
├── src/
│   ├── assets/      # App-specific stylesheets and icons
│   ├── components/  # Shared Vue UI components and shadcn primitives
│   ├── i18n/        # Bilingual dictionaries (English & Spanish)
│   ├── modules/     # Modular domain features (auth, media, scheduler, settings, workspace)
│   ├── router/      # Vue Router route definitions and navigation guards
│   ├── stores/      # Pinia state management stores
│   └── views/       # Top-level page views
├── public/          # Static favicons and images
└── e2e/             # Playwright E2E suites (scheduler & media mocked/real)
```

## Testing

### Unit tests

Run Vitest unit test suite:

```bash
pnpm --filter app test:run
```

Run unit tests with coverage:

```bash
pnpm --filter app test:coverage
```

### Type checking & linting

```bash
pnpm --filter app type-check
pnpm --filter app lint
```

### E2E tests

Run Playwright E2E test suites:

```bash
just app-test-e2e-media-mocked
just app-test-e2e-media-real
```

## API / Public interface

This single-page application registers client-side routes:

- `/login`, `/register` — Authentication flows
- `/dashboard` — Main overview and social engagement metrics
- `/scheduler` — Calendar and post scheduling view
- `/media` — Asset library and uploader
- `/settings` — Workspace configuration, member management, and privacy/consent controls

## Configuration

- `vite.config.ts`: Configures Tailwind CSS V4, Vue DevTools, and `@shared/assets` path aliases.
- `components.json`: shadcn-vue component generator configuration.

## Contributing

Please review the [Root CONTRIBUTING.md](../../../CONTRIBUTING.md) for workflow rules, commit conventions, and pull request guidelines.

## License

This project is licensed under the GNU Affero General Public License v3.0 (AGPL-3.0). See the [Root LICENSE](../../../LICENSE) for details.

---
Back to [Root README](../../../README.md)
