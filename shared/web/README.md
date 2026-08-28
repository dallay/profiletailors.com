# Shared Web Workspace (`@profiletailors/shared-web`)

Framework-agnostic TypeScript library defining canonical GDPR consent models, Zod validation schemas, localStorage persistence utilities, and privacy signal detectors shared across web applications.

## Role in the platform

Provides a single source of truth for user consent and privacy standards across the Profile Tailors ecosystem. It is imported as a workspace package (`@profiletailors/shared-web`) by `apps/web/marketing`, `apps/web/app`, and `apps/web/admin`, ensuring consistent consent validation, analytics loading gates (`window.__PT_CONSENT_ANALYTICS`), and privacy header compliance (DNT/GPC).

## Tech stack

- **Runtime & Language**: Node.js (`>=24.19.0`), TypeScript 6.0
- **Validation**: Zod 4.4
- **Testing**: Vitest 3.2 with JSDOM environment

## Getting started

### Prerequisites

- Node.js `>= 24.19.0`
- pnpm `>= 11.8.0`

### Installation

Install as part of the workspace dependencies from the monorepo root:

```bash
just setup
```

### Running locally

This package is compiled and bundled automatically as an ESM module when consumed by web applications via pnpm workspace link.

### Environment variables

No specific environment variables are required for this shared package.

## Project structure

```text
shared/web/
├── types/         # TypeScript interfaces (ConsentReceipt, ConsentCategory, PrivacySignals)
├── utils/         # Storage managers (loadConsent, saveConsent) and privacy signal detectors
├── validation/    # Zod schemas (validateConsentReceipt) and consent policy versions
├── index.ts       # Module entry point exporting public contract
└── vitest.config.ts # Unit test runner configuration
```

## Testing

Run unit tests across shared web utilities:

```bash
pnpm --filter @profiletailors/shared-web test:run
```

Run unit tests with coverage:

```bash
pnpm --filter @profiletailors/shared-web test:coverage
```

## API / Public interface

Main exports from `./index.ts`:

- `validateConsentReceipt(data: unknown)`: Validates raw consent payloads against Zod schema.
- `loadConsent()` / `saveConsent(receipt)`: Manages `pt-consent` key in `localStorage`.
- `detectPrivacySignals()`: Checks `navigator.doNotTrack` and `navigator.globalPrivacyControl`.
- `EXPECTED_CONSENT_VERSION`: Version string constant for receipt compatibility checks.

## Configuration

- `package.json`: Configures subpath exports for `./types/*`, `./utils/*`, and `./validation/*`.

## Contributing

Please review the [Root CONTRIBUTING.md](../../CONTRIBUTING.md) for workflow rules, commit conventions, and pull request guidelines.

## License

This project is licensed under the GNU Affero General Public License v3.0 (AGPL-3.0). See the [Root LICENSE](../../LICENSE) for details.

---
Back to [Root README](../../README.md)
