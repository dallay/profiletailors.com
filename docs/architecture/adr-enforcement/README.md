# ADR Enforcement

## Overview

Executable checks that keep ADR-0001, ADR-0002, ADR-0007, and ADR-0008 honest. Each file maps one ADR to its owners (tests, lint, docs).

## Usage

- [ADR-0001](./ADR-0001.md) — modular monolith boundaries
- [ADR-0002](./ADR-0002.md) — hexagonal architecture
- [ADR-0007](./ADR-0007.md) — frontend split
- [ADR-0008](./ADR-0008.md) — multi-tenancy

## Troubleshooting

- A failing enforcement check is design feedback, not a config to silence. Fix the code direction.
- Do not add a second equivalent architecture suite; the owners are `HexagonalArchTest` and `ComponentScanArchTest` plus Modulith/Konsist tests.

## References

- [ADR Index](../adr/README.md)
- [Architecture Overview](../README.md)
