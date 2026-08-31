# OpenSpec — Product and Change Contracts

## Overview

`openspec/` is the source of truth for product behavior and spec-driven development (SDD). It
contains durable product specifications plus the proposal, design, task, application, and
verification artifacts for individual changes.

OpenSpec is limited to product, domain, user-facing, legal, privacy, security, and business
readiness contracts. CI and quality gates, dependency policy, architecture governance, refactors,
test-harness plans, and deployment plumbing belong in the repository's workflows, ADRs, skills,
testing documentation, or operational documentation instead of `openspec/specs/`. A change may
include technical design and verification evidence when it implements a product contract, but a
purely technical task must not create a standalone OpenSpec capability.

Use the repository [documentation index](../docs/README.md) for operational, architecture,
security, infrastructure, and onboarding documentation. Use this directory when you need to
understand what the product must do, why a change was made, or what evidence verifies it.

## Changes

- **2026-08-29**: `mcp-write-tools` archived to `archive/2026-08-29-mcp-write-tools/`. Added MCP
  write tools (create/edit/delete/cancel/retry publications) with idempotency, audit, rate limiting,
  and per-tool scope authorization. 3 units merged to main.
- **2026-08-03**: Updated password-recovery artifact references from `changes/password-recovery/...`
  to `changes/archive/2026-07-29-password-recovery/...` (dead-reference-cleaner automation).

## Structure

| Path               | Purpose                                   |
|--------------------|-------------------------------------------|
| `specs/`           | Current product and capability contracts. |
| `changes/<name>/`  | Active change artifacts.                  |
| `changes/archive/` | Completed or superseded change history.   |

An active change commonly contains:

- `proposal.md` — problem, scope, and intended outcome.
- `spec.md` — requirements and executable-style scenarios.
- `design.md` — technical approach and boundaries.
- `tasks.md` — sequenced implementation work.
- `apply-progress.md` — implementation evidence and progress.
- `verify-report.md` — independent verification result and remaining gaps.
- `state.yaml` — current phase, slice status, and next action when the change uses state tracking.

## Usage

When implementing a feature:

1. Locate the relevant contract under `specs/`.
2. Inspect any matching active change under `changes/`.
3. Treat `state.yaml` and the final verification report as the authoritative status for an active
   change.
4. Do not mark a change complete while verification reports untested or failing approved
   scenarios.
5. Archive a change only after implementation, verification, and source-spec synchronization are
   complete.

For the archived password-recovery work (completed and archived 2026-07-29), refer to:

- [Password-recovery state](changes/archive/2026-07-29-password-recovery/state.yaml)
- [Password-recovery specification](changes/archive/2026-07-29-password-recovery/spec.md)
- [Password-recovery task progress](changes/archive/2026-07-29-password-recovery/apply-progress.md)
- [Password-recovery verification report](changes/archive/2026-07-29-password-recovery/verify-report.md)

## Troubleshooting

If a ticket says a feature is complete but its OpenSpec directory is missing, the ticket is not
implementation evidence. Reconcile the ticket, the active/archive paths, the source code, and the
verification report before declaring release readiness.

If `state.yaml` and a report appear inconsistent, use the report's final verdict and the explicit
`next` field in state as the immediate work queue; resolve the inconsistency before archiving.

## References

- [Repository documentation](../docs/README.md)
- [Architecture documentation](../docs/architecture/README.md)
- [Contribution guide](../CONTRIBUTING.md)
