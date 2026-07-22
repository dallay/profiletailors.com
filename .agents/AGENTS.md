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
