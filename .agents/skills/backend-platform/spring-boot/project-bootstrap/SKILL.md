---
name: spring-boot-project-bootstrap
description: Use when bootstrapping a new Spring Boot 4 backend from Spring Initializr, choosing Kotlin + WebFlux + Gradle defaults, defining a hexagonal package-by-feature structure, and wiring local development services for a reactive stack.
allowed-tools: Read, Write, Edit, Bash, Glob, Grep
---

# Spring Boot Project Bootstrap

## Overview

Bootstrap new backend services with the **canonical platform baseline** for this codebase:

- **Spring Boot 4**
- **Kotlin**
- **Gradle Kotlin DSL**
- **WebFlux + coroutines**
- **reactive-first persistence**
- **hexagonal architecture inside each feature**

This skill exists to prevent scaffolding the wrong foundation. MVC, Java, Maven, and blocking
persistence stacks are **not** the default here.

## When to Use

- creating a new Spring Boot backend service from scratch
- scaffolding a new Kotlin microservice from Spring Initializr
- starting a reactive API with PostgreSQL, Redis, or MongoDB
- establishing a standard package structure before feature work begins
- setting up local development containers for a new service

## Canonical Defaults

| Concern         | Default                                                                        |
|-----------------|--------------------------------------------------------------------------------|
| Language        | Kotlin                                                                         |
| Spring Boot     | 4.x                                                                            |
| Build tool      | Gradle (`build.gradle.kts`)                                                    |
| Web stack       | WebFlux                                                                        |
| Concurrency     | Coroutines                                                                     |
| SQL persistence | R2DBC                                                                          |
| API docs        | SpringDoc WebFlux starter                                                      |
| Architecture    | Package by feature with `domain/`, `application/`, `infrastructure/`           |
| Tests           | Kotest for pure Kotlin tests; JUnit 5 acceptable for Spring slices/integration |

## Exception Policy

Use these only when the user explicitly asks for them or existing constraints require them:

- **Blocking persistence stacks** → compatibility path only, never the default for a reactive
  service
- **Spring MVC** → only for servlet-based legacy or explicit non-reactive requirements
- **Maven** → only when organization tooling requires it
- **Java** → only when Kotlin is explicitly out of scope

Document every exception clearly in the generated project notes.

## 1. Gather Project Configuration

Ask for only the parameters that materially affect the scaffold.

| Parameter           | Default                       | Notes                                        |
|---------------------|-------------------------------|----------------------------------------------|
| Group ID            | `com.example`                 | valid package root                           |
| Artifact ID         | `demo-service`                | kebab-case                                   |
| Package name        | derived from group + artifact | Kotlin package                               |
| Spring Boot version | latest stable `4.x`           | prefer latest stable available in Initializr |
| Java version        | `21`                          | current LTS baseline                         |
| Primary datastore   | user choice                   | PostgreSQL / Redis / MongoDB / none          |
| Build tool          | `gradle`                      | use Maven only by explicit request           |

Do **not** ask the user to choose between layered vs DDD as if both were equal defaults. For this
platform, scaffold the hexagonal baseline automatically unless the user explicitly asks otherwise.

## 2. Generate Project with Spring Initializr

Use Spring Initializr with Kotlin + Gradle + WebFlux.

### Base dependencies

- `webflux`
- `validation`
- `actuator`
- `docker-compose`
- `testcontainers`

### Conditional dependencies

- PostgreSQL selected → `data-r2dbc`, `postgresql`, `r2dbc`
- Redis selected → `data-redis-reactive`
- MongoDB selected → `data-mongodb-reactive`

### Example

```bash
curl -s "https://start.spring.io/starter.zip" \
  -d type=gradle-project-kotlin \
  -d language=kotlin \
  -d bootVersion=4.0.0 \
  -d groupId=com.example \
  -d artifactId=demo-service \
  -d packageName=com.example.demoservice \
  -d javaVersion=21 \
  -d packaging=jar \
  -d dependencies=webflux,validation,actuator,docker-compose,data-r2dbc,postgresql,r2dbc,testcontainers \
  -o starter.zip

unzip -o starter.zip -d ./demo-service
rm starter.zip
```

If Initializr offers a newer stable Spring Boot 4 version, use that instead of the example value.

## 3. Add Platform Dependencies

Add only what the reactive Kotlin baseline actually needs.

### Build additions to prefer

