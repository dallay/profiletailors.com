# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Stack

Node.js CLI, TypeScript, Zod 4, YAML, Vitest.

## Users

Platform operator (Yuniel Acosta) and CI pipeline. Used to validate the `docs/compliance/data-inventory.yaml` file before merge and in CI. Not a user-facing product — no surface, no public URL.

## Product Purpose

Ensure the GDPR-inspired data inventory document is well-formed, complete, and structurally valid before it reaches production or CI. Acts as a schema gate: if the inventory fails validation, CI fails.

## Positioning

Compliance tooling — a build hygiene check, not a product. The inventory document is the source of truth; this tool is the validator.

## Operating Context

- Runs locally: `node tools/compliance/check-data-inventory.ts [path]`
- Runs in CI: part of the `just ci` pipeline
- Default path: `docs/compliance/data-inventory.yaml` (can be overridden via argument)
- Exits with code 1 on validation failure; exits with code 0 on success
- Operates in the `tools/compliance/` pnpm workspace

## Capabilities and Constraints

- Parses YAML with the `yaml` npm package
- Validates against the `DataInventory` Zod schema (see `check-data-inventory.ts`)
- Schema covers: processing activities, purposes, personal data categories, data subjects, recipients, retention, security measures, evidence references, legal review status
- Validates schema version format (`major.minor`)
- Reports structured error messages with JSON path on failure
- Reads the file; does not write, modify, or transmit data

**Constraints:**

- No network calls, no external API calls
- No interactive mode
- No partial validation — all required fields must be present or the file fails
- GDPR-inspired but not legally verified — legal review status is a field in the schema, not an endorsement

## Brand Commitments

None.

## Evidence on Hand

- YAML inventory at `docs/compliance/data-inventory.yaml`
- Zod schema encodes all required fields, enums, and formats

## Product Principles

1. Strict gate — any schema violation blocks CI; no silent warnings
2. Actionable errors — messages include the field path and the validation rule that failed
3. No false passes — validation must be comprehensive; partial validation creates false confidence
4. Schema is the contract — the YAML file must match the schema, not the other way around

## Accessibility & Inclusion

No user-facing surface.
