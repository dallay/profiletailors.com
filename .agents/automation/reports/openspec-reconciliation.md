# OpenSpec Scope Reconciliation Report

## Purpose

Reconcile the OpenSpec inventory with the repository rule that OpenSpec contains product and
business contracts, not standalone engineering-task specifications.

## Execution Result

The audit completed with **SCOPE_CLEANUP_APPLIED**.

- Three active changes remain: `mcp-server`, the consent UX change, and
  `private-beta-launch-readiness`.
- Thirty-eight archived changes remain after removing technical-only history.
- Forty canonical specification directories remain under `openspec/specs/`.
- The removed artifacts covered quality gates, dependency policy, architecture governance,
  frontend refactors, type-check remediation, E2E harness plans, storage adapters/deduplication,
  deployment ingress, release pipeline work, and semantic PR workflow behavior.

## Scope Rule

OpenSpec is reserved for product, domain, user-facing, legal, privacy, security, accessibility,
and business-readiness contracts. Technical design and verification evidence may accompany a
product change, but CI/quality, architecture, refactor, test-harness, and deployment-only work
belongs in workflows, ADRs, skills, testing documentation, or operational documentation.

## Preserved Product Areas

Authentication and registration, waitlist and private beta, publishing and social content,
channels and OAuth, media authoring and attribution/takedown, dashboards, calendar and scheduler
behavior, email verification and recovery, legal/privacy/consent, IAM authorization, MCP
integration, and public capability contracts remain represented.

## Validation

| Check | Result |
|---|---|
| Active change directory inventory | Passed: 3 active changes |
| Archived change directory inventory | Passed: 38 retained product/business changes |
| Canonical spec directory inventory | Passed: 40 retained contracts |
| Deleted-path reference review | Passed after updating maintained reports/docs |

## Follow-up

Future standalone technical work must use the repository's workflows, ADRs, skills, testing docs,
or operational docs rather than adding a new OpenSpec capability.