- SpringDoc starter for **WebFlux**, not MVC
- ArchUnit for architecture enforcement
- Kotest + MockK for pure Kotlin tests if not already present
- Testcontainers modules that match the selected infrastructure

### Example additions

```kotlin
dependencies {
    implementation("org.springdoc:springdoc-openapi-starter-webflux-ui:2.8.15")

    testImplementation("com.tngtech.archunit:archunit-junit5:1.4.1")
    testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
    testImplementation("io.kotest:kotest-assertions-core:5.9.1")
    testImplementation("io.mockk:mockk:1.13.12")
}
```

## 4. Create the Standard Package Structure

Prefer **package by feature**, with hexagonal boundaries **inside** each feature.

```text
src/main/kotlin/com/example/demoservice/
  common/
    application/
      ApplicationService.kt
  workspace/
    domain/
      Workspace.kt
      WorkspaceId.kt
      WorkspaceRepository.kt
    application/
      create/
        CreateWorkspaceCommand.kt
        CreateWorkspaceCommandHandler.kt
        WorkspaceCreator.kt
      find/
        FindWorkspaceQuery.kt
        FindWorkspaceQueryHandler.kt
        WorkspaceFinder.kt
    infrastructure/
      http/
        WorkspaceController.kt
        request/
        response/
      persistence/
        WorkspaceEntity.kt
        WorkspaceMapper.kt
        WorkspaceR2dbcRepository.kt
        WorkspaceStoreR2dbcAdapter.kt
      configuration/
        WorkspaceConfiguration.kt
```

Rules:

- `domain/` is pure Kotlin
- `application/` is framework-agnostic
- `infrastructure/` contains all Spring code
- never scaffold global `controller/`, `service/`, `repository/`, or `dto/` folders as the default

## 5. Configure Properties

Use `application.yml` or `application.properties`; either is acceptable. Prefer typed
`@ConfigurationProperties` over scattered `@Value` usage.

### PostgreSQL + R2DBC example

```properties
spring.application.name=demo-service

spring.r2dbc.url=r2dbc:postgresql://localhost:5432/${POSTGRES_DB:postgres}
spring.r2dbc.username=${POSTGRES_USER:postgres}
spring.r2dbc.password=${POSTGRES_PASSWORD:changeme}

springdoc.swagger-ui.doc-expansion=none
management.endpoints.web.exposure.include=health,info
```

If Flyway or Liquibase is added later, keep that decision explicit. Do **not** default to hidden
schema mutation patterns just because a database exists.

## 6. Local Development Services

Create `compose.yaml` only for the infrastructure actually needed.

### PostgreSQL example

```yaml
services:
  postgres:
    image: postgres:17
    ports:
      - "5432:5432"
    environment:
      POSTGRES_USER: ${POSTGRES_USER:-postgres}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-changeme}
      POSTGRES_DB: ${POSTGRES_DB:-postgres}
    volumes:
      - ./postgres_data:/var/lib/postgresql/data
```

If Redis or MongoDB are required, add only those services. Keep local credentials in a git-ignored
`.env` file.

## 7. Verification

Run the narrowest setup validation that proves the scaffold is healthy.

```bash
./gradlew test
./gradlew build
```

If local containers are required for a selected adapter, start them before running the relevant
integration tests.

## 8. Deliverable Summary

When finished, summarize:

- chosen Boot/Kotlin/Gradle/WebFlux baseline
- selected data stores
- generated feature structure
- any explicit exceptions from the canonical stack
- exact next commands to run locally

## Common Mistakes

- ❌ Bootstrapping Java + MVC + a blocking persistence stack as if it were the default here
- ❌ Generating a global layered package layout for a hexagonal codebase
- ❌ Putting Spring stereotypes into the application layer
- ❌ Treating blocking persistence and reactive persistence as interchangeable in this service
- ❌ Adding dependencies “just in case” before the first feature needs them
- ❌ Running full infrastructure when a simple scaffold build check is enough

## Related Skills

- [`../SKILL.md`](../SKILL.md) — core Spring Boot 4 reactive rules
- [`../../hexagonal-architecture/SKILL.md`](../../hexagonal-architecture/SKILL.md) —
  domain/application/infrastructure boundaries
- [`../../../languages-typing/kotlin/SKILL.md`](../../../languages-typing/kotlin/SKILL.md) — Kotlin
  conventions and test style
