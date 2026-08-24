# ADR-0016: Aggregates Communicate by Identity Only Across Bounded Contexts

- Status: Accepted
- Date: 2026-08-09
- Decision owners: Principal Architect
- Scope: Backend (`server/smp`), shared kernel (`shared/common`)
- Supersedes: None
- Superseded by: None
- Related:
    - Skills: `.agents/skills/backend-platform/ddd-architecture/SKILL.md`
    - Companion ADRs:
        - ADR-0015: Aggregate Root Is the Sole Entry Point to an Aggregate
        - ADR-0017: Value Objects Are Immutable and Validate at Construction
    - Marker annotations: `com.profiletailors.common.domain.AggregateRoot`,
      `com.profiletailors.common.domain.DomainEntity`

## Context

A DDD aggregate is a transactional consistency boundary accessed only through its root. The
second invariant — aggregates **communicate by identity only** — preserves that boundary by
forbidding one aggregate from holding a direct object reference to another. The reference becomes
`workspaceId: String` (or `principalId: String`), never `workspace: Workspace`.

Without an executable guard, this invariant erodes silently:

- A handler in one bounded context reaches directly for `workspace: Workspace` to read its name,
  dragging the entire Workspace aggregate into the caller's transactional boundary.
- A new `@AggregateRoot` class starts life without a marker, then six months later holds
  `principal: Principal` instead of `principalId: String`, and nobody notices because no test
  was checking property types on aggregate roots.
- A `Set<User>` sneaks in where `Set<String>` (user IDs) was correct.

`AggregateBoundaryTest` (ADR-0015) and `ValueObjectImmutabilityTest` (ADR-0017) already
validate cross-context imports and value-object shape. Neither validates the **type** of
properties an aggregate holds.

## Decision drivers

- Identity-only references must be enforced automatically; review-time discipline does not
  scale.
- The rule applies to `@AggregateRoot` and `@DomainEntity` classes. Value objects, ports,
  repositories, and pure-logic classes do not follow the aggregate-to-aggregate reference
  rule — value objects are immutable descriptors, ports expose repository contracts,
  repositories know their aggregates' internal layout, and pure-logic classes are free to
  hold whatever they need.
- The rule targets cross-context coupling. Within a bounded context, multiple aggregates may
  hold direct references for tight transactional coupling — the rule does not apply there.
- The bootstrap MUST land in one commit, pass every existing test, and stay green for every
  annotated aggregate. Annotating more aggregates is opt-in; the rule enforces what is marked.
- "All checks must pass" is the operating bar. The first commit MUST NOT leave the build red.

## Decision

### 1. The rule, scoped to marked classes

A property declared on a class annotated with `@AggregateRoot` or `@DomainEntity` whose
declared type's FQN starts with `com.profiletailors.smp.` (i.e., references a class in another
bounded context inside this codebase) MUST be an identity reference. The property's source
type name MUST end with one of:

- `Id`
- `Ids`
- `Identifier`
- `Id?` (nullable identity)

Examples:

- ✅ `val workspaceId: String` — cross-context identity (currently `String`-typed, not yet a
  typed ID; ADR-0016 is about the name, not the type)
- ✅ `val ownerPrincipalId: String` — cross-context identity
- ❌ `val workspace: Workspace` — cross-context direct object reference
- ❌ `val principal: Principal` — cross-context direct object reference

Same-context references are allowed. Within one bounded context, multiple aggregates may
reference each other directly. The rule targets cross-context coupling only.

### 2. Annotated classes at bootstrap

Eight classes are marked at the commit that introduces this ADR:

- **tenancy**: `Workspace` (@AggregateRoot), `WorkspaceMembership` (@DomainEntity),
  `WorkspaceOwnership` (@DomainEntity)
- **identity**: `EmailVerificationToken` (@AggregateRoot), `PasswordResetToken`
  (@AggregateRoot)
- **privacy**: `DataSubjectRequest` (@AggregateRoot)
- **platformadmin**: `WaitlistInvitation` (@AggregateRoot), `PlatformRoleAssignment`
  (@AggregateRoot)

All eight conform to the rule today — every cross-context property is already an identity
reference. No production refactor was needed to land this ADR.

### 3. Bounded contexts mark their aggregates and entities

Every bounded context in `com.profiletailors.smp.{context}.domain` MUST mark aggregates
(`@AggregateRoot`) and internal entities (`@DomainEntity`) so the test guards them. Unmarked
classes are simply not checked — a known limitation that the Follow-up Actions commit to
close.

### 4. Enforcement lives in `IdentityOnlyAggregateCommunicationTest`

The rule is enforced by
`server/smp/src/test/kotlin/com/profiletailors/smp/IdentityOnlyAggregateCommunicationTest.kt`,
tagged `@Tag("ddd-conformance")` so it runs as part of `./gradlew :server:smp:test` by default.
The test uses Konsist to scan the production source set only.

Scenario:

- `aggregatesCommunicateByIdentityAcrossContexts` — fails when a property on a `@AggregateRoot`
  or `@DomainEntity` class has a declared type in `com.profiletailors.smp.*` (i.e., a class
  in another bounded context) and that property's name does not end with `Id`, `Ids`,
  `Identifier`, or `Id?`.

Failure messages cite `ADR-0016 violated` and the offending `Class.property: TypeName`.

## Scope and boundaries

- Applies to all production classes annotated with `@AggregateRoot` or `@DomainEntity` in
  `com.profiletailors.smp.*.domain`.
- Test source sets are explicitly excluded.
- Value objects, ports, repositories, exceptions, policies, and resolvers are NOT scanned
  even when their file lives under `*.domain`.
