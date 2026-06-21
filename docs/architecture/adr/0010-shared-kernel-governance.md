# ADR-0010: Shared Kernel Governance

- Status: Accepted
- Date: 2026-06-21
- Decision owners: Principal Architect
- Scope: Shared Modules (`shared/`)
- Supersedes: None
- Superseded by: None
- Related:
  - C4: [Dependency Graph](../shared/dependencies.md)

## Context
A monorepo with multiple bounded contexts requires shared code to avoid duplication, but an ungoverned shared module can quickly lead to tight coupling and a "distributed monolith" inside a single build.

## Decision drivers
- Independence (contexts should be able to evolve separately).
- Stability (changes in shared code should be rare and well-vetted).
- Framework Isolation (keeping the core domain free of technical details).

## Decision
The `shared/` modules MUST follow strict governance rules:
1. **Acyclic Dependencies**: The dependency graph MUST be strictly acyclic.
2. **Framework Isolation in Common**: `shared:common` MUST NOT have any dependencies on Spring, R2DBC, or other technical frameworks. It is reserved for pure Kotlin domain primitives.
3. **Admission Criteria**: A component MAY be moved to shared only if it is:
   - A technical cross-cutting concern (e.g., Bus, Hasher, Storage).
   - A domain primitive used by at least **three** different Bounded Contexts.
4. **Ownership**: Shared modules are owned by the Principal Architect or a cross-team platform group.

## Scope and boundaries
- All Gradle modules under `shared/`.

## Alternatives considered
### Duplication
- Advantages: Maximum independence between teams.
- Disadvantages: Inconsistency in core concepts (e.g., how an Email is validated).
- Reason rejected: Leads to high maintenance effort for cross-cutting security or infrastructure changes.

## Consequences
### Positive
- Clean, reusable foundation.
- Enforced architectural layers.
### Negative
- Higher barrier to sharing code (intentional).
- Changes to `shared:common` require recompiling the entire system.

## Compliance and enforcement
Enforced via Gradle build configuration and ArchUnit tests.

## Verification
- `:shared:common` build file has minimal/no dependencies.
- No circular dependencies between `:server:smp` and `:shared:*`.

## Migration or remediation
None required; the current structure is already exceptionally clean.

## Revisit conditions
- The time to compile the entire project becomes a major productivity bottleneck.
- The shared kernel grows too large to be understood by a single contributor.
