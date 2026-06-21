# ADR-0002: Adhere to Hexagonal Architecture

- Status: Accepted
- Date: 2026-06-21
- Decision owners: Principal Architect
- Scope: Backend (`server/smp`)
- Supersedes: None
- Superseded by: None
- Related:
  - C4: [Code Diagram](../c4/04-code.md)

## Context
The project requires a high degree of testability and the ability to evolve infrastructure (e.g., swapping email providers or database drivers) without affecting core business logic.

## Decision drivers
- Maintainability (logic is not buried in framework code).
- Testability (domain logic can be tested with pure unit tests).
- Flexibility (ports and adapters isolate external dependencies).

## Decision
Every bounded context MUST follow the Hexagonal Architecture pattern with three distinct layers:
1. **Domain**: Pure Kotlin logic, models, and policies. MUST NOT depend on any framework or other layers.
2. **Application**: Use-case handlers and ports (interfaces). MUST NOT depend on the infrastructure layer or Spring stereotypes.
3. **Infrastructure**: Adapters (Controllers, Repositories, Client clients). MUST depend on Application and Domain to implement ports.

Application services MUST use the custom `com.profiletailors.common.domain.Service` marker instead of Spring's `@Service`.

## Scope and boundaries
- Applies to all packages under `com.profiletailors.smp.{context}`.

## Alternatives considered
### Layered Architecture (Traditional)
- Description: Controllers -> Services -> Repositories.
- Disadvantages: Core logic often becomes dependent on the database schema and Spring framework.
- Reason rejected: Makes unit testing difficult and increases coupling.

## Consequences
### Positive
- Business logic is clearly isolated and highly testable.
- Infrastructure changes are local to the `infrastructure` package.
### Negative
- Increased boilerplate (mappings between domain models and DTOs/Entities).
- Higher cognitive load for new developers.
### Accepted trade-offs
- The overhead of mapping and strict layering is accepted for long-term maintainability.

## Compliance and enforcement
Enforced via `HexagonalArchTest.kt` using ArchUnit. This test fails the build if layer dependency rules are violated.

## Verification
- No source file under `domain/` imports `org.springframework.*`.
- No source file under `application/` imports `..infrastructure..`.

## Migration or remediation
None required; the codebase currently adheres well to this ADR.

## Follow-up actions
- [ ] Add custom ArchUnit rules to detect Spring stereotypes in the application layer.

## Revisit conditions
- The complexity of mapping becomes a significant bottleneck for feature delivery.
