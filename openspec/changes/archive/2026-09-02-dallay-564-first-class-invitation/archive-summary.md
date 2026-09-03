# Archive Summary: First-Class Invitation (DALLAY-564)

## Delivered

DALLAY-564 promotes `Invitation` to the canonical standalone authorization to register, separate from waitlist entries, provisioning, notifications, and token handoff. The delivered capability includes DDD marker coverage, construction and source invariants, semantic lifecycle transitions, explicit expiration, acceptance metadata, canonical repository transitions, exactly-once concurrent acceptance, additive PostgreSQL schema protections, safe audit boundaries, legacy compatibility, and reviewable documentation.

## Evidence

- `verify-report.md` records PASS evidence for 81/81 in-scope tests, including domain lifecycle, repository CAS/locking/concurrent acceptance race, handler orchestration, security boundary, Liquibase hardening, HTTP registration, architecture tests, Detekt, Spotless, and diff checks.
- `qa-report.md` records final QA verdict PASS with no unresolved CRITICAL/P0/P1 findings and no acceptance-relevant BLOCKED or NOT TESTED scenarios.
- `tasks.md` records units 1, 2, and 3 as completed and verified; archive state records final QA and archive completion.
- `openspec/specs/invitations/spec.md` is now the promoted source-of-truth specification for the `invitations` capability.

## Out of Scope

- DALLAY-565 notification integration.
- DALLAY-566 concrete secure token generation, hashing, lookup, enforcement, and handoff.
- DALLAY-567 registration provisioning beyond consuming the invitation acceptance boundary.
- DALLAY-568 admin creation and revocation commands.
- DALLAY-570 waitlist conversion and entry-state migration.
- Replacing `WaitlistInvitation` flows, full DALLAY-556, UI, bulk operations, or destructive migration.

## Archive Result

Archive gate satisfied on 2026-09-02T13:46:04Z. The invitations delta spec was promoted to `openspec/specs/invitations/spec.md`, and the completed change was archived to `openspec/changes/archive/2026-09-02-dallay-564-first-class-invitation/`.
