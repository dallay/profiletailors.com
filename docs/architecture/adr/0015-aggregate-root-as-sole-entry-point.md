# ADR-0015: Aggregate Root Is the Sole Entry Point to an Aggregate

- Status: Accepted
- Date: 2026-08-08
- Decision owners: Principal Architect
- Scope: Backend (`server/smp`), shared kernel (`shared/common`)
- Supersedes: None
- Superseded by: None
- Related:
  - Skills: `.agents/skills/backend-platform/ddd-architecture/SKILL.md`
  - Companion ADRs:
    - ADR-0016: Aggregates Communicate by Identity Only (planned)
    - ADR-0017: Value Objects Are Immutable (planned)
  - Issues/PRs: bootstrap commit for marker annotations and `AggregateBoundaryTest`

## Context

DDD strategic patterns (Evans, Vernon) require that an aggregate is a transactional consistency
boundary accessed only through its root. Internal entities inside the aggregate MUST NOT be
referenced, mutated, or constructed by code outside the aggregate. Without an executable guard,
this invariant erodes silently as the codebase grows:

- A handler in one bounded context reaches directly for `WorkspaceMembership` (a tenancy internal
  entity) to send a notification, breaking transactional consistency and tying two contexts to
  the same evolution cadence.
- An infrastructure adapter exposes a public `set*` mutator on an internal entity, letting callers
  bypass the Aggregate Root and the invariants it enforces.
- A new bounded context is added, and a `data class` with the wrong shape quietly becomes the
  source of truth because nothing flagged it as not-a-value-object.

Spring Modulith (ADR-0001) and `HexagonalArchTest` (ADR-0002) already validate **where** code
lives — module boundaries and layer imports. They do not validate **what** code references inside
the domain. A separate enforcement layer is required for intra-domain invariants.

## Decision drivers

