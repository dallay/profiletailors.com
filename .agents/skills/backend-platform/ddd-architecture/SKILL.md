---
name: ddd-architecture
description: Use when enforcing DDD conformance in the Kotlin backend — aggregate-root boundaries, identity-only inter-aggregate references, value-object immutability, bounded-context isolation, or ADR-backed architectural decisions. Complements hexagonal-architecture (layer/import direction) and Spring Modulith (backend module boundaries).
allowed-tools: Read, Edit, Write, Glob, Grep, Bash
metadata:
  author: profiletailors
  version: "1.0"
---

# DDD Architecture Skill

Use this skill for Kotlin domain invariants inside `server/smp` and the shared Kotlin domain
contracts. It is the source of guidance for `ARCH-003`, `ARCH-004`, and `ARCH-005` in the shared
`architecture-governance` skill. It composes, rather than replaces, the repository's existing
architecture enforcement:

- `hexagonal-architecture` and `ARCH-001` govern layer/import direction with ArchUnit.
- Spring Modulith and `ARCH-002` govern bounded-context/module isolation.
- This skill and Konsist govern marked Kotlin DDD/source-shape invariants.

Do not apply this Kotlin guidance to Vue, TypeScript, Astro, or `shared/web`; use
`frontend-platform/frontend-architecture` for those surfaces.

## When to Use

- Adding or marking an aggregate root, internal entity, or value object.
- Reviewing a cross-context domain reference or aggregate boundary.
- Reviewing whether an ADR-backed DDD decision remains executable.
- Diagnosing source-shape drift that layer or module checks cannot express.
- Planning a replacement of one DDD check (first prove equivalent failure evidence).

## Repository Contracts

| Contract | Invariant | Owner | Scope | ADR |
|---|---|---|---|---|
| `ARCH-003` | Aggregate root is the entry point; marked internal entities are not imported from another bounded context and expose no public `set*`/`update*` mutators. | Konsist `AggregateBoundaryTest.kt` | `server/smp` production source | ADR-0015 |
| `ARCH-004` | Marked aggregate roots and domain entities use identity-only names for cross-context aggregate references. | Konsist `IdentityOnlyAggregateCommunicationTest.kt` | `server/smp` marked production domain classes | ADR-0016 |
| `ARCH-005` | Marked value objects are immutable and validate at construction or an approved factory. | Konsist `ValueObjectImmutabilityTest.kt` | Marked `server/smp` production domain classes and `shared/common` value objects | ADR-0017 |

Every failure must retain the existing ADR-labelled detail and identify the offending class,
property, function, import, or missing validation. Do not weaken an assertion or hide a failure to
make a new annotation sweep green.

## Domain Contracts and Markers

The repository deliberately has two different aggregate-root contracts:

1. `com.profiletailors.common.domain.AggregateRoot` is a runtime-retained **marker annotation**.
   It opts a class into the marker-driven Konsist checks in `server/smp`.
2. `com.profiletailors.common.domain.model.AggregateRoot<ID>` is an abstract **base class** that
   models identity and event recording for aggregates that need inheritance.

They are not interchangeable and must not be unified. A plain data class may use the annotation
without inheriting from the base class; a base-class aggregate may also be marked when it needs the
Konsist guard rails. Keep the imports separate because the types intentionally live in different
packages.

Other shared markers are in `com.profiletailors.common.domain`:

- `@DomainEntity` marks an entity internal to an aggregate.
- `@ValueObject` marks an immutable value type whose construction invariants are enforced.

Markers are commitments to the corresponding tests, not decoration. Mark a new aggregate, internal
entity, or value object in the same change that introduces it.

## Domain Events

`com.profiletailors.common.domain.bus.event.DomainEvent` is an interface, not an annotation. It
exposes `eventVersion(): Int` and `occurredOn(): LocalDateTime?`. `BaseDomainEvent` provides the
shared implementation for events that need a default timestamp and version. Domain event classes
may implement the interface directly or extend the base class according to their existing needs.

Do not invent or document a `DomainEvent` annotation. Cross-context event publication and module
exposure remain governed by the existing domain/application ports and Spring Modulith rules.

## Aggregate Root Boundary — `ARCH-003`

**Invariant:** external code reaches an aggregate through its root; a `@DomainEntity` is not a
cross-context public entry point and does not expose public mutators.

The executable owner is:

```text
server/smp/src/test/kotlin/com/profiletailors/smp/AggregateBoundaryTest.kt
```

It uses `Konsist.scopeFromProduction()`, so test source sets are not scanned for cross-context
imports. The test currently verifies:

- production files in another bounded context do not import a marked `@DomainEntity`;
- marked internal entities do not expose public functions beginning with `set` or `update`.

Same-context application ports and persistence adapters may materialise internal entities because
they must map and persist the aggregate. `ModuleMetadata` is infrastructure wiring and is exempt
from business architecture rules per ADR-0002.

Failure messages begin with `ADR-0015 violated` and include offending paths. Preserve this
contract when extending the rule.

## Identity-Only Communication — `ARCH-004`

**Invariant:** a marked `@AggregateRoot` or `@DomainEntity` may reference a type from another
bounded context only through an identity-shaped property name: `Id`, `Ids`, `Identifier`, or a
nullable `Id?` form.

