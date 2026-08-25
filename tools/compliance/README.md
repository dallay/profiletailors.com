# Compliance Tools (`@profiletailors/compliance-tools`)

Node.js CLI and validation workspace for verifying GDPR data inventories, compliance schemas, security configuration drift, and test suite hygiene across Profile Tailors repositories.

## Role in the platform

Acts as an automated compliance gate in local development and CI pipelines. It parses and validates `docs/compliance/data-inventory.yaml` against JSON Schema definitions using Zod and YAML parsers, ensuring data mapping, privacy disclosures, and security drift rules are strictly upheld before code is merged.

## Tech stack

- **Runtime & Language**: Node.js (`>=22.12.0`), TypeScript 5.9
- **Validation**: Zod 4.4, YAML 2.9, JSON Schema
- **Testing**: Vitest 4.1

## Getting started

### Prerequisites

- Node.js `>= 22.12.0`
- pnpm `>= 11.8.0`

### Installation

Installed automatically with monorepo dependencies:

```bash
just setup
```

### Running locally

Execute data inventory compliance validation:

```bash
pnpm --filter @profiletailors/compliance-tools exec tsx check-data-inventory.ts
```

### Environment variables

No specific environment variables required.

## Project structure

```text
tools/compliance/
├── schema/                 # JSON schema specifications (data-inventory-schema.json)
├── __tests__/              # Compliance test suites and artifact validators
├── check-data-inventory.ts # Main CLI validation script
└── tsconfig.json           # TypeScript configuration
```

## Testing

Run compliance tool unit test suite:

```bash
pnpm --filter @profiletailors/compliance-tools test
```

## API / Public interface

- `check-data-inventory.ts`: Executable CLI script that validates `docs/compliance/data-inventory.yaml`. Returns exit code `0` on success and non-zero with validation errors on failure.

## Configuration

- `schema/data-inventory-schema.json`: Canonical JSON schema enforcing required fields for data categories, storage retention, processing purpose, and third-party recipients.

## Contributing

Please review the [Root CONTRIBUTING.md](../../CONTRIBUTING.md) for workflow rules, commit conventions, and pull request guidelines.

## License

This project is licensed under the GNU Affero General Public License v3.0 (AGPL-3.0). See the [Root LICENSE](../../LICENSE) for details.

---
Back to [Root README](../../README.md)
