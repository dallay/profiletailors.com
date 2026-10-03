# Backend Skill Semantics

## Requirements

- Backend skills describe the current Profile Tailors baseline: Kotlin, Spring Boot, WebFlux,
  coroutines, and reactive persistence where the adapter supports it.
- Domain owns repository and gateway ports. Application uses those ports, and infrastructure
  implements them, as defined in ADR-0002.
- Application services use the real framework-free
  `com.profiletailors.common.domain.Service` marker. Spring stereotypes and framework-specific
  wiring remain in infrastructure.
- Active examples reflect Kotlin and the project's test conventions. Examples using servlet,
  JPA/JDBC, blocking clients, Mockito, or Java are clearly identified as migration, legacy, or
  general reference material and are not presented as the Profile Tailors default.
- Coroutine examples preserve structured concurrency and distinguish genuinely blocking APIs from
  WebFlux and R2DBC work. `Dispatchers.IO` is not a generic repository dispatcher.
- Dependency versions for an existing service come from `gradle/libs.versions.toml` and its build
  configuration. New services use current compatible releases.

## Review

Review changed Spring skill files and their directly linked examples against the repository's
current build and architecture. Do not require a recursive scanner, fixture corpus, or CI gate for
skill content; manual review follows the decision in ADR-0026.
