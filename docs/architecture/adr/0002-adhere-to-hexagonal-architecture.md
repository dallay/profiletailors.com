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

The project requires a high degree of testability and the ability to evolve infrastructure (e.g.,
swapping email providers or database drivers) without affecting core business logic.

## Decision drivers

- Maintainability (logic is not buried in framework code).
- Testability (domain logic can be tested with pure unit tests).
- Flexibility (ports and adapters isolate external dependencies).

## Decision

Every bounded context MUST follow the Hexagonal Architecture pattern with three distinct layers:

1. **Domain**: Pure Kotlin logic, models, and policies. MUST NOT depend on any framework or other
   layers.
2. **Application**: Use-case handlers and ports (interfaces). MUST NOT depend on the infrastructure
   layer or Spring stereotypes.
3. **Infrastructure**: Adapters (Controllers, Repositories, Client clients). MUST depend on
   Application and Domain to implement ports.

Application services MUST use the custom `com.profiletailors.common.domain.Service` marker instead
of Spring's `@Service`.

## Scope and boundaries

- Applies to all packages under `com.profiletailors.smp.{context}`.

### Accepted exceptions

- **`com.profiletailors.smp.config`**: Cross-cutting infrastructure configuration (e.g.,
  `PersistenceConfig.kt` for R2DBC transaction management). This package lives outside any
  bounded context because it provides shared Spring Boot infrastructure wiring that spans
  multiple contexts. It does NOT contain business logic.

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

Enforced via two ArchUnit test suites that fail the build if layer rules are violated:

- **`HexagonalArchTest.kt`**: Layer isolation rules — domain must not depend on Spring, application
  must not depend on infrastructure or Spring stereotypes, all bounded contexts must expose all
  three layers.
- **`ComponentScanArchTest.kt`**: Spring stereotype guards — application layer must use the custom
  `@Service` marker (not Spring's `@Component`, `@Service`, or `@Repository`), and no nested
  `@ComponentScan` in infrastructure configs.

## Verification

- No source file under `domain/` imports `org.springframework.*`.
- No source file under `application/` imports `..infrastructure..`.

## Migration or remediation

None required; the codebase currently adheres well to this ADR.

## Follow-up actions

- [x] Add custom ArchUnit rules to detect Spring stereotypes in the application layer.
  See `ComponentScanArchTest.kt` (implemented 2026-06-30).

## Revisit conditions

- The complexity of mapping becomes a significant bottleneck for feature delivery.
