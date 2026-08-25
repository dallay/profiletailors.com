# Profile Tailors Admin Portal

Internal Vue 3 single-page application for platform operators to manage waitlist signups, user accounts, system permissions, and audit logs for Profile Tailors.

## Role in the platform

Serves as an internal-only administration surface (`https://pt-admin.localhost`). It consumes administrative REST endpoints from the Spring Boot backend (`server/smp`), depends on `@profiletailors/shared-web` for shared validation utilities, and allows operators (such as system administrators) to review early-access waitlist entries and audit system activity.

## Tech stack

- **Runtime & Language**: Node.js (`>=22.12.0`), TypeScript 6.0
- **Framework & State**: Vue 3.5, Pinia 4.0, Vue Router 5.2
- **UI & Styling**: Tailwind CSS 4.3, Reka UI, shadcn-vue, Lucide Vue, TanStack Table
- **Internationalization**: Vue I18n 11.4
- **Testing**: Vitest 3.2 (Unit & Component)
- **Code Quality**: Biome 2.5 (Linting & Formatting)

## Getting started

### Prerequisites

- Node.js `>= 22.12.0`
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

Start development server with Portless routing:

```bash
pnpm --filter @profiletailors/admin dev
```

Target URL: `https://pt-admin.localhost` (or `http://localhost:5174` fallback).

### Environment variables

| Variable | Required | Description | Default |
| --- | --- | --- | --- |
| `VITE_API_BASE_URL` | No | Base URL for backend administrative endpoints | `http://localhost:7638` |

## Project structure

```text
apps/web/admin/
├── src/
│   ├── assets/      # Stylesheets and global styling
│   ├── i18n/        # Internationalization dictionaries
│   ├── layouts/     # Admin portal layout wrappers
│   ├── router/      # Admin route definitions and access control guards
│   ├── stores/      # Pinia authentication and management stores
│   └── views/       # Operations views (Waitlist, Users, Audit, Dashboard)
```

## Testing

### Unit and component tests

Run Vitest unit tests:

```bash
just admin-test
```

Run test suite with coverage:

```bash
pnpm --filter @profiletailors/admin test:coverage
```

### Type checking & linting

```bash
just admin-check
pnpm --filter @profiletailors/admin lint
```

### Build verification

```bash
just admin-build
```

## API / Public interface

This internal single-page application manages administrative views:

- `/login` — Admin authentication
- `/dashboard` — Operator metrics summary
- `/waitlist` — Early access waitlist review, search, and filtering
- `/users` — User account inspection and permission management
- `/audit` — System activity audit logs

## Configuration

- `vite.config.ts`: Configures Vue plugin, Tailwind CSS V4, and workspace path aliases.

## Contributing

Please review the [Root CONTRIBUTING.md](../../../CONTRIBUTING.md) for workflow rules, commit conventions, and pull request guidelines.

## License

This project is licensed under the GNU Affero General Public License v3.0 (AGPL-3.0). See the [Root LICENSE](../../../LICENSE) for details.

---
Back to [Root README](../../../README.md)
