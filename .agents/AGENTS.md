# AGENTS.md

> AI agent instructions for the `profiletailors.com` monorepo.

## Project Identity

**Profile Tailors** — social media management platform (schedule, publish, analyze, engage,
collaborate).
The product name is **Profile Tailors**; `profiletailors.com` is the domain/org name only.

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

**All commands go through `just`** — run `just -l` to list everything.

### Setup

| Command              | Action                                                      |
|----------------------|-------------------------------------------------------------|
| `just setup`         | Full initial setup: .env → install → hooks → agentsync → codegraph |
| `just install`       | Install dependencies (`pnpm install --frozen-lockfile`)      |
| `just hooks-install` | Set up Lefthook git hooks                                  |

### Frontend Dev (run from repo root)

| Command                  | Action                          |
|--------------------------|---------------------------------|
| `just frontend-dev`      | Start both frontend dev servers |
| `just frontend-build`    | Build marketing site            |
| `just frontend-lint`     | Biome lint                      |
| `just frontend-format`   | Biome format                    |
| `just frontend-test`     | Vitest unit tests               |
| `just frontend-test-e2e` | Playwright E2E tests            |

### Backend Dev (run from repo root)

| Command                     | Action                             |
|-----------------------------|------------------------------------|
| `just backend-run`          | Start Spring Boot (dev profile)    |
| `just backend-build`        | Compile and package                |
| `just backend-test-fast`    | Fast tests (excludes BDD)          |
| `just backend-check`        | Detekt + tests                     |
| `just backend-bdd-fast`     | Fast BDD suite                     |
| `just backend-bdd-postgres` | Postgres BDD (requires `infra-up`) |

### Full Stack

| Command                | Action                                            |
|------------------------|---------------------------------------------------|
| `just serve`           | Start backend + frontend app in parallel          |
| `just serve --force`   | Kill existing servers, then start fresh            |
| `just kill-servers`    | Stop all dev servers (bootRun, Vite, GradleDaemon)|

### Infrastructure

| Command           | Action                    |
|-------------------|---------------------------|
| `just infra-up`   | Start Postgres + services |
| `just infra-down` | Stop containers           |

### CI (mandatory before push/PR)

| Command         | What it covers                                      |
|-----------------|-----------------------------------------------------|
| `just ci`       | Full CI: gitleaks, lint, tests, build, E2E          |
| `just ci-local` | Fast subset (no E2E, no Postgres BDD)               |
| `just ci-full`  | ci-local + Postgres BDD (requires `infra-up` first) |

## Backend Architecture (Hexagonal)

**Dependency rule:** `domain ← application ← infrastructure`

| Layer              | Can depend on         | Must NOT depend on                  |
|--------------------|-----------------------|-------------------------------------|
| **Domain**         | Nothing (pure Kotlin) | Application, Infrastructure, Spring |
| **Application**    | Domain                | Infrastructure, Spring stereotypes  |
| **Infrastructure** | Domain + Application  | —                                   |

> **Exception:** `ModuleMetadata` classes (or `package-info.java`) are allowed in any package (including `domain`) solely for Spring Modulith boundary and named interface definitions. These are intentionally exempted from architecture tests but must NOT contain business logic or other framework dependencies.

- Package convention: `com.profiletailors.smp.{context}.{layer}`
- CQRS naming: `GetXQuery`, `{Verb}XCommand`, `XHandler`, `R2dbcXRepository`
- Use `com.profiletailors.common.domain.Service` (not Spring `@Service`)
- Shared libs (`shared/common`, `shared/bus`, etc.) are cross-cutting, not feature slices

## Frontend Apps

### `apps/web/marketing` (Astro 6)

- Static-first, no SSR
- i18n: English (default) + Spanish (`/es/`)
- All user-facing strings in locale files (`src/i18n/`), never hardcoded
- Fonts: Space Grotesk (body), Space Mono (labels), Doto (hero only)
- Style: Nothing-inspired, monochrome, dark-first
- Package manager: pnpm, Node >= 22.12.0
- Linter: Biome

### `apps/web/app` (Vue 3 + shadcn-vue)

- SPA dashboard with Vue Router + Pinia
- shadcn-vue components via `npx shadcn-vue@latest add`
- E2E tests: `playwright -c e2e/playwright.scheduler.config.ts`
- Linter: Biome

### Shared Assets

- `shared/assets/web/*` is copied to `dist/` at build time via Vite plugin
- Import with `@shared/assets/` alias

## Environment Setup

1. Copy `.env.example` → `.env` and fill in values
2. Run `bin/setup-env.sh` (creates symlinks for subprojects)
3. `just install` to install all dependencies
4. `just hooks-install` to set up git hooks (Lefthook)

Or simply: `just setup` (does steps 1, 3, 4 + agentsync + codegraph in one shot).

## Backend .env Loading

The `bootRun` task reads the root `.env` file and exports vars to the JVM — works for both CLI and
IntelliJ.

## Test Tags (Pre-existing Exclusions)

These tags are excluded in CI due to known pre-existing failures:

| Tag        | Reason                                                     |
|------------|------------------------------------------------------------|
| `postgres` | Postgres/Testcontainers integration tests (not configured) |

## Design Spec

Read `docs/architecture/` (C4 models) before architectural decisions.
Read design specs in docs before UI work.

## Documentation Rules

All documentation MUST be:

- In English
- Files: lowercase `kebab-case.md` (except `README.md`)
- Structure: Overview → Changes → Usage → Troubleshooting → References

## Dependency Policy

- Check dependency scores with `socket-mcp_depscore` before adding new deps
- Keep marketing site lightweight — prefer Astro primitives over dependencies
- Use existing skills from `.agents/skills/` when available

## Skills

Repo-local skills in `.agents/skills/` cover: backend-platform, frontend-platform,
kotlin, typescript, vue, pinia, shadcn-vue, astro, pnpm, gradle, spring-boot,
testing (vitest, playwright), hexagonal-architecture, docker.

## Key Gotchas

- **Gradle wrapper detection:** Windows (CMD/PowerShell) uses `gradlew.bat`, POSIX uses `./gradlew`
- **Spanish copy:** Longer than English — never use fixed-width containers
- **Portless:** Both frontend apps use named local URLs (e.g., `https://profile-tailors.localhost`)
- **Shared assets plugin:** Copies `shared/assets/web/*` to `dist/` at build time
- **CI is mandatory:** Never push without `just ci` passing
- **Conventional commits:** `feat(scope):`, `fix(scope):`, `docs(scope):`, `chore(scope):`