- Aggregate boundaries must be enforced by automated tests, not by review.
- The enforcement must catch cross-context leakage as the primary failure mode (one bounded
  context reaching into another's internal cluster of objects).
- Marker annotations must live in the shared kernel so any bounded context can apply them.
- The first iteration MUST be small enough to land in one commit and green-light in CI; broader
  rules (identity-only references, value-object invariants) come in subsequent ADRs.
- Tests in `src/test` legitimately need to materialise entities for fixtures; the rule MUST NOT
  apply to test source sets.

## Decision

### 1. Marker annotations live in `com.profiletailors.common.domain`

Three marker annotations are introduced in the shared kernel:

- `@AggregateRoot` — applied to the entry point of an aggregate. Carries no behaviour.
- `@DomainEntity` — applied to an internal entity that lives inside an aggregate. Must NOT be
  reachable from outside the aggregate.
- `@ValueObject` — applied to an immutable, side-effect-free descriptor (full invariants enforced
  by ADR-0017 once landed).

These annotations are intentionally separate from the existing
`com.profiletailors.common.domain.model.AggregateRoot<ID>` base class. The base class models
identity and domain-event recording for aggregates that need them; the annotation is a static
marker usable on any class shape (`data class`, regular `class`, sealed types) without forcing
inheritance. Both names live in different sub-packages; they MUST NOT be imported together in the
same file.

### 2. Bounded contexts mark their aggregates and internal entities

Every bounded context in `com.profiletailors.smp.{context}.domain` MUST:

- Mark the entry point class with `@AggregateRoot`.
- Mark every internal entity (a class that exists only inside an aggregate's consistency boundary)
  with `@DomainEntity`.
- Leave policy classes, value objects, ports, and pure logic without a marker.

Tenancy bootstrap (landed in the commit that introduces this ADR):

- `Workspace` → `@AggregateRoot`
- `WorkspaceMembership` → `@DomainEntity`
- `WorkspaceOwnership` → `@DomainEntity`
- `WorkspaceOwnershipPolicy` — no marker (pure logic)

### 3. `@DomainEntity` MUST NOT be imported from another bounded context

A class annotated with `@DomainEntity` is internal to its owning aggregate. A file under
`com.profiletailors.smp.{other_context}.{layer}` MUST NOT import it. Internal references inside
the same context (application ports, infrastructure persistence adapters) are explicitly allowed
because:

- Persistence adapters MUST materialise entities to map database rows.
- Application ports expose the entity contract that command handlers depend on.

These same-context uses are NOT violations; the rule targets cross-context leakage only.

### 4. `@DomainEntity` MUST NOT expose public mutators

A class annotated with `@DomainEntity` MUST NOT expose public functions whose name starts with
`set` or `update`. State changes inside the aggregate MUST flow through the Aggregate Root, which
preserves invariants. Auto-generated `copy()`, `componentN()`, `equals/hashCode/toString` are
allowed because they do not match these prefixes and do not mutate the receiver.

### 5. Enforcement lives in `AggregateBoundaryTest`

The rule is enforced by `server/smp/src/test/kotlin/com/profiletailors/smp/AggregateBoundaryTest.kt`,
tagged `@Tag("ddd-conformance")` so it runs as part of `./gradlew :server:smp:test` by default.
The test uses Konsist to scan the production source set only (test source sets legitimately
reference entities to construct fixtures).

Failure messages MUST cite this ADR by number (`ADR-0015 violated`) and the offending path so the
violation is self-explanatory in CI logs.

## Scope and boundaries

- Applies to all Kotlin files under `com.profiletailors.smp.{context}.domain`.
- Marker annotations live in `shared/common/src/main/kotlin/com/profiletailors/common/domain/` —
  the shared kernel. They are NOT application-specific.
- Test source sets are explicitly excluded from the cross-context rule. Internal-entity
  mutator rules DO apply to tests: a test fixture that exposes a public mutator on an internal
  entity still violates the invariant, even if the production code is clean.

### Accepted exceptions

- **`ModuleMetadata` classes**: per the project convention in ADR-0002, `ModuleMetadata` classes
  are exempted from architecture rules because they carry Spring Modulith wiring, not business
  logic.
- **Konsist-tagged test fixtures**: a test fixture that exposes a `set*` mutator on a fake entity
  is fine if the entity is itself a test fixture, not a production entity. The current scan
  cannot distinguish fixture classes from production classes; this gap is acknowledged and will
  be closed by ADR-0017 (value-object immutability) and a follow-up ADR on test fixtures.

## Alternatives considered

### Generic SonarQube / detekt custom rule

- Description: encode the rule in the existing static-analysis pipeline.
- Advantages: no new dependency.
- Disadvantages: rules live in YAML/JSON, far from the code they police; harder to read the rule
  alongside the production code; limited AST expressiveness for cross-file import checks.
- Reason rejected: Konsist gives a Kotlin-native AST, version-controlled next to the code, and
  reuses the existing JUnit5 test runner. SonarQube/detekt can layer on top later for additional
  metrics but cannot replace this rule.

### Strict "internal entity only references from its own domain package"

- Description: a stricter rule that flags any external reference, including same-context
  infrastructure.
- Advantages: harder to drift; no legitimate cross-context leakage.
- Disadvantages: requires every repository to materialise entities in a separate port-only
  DTO layer, which is significant boilerplate for a small team. Persistence adapters would need
  parallel "row" types and mappers.
- Reason rejected: the team's tolerance for this refactor is low right now, and the same-context
  cases are not the actual failure mode. The cross-context rule catches the real bug.

### Manual review

- Description: rely on code review to enforce aggregate boundaries.
- Advantages: zero tooling.
- Disadvantages: scales linearly with team size; six months from now, nobody remembers why the
  rule exists; regression risk.
- Reason rejected: same reason the layer rules in ADR-0002 are automated.

## Consequences

### Positive

- The first real cross-context leakage (`governance → tenancy.WorkspaceOwnership` in
  `TakedownEmailConsumers.kt`) was caught by the test the moment the markers were applied.
  Without the rule, the leak would have stayed silently for months.
- Adding a new bounded context automatically gets aggregate-boundary checks for free once the
  team marks their `AggregateRoot` and `DomainEntity` classes.
- Future ADRs (ADR-0016, ADR-0017) have a tested pattern to extend: add a `@Tag("ddd-conformance")`
  test next to `AggregateBoundaryTest.kt`, scan the same Konsist scope.

### Negative

- One new Gradle dependency (Konsist 0.17.3) added to `server/smp`'s test classpath.
- Developers must remember to mark new aggregates and entities. A future ADR or skill should add
  a Konsist test that fails when a class in `*.domain` exposes public state but is unmarked, to
  catch the "forgot to annotate" case.

### Risks

- Konsist's `scopeFromProduction()` default may behave differently across Gradle plugin
  versions. If a future upgrade changes the scope, the test could silently start passing against
  a partial source set. Mitigation: the test reports the file path of every violation, so a
  reviewer can spot when the count drops unexpectedly.
- The marker annotation pattern depends on reflection. A future Kotlin compiler change that hides
  retention metadata could break `hasAnnotationOf`. Mitigation: pin Konsist and revisit if Kotlin
  ships a new metadata format.

### Accepted trade-offs

- The rule allows same-context infrastructure to reference internal entities because the
  alternative (DTO + mapper everywhere) is too heavy for this stage. If future profiling shows
  the boilerplate is worth it, ADR-0015 can be tightened.
- The mutator rule looks only at function name prefixes. A method named `apply(...)` or
  `withStatus(...)` that mutates state will not be caught. This is intentional — false positives
  on idiomatic Kotlin (`copy`, `apply`) would be too costly. A complementary review checklist is
  the right place to catch semantic mutation.

## Compliance and enforcement

Enforced by `AggregateBoundaryTest` (Konsist, JUnit5):

- Test 1: `internalEntitiesAreNotReferencedFromOtherBoundedContexts` — fails if a production
  file outside the owning context imports any class annotated with `@DomainEntity`.
- Test 2: `internalEntitiesDoNotExposePublicMutators` — fails if any `@DomainEntity` exposes a
  public function whose name starts with `set` or `update`.

Failure messages cite `ADR-0015 violated` and the offending file path.

Tag `@Tag("ddd-conformance")` makes the test run as part of `./gradlew :server:smp:test` by
default. No special CI wiring required.

## Verification

- `./gradlew :server:smp:test --tests "*AggregateBoundaryTest*"` MUST pass.
- A build-time check on a deliberate violation (e.g., adding a temporary import from
  `identity.application` to `tenancy.domain.WorkspaceMembership`) MUST fail with a message that
  starts with `ADR-0015 violated`.

## Migration or remediation

The bootstrap commit landed alongside this ADR applied the markers to `Workspace`,
`WorkspaceMembership`, and `WorkspaceOwnership`, and refactored `TakedownEmailConsumers.kt` to
use the new `WorkspaceOwnershipRepository.findOwnerPrincipalIdsByWorkspaceId` port method so
that governance no longer imports the internal entity.

Future bounded contexts MUST mark their aggregates and internal entities in the same commit that
introduces them; doing so on a follow-up commit leaves the cross-context rule unguarded for
the gap window.

## Follow-up actions

- [ ] Land ADR-0016 (Aggregates Communicate by Identity Only) with `IdentityOnlyAggregateCommunicationTest`.
- [ ] Land ADR-0017 (Value Objects Are Immutable) with `ValueObjectImmutabilityTest`.
- [ ] Annotate value objects across all bounded contexts (`WorkspaceId`, `PrincipalType`,
      existing VO under `shared/common/domain/vo/`).
- [ ] Add a Konsist test that fails when a class in `*.domain` has public state but lacks both
      `@AggregateRoot` and `@DomainEntity` markers — the "forgot to annotate" guard rail.

## Revisit conditions

- Konsist 0.17.x stops being maintained and a successor API requires a non-trivial migration.
- The same-context exceptions begin to dominate real DDD violations (signal that the rule is
  too lenient).
- The team adopts a DTO/mapper convention for persistence adapters, making the cross-context-only
  rule obsolete (in which case the rule can tighten to "no external imports at all").
