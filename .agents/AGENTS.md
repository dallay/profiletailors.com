# AGENTS.md

> AI agent instructions for the `profiletailors.com` monorepo.

## Project Identity

**Profile Tailors** — social media management platform (schedule, publish, analyze, engage,
collaborate). The product name is **Profile Tailors**; `profiletailors.com` is the domain only.

## Monorepo Structure

```text
apps/web/marketing/   # Astro 6 landing page (EN + ES)
apps/web/app/         # Vue 3 + shadcn-vue SPA (dashboard)
server/smp/          # Spring Boot 4 backend (Kotlin, WebFlux, R2DBC)
shared/               # Kotlin libraries + shared assets
docs/                 # Architecture docs, design specs
openspec/             # SDD artifacts (spec-driven development)
.agents/              # Agent tooling (skills, commands)
```

## Command Hub

**All commands go through `just`** — run `just -l` to list everything. Never guess a command.

### Setup

| Command              | Action                                                 |
|----------------------|--------------------------------------------------------|
| `just setup`         | Full initial setup: .env → install → hooks → agentsync |
| `just install`       | Install deps (`pnpm install --frozen-lockfile`)        |
| `just hooks-install` | Set up Lefthook git hooks                              |

### Frontend Dev

| Command                  | Action                          |
|--------------------------|---------------------------------|
| `just dev-frontend`      | Start both frontend dev servers |
| `just frontend-build`    | Build marketing site            |
| `just frontend-lint`     | Biome lint                      |
| `just frontend-format`   | Biome format                    |
| `just frontend-test`     | Vitest unit tests               |
| `just frontend-test-e2e` | Playwright E2E tests            |

### Backend Dev

| Command                      | Action                                             |
|------------------------------|----------------------------------------------------|
| `just backend-run`           | Start Spring Boot (dev profile)                    |
| `just backend-build`         | Compile and package (`bootJar`)                    |
| `just backend-test`          | Run unit tests (optional: `'postgres'` to exclude) |
| `just backend-test-fast`     | Run unit tests (fast, same as `backend-test`)      |
| `just backend-check`         | Detekt + tests (excludes BDD suites)               |
| `just backend-bdd-fast`      | Run fast BDD suite (Testcontainers Postgres)       |
| `just backend-bdd-postgres`  | Run Postgres BDD suite (requires `infra-up`)       |
| `just backend-test-postgres` | Postgres integration tests (requires `infra-up`)   |

### Infrastructure & CI

| Command           | Action                                          |
|-------------------|-------------------------------------------------|
| `just infra-up`   | Start Postgres + services                       |
| `just infra-down` | Stop containers                                 |
| `just ci`         | Full CI: gitleaks, lint, tests, BDD, build, E2E |
| `just ci-local`   | Fast CI (no E2E, no Postgres BDD)               |
| `just ci-full`    | ci-local + Postgres BDD (infra-up first)        |

## Backend Architecture (Hexagonal)

**Dependency rule:** `domain ← application ← infrastructure`

| Layer              | Can depend on         | Must NOT depend on                  |
|--------------------|-----------------------|-------------------------------------|
| **Domain**         | Nothing (pure Kotlin) | Application, Infrastructure, Spring |
| **Application**    | Domain                | Infrastructure, Spring stereotypes  |
| **Infrastructure** | Domain + Application  | —                                   |

- Package: `com.profiletailors.smp.{context}.{layer}`
- CQRS naming: `GetXQuery`, `{Verb}XCommand`, `XHandler`, `R2dbcXRepository`
- Use `com.profiletailors.common.domain.Service` (not Spring `@Service`)
- Shared libs are cross-cutting, not feature slices
- ModuleMetadata classes are exempted from architecture tests (no business logic allowed)

## Frontend Apps

- **Marketing (Astro 6):** static-first, no SSR. i18n via locale files. Nothing-inspired dark theme.
- **App (Vue 3 + shadcn-vue):** SPA with Vue Router + Pinia. shadcn-vue components via
  `npx shadcn-vue@latest add`.
- Linter: Biome (both). E2E: Playwright.
- Shared assets at `shared/assets/web/*` → imported via `@shared/assets/` alias, copied at build
  time.

## BDD (Cucumber) — MANDATORY

Every new backend feature, command, or endpoint MUST include BDD scenarios.

- **Feature files:** `server/smp/src/test/resources/features/{domain}-{entity}.feature`
- **Step definitions:** `server/smp/src/test/kotlin/com/profiletailors/smp/bdd/glue/*BddSteps.kt`
- **Database seeding:** Use `BddDatabaseSupport` (injected via `@Autowired`). Reset is automatic via
  `resetDatabase()` before each scenario.
- **HTTP client:** Use `WebTestClient` (injected via `@Autowired`). Capture responses in a
  `latestResponse` field.
