# Architecture Evidence Ledger

## Finding: Modular Monolith with Spring Modulith

### Observed implementation

The backend is a single Gradle module (`:server:smp`) containing multiple domain-focused packages (
identity, authorization, tenancy, publishing, etc.). Spring Modulith dependencies are present in
`build.gradle.kts`.

### Evidence

- `server/smp/build.gradle.kts`: `implementation(libs.spring.modulith.starter.core)`
- `server/smp/src/main/kotlin/com/profiletailors/smp/`: Package structure organized by context.
- `server/smp/src/test/kotlin/com/profiletailors/smp/ModularityVerificationTest.kt`: Uses
  `ApplicationModules.of(SmpApplication::class.java).verify()`.

### Documented intention

"Start with a modular monolith, not microservices... Spring Modulith enforces module boundaries." (
`docs/architecture/README.md`)

### Possible rationale

Simpler deployment and operations, easier refactoring, and clear future extraction path for
microservices if needed.

### Contradictions

The `ModularityVerificationTest.kt` is explicitly disabled with a comment noting a pre-existing
violation: `authorization -> audit :: application`.

### Current lifecycle

Implemented (partially enforced).

### Architectural significance

High.

### Confidence

High.

### ADR candidate

Yes.

### Open questions

- What is the remediation plan for the current Modulith violation?
- Are there strict rules for inter-module communication (e.g., event-only vs. shared-api)?

---

## Finding: Hexagonal Architecture (Ports & Adapters)

### Observed implementation

Each bounded context follows a strict `domain`, `application`, and `infrastructure` package
structure. A custom `@Service` marker is used in the application layer.

### Evidence

- `server/smp/src/main/kotlin/com/profiletailors/smp/{context}/`: `domain/`, `application/`,
  `infrastructure/` subpackages.
- `server/smp/src/test/kotlin/com/profiletailors/smp/HexagonalArchTest.kt`: ArchUnit tests enforcing
  layer dependencies.
- `shared/common/src/main/kotlin/com/profiletailors/common/domain/Service.kt`: Custom annotation.

### Documented intention

"Separate domain, application, and infrastructure layers... Testability (domain logic isolated from
frameworks)..." (`docs/architecture/README.md`)

### Possible rationale

Decouple business logic from external technologies like Spring, R2DBC, or specific HTTP frameworks.

### Contradictions

None found in enforcement code, but some infrastructure adapters manage transactions manually (e.g.,
`R2dbcApiKeyCredentialReplacementGateway.kt`) which might leak infrastructure concerns if not
carefully isolated.

### Current lifecycle

Implemented.

### Architectural significance

High.

### Confidence

High.

### ADR candidate

Yes.

---

## Finding: Reactive Stack (WebFlux, Coroutines, R2DBC)

### Observed implementation

The entire backend uses non-blocking types. Controllers are `suspend`, repositories use
`DatabaseClient` or `ReactiveSearchRepository`, and R2DBC is the database driver.

### Evidence

- `server/smp/build.gradle.kts`: Starters for `webflux` and `data-r2dbc`.
- `server/smp/src/main/kotlin/com/profiletailors/smp/{context}/infrastructure/http/`: Controllers
  use `suspend fun`.
- `server/smp/src/main/kotlin/com/profiletailors/smp/{context}/infrastructure/persistence/`:
  Repositories use `DatabaseClient`.

### Documented intention

"Use Spring WebFlux + Kotlin coroutines + R2DBC... Non-blocking I/O for better resource
utilization..." (`docs/architecture/README.md`)

### Possible rationale

High concurrency requirements and efficient resource usage.

### Contradictions

None.

### Current lifecycle

Implemented.

### Architectural significance

High.

### Confidence

High.

### ADR candidate

Yes.

---

## Finding: CQRS Pattern

### Observed implementation

Commands and Queries are explicitly defined as data classes. Handlers are separate classes (e.g.,
`RegisterUserHandler`). A `Mediator` is used for dispatch.

### Evidence

- `shared/bus/src/main/kotlin/com/profiletailors/common/domain/bus/`: Interfaces for `Command`,
  `Query`, and `Mediator`.
- `server/smp/src/main/kotlin/com/profiletailors/smp/{context}/application/`: Handlers implement
  `CommandHandler` or `QueryHandler`.

### Documented intention

"Separate commands (mutations) from queries (reads)... Clear intent... Enables different
optimization strategies." (`docs/architecture/README.md`)

### Possible rationale

Decouple request entry points from business logic and support independent evolution of read/write
models.

### Contradictions

Some handlers return data on commands (`CommandWithResult`), which is a common but "relaxed" CQRS
variant.

### Current lifecycle

