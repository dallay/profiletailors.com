---
name: hexagonal-architecture
description: >-
  Place Kotlin backend behavior across domain, application, and infrastructure boundaries in
  Profile Tailors. Use when adding or changing a bounded context, use case, port, adapter, or
  dependency direction; pair with ddd-architecture for aggregate and value-object decisions.
allowed-tools: Read, Edit, Write, Glob, Grep, Bash
metadata:
  category: backend-platform
  family: kotlin
  source: local
  version: 2026-09-28
---

# Hexagonal Architecture

Keep the dependency rule clear while matching the existing bounded context. The architecture
defines boundaries and dependency direction; it does not require every operation to have the same
classes, folders, or CQRS ceremony.

## Source of truth

- Read `.agents/AGENTS.md`, the relevant ADRs, and the existing feature before changing backend
  structure.
- `ARCH-001` and `HexagonalArchTest.kt` own layer and dependency rules. Follow the executable
  checks and preserve their failure messages; do not duplicate or weaken them.
- Use `ddd-architecture` for `ARCH-003` through `ARCH-005`, aggregates, identity references, and
  value-object invariants. Spring Modulith and `ARCH-002` own module isolation.
- Use `spring-boot` for framework, WebFlux, R2DBC, transaction, and HTTP adapter details.

## Dependency rule

```text
domain <- application <- infrastructure
```

- **Domain** owns business invariants and domain concepts. Keep it independent of Spring, transport,
  persistence, and provider SDKs.
- **Application** coordinates use cases through domain concepts and ports. Keep Spring and transport
  types out of application contracts.
- **Infrastructure** connects the application to HTTP, persistence, messaging, and external
  providers. It implements ports and maps between external representations and internal contracts.
- A dependency may point inward at a boundary, but domain and application code must not depend on
  infrastructure implementations.

## Choose the smallest useful shape

For each behavior, decide what needs to be isolated or substituted:

1. Put a business invariant in a domain type or policy when it belongs to the model.
2. Put orchestration in an application use case. A focused function or class is enough when it has
   one clear responsibility.
3. Define a port where the inner layer needs an external capability or a boundary improves
   isolation. Keep the port near the policy that uses it.
4. Add an adapter only for a real external boundary, such as persistence, HTTP, or a provider.
5. Add a command/query pair or separate handler and service only when the distinction improves
   independent behavior, reuse, transaction boundaries, or testing. Do not create wrappers that
   only forward arguments.

The three architectural layers apply to the bounded context, not mechanically to every feature or
operation. Follow the package and class shape already used by nearby code unless the task calls for
a boundary change.

## Repository conventions

- Backend code lives under `server/smp/src/main/kotlin/com/profiletailors/smp/<context>/`.
- Keep a bounded context's domain, application, and infrastructure concerns within its existing
  module/package boundaries. Do not add empty layers just to make a tree look uniform.
- Application services use `com.profiletailors.common.domain.Service` where component discovery
  requires it. Do not substitute Spring stereotypes in domain or application code.
- Prefer explicit, local interfaces over generic repository abstractions. Add a port when it
  represents a boundary or policy, not solely to mirror each concrete class.
- Keep transport DTOs and persistence records at their adapters. Map explicitly when the models
  differ in responsibility or lifecycle.
- Keep transaction setup in infrastructure according to the established R2DBC policy. Use
  `spring-boot` before changing transaction or coroutine integration.

## Review questions

- Does each dependency point toward policy rather than implementation?
- Is there a real boundary behind every new port, adapter, handler, or service?
- Can a simpler use case meet the same isolation and testability needs?
- Are persistence, HTTP, or provider types leaking inward?
- Do existing architecture tests cover the intended contract? Is a behavioral test needed for a
  semantic invariant that a structural test cannot prove?

## Related guidance

- `.agents/AGENTS.md`
- `.agents/skills/architecture-governance/SKILL.md`
- `.agents/skills/ddd-architecture/SKILL.md`
- `.agents/skills/spring-boot/SKILL.md`
- `docs/architecture/adr/0002-adhere-to-hexagonal-architecture.md`
- `server/smp/src/test/kotlin/com/profiletailors/smp/HexagonalArchTest.kt`
