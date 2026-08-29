# Architecture Decision Records (ADR)

This directory contains the Architecture Decision Records for the Profile Tailors project.

## Purpose

ADRs capture significant architectural decisions, their context, and their consequences. They serve
as a historical record and a guide for current and future development.

## Lifecycle

- **Proposed**: The decision is under review.
- **Accepted**: The decision has been approved and implemented (or is being implemented).
- **Experimental**: The decision is being evaluated in a limited scope.
- **Deprecated**: The decision is still in the codebase but should not be followed for new work.
- **Superseded**: The decision has been replaced by a newer ADR.

## Status Definitions

- **MUST**: Mandatory requirement.
- **SHOULD**: Recommended, exceptions must be justified.
- **MAY**: Optional implementation.

## Index

| ID   | Title                                                                                    | Status   | Date       |
|------|------------------------------------------------------------------------------------------|----------|------------|
| 0001 | [Use a Modular Monolith Backend](./0001-use-a-modular-monolith-backend.md)               | Accepted | 2026-06-21 |
| 0002 | [Adhere to Hexagonal Architecture](./0002-adhere-to-hexagonal-architecture.md)           | Accepted | 2026-06-21 |
| 0003 | [Mandatory Reactive Stack](./0003-mandatory-reactive-stack.md)                           | Accepted | 2026-06-21 |
| 0004 | [Implement CQRS via Mediator](./0004-implement-cqrs-via-mediator.md)                     | Accepted | 2026-06-21 |
| 0005 | [Use Prefixed String Identifiers](./0005-use-prefixed-string-identifiers.md)             | Accepted | 2026-06-21 |
| 0006 | [Resource Creation via POST](./0006-resource-creation-via-post.md)                       | Accepted | 2026-06-21 |
| 0007 | [Astro & Vue Frontend Split](./0007-astro-and-vue-frontend-split.md)                     | Accepted | 2026-06-21 |
| 0008 | [Application-Level Multi-tenancy](./0008-application-level-multi-tenancy.md)             | Accepted | 2026-06-21 |
| 0009 | [JWT & HttpOnly Cookie Authentication](./0009-jwt-and-httponly-cookie-authentication.md) | Accepted | 2026-06-21 |
| 0010 | [Shared Kernel Governance](./0010-shared-kernel-governance.md)                           | Accepted | 2026-06-21 |
| 0011 | [Reusable Lead Capture Waitlist Capability](./0011-reusable-lead-capture-waitlist.md)    | Accepted | 2026-06-25 |
| 0012 | [AGPL-3.0 Commercial Strategy](./0012-agpl-commercial-strategy.md)                       | Accepted | 2026-07-17 |
| 0013 | [RateLimitTier vs SubscriptionPlan](./0013-ratelimit-tier-vs-subscription-plan.md)       | Accepted | 2026-07-22 |
| 0014 | [Future Billing Architecture](./0014-future-billing-architecture.md)                     | Accepted | 2026-07-22 |
| 0015 | [Aggregate Root Is the Sole Entry Point to an Aggregate](./0015-aggregate-root-as-sole-entry-point.md) | Accepted | 2026-08-08 |
| 0016 | [Aggregates Communicate by Identity Only Across Bounded Contexts](./0016-aggregates-communicate-by-identity-only.md) | Accepted | 2026-08-09 |
| 0017 | [Value Objects Are Immutable and Validate at Construction](./0017-value-objects-are-immutable.md) | Accepted | 2026-08-09 |
| 0018 | [Regional Data Residency and Controlled Transfer Architecture](./0018-regional-data-residency-and-controlled-transfer-architecture.md) | Accepted | 2026-08-26 |
| 0019 | [MCP Write Tools for Publication Lifecycle](./0019-mcp-write-tools.md) | Accepted | 2026-08-28 |

## Relationship with other docs

- **OpenSpec**: Describes specific changes or features.
- **C4**: Documents the current architecture visually.
- **AGENTS.md**: Defines implementation constraints for AI agents.
- **ADRs**: Preserve durable, cross-cutting architectural decisions.