- **Tags on every feature:** domain tag + `@smoke` + `@fast`. Use `@postgres` only when the scenario
  needs the Postgres-variant infra (otherwise just `@fast`).
- **Bearer tokens:** Use `BddDatabaseSupport.USER_BEARER` (`"Bearer valid-token"`). JWT decoder in
  tests accepts tokens matching `valid-token`, `e2e-*`, `register-*`, `pending-*`, `verified-*`,
  `owner-*` prefixes.
- **Headers:** Always set `Authorization`, `Accept` (`application/vnd.api.v1+json`), and
  `X-Workspace-Id` headers.
- **Run locally:** `just backend-bdd-fast` (fast suite) or `just backend-bdd-postgres` (Postgres
  suite, needs `just infra-up` first).
- **CI enforcement:** BDD fast suite runs on every backend change. Postgres BDD runs on every
  backend change too. Both must pass before merge.

## Documentation Rules

- English only. Files: lowercase `kebab-case.md` (except `README.md`).
- Structure: Overview → Changes → Usage → Troubleshooting → References.

## Dependency Policy

- Check dependency scores with `socket-mcp_depscore` before adding new deps.
- Keep marketing site lightweight — prefer Astro primitives over deps.
- Use existing skills from `.agents/skills/` when available.

## Code Comments Policy

Comments are exceptional. The code must be self-documenting.

**Allowed:** KDoc/TSDoc on public APIs. **Prohibited:** inline `//` comments explaining WHAT;
TODO/FIXME/HACK; commented-out code.

**Exception:** a brief comment explaining WHY a non-obvious approach was taken, or linking to an
external reference (spec, RFC, issue).

## Key Gotchas

- **Gradle wrapper:** `gradlew.bat` on Windows, `./gradlew` on POSIX.
- **Spanish copy:** Longer than English — never use fixed-width containers.
- **Portless:** Both frontend apps use named URLs (`https://profile-tailors.localhost`).
- **CI mandatory:** Never push without `just ci` passing.
- **Conventional commits:** `feat(scope):`, `fix(scope):`, `docs(scope):`, `chore(scope):`.
- **Env loading:** `bootRun` reads root `.env`. Tests need `SMP_DB_TEST_PASSWORD` set.
- **Test tags:** `@Tag("postgres")` and `@Tag("bdd")` exist but do NOT hide failures — all tests run
  by default.

## Consent Management

Consent management spans both frontend surfaces (marketing + app) using a shared layer at
`shared/web/`.

### Shared Layer (`shared/web/`)
- **Types**: `types/consent.ts` — `ConsentReceipt` interface, version/policy constants
- **Validation**: `validation/consent.ts` — Zod schema with `validateConsentReceipt()`
- **Storage**: `utils/consent-storage.ts` — `loadConsent()`, `saveConsent()`, `clearConsent()`
- **Privacy signals**: `utils/detect-privacy-signals.ts` — DNT/GPC detection

### localStorage Schema
- Key: `pt-consent`
- Contains: `consentVersion`, `policyVersion`, `timestamp`, `region`, `categories`,
  `dnt`, `source`
- Source values: `'banner'` | `'settings-panel'`
- Invalid receipts are treated as no consent (graceful degradation)

### Consent Flow
1. Inline script reads localStorage → validates → sets `window.__PT_CONSENT_ANALYTICS`
2. If no valid consent → banner shows → user accepts/rejects → saved to localStorage
3. Analytics scripts check `window.__PT_CONSENT_ANALYTICS` before loading
4. DNT/GPC signals pre-disable analytics toggle when detected

### Version Upgrades
- Increment `EXPECTED_CONSENT_VERSION` in `validation/consent.ts`
- Old consent receipts fail validation → banner re-shows → user re-consents

### Backend API
- `POST /api/governance/consent` — record consent
- `POST /api/governance/consent/withdraw` — withdraw consent
- `GET /api/governance/consent` — list consent records
- `GET /api/governance/consent/history` — consent history
- Backend consent API lacks Cucumber BDD coverage (gap documented)

### E2E Tests
- Marketing: `apps/web/marketing/e2e/consent.spec.ts` (4 scenarios)
- App: `apps/web/app/e2e/specs/consent.spec.ts` (3 scenarios)

## Available Skills

Skills provide specialized instructions and workflows for specific tasks. Load with the `skill` tool when a task matches its description.

### Backend Platform

