# Runbooks

## Overview

Step-by-step operator procedures. Each runbook states audience, preconditions, and rollback before any destructive step.

## Quick path

1. Rollback: open [Production Rollback](./production-rollback.md), run `just production-status` first
2. Password recovery: open [Password Recovery](./password-recovery.md)
3. Record every command and its output before declaring done

## Usage

- [Production Rollback](./production-rollback.md) — production rollback path
- [Password Recovery](./password-recovery.md) — password recovery operations

## Troubleshooting

- If a runbook conflicts with `docs/infrastructure/` state, trust the infrastructure file plus `just production-status` output, then fix the runbook in the same change.
- Record actual commands run and their output; do not mark a runbook complete from memory.

## References

- [Docs index](../README.md)
- [Infrastructure](../infrastructure/README.md)
