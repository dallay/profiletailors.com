# ADR-0001: Use a Modular Monolith Backend

- Status: Accepted
- Date: 2026-06-21
- Decision owners: Principal Architect
- Scope: Backend (`server/smp`)
- Supersedes: None
- Superseded by: None
- Related:
  - C4: [Container Diagram](../c4/02-container.md), [Component Diagram](../c4/03-component.md)

## Context

The Profile Tailors backend manages multiple complex domains including Identity, Authorization,
Tenancy, Publishing, and Analytics. A microservices architecture was considered but rejected early
to avoid operational complexity and network latency between tightly coupled domains.

Current implementation uses a single deployable Gradle module (`:server:smp`) with internal packages
representing Bounded Contexts.

## Decision drivers

- Operational simplicity (single deployment unit).
- Developer productivity (easy refactoring across context boundaries).
- Consistency (shared foundation and programming model).
- Future flexibility (ability to extract microservices if scaling or ownership requires it).

## Decision

The backend MUST be built as a Modular Monolith.
Bounded contexts MUST be isolated by package boundaries.
Spring Modulith MUST be used to enforce these boundaries.
Backend sources under `server/smp/src` MUST use Kotlin. Spring Modulith package metadata MUST use
internal `ModuleMetadata` types annotated with `@PackageInfo` and `@NamedInterface` instead of Java
`package-info.java` files.

## Scope and boundaries

- Affected: All code under `com.profiletailors.smp`.
- Rule: Contexts MUST NOT depend on the `infrastructure` or `application` layers of other contexts.
- Rule: Cross-context communication SHOULD happen via Domain Events or shared API interfaces in the
  `application` layer.

## Alternatives considered

### Microservices

- Advantages: Independent scaling, technology diversity.
- Disadvantages: High operational overhead, distributed transaction complexity.
- Reason rejected: Excessive for current team size and stage.

## Consequences

### Positive

- Fast local development and testing.
- Strong type safety across boundaries.
- Simplified CI/CD pipeline.

### Negative

- Single point of failure for the entire backend.
- Shared resource contention (e.g., CPU, Memory, DB connections).

### Risks

- "Big ball of mud" if Modulith boundaries are not strictly guarded.

### Accepted trade-offs

- Deployment coupling is accepted in exchange for development speed.

## Compliance and enforcement

Enforced via `ModularityVerificationTest.kt` using Spring Modulith's `verify()` method.

## Verification

- All tests in `com.profiletailors.smp.ModularityVerificationTest` MUST pass.
- No direct package imports from `com.profiletailors.smp.{other_context}.infrastructure`.

## Migration or remediation

The previously recorded `authorization -> audit :: application` violation was resolved without
weakening Modulith verification. The Kotlin metadata migration preserves the resulting module and
named-interface model.

## Follow-up actions

- [x] Fix pre-existing Modulith violation between Authorization and Audit.
- [x] Re-enable `ModularityVerificationTest`.

## Revisit conditions

- Domain ownership moves to separate teams.
- A specific context requires significantly different scaling or availability characteristics.