- Properties whose FQN starts with `com.profiletailors.common.*` (the shared kernel) are NOT
  scanned — they are infrastructure types, not aggregates.
- Generic types and nullable types are partially supported: `sourceType` includes the `?`
  suffix for nullable and the generic arguments inline. Future revisions may need to handle
  `Set<Workspace>`-style references with explicit AST traversal of type arguments.

### Accepted exceptions

- **`ModuleMetadata` classes** — exempted per ADR-0015.
- **Spring Modulith wiring** — never annotated as an aggregate.
- **Test fixture classes** — Konsist's `scopeFromProduction()` excludes `src/test/`.

## Alternatives considered

### Strict rule (same-context also forbidden)

- Description: forbid `workspace: Workspace` everywhere, even within tenancy.
- Advantages: stronger guarantees; same rule everywhere.
- Disadvantages: forces value-object-style wrapping for every cross-aggregate reference inside
  a single context, which the team has not adopted.
- Reason rejected: out of scope for a bootstrap; revisit once typed IDs and value objects are
  the norm inside every aggregate.

### Scan all `*.domain` classes (not just marked ones)

- Description: the test inspects every class in `com.profiletailors.smp.*.domain` regardless of
  marker. The skill's pseudocode uses this approach.
- Advantages: no marker discipline required; catches unmarked aggregates.
- Disadvantages: 92+ files today, several hundred lines per file. Unmarked classes pollute the
  failure log. Future contributors would not be motivated to mark aggregates because unmarked
  ones still get checked.
- Reason rejected: marker-driven scanning is the discipline the team needs. A separate
  "unmarked aggregate detector" is a better solution (see Follow-up Actions).

### Spring Modulith / ArchUnit

- Description: encode the rule in ArchUnit, matching by import FQN.
- Advantages: integrates with the existing layer test suite.
- Disadvantages: ArchUnit operates on bytecode, not source. The "property name suffix" rule
  is a string match on the declared type — simpler in Konsist.
- Reason rejected: this is a string-shape rule, not an import or layer rule.

## Consequences

### Positive

- Eight aggregate-bearing classes are now guarded by an executable rule. Any future
  contributor who adds `workspace: Workspace` to one of them fails the build before review.
- The rule is marker-driven — annotating a new aggregate brings it under enforcement
  immediately.
- The annotation surface (`@AggregateRoot`, `@DomainEntity`) is the same as ADR-0015. No new
  marker needed.

### Negative

- The rule is currently a guard rail for the eight annotated classes only. Adding more
  aggregates without marking them leaves them unguarded.
- The current implementation uses `sourceType` for property type extraction. Generic types
  (`Set<Workspace>`) and complex nullable cases may produce false negatives until the test is
  extended with explicit type-argument traversal.

### Risks

- Konsist's `sourceType` includes the source representation, including nullable `?` markers.
  The suffix checks (`Id?`, `Ids`, `Identifier`) are sufficient for the cases observed in the
  eight annotated classes today but may miss edge cases.
- Konsist 0.17.3 might evolve the type extraction API in future releases. Pin Konsist and
  revisit if a future Kotlin compiler change alters how property types are represented.

### Accepted trade-offs

- The rule does NOT require typed IDs (e.g., `WorkspaceId` instead of `String`). Today's
  codebase uses raw `String` for IDs. Typed IDs are a separate concern (and would be enforced
  via a different Konsist test or compiler plugin).
- The rule accepts same-context direct references. Within one bounded context, this is the
  pragmatic trade-off — refactoring every same-context reference would explode scope.

## Compliance and enforcement

Enforced by `IdentityOnlyAggregateCommunicationTest` (Konsist, JUnit5):

- Scenario: `aggregatesCommunicateByIdentityAcrossContexts` — fails on direct cross-context
  references on `@AggregateRoot` or `@DomainEntity` classes.

Failure messages cite `ADR-0016 violated` and the offending `Class.property: TypeName`.

Tag `@Tag("ddd-conformance")` makes the test run as part of `./gradlew :server:smp:test` by
default. No special CI wiring required.

## Verification

- `./gradlew :server:smp:test --tests "*IdentityOnlyAggregateCommunicationTest*"` MUST pass.
- A deliberate violation (e.g., adding `val principal: Principal` to a tenancy class) MUST
  fail with a message that starts with `ADR-0016 violated`.

## Migration or remediation

The bootstrap commit lands alongside this ADR marks eight aggregates. Every future commit
that introduces a new `@AggregateRoot` or `@DomainEntity` MUST verify the new class conforms
to the rule. Existing unmarked classes remain unguarded until they are marked.

## Follow-up actions

- [ ] Audit and mark aggregates and internal entities in every bounded context's `domain`
  package: analytics, audit, authorization, credentials, governance, hashtags, ideas,
  leadcapture, mcp, media, notifications, observability, publishing.
- [ ] Add a Konsist "unmarked aggregate detector" that fails when a `data class` in `*.domain`
  has an `id`/`Id` property of type `String` or `UUID` but lacks `@AggregateRoot` — the
  "forgot to annotate" guard rail.
- [ ] Revisit generic type handling: add explicit AST traversal of `KoTypeArgumentProvider`
  so `Set<Workspace>`-style references are checked, not just direct property types.
- [ ] Consider typed IDs (`WorkspaceId` instead of `String`) as a follow-up ADR; not in scope
  here.

## Revisit conditions

- Konsist 0.17.x stops being maintained and a successor API requires a non-trivial migration.
- Generic type references (`Set<AggregateRoot>`) start showing up in real code, demanding
  proper type-argument traversal.
- The team adopts typed IDs everywhere, making the current `String`-typed `workspaceId`
  references inconsistent with the marker pattern.