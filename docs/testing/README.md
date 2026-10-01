# Testing

## Overview

How to run backend, frontend, and regression tests without guessing tags or environment variables.

## Quick path

1. Backend fast: `just backend-test-fast`
2. Backend BDD: `just infra-up`, then `just backend-bdd-fast`
3. Frontend: `just frontend-test` (marketing), `just admin-test` (admin)

## Usage

- Backend tags and env: [Test Tags and Env](./test-tags-and-env.md) — `just backend-test`, `just backend-test-postgres`, `just backend-bdd-fast`
- Mutation testing: [Mutation Testing](./mutation-testing.md) — mutflow baseline and DALLAY-544/545 scope
- Accessibility regression: [Accessibility Regression Strategy](./accessibility-regression-strategy.md) — marketing and app lanes

## Troubleshooting

- PostgreSQL suites need `just infra-up` first; Testcontainers fixtures use their own credential, not `.env`.
- `backend-check` excludes the two BDD suites by design; run `just backend-bdd-fast` separately for user-visible behavior.
- Do not use `@Tag("postgres")` or `@Tag("bdd")` to hide failures.

## References

- [Docs index](../README.md)
- [Coverage Setup](../codecov-setup.md)
- [SonarQube Setup](../sonarqube-setup.md)
