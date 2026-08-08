---
name: ddd-architecture
description: Use when enforcing DDD conformance — aggregate root boundaries, identity-only inter-aggregate references, value-object immutability, bounded-context isolation, or ADR-backed architectural decisions in the smp backend. Complements hexagonal-architecture (which validates import direction between layers) with intra-domain invariants.
allowed-tools: Read, Edit, Write, Glob, Grep, Bash
metadata:
  author: profiletailors
  version: "0.1"
---

# DDD Architecture Skill

Enforce strategic DDD invariants as executable tests. Complements `hexagonal-architecture`: that
skill validates *where* code lives (layer import direction); this one validates *what* code
references (aggregate boundaries, identity-only communication, invariants preserved).

## When to Use

- Adding a new bounded context and validating its boundaries
- Introducing a new Aggregate Root and binding its internal entities
- Defining new Value Objects with invariants that must survive refactors
- Reviewing whether an architectural decision from an ADR is still honored
- Diagnosing "smell of bleed" between contexts that the layer tests didn't catch
- Triaging whether a refactor crossed aggregate boundaries unnecessarily

## Complements hexagonal-architecture

| Skill                   | Invariant                                                                 | Level              |
|-------------------------|---------------------------------------------------------------------------|--------------------|
| `hexagonal-architecture`| Domain layer has no Spring; application layer has no R2DBC                | Layer (imports)    |
| `ddd-architecture`      | Aggregate Root is the only entry to the aggregate                         | Domain (refs)      |
| `ddd-architecture`      | Aggregates communicate by identity only                                   | Domain (types)     |
| `ddd-architecture`      | Value Objects are immutable and validate invariants in init               | Domain (shape)     |
| `ddd-architecture`      | Bounded contexts don't import each other's internals                      | Module (boundaries)|
| `ddd-architecture`      | ADRs are enforced by executable tests                                     | Decision (compliance) |

**Rule of thumb:** if the violation is about *import direction between layers*, use the
hexagonal skill. If it is about *references inside the domain*, use this one.

## Marker Annotations

Domain classes need explicit markers so the architecture tests have an anchor. These live in
`com.profiletailors.common.domain`:

```kotlin
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class AggregateRoot

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class DomainEntity // internal entity of an aggregate

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ValueObject

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class DomainEvent
```

Apply them once. Tests below depend on them being present.

## Rule 1: Aggregate Root as sole entry point

**Invariant:** Nothing outside the aggregate can directly construct, expose, or mutate an internal
entity. Only the Aggregate Root can navigate into its members.

```kotlin
// Architecture test: server/smp/src/test/.../architecture/AggregateBoundaryTest.kt
class AggregateBoundaryTest : StringSpec({

    val domains = konsist.scopeFromProject().packages("..domain..")

    "internal entities are only referenced by their Aggregate Root" {
        domains.forEach { pkg ->
            val roots = pkg.classes().filter { it.hasAnnotationOf<AggregateRoot>() }
            val internals = pkg.classes().filter { it.hasAnnotationOf<DomainEntity>() }
            val allowedPackages = roots.map { pkg.name }.toSet()

            internals.forEach { internal ->
                val outsidePkg = konsist.scopeFromProject()
                    .files
                    .filter { !it.packageName.startsWith(pkg.name) || it.packageName == pkg.name }
                    .filter { it.path != internal.containingFile.path }

                outsidePkg.forEach { file ->
                    file.imports
                        .filter { it.name == internal.name }
                        .assertEmpty(
                            "Internal entity '${internal.name}' is imported from " +
                            "'${file.relativePath}' which is outside the aggregate. " +
                            "ADR-0001: only the Aggregate Root can touch internal entities."
                        )
                }
            }
        }
    }

    "internal entities have no public mutators" {
        konsist.scopeFromProject()
            .classes()
            .filter { it.hasAnnotationOf<DomainEntity>() }
            .forEach { entity ->
                entity.functions()
                    .filter { it.hasPublicOrDefaultModifier }
                    .filter { it.name.startsWith("set") || it.name.startsWith("update") }
                    .assertEmpty(
                        "Internal entity '${entity.name}' must not expose public mutators. " +
                        "Mutations must go through the Aggregate Root."
                    )
            }
    }
})
```

**Real example** in `tenancy/domain/`:
- `Workspace.kt` — mark as `@AggregateRoot`
- `WorkspaceMembership.kt` — mark as `@DomainEntity`
- `WorkspaceOwnership.kt` — mark as `@DomainEntity`
- `WorkspaceOwnershipPolicy.kt` — pure logic, no marker needed

## Rule 2: Aggregates communicate by identity only

