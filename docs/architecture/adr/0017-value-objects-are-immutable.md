# ADR-0017: Value Objects Are Immutable and Validate at Construction

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
        - ADR-0016: Aggregates Communicate by Identity Only (planned)
    - Marker annotation: `com.profiletailors.common.domain.ValueObject`

## Context

A DDD Value Object is a small, immutable, side-effect-free descriptor defined entirely by its
attributes. Two instances with the same attributes are considered equal; an instance can never
be in a partially-valid state because the constructor enforces every invariant up front.

Without an executable guard rail, value-object invariants erode silently:

- A `data class` accumulates a mutable `var` property that lets callers mutate state after
  construction, breaking referential transparency and invalidating any cached hash/equality.
- A constructor accepts raw input and lets invalid values (blank strings, negative durations,
  unparseable identifiers) reach the domain layer.
- A setter sneaks in through `setX(...)` because the surrounding class looks like a domain
  entity but is actually a value type — and nobody notices for months.

`AggregateBoundaryTest` (ADR-0015) already catches structural mistakes across aggregate
boundaries. It does not validate the **shape** of value objects inside the domain.

## Decision drivers

- Immutability must be enforced automatically; review-time discipline does not scale.
- Validation must happen at construction, not at use site. Catching a malformed `Email` at the
  repository layer is too late.
- The rule MUST apply to `data class`, `value class`, and `enum class` VOs with one shared
  marker (`@ValueObject`).
- Tests in `src/test` legitimately need raw material to build test fixtures; the rule applies
  to production code only.
- The bootstrap MUST land in one commit and stay green for every existing VO in the shared
  kernel — adding VOs is opt-in, marking them is mandatory.

## Decision

### 1. The `@ValueObject` marker

`com.profiletailors.common.domain.ValueObject` is a CLASS-target annotation in the shared
kernel. It commits a class to the three invariants below:

1. **Immutability** — every property is `val` or `private`; no `set*` or `mutate*` public
   functions exist.
2. **Validation** — every invariant is enforced in `init`, a factory method (`of`, `create`,
   `from`, `fromRaw`, `ensure`, `generate`, `random`), or by composition with other VOs that
   validate themselves.
3. **Equality by attributes** — auto-derived for `data class` and `value class`. Enums are
   equal by constant identity and need no further check.

Enums are exempted from the immutability and validation checks because they are inherently
immutable and inherently valid by construction. The marker still applies — it captures the
"value type" semantic for grep, code review, and future static-analysis tools.

### 2. Annotating the shared kernel

Every value object in `shared/common/src/main/kotlin/com/profiletailors/common/domain/` MUST
be marked. The bootstrap commit lands the annotations on:

- `vo/credential/Credential.kt` — `Credential`, `CredentialId`, `CredentialValue`
- `vo/email/Email.kt` — `Email`
- `vo/ip/IpHash.kt` — `IpHash`
- `vo/name/` — `FirstName`, `LastName`, `Name`
- `vo/Username.kt` — `Username`
- `model/WorkspaceId.kt` — `WorkspaceId`
- `model/Language.kt` — `Language`
- `context/PrincipalType.kt` — `PrincipalType`
- `observability/RequestOutcome.kt` — `RequestOutcome`

Plus `WorkspaceStatus` in `server/smp/tenancy/domain/Workspace.kt` (the only status enum
inside an aggregate the boundary test already covers).

### 3. Bounded contexts mark their own VOs

Every bounded context in `com.profiletailors.smp.{context}.domain` MUST mark its VOs with
`@ValueObject`. Adding a new VO without the marker fails the build.

### 4. Enforcement lives in `ValueObjectImmutabilityTest`

The rule is enforced by
`server/smp/src/test/kotlin/com/profiletailors/smp/ValueObjectImmutabilityTest.kt`,
tagged `@Tag("ddd-conformance")` so it runs as part of `./gradlew :server:smp:test` by default.
The test uses Konsist to scan the production source set only (test fixtures legitimately
materialise raw values).

Two scenarios:

- `valueObjectsAreImmutable` — fails on any mutable property or public mutator function on a
  `@ValueObject` class. Enums are exempted.
- `valueObjectsValidateInvariants` — fails when a `@ValueObject` class has neither an `init`
  block nor a recognised factory method. Enums are exempted.

Failure messages cite `ADR-0017 violated` and the offending member.

## Scope and boundaries

- Applies to all production classes annotated with `@ValueObject` in `com.profiletailors.smp.*`
  and `com.profiletailors.common.domain.*`.
- Test source sets are explicitly excluded.
- Interfaces are excluded — `@Target(AnnotationTarget.CLASS)` keeps the marker on classes only.
- ID-style value classes wrapping primitives (`WorkspaceId(value: UUID)`,
  `CredentialId(value: UUID)`)
  pass the validation check via the `random()` factory that ships with them.

### Accepted exceptions

- **ModuleMetadata classes** — per the project convention in ADR-0002 and ADR-0015.
- **Spring Modulith wiring** — never annotated as a value object.
- **Test fixture classes** — Konsist's `scopeFromProduction()` excludes `src/test/` entirely;
  any `@ValueObject` test double is allowed to use `var` for assertion convenience.

