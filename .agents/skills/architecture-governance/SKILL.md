---
name: architecture-governance
description: Use when defining, reviewing, or verifying repository architecture contracts, ADR ownership, bounded-context boundaries, or architecture-check rollout policy.
allowed-tools: Read, Edit, Write, Glob, Grep, Bash
metadata:
  author: profiletailors
  version: "1.0"
---

# Architecture Governance Skill

Use this skill as the shared vocabulary for architecture decisions in the Profile Tailors
monorepo. It owns the `ARCH-001..005` contract matrix and routes implementation work to the
language-specific backend and frontend skills. It does not replace executable enforcement or
pretend that Kotlin, Vue/TypeScript, and Astro have identical boundary semantics.

## When to Use

- Reviewing or proposing a change to a bounded-context or layer boundary.
- Mapping a new or existing architecture rule to an ADR and its executable owner.
- Deciding whether a violation is an approved exception, a warning, or a blocking failure.
- Planning a future `just architecture-check` gate.
- Choosing between `ddd-architecture`, `hexagonal-architecture`, and frontend architecture
  guidance.

## Contract Matrix

The repository has exactly five shared architecture contracts. The `ARCH` identifier is the
stable governance label; the ADR and executable test remain authoritative for the rule.

| Contract | Rule and scope | Owner and executable coverage | ADR mapping | Approved exceptions | Severity and failure metadata |
|---|---|---|---|---|---|
| `ARCH-001` | Layer and import direction for `server/smp` bounded contexts: `domain <- application <- infrastructure`. Domain stays framework-free; application does not depend on infrastructure or transport/persistence frameworks. | ArchUnit: `HexagonalArchTest.kt` and `ComponentScanArchTest.kt`. | ADR-0002 | `ModuleMetadata` wiring and the explicitly documented cross-cutting `com.profiletailors.smp.config` package. Exceptions must contain no business logic. | Blocking. A result must identify `ARCH-001`, owner `ArchUnit`, scope `server/smp`, ADR-0002, and the offending dependency or annotation. |
| `ARCH-002` | Backend bounded contexts in `server/smp` remain isolated modules. Cross-context dependencies use the exposed module API, shared kernel, or approved event seam rather than another context's internals. | Spring Modulith: `ModularStructureTest.kt` and `ModularityVerificationTest.kt`. Keep both existing suites; do not add a duplicate verification. | ADR-0001 and ADR-0002 | Spring Modulith `ModuleMetadata`/named interfaces and any exception explicitly recorded by the owning ADR or Modulith configuration. A pre-existing failure is not an approval. | Blocking when the existing Modulith suites fail. A result must identify `ARCH-002`, owner `Spring Modulith`, scope `server/smp`, mapped ADR, and the module dependency. |
| `ARCH-003` | An aggregate root is the entry point to its aggregate. Internal entities are not imported from another bounded context and do not expose public `set*`/`update*` mutators. The rule applies to marked production domain classes. | Konsist: `AggregateBoundaryTest.kt`, tagged `ddd-conformance`. | ADR-0015 | Same-context application ports and persistence adapters may materialise internal entities. Test fixtures are outside the production-source scan. `ModuleMetadata` is architecture wiring, not a domain entity. | Blocking. A result must identify `ARCH-003`, owner `Konsist`, scope `server/smp` production source, ADR-0015, and the offending import or mutator. |
| `ARCH-004` | Marked aggregate roots and domain entities communicate across bounded contexts by identity (`Id`, `Ids`, or `Identifier` names), not by direct aggregate object references. | Konsist: `IdentityOnlyAggregateCommunicationTest.kt`, tagged `ddd-conformance`. | ADR-0016 | Same-context direct references are allowed. Shared-kernel types, ports, repositories, policies, resolvers, and test fixtures are outside the rule's target. Generic type arguments remain a documented implementation limitation until the test supports them. | Blocking. A result must identify `ARCH-004`, owner `Konsist`, scope `server/smp` marked production domain classes, ADR-0016, and `Class.property: Type`. |
| `ARCH-005` | Marked value objects are immutable and validate at construction or through an approved factory. The production scan covers the shared kernel and marked backend domain value objects. | Konsist: `ValueObjectImmutabilityTest.kt`, tagged `ddd-conformance`. | ADR-0017 | Enums are inherently immutable and valid by construction. Test fixtures are outside the production-source scan. `ModuleMetadata` and unmarked classes are not value-object contracts. | Blocking. A result must identify `ARCH-005`, owner `Konsist`, scope `shared/common` and marked `server/smp` production source, ADR-0017, and the offending member or missing validation. |

No additional `ARCH` contract may be introduced by renaming or duplicating one of these rules. A
new concern requires a separate proposal and ADR mapping.

## Failure Message Convention

Existing tests retain their current assertions and messages. Any future aggregator or new
architecture check MUST prepend enough metadata to make the failure attributable:

```text
ARCH-00N | owner=<owner> | scope=<path> | adr=<ADR-NNNN> | severity=<blocking|warning|unverified>
<existing failure detail>
```

The detail should include the offending file, import, dependency, member, or module edge. An
approved exception must be visible in the report with its ADR/configuration rationale; it must not
be represented as an unexplained pass. Unrelated test failures must remain visible and must not be
converted into architecture warnings.

## Routing

| Question | Use |
|---|---|
| Is the problem Kotlin layer/import direction or Spring component placement? | `backend-platform/hexagonal-architecture` and `ARCH-001`. |
| Is the problem a Kotlin aggregate boundary, identity-only reference, value-object shape, or ADR-backed DDD invariant? | `backend-platform/ddd-architecture` and `ARCH-003..005`. |
| Is the problem a backend bounded-context/module dependency? | Spring Modulith guidance and `ARCH-002`. |
| Is the problem a Vue feature boundary, Pinia store, admin SPA, Astro surface, or `shared/web` import? | `frontend-platform/frontend-architecture`; do not apply Kotlin DDD markers. |

## Scope Profiles

- Backend governance is limited to `server/smp` and the shared Kotlin contracts named by the
  mapped ADRs.
- Vue app governance is limited to `apps/web/app`; its public feature boundaries are described in
  `frontend-platform/frontend-architecture`.
- Admin governance is a separate, flatter Vue profile under `apps/web/admin`.
- Marketing governance is static-first Astro under `apps/web/marketing`.
- `shared/web` is a dependency-light contract package consumed by frontend surfaces and must not
  import an application, admin, or marketing package.

A check for one profile MUST NOT silently scan another profile. Cross-surface changes require an
explicit composition-root or shared-package rationale.

## Exceptions and Migration

Exceptions are narrow, named, and reviewable. Record the reason in the relevant ADR or approved
architecture artifact; do not weaken a test or add a broad wildcard solely to make a check green.
When replacing an owner, first create a focused deliberate violation, demonstrate equivalent old
and new failure evidence, and only then remove or deprecate the old enforcement in a separate
approved change. Preserve `ARCH-001..005`, existing ADRs, marker annotations, and test contracts
until equivalent coverage is proven.

## Architecture-Check Policy

`just architecture-check` is **unverified and deferred** in this phase. Do not add the recipe, do
not make it CI-required, and do not replace `just backend-test-fast`, `just backend-check`, or the
existing frontend checks.

A later proposal may make it an opt-in `just` aggregator only after it defines contract-labelled
output, proves a clean baseline, avoids running the two Modulith suites redundantly, preserves
unrelated failures, and documents rollback. CI adoption requires a separate decision after a
frontend checker and baseline evidence exist.

## Verification Baseline

Use the repository command hub rather than inventing direct commands:

```bash
just backend-test-fast
just backend-check
just frontend-lint
just frontend-check
just frontend-test
just admin-test
just admin-check
pnpm --filter app type-check
```

These commands verify the existing enforcement and frontend baselines; they do not constitute a
new unified gate. AgentSync distributes canonical skills from `.agents/skills`. Edit canonical
files only and inspect synchronization status without editing generated agent copies.

## References

- `docs/architecture/adr/0001-use-a-modular-monolith-backend.md`
- `docs/architecture/adr/0002-adhere-to-hexagonal-architecture.md`
- `docs/architecture/adr/0015-aggregate-root-as-sole-entry-point.md`
- `docs/architecture/adr/0016-aggregates-communicate-by-identity-only.md`
- `docs/architecture/adr/0017-value-objects-are-immutable.md`
- `.agents/skills/backend-platform/ddd-architecture/SKILL.md`
- `.agents/skills/frontend-platform/frontend-architecture/SKILL.md`