**Invariant:** An aggregate holds identity references (`UserId`, `WorkspaceId`) to other
aggregates — never direct object references. This preserves transactional consistency and prevents
accidental lazy loads across aggregate boundaries.

```kotlin
"aggregates reference other aggregates only by identity" {
    val pkg = "com.profiletailors.smp"
    konsist.scopeFromPackage("$pkg.*.domain")
        .classes()
        .forEach { source ->
            source.properties.forEach { prop ->
                val targetType = prop.sourceType ?: return@forEach
                if (!targetType.startsWith("$pkg.")) return@forEach

                val sourceContext = source.containingFile.packageName
                    .substringAfter("$pkg.").substringBefore('.')
                val targetContext = targetType
                    .substringAfter("$pkg.").substringBefore('.')

                if (sourceContext == targetContext) return@forEach

                val isIdentity = targetType.endsWith("Id") ||
                                 targetType.endsWith("Identifier") ||
                                 targetType.endsWith("Id?") ||
                                 targetType.endsWith("Ids")

                assert(isIdentity) {
                    "Cross-context reference in '${source.name}' to " +
                    "'$targetType' must be an identity (Id/Identifier). " +
                    "ADR-0002: aggregates communicate by identity only."
                }
            }
        }
}
```

**Already-good example** in `WorkspaceOwnership.kt`:

```kotlin
data class WorkspaceOwnership(
    val workspaceId: String,         // ✅ identity
    val ownerPrincipalId: String,    // ✅ identity
    val ownerPrincipalType: PrincipalType,
)
```

The test **blinds** so nobody can change `workspaceId: String` to `workspace: Workspace` six months
from now.

## Rule 3: Value Objects preserve invariants

Three invariants: immutability, construction-time validation, no setters.

```kotlin
"value objects are immutable" {
    konsist.scopeFromProject()
        .classes()
        .filter { it.hasAnnotationOf<ValueObject>() }
        .forEach { vo ->
            vo.properties().assertTrue(
                predicate = { it.isVal || it.hasPrivateModifier },
                errorMessage = "VO '${vo.name}' must use only val or private properties"
            )
            vo.functions().assertTrue(
                predicate = { !it.name.startsWith("set") && !it.name.startsWith("mutate") },
                errorMessage = "VO '${vo.name}' must not expose setters or mutators"
            )
        }
}

"value objects validate invariants in init or factory" {
    konsist.scopeFromProject()
        .classes()
        .filter { it.hasAnnotationOf<ValueObject>() }
        .forEach { vo ->
            val primaryCtor = vo.primaryConstructor
            val hasParamValidation = primaryCtor?.parameters?.any { param ->
                param.annotations.any { it.name in listOf("field", "require") }
            } ?: false
            val hasFactory = vo.functions().any {
                it.name in listOf("of", "ensure", "create", "from")
            }
            val hasInitBlock = vo.primaryConstructor?.hasInitBlock ?: false

            assert(hasParamValidation || hasFactory || hasInitBlock) {
                "VO '${vo.name}' must validate invariants via init parameters, " +
                "an init block, or a factory function."
            }
        }
}
```

**Complement with unit tests** — the static rule catches the shape; the unit test catches the
semantics. Example:

```kotlin
@Test
fun `email rejects malformed input`() {
    assertThrows<IllegalArgumentException> { Email("invalid") }
}

@Test
fun `email rejects empty string`() {
    assertThrows<IllegalArgumentException> { Email("") }
}
```

## Rule 4: Bounded contexts don't mix silently

`Spring Modulith` already validates module boundaries. This rule goes further on domain imports
and event leakage.

```kotlin
"domain of one context must not depend on infrastructure of another" {
    val domains = konsist.scopeFromProject().packages("..domain..")

    domains.forEach { sourcePkg ->
        val sourceContext = sourcePkg.name
            .substringAfter("com.profiletailors.smp.").substringBefore('.')
        sourcePkg.containingFile.acceptedDependencies.forEach { dep ->
            if (dep.hasMatchingPath("com/profiletailors/smp/.*/infrastructure")) {
                val targetContext = dep.name
                    .substringAfter("com.profiletailors/smp/").substringBefore('/')
                assert(sourceContext == targetContext) {
                    "Domain in '$sourceContext' depends on infrastructure of " +
                    "'$targetContext'. Cross-context coupling must go through " +
                    "domain events or shared kernel only."
                }
            }
        }
    }
}

"domain events are not directly imported across contexts" {
    val events = konsist.scopeFromProject()
        .classes()
        .filter { it.hasAnnotationOf<DomainEvent>() }

    events.forEach { event ->
        val sourceContext = event.containingFile.packageName
            .substringAfter("com.profiletailors.smp.").substringBefore('.')
        konsist.scopeFromProject()
            .files
            .filter { !it.packageName.startsWith("com.profiletailors.smp.$sourceContext") }
            .forEach { file ->
                file.imports
                    .filter { it.containingClass == event }
                    .assertEmpty(
                        "Domain event '${event.name}' from '$sourceContext' is " +
                        "directly imported by another context. Use the integration " +
                        "contract layer instead."
                    )
            }
    }
}
```