## Alternatives considered

### Per-class invariants documented in KDoc only

- Description: rely on KDoc to declare each VO's immutability contract.
- Advantages: zero tooling.
- Disadvantages: non-executable; six months from now nobody reads KDoc for invariants;
  a `setX(...)` sneaks in silently.
- Reason rejected: same reasoning as ADR-0015's "manual review" alternative.

### Constrain via `value class` only

- Description: forbid `data class` for VOs and require `@JvmInline value class`.
- Advantages: free immutability enforcement from the Kotlin type system.
- Disadvantages: value classes can only wrap a single property. `Name(firstName, lastName)`,
  `Credential(id, credentialValue)`, and `WorkspaceOwnership(...)` cannot be value classes.
- Reason rejected: too restrictive; the marker is more flexible.

### Use Kotlin's `data object` for enums

- Description: drop enum-style VOs in favour of `data object` singletons.
- Advantages: stronger typing for "constants that behave like values".
- Disadvantages: large refactor across the codebase; `PrincipalType.USER` becomes
  `PrincipalType.User` (different names, different equality semantics).
- Reason rejected: out of scope for a conformance bootstrap.

## Consequences

### Positive

- Every existing VO in the shared kernel is now an executable contract. A future contributor
  who adds `var` to `Email` or removes the `require()` from `IpHash` fails the build before
  review.
- The annotation appears at the top of every VO file, making the "value type" semantic
  visible at code-review time.
- New bounded contexts that follow the pattern get VO enforcement for free once they annotate
  their classes.

### Negative

- One new Konsist scenario adds a few seconds to the test suite.
- Developers must remember to mark new VOs. A follow-up ADR should add a Konsist test that
  fails when a class in `*.domain` has `data` keyword + `val` properties but lacks the marker
  (the "forgot to annotate" guard rail).

### Risks

- The validation test's factory whitelist (`of`, `create`, `from`, `fromRaw`, `ensure`,
  `generate`, `random`) is pragmatic but not exhaustive. A factory named
  `buildValidatedCredential(...)` would be missed. Mitigation: a failing PR review checklist
  is the right place to catch semantic mutation; the Konsist test catches shape.
- Konsist 0.17.x's `hasEnumModifier` is the only enum-detection API at this layer. If a
  future Kotlin release introduces new "value-like" class shapes (sealed interfaces,
  value-class hierarchies), the marker pattern needs revisiting.

### Accepted trade-offs

- The validation rule allows `random()`-named factories for ID types wrapping primitives
  (`UUID`, `Long`). These types are trivially valid because their underlying primitive is
  always valid; the `random()` factory exists to give callers a clean entry point. We accept
  the broader whitelist to keep these types conforming.
- The rule does not check that `data class` has at least one property. An empty `data class`
  passes. This is harmless: empty data classes have no invariants to break.

## Compliance and enforcement

Enforced by `ValueObjectImmutabilityTest` (Konsist, JUnit5):

- Test 1: `valueObjectsAreImmutable` — fails on `var` properties or `set*`/`mutate*` public
  functions on `@ValueObject` classes.
- Test 2: `valueObjectsValidateInvariants` — fails when a `@ValueObject` class has neither
  an `init` block nor a recognised factory method.

Failure messages cite `ADR-0017 violated` and the offending class name.

Tag `@Tag("ddd-conformance")` makes the test run as part of `./gradlew :server:smp:test` by
default. No special CI wiring required.

## Verification

- `./gradlew :server:smp:test --tests "*ValueObjectImmutabilityTest*"` MUST pass.
- A deliberate violation (e.g., adding `var foo: String` to `Email`, or removing `init`
  validation from `IpHash`) MUST fail with a message that starts with `ADR-0017 violated`.

## Migration or remediation

The bootstrap commit lands alongside this ADR marks every VO in the shared kernel and
`WorkspaceStatus` in tenancy. Future bounded contexts MUST mark their VOs in the same
commit that introduces them; doing so on a follow-up commit leaves the immutability rule
unguarded for the gap window.

Existing VOs in bounded contexts that are NOT yet annotated (e.g., publishing, governance,
identity) MUST be marked in follow-up commits before the corresponding bounded context's
DDD conformance can be considered complete.

## Follow-up actions

- [ ] Audit and annotate VOs in `server/smp/{context}/domain/` for every bounded context
  (publishing, governance, identity, privacy, authorization, ideas, platformadmin, media,
  analytics, notifications, leadcapture, hashtags, mcp, observability, audit, credentials).
- [ ] Add a Konsist test that fails when a `data class` in `*.domain` has only `val`
  properties and no marker — the "forgot to annotate" guard rail.
- [ ] Land ADR-0016 (Aggregates Communicate by Identity Only) with
  `IdentityOnlyAggregateCommunicationTest`.

## Revisit conditions

- Konsist 0.17.x stops being maintained and a successor API requires a non-trivial migration.
- Kotlin introduces a new class shape (e.g., `data object`) that needs its own marker.
- The factory whitelist becomes a real source of false negatives; switch to an AST-based
  pattern that recognises "function on companion object" as the canonical factory site.