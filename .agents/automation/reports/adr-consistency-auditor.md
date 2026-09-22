# ADR Consistency Audit Report

## Purpose

Audit accepted Architecture Decision Records (ADRs) against the current codebase, tests, configuration, and specifications in accordance with the repository maintenance framework.

## Execution Result

`CHANGES_APPLIED`: Identified and resolved a duplicate ADR number collision (0022) by renumbering `0022-durable-admin-mutable-operational-configuration.md` to `0024-durable-admin-mutable-operational-configuration.md`, updating its title header to `# ADR-0024: Durable Admin-Mutable Operational Configuration`, and adding ADR 0024 to the index in `docs/architecture/adr/README.md`.

## Scope Inspected

- `docs/architecture/adr/` (ADR-0001 through ADR-0024)
- `docs/architecture/adr/README.md`
- `docs/architecture/adr-discovery/` and `docs/architecture/adr-enforcement/`
- OpenSpec specifications and historical change records citing ADRs

## Changes Applied

- Renamed `docs/architecture/adr/0022-durable-admin-mutable-operational-configuration.md` to `docs/architecture/adr/0024-durable-admin-mutable-operational-configuration.md`.
- Updated top header in `docs/architecture/adr/0024-durable-admin-mutable-operational-configuration.md` to `# ADR-0024: Durable Admin-Mutable Operational Configuration`.
- Added row 0024 to the ADR index table in `docs/architecture/adr/README.md`.

## Evidence Table

| ADR ID | Document File | Shipped Reality / Evidence | Status |
| :--- | :--- | :--- | :--- |
| ADR-0001 | `0001-use-a-modular-monolith-backend.md` | `server/smp` bounded contexts and Spring Modulith structure | CONSISTENT |
| ADR-0002 | `0002-adhere-to-hexagonal-architecture.md` | Domain/Application/Infrastructure layers across bounded contexts | CONSISTENT |
| ADR-0003 | `0003-mandatory-reactive-stack.md` | Spring WebFlux & R2DBC reactive stack in backend | CONSISTENT |
| ADR-0004 | `0004-implement-cqrs-via-mediator.md` | Mediator implementation and Command/Query separation | CONSISTENT |
| ADR-0005 | `0005-use-prefixed-string-identifiers.md` | Prefixed IDs (`usr_`, `wsp_`, etc.) across domain models | CONSISTENT |
| ADR-0006 | `0006-resource-creation-via-post.md` | REST resource creation endpoints use POST | CONSISTENT |
| ADR-0007 | `0007-astro-and-vue-frontend-split.md` | Marketing (Astro) & App/Admin (Vue 3 / Vite) split | CONSISTENT |
| ADR-0008 | `0008-application-level-multi-tenancy.md` | Application-level multi-tenancy context isolation | CONSISTENT |
| ADR-0009 | `0009-jwt-and-httponly-cookie-authentication.md` | HttpOnly cookie auth for JWT tokens | CONSISTENT |
| ADR-0010 | `0010-shared-kernel-governance.md` | Shared kernel module structure under `shared/` | CONSISTENT |
| ADR-0011 | `0011-reusable-lead-capture-waitlist.md` | Lead capture bounded context in `server/smp` | CONSISTENT |
| ADR-0012 | `0012-agpl-commercial-strategy.md` | AGPL-3.0 license declarations across repository | CONSISTENT |
| ADR-0013 | `0013-ratelimit-tier-vs-subscription-plan.md` | Rate limit tier and subscription plan domain separation | CONSISTENT |
| ADR-0014 | `0014-future-billing-architecture.md` | Billing bounded context boundaries | CONSISTENT |
| ADR-0015 | `0015-aggregate-root-as-sole-entry-point.md` | `AggregateBoundaryTest.kt` DDD boundary checks | CONSISTENT |
| ADR-0016 | `0016-aggregates-communicate-by-identity-only.md` | `IdentityOnlyAggregateCommunicationTest.kt` checks | CONSISTENT |
| ADR-0017 | `0017-value-objects-are-immutable.md` | `ValueObjectImmutabilityTest.kt` immutability checks | CONSISTENT |
| ADR-0018 | `0018-regional-data-residency-and-controlled-transfer-architecture.md` | Regional data residency headers and enforcement | CONSISTENT |
| ADR-0019 | `0019-mcp-write-tools.md` | `mcp-write-tools.feature` & MCP module implementation | CONSISTENT |
| ADR-0020 | `0020-first-class-invitation-aggregate.md` | Invitation aggregate in `platformadmin` | CONSISTENT |
| ADR-0021 | `0021-operational-event-safety-boundary.md` | Safety boundary in `shared/observability/` | CONSISTENT |
| ADR-0022 | `0022-release-driven-frontend-deployment.md` | `.github/workflows/release-please.yml` Cloudflare deploy | CONSISTENT |
| ADR-0023 | `0023-platformadmin-governance-application-takedown.md` | `platformadmin` takedown consumption of governance ports | CONSISTENT |
| ADR-0024 | `0024-durable-admin-mutable-operational-configuration.md` | `platform_operational_config` table & `RegistrationModeGateway` | CONSISTENT (renumbered from 0022) |

## Validation Table

| Check Name | Target | Status | Notes |
| :--- | :--- | :--- | :--- |
| `adr-index-alignment` | `docs/architecture/adr/README.md` | Passed | Confirmed all 24 ADRs are indexed with matching filenames and non-colliding IDs. |
| `adr-content-verification` | `docs/architecture/adr/` | Passed | Confirmed titles, statuses, and cross-references for all 24 ADRs. |
| `markdown-lint` | `docs/architecture/adr/` | Passed | `pnpm exec markdownlint-cli2` passed with 0 errors. |

## Unresolved Findings

None.

## Blockers

None.

## Automation State

- **Last Execution:** `2026-09-22T18:00:00Z`
- **Schema Version:** `1`
- **Task Identity:** `adr-consistency-auditor`

## Risk Assessment

- **Overall Risk:** LOW RISK. The change renumbers a duplicate ADR ID (0022 -> 0024) for operational configuration, updates the file's title header, and indexes it in `docs/architecture/adr/README.md`. No domain code or runtime logic was altered.

## Human Review Notes

Reconciled duplicate ADR 0022 ID collision between Release-Driven Frontend Deployment and Durable Admin-Mutable Operational Configuration by renumbering the operational configuration ADR to ADR-0024 and updating the index table in `docs/architecture/adr/README.md`.