Implemented.

### Architectural significance

Medium.

### Confidence

High.

### ADR candidate

Yes (naming conventions and mediator usage).

---

## Finding: Mixed Identifier Strategy

### Observed implementation

Most entities use `varchar(64)` primary keys with human-readable prefixes (e.g., `user-`, `ws-`,
`wm-`). However, the `secure_credentials` table uses raw `uuid`.

### Evidence

-

`server/smp/src/main/kotlin/com/profiletailors/smp/tenancy/infrastructure/R2dbcWorkspaceProvisioningService.kt`:
`val workspaceId = "ws-${UUID.randomUUID()}"`.

- `server/smp/src/main/resources/db/changelog/publishing/008-create-secure-credentials.yaml`:
  `type: uuid`.

### Documented intention

Some docs mention "client-generated UUIDs" and "UUID v4 asset identifier... MUST NOT be
sequential" (`04-code.md`).

### Possible rationale

Prefixed IDs improve observability and log readability.

### Contradictions

Drift between target (client-generated UUIDs) and implementation (backend-generated prefixed UUIDs).

### Current lifecycle

Implemented (prefixed backend IDs).

### Architectural significance

Medium.

### Confidence

High.

### ADR candidate

Yes.

---

## Finding: Resource Creation via POST (vs PUT)

### Observed implementation

Resources like Workspaces, Media Assets, and Publications are created using `POST` endpoints.

### Evidence

- `MediaAssetController.kt`: `@PostMapping` for `createAsset`.
- `PublishingControllers.kt`: `@PostMapping` for `publications`.

### Documented intention

"Every creation endpoint for offline-capable aggregates uses PUT /{resource}/{id}." (
`docs/architecture/README.md` template example, but possibly aspirational).

### Possible rationale

Standard REST conventions (POST for creation, server generates ID).

### Contradictions

Documentation mentions PUT-based creation for offline support, but code does not reflect this.

### Current lifecycle

Implemented (POST).

### Architectural significance

Medium.

### Confidence

High.

### ADR candidate

Yes (clarify strategy).

---

## Finding: Frontend Framework Split (Vue + Astro)

### Observed implementation

Marketing site is Astro 7. Web application (dashboard) is Vue 3 with Pinia.

### Evidence

- `apps/web/marketing/package.json`: `"astro": "^7.1.0"`.
- `apps/web/app/package.json`: `"vue": "3.5.38"`, `"pinia": "3.0.4"`.

### Documented intention

C4 diagrams and some status docs mention "Web application (React)".

### Possible rationale

Astro for SEO/speed on marketing, Vue for rich SPA experience in dashboard.

### Contradictions

Documentation drift (mentions React).

### Current lifecycle

Implemented (Vue/Astro).

### Architectural significance

High.

### Confidence

High.

### ADR candidate

Yes.

---

## Finding: Multi-tenancy via Application-level Filtering

### Observed implementation

Tenancy is isolated by `workspace_id` columns in most tables. Repositories include
`WHERE workspace_id = :workspaceId` in queries. A web filter extracts `X-Workspace-Id` from headers.

### Evidence

- `WorkspaceContextWebFilter.kt`: Reads `X-Workspace-Id`.
- `R2dbcWorkspaceReadRepository.kt`: Filters by `principalId` and joins.
- Repositories in `publishing` and `media` include `workspaceId` in most methods.

### Documented intention

"Multi-tenant workspaces... data isolation is application-enforced..." (Inferred from `SUMMARY.md`).

### Possible rationale

Simple to implement with R2DBC and relational schema.

### Contradictions

No evidence of PostgreSQL RLS being used yet, despite multi-tenancy being a core feature.

### Current lifecycle

Implemented.

### Architectural significance

High.

### Confidence

High.

### ADR candidate

Yes.

---

## Finding: Authentication Flow (JWT + HttpOnly Cookie)

### Observed implementation

Login/Register returns AuthTokens (including AccessToken). Refresh token is handled via HttpOnly
cookie. Access token lives only in memory on the frontend.

### Evidence

- `LocalAuthHandlers.kt`: `issueAuthSession` creates tokens.
- `auth-api.ts`: `requestRaw` includes `credentials: 'include'`.
- `auth.ts` (Vue store): "Access token lives ONLY in memory".

### Documented intention

"JWT tokens: Short-lived (15 min)..." (`SUMMARY.md`). "Dedicated refresh and logout endpoints..." (
`platform/spec.md`).

### Possible rationale

Security (preventing XSS access to refresh tokens) and SPA state management.

### Contradictions

None.

### Current lifecycle

Implemented.

### Architectural significance

High.

### Confidence

High.

### ADR candidate

Yes.