The executable owner is:

```text
server/smp/src/test/kotlin/com/profiletailors/smp/IdentityOnlyAggregateCommunicationTest.kt
```

It scans marked production classes and permits same-context references. It does not scan value
objects, ports, repositories, policies, resolvers, shared-kernel types, or test fixtures. The
current Konsist implementation handles direct property source types; generic type arguments such as
`Set<Workspace>` are a known limitation and require a separate approved test enhancement.

Good:

```kotlin
@AggregateRoot
class Workspace(
    val ownerPrincipalId: String,
)
```

Bad:

```kotlin
@AggregateRoot
class Workspace(
    val owner: com.profiletailors.smp.identity.domain.User,
)
```

Failure messages begin with `ADR-0016 violated` and include `Class.property: TypeName`.

## Value-Object Invariants — `ARCH-005`

**Invariant:** a marked `@ValueObject` is immutable and rejects invalid state at construction or
through a recognised factory. The executable owner is:

```text
server/smp/src/test/kotlin/com/profiletailors/smp/ValueObjectImmutabilityTest.kt
```

The current Konsist tests verify:

- every non-enum property is `val` or private;
- no public `set*` or `mutate*` function exists;
- every non-enum value object has an `init` block or recognised factory such as `of`, `create`,
  `from`, `fromRaw`, `ensure`, `generate`, or `random`.

Enums are inherently immutable and valid by construction and are exempt from the shape/validation
scan. Test fixtures are outside `scopeFromProduction()`. A unit test should still cover the
semantic boundary, for example malformed `Email` input, because the Konsist rule checks source
shape rather than every runtime invariant.

Failure messages begin with `ADR-0017 violated` and include the offending member or class.

## Relationship to Backend Enforcement

`ARCH-001` and `ARCH-002` are not duplicated here:

- `HexagonalArchTest.kt` owns domain/application/infrastructure layer direction and framework
  dependency rules.
- `ComponentScanArchTest.kt` owns Spring stereotype and nested component-scan guards.
- `ModularStructureTest.kt` and `ModularityVerificationTest.kt` own Spring Modulith verification.
  Keep both existing suites and do not add a third identical `ApplicationModules.verify()` test.

When an issue is about a layer import, use `hexagonal-architecture`. When it is about a backend
module edge, use Spring Modulith guidance. When it is about a marked domain reference or value
object shape, use this skill.

## New Bounded Context Checklist

1. Create `server/smp/src/main/kotlin/com/profiletailors/smp/<context>/{domain,application,infrastructure}`
   according to the existing hexagonal structure.
2. Keep domain pure Kotlin; application code uses domain ports and the project service marker, not
   Spring stereotypes; infrastructure contains Spring, HTTP, R2DBC, and adapter code.
3. Add the context's Modulith metadata where required and let existing module verification cover it.
4. Mark aggregate roots, internal entities, and value objects in the same change that introduces
   them.
5. Add focused domain tests and run the existing architecture/conformance checks.
6. For cross-context references, expose an approved application/domain contract or event seam and
   retain identity-only references on marked aggregates.

## Commands

All repository commands go through `just`:

```bash
# Focused baseline for DDD and backend architecture
just backend-test-fast
just backend-check

# Focused manual verification of the DDD suite, when narrowing a failure
./gradlew :server:smp:test --tests "*AggregateBoundaryTest*"
./gradlew :server:smp:test --tests "*IdentityOnlyAggregateCommunicationTest*"
./gradlew :server:smp:test --tests "*ValueObjectImmutabilityTest*"
```

The direct Gradle invocations are for focused diagnosis; normal verification uses the command hub.
There is no `just architecture-check` recipe in this phase. A future command is unverified and
must be proposed separately as an opt-in aggregator with labelled output, no duplicate Modulith
execution, preserved unrelated failures, and a rollback plan before CI adoption.

## Anti-Patterns

- Replacing Konsist DDD checks with ArchUnit merely to use one tool.
- Treating `com.profiletailors.common.domain.AggregateRoot` and
  `com.profiletailors.common.domain.model.AggregateRoot<ID>` as the same contract.
- Creating a `DomainEvent` annotation; the repository contract is an interface plus
  `BaseDomainEvent`.
- Applying Kotlin markers to TypeScript/Vue/Astro code.
- Importing another bounded context's infrastructure or internal entity to bypass an API seam.
- Removing an existing ADR or architecture test before focused old/new failure evidence exists.
- Adding a new command or CI gate without an approved proposal and clean baseline.

## References

- `docs/architecture/adr/0001-use-a-modular-monolith-backend.md`
- `docs/architecture/adr/0002-adhere-to-hexagonal-architecture.md`
- `docs/architecture/adr/0015-aggregate-root-as-sole-entry-point.md`
- `docs/architecture/adr/0016-aggregates-communicate-by-identity-only.md`
- `docs/architecture/adr/0017-value-objects-are-immutable.md`
- `.agents/skills/architecture-governance/SKILL.md`
- `.agents/skills/backend-platform/hexagonal-architecture/SKILL.md`
- `server/smp/src/test/kotlin/com/profiletailors/smp/`