## Rule 5: Architectural decisions stay true six months later

**The pattern:** every ADR is born with one or more executable tests that fail the build if the
decision is violated.

### Structure

```text
docs/architecture/decisions/
├── template.md
├── 0001-aggregate-root-as-sole-entry.md
├── 0002-aggregate-communication-by-identity.md
├── 0003-value-object-immutability.md
├── 0004-bounded-context-isolation.md
└── 0005-cross-aggregate-via-domain-events.md
```

### ADR template (Nygard)

```markdown
# ADR-NNNN: Title

## Status
Accepted — YYYY-MM-DD

## Context
What problem we faced. What forces were in play.

## Decision
What we chose. Which skill/test enforces this.

## Consequences
Pros, cons, and what becomes easier/harder.

## Enforcement
- Architecture test: `class FooTest : StringSpec({ ... })`
- Tag: `@Adr("NNNN")`
- Failure mode: build fails in CI with reason.
```

### Test tag

```kotlin
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class Adr(val id: String)
```

### Failure message convention

```
ADR-0002 violated: aggregate 'Workspace' directly references 'User' in
'com.profiletailors.smp.tenancy.application.WorkspaceService.kt:42'.
Aggregates must communicate by identity only.
```

This is the contract. The CI failure tells a developer *which* decision is broken and *where*.

## Decision Tree: Where Does This Rule Belong?

```markdown
Is the rule about *import direction between layers*?
├── YES → hexagonal-architecture skill
└── NO → Is the rule about *intra-domain* invariants?
    ├── Aggregate boundaries → ddd-architecture (this skill)
    ├── Inter-aggregate references → ddd-architecture
    ├── Value-object immutability → ddd-architecture
    ├── Bounded-context isolation → ddd-architecture
    └── Business decision enforcement → ddd-architecture (ADR-backed tests)
```

## Test Tagging Convention

| Tag                     | Purpose                          | CI? |
|-------------------------|----------------------------------|-----|
| `@Tag("architecture")`  | Hexagonal layer rules            | Yes |
| `@Tag("modularity")`    | Spring Modulith boundaries       | Yes |
| `@Tag("ddd-conformance")` | DDD invariants within domain   | Yes |
| `@Tag("adr-compliance")` | ADR-backed decisions             | Yes |

All four tags run as part of `just backend-test` by default. No special CI wiring.

## Anti-Patterns

❌ **Lazy VO with `var` properties** — value objects must be immutable.
❌ **Cross-aggregate direct reference** — `workspace: Workspace` in another aggregate.
❌ **Internal entity with public constructor** — bypasses AR invariants.
❌ **ADR with no test** — a decision without enforcement is just a wish.
❌ **Anemic VOs** — no validation logic in `init`/`require` blocks.
❌ **Cross-context direct imports** — must use domain events or shared kernel.
❌ **Marker annotation with no enforcement test** — markers without tests are decoration.

## Commands

```bash
# Run only DDD conformance tests
./gradlew :server:smp:test --tests "*DddConformance*"

# Run all architecture tests (hexagonal + modularity + DDD + ADRs)
./gradlew :server:smp:test --tests "*ArchTest" --tests "*Conformance*"

# Quick manual sweep: cross-context imports in domain
rg "import com.profiletailors.smp\.[a-z]+\.domain" \
   server/smp/src/main/kotlin/com/profiletailors/smp/*/domain/

# Quick manual sweep: cross-context imports in application (excluding own context)
rg "import com.profiletailors.smp\.[a-z]+\.domain" \
   server/smp/src/main/kotlin/com/profiletailors/smp/*/application/
```

## Resources

- [Domain-Driven Design Reference](https://domainlanguage.com/ddd/reference/) — Eric Evans
- [Implementing DDD](https://www.amazon.com/Implementing-Domain-Driven-Design-Vaughn-Vernon/dp/0321834577) — Vaughn Vernon
- [Konsist](https://lemonrock.github.io/kotlin-consistent-architecture/) — Kotlin architecture testing
- `hexagonal-architecture` skill — for layer-level invariants (import direction)
- `spring-boot` skill — for module-level wiring
- `kotlin` skill — for language conventions