| Skill | Description |
|-------|-------------|
| `gradle` | Gradle build configuration and performance optimization |
| `hexagonal-architecture` | Hexagonal Architecture (Ports and Adapters) with CQRS patterns |
| `ddd-architecture` | DDD conformance — aggregate boundaries, identity-only inter-aggregate references, value-object immutability, bounded-context isolation, ADR-backed enforcement |
| `spring-boot` | Spring Boot 4 infrastructure layer patterns (Kotlin, WebFlux, R2DBC) |
| `spring-boot/actuator` | Production-grade monitoring and health probes with Actuator |
| `spring-boot/ai-mcp-server-patterns` | MCP servers with Spring AI for tool calling and function execution |
| `spring-boot/api-standards` | RESTful API design standards for reactive Spring Boot |
| `spring-boot/cache` | Caching strategies with Spring Cache abstraction |
| `spring-boot/data-neo4j-reactive` | Reactive Neo4j graph database integration |
| `spring-boot/messaging` | Event-driven patterns with reactive messaging |
| `spring-boot/openapi` | OpenAPI documentation with SpringDoc |
| `spring-boot/project-bootstrap` | Bootstrap new Spring Boot backend services |
| `spring-boot/resilience` | Fault-tolerance patterns: retries, circuit breakers, rate limiting |
| `spring-boot/saga-pattern` | Distributed transaction coordination with Saga pattern |
| `spring-boot/security` | Reactive authentication and authorization with Spring Security |
| `spring-boot/testing-core` | Fast unit tests for services and domain logic |
| `spring-boot/testing-integrations` | Integration tests with external dependencies |
| `spring-boot/testing-webflux` | Testing reactive HTTP endpoints and controllers |
| `docker-expert` | Docker containerization, multi-stage builds, image optimization |

### Design Patterns

| Pattern | Intent |
|---------|--------|
| `abstract-factory` | Create families of related objects without coupling to concrete classes |
| `adapter` | Translate one interface into another expected by the client |
| `bridge` | Separate abstraction from implementation for independent evolution |
| `builder` | Construct complex objects step by step |
| `chain-of-responsibility` | Pass requests through a chain of handlers |
| `command` | Encapsulate requests as objects for queuing, logging, undo |
| `composite` | Represent part-whole hierarchies as trees |
| `decorator` | Add responsibilities dynamically by wrapping objects |
| `facade` | Simplified interface over a complex subsystem |
| `factory-method` | Define creation operations through abstraction |
| `flyweight` | Share immutable state to reduce memory usage |
| `iterator` | Traverse collections without exposing internal structure |
| `mediator` | Centralize complex communications between objects |
| `memento` | Capture and restore object state for undo operations |
| `observer` | Subscribe to and receive notifications of state changes |
| `prototype` | Clone objects without coupling to their classes |
| `proxy` | Control access to an object through a surrogate |
| `singleton` | Ensure a class has only one instance |
| `state` | Change behavior when internal state changes |
| `strategy` | Define a family of interchangeable algorithms |
| `template-method` | Define algorithm skeleton, defer steps to subclasses |
| `visitor` | Add operations to object structures without modifying them |

### Frontend Platform

| Skill | Description |
|-------|-------------|
| `accessibility` | Web accessibility audits following WCAG 2.1 guidelines |
| `animate-text` | Text animation effects catalog (typewriter, blur, reveal, stagger) |
| `astrolicious-astro` | Astro 6 static site generation and islands architecture |
| `best-practices` | Modern web security, compatibility, and code quality |
| `chrome-extensions` | Chrome Extension development with Manifest V3 |
| `core-web-vitals` | Optimize LCP, CLS, INP, and page experience metrics |
| `frontend-design` | High-quality web components avoiding generic AI aesthetics |
| `modern-web-guidance` | Modern web APIs: View Transitions, Container Queries, Popover API |
| `nothing-design` | Nothing-inspired design system with intentional minimalism |
| `performance` | Web performance optimization and load time reduction |
| `pinia` | Pinia state management for Vue 3 applications |
| `seo` | Search engine optimization and structured data |
| `shadcn-vue` | shadcn-vue component library integration and composition |
| `vue` | Vue 3 Composition API, composables, and reactive patterns |

### Languages & Typing

| Skill | Description |
|-------|-------------|
| `kotlin` | Kotlin language patterns, coroutines, and idioms |
| `typescript` | TypeScript best practices and type-safe patterns |
| `zod-4` | Zod 4 schema validation with breaking changes from v3 |

### Testing

| Skill | Description |
|-------|-------------|
| `playwright` | Comprehensive E2E testing with Playwright: planning, generation, healing |
| `playwright-best-practices` | Advanced Playwright patterns: POM, CI/CD, flaky test fixes, accessibility |
| `vitest` | Fast unit testing with Vitest, mocking, coverage, and fixtures |

### Tools

| Skill | Description |
|-------|-------------|
| `open-pencil` | Work with Figma .fig files: inspect, query, export, modify designs |
| `pinned-tag` | Fix unpinned GitHub Actions tags and pin dependencies to git versions |
| `pnpm` | pnpm package manager: workspaces, catalogs, patches, strict resolution |
