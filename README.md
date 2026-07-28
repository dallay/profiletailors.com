# Profile Tailors

<div align="center">

![Profile Tailors Logo](shared/assets/og.svg)

**Schedule smarter. Post everywhere.**

Public-facing marketing site for the Profile Tailors social media management platform.

</div>

[![License](https://img.shields.io/github/license/dallay/profiletailors.com?style=for-the-badge&color=2d3748)](LICENSE)
[![CI](https://img.shields.io/github/actions/workflow/status/dallay/profiletailors.com/release-please.yml?style=for-the-badge&color=2d3748&label=CI)](https://github.com/dallay/profiletailors.com/actions)
[![Astro](https://img.shields.io/badge/Astro-6.4.8-2d3748?style=for-the-badge&logo=astro&logoColor=ffffff)](https://astro.build)
[![Node.js](https://img.shields.io/badge/Node.js-22.12%2B-2d3748?style=for-the-badge&logo=node.js&logoColor=5fa04e)](https://nodejs.org)
[![pnpm](https://img.shields.io/badge/pnpm-11.x-2d3748?style=for-the-badge&logo=pnpm&logoColor=f69220)](https://pnpm.io)

[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=dallay_profiletailors.com&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=dallay_profiletailors.com)
[![Bugs](https://sonarcloud.io/api/project_badges/measure?project=dallay_profiletailors.com&metric=bugs)](https://sonarcloud.io/summary/new_code?id=dallay_profiletailors.com)
[![Code Smells](https://sonarcloud.io/api/project_badges/measure?project=dallay_profiletailors.com&metric=code_smells)](https://sonarcloud.io/summary/new_code?id=dallay_profiletailors.com)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=dallay_profiletailors.com&metric=coverage)](https://sonarcloud.io/summary/new_code?id=dallay_profiletailors.com)
[![Duplicated Lines (%)](https://sonarcloud.io/api/project_badges/measure?project=dallay_profiletailors.com&metric=duplicated_lines_density)](https://sonarcloud.io/summary/new_code?id=dallay_profiletailors.com)
[![Reliability Rating](https://sonarcloud.io/api/project_badges/measure?project=dallay_profiletailors.com&metric=reliability_rating)](https://sonarcloud.io/summary/new_code?id=dallay_profiletailors.com)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=dallay_profiletailors.com&metric=security_rating)](https://sonarcloud.io/summary/new_code?id=dallay_profiletailors.com)
[![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=dallay_profiletailors.com&metric=sqale_rating)](https://sonarcloud.io/summary/new_code?id=dallay_profiletailors.com)
[![codecov](https://codecov.io/gh/dallay/profiletailors.com/graph/badge.svg?token=B70EGR3KBD)](https://codecov.io/gh/dallay/profiletailors.com)

---

## Getting Started

### Prerequisites

| Requirement    | Version               | Install                                    |
|----------------|-----------------------|--------------------------------------------|
| Node.js        | `>= 22.12.0`          | [nodejs.org](https://nodejs.org)           |
| pnpm           | `>= 11.8.0`           | `npm install -g pnpm`                      |
| just           | `>= 1.30`             | `brew install just` / `winget install Casey.Just` / `cargo install just` |

> **Windows users:** `just` runs natively on Windows. The Gradle wrapper is auto-detected
> (`gradlew.bat` in CMD/PowerShell, `./gradlew` in Git Bash/WSL). Recipes that use `rm -rf`
> still require a POSIX shell — use **Git Bash** (included with [Git for Windows](https://git-scm.com))
> or **WSL**.

### Project Structure

```text
profiletailors.com/
├── apps/
│   └── web/
│       ├── app/                  # Vue 3 dashboard application
│       └── marketing/            # Astro marketing site
├── server/
│   └── smp/                     # Spring Boot 4 backend (Kotlin)
├── shared/                      # Kotlin libraries + shared web assets
│   ├── assets/                  # Shared logos, icons, and web assets
│   ├── common/                  # Domain primitives, value objects, shared kernel
│   ├── bus/                     # Event bus abstractions
│   ├── security/                # Security primitives
│   ├── web/                     # Shared web layer (consent, types, validation, utils)
│   └── ...                      # Additional shared libraries
├── tools/
│   └── compliance/              # Compliance tooling (pnpm workspace)
├── .agents/                      # Agent tooling config and skills
├── .devcontainer/                # VS Code dev container configuration
├── .github/workflows/            # CI and automation
├── docs/                        # Architecture and security docs
├── openspec/                    # SDD artifacts
├── CONTRIBUTING.md
├── CLA.md
├── LICENSE
└── README.md
```

### Install and run locally

#### 1) Install `just`

- **macOS:** `brew install just`
- **Windows:** `winget install Casey.Just`
- **Ubuntu/Debian (recommended):**

```bash
curl --proto '=https' --tlsv1.2 -sSf https://just.systems/install.sh | sudo bash -s -- --to /usr/local/bin
just --version
```

Alternative Ubuntu/Debian installation methods are also documented in the repository `Justfile`.

#### 2) Bootstrap the workspace

```bash
git clone https://github.com/dallay/profiletailors.com.git
cd profiletailors.com
just setup
```

For full onboarding and troubleshooting, see [docs/getting-started.md](docs/getting-started.md).

`just setup` will:

- copy `.env.example` to `.env` when needed,
- install workspace dependencies with `pnpm install --frozen-lockfile`,
- install Lefthook unless Git hooks are globally disabled (`core.hooksPath=/dev/null`, e.g. Jules).
- apply AI agent configurations with `pnpm dlx @dallay/agentsync apply`.

#### 3) Start local development

```bash
just dev-frontend  # starts both Astro and Vue dev servers
```

- Marketing site: [http://localhost:4321](http://localhost:4321)
- Dashboard app: [https://pt-app.localhost](https://pt-app.localhost) (requires [Portless](docs/portless-setup.md))

### Command Hub

This repo uses [`just`](https://github.com/casey/just) as a centralized command runner.
All common operations are available via `just <recipe>` — no need to remember pnpm, Gradle, or
Docker commands separately. Run `just -l` to list everything.

#### Frontend (Astro + Vue / pnpm)

| Command                     | What it does                               |
|-----------------------------|--------------------------------------------|
| `just dev-frontend`         | Start both dev servers in parallel         |
| `just app`                  | Start only the Vue 3 dashboard app         |
| `just frontend-build`       | Build the marketing site for production    |
| `just frontend-preview`     | Preview marketing production build         |
| `just frontend-lint`        | Lint marketing with Biome                  |
| `just frontend-format`      | Format marketing code with Biome           |
| `just frontend-check`       | Run Astro type/content checks              |
| `just frontend-test`        | Run marketing unit tests (Vitest)          |
| `just frontend-test-cov`    | Run marketing unit tests with coverage     |
| `just frontend-test-e2e`    | Run marketing E2E tests (Playwright)       |
| `just app-test-e2e-media`   | Run app Media Library E2E tests            |

#### Backend (Gradle / Kotlin / Spring Boot)

| Command                     | What it does                        |
|-----------------------------|-------------------------------------|
| `just backend-build`        | Compile and package                 |
| `just backend-run`          | Start Spring Boot (dev profile)     |
| `just backend-test-fast`    | Run unit tests (fast: no Postgres)  |
| `just backend-test`         | Run unit tests (pass exclude-tags)  |
| `just backend-lint`         | Run Detekt static analysis          |
| `just backend-check`        | Full check (tests + Detekt)         |
| `just backend-coverage`     | Tests with JaCoCo coverage report   |

#### Infrastructure (Docker)

| Command              | What it does                    |
|----------------------|---------------------------------|
| `just infra-up`      | Start Postgres + services       |
| `just infra-down`    | Stop and remove containers      |
| `just infra-logs`    | Tail service logs               |

#### CI Simulation

| Command             | What it does                                   |
|---------------------|------------------------------------------------|
| `just ci-local`     | Full CI pipeline simulation (fast, local-only) |
| `just ci-full`      | CI pipeline + Postgres BDD tests               |

#### Setup & Maintenance

| Command               | What it does                          |
|-----------------------|---------------------------------------|
| `just install`        | Install all dependencies              |
| `just setup`          | Full initial setup (.env + install + hooks + agentsync) |
| `just hooks-install`  | Install Lefthook git hooks            |
| `just clean`          | Clean all build artifacts and caches  |

---

## Development Notes

- **Frontend Apps**:
    - `apps/web/marketing/`: Astro-based marketing site.
    - `apps/web/app/`: Vue 3-based dashboard application.
- The marketing site uses Astro's built-in locale routing with **English as the default locale** and **Spanish under `/es/`**.
- User-facing copy is maintained in locale files under `apps/web/marketing/src/i18n/` and `apps/web/app/src/i18n/`.
- Shared web assets are sourced from `shared/assets/` using the `@shared/assets/` import alias. Files in `shared/assets/web/*` are copied into `dist/` at build time by the Astro build configuration.
- The current waitlist flow is **client-side only** (Astro component); backend persistence is documented as planned (ADR-0011).
- Code quality: **Biome** for linting and formatting in the frontend, **Detekt** for the backend.
- The backend lives in `server/smp/` — Spring Boot 4 with Kotlin and WebFlux (reactive).
- SDD artifacts live in `openspec/` for tracking specs, designs, and tasks.

---

## Architecture

Profile Tailors follows a **hexagonal architecture** with **bounded contexts** from Domain-Driven
Design. The backend is built as a **modular monolith** using Spring Boot 4, Kotlin, and reactive
programming.

**📐 [View C4 Architecture Models](docs/architecture/c4/)**

- **[System Context](docs/architecture/c4/01-system-context.md)** — Big picture, external
  dependencies
- **[Container](docs/architecture/c4/02-container.md)** — Deployable units, technology stack
- **[Component](docs/architecture/c4/03-component.md)** — Internal structure, bounded contexts
- **[Code](docs/architecture/c4/04-code.md)** — Implementation patterns, class design
- **[Summary](docs/architecture/c4/SUMMARY.md)** — Executive summary and roadmap

**Key Architectural Patterns**:

- Hexagonal Architecture (Ports & Adapters)
- Domain-Driven Design (Bounded Contexts)
- CQRS (Command Query Responsibility Segregation)
- Reactive Programming (Kotlin coroutines + R2DBC)
- Modular Monolith (Spring Modulith)

---

## Contributing

Contributions are welcome. Before opening a pull request:

1. Read [`CONTRIBUTING.md`](CONTRIBUTING.md).
2. Open an issue or discussion first for non-trivial changes.
3. Sign the [`CLA.md`](CLA.md) when prompted on your first PR.
4. Verify your changes locally from `apps/web/marketing/`.

We use [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/):

```text
feat(scope): short description
fix(scope): short description
docs(scope): short description
chore(scope): short description
```

Examples:

```text
feat(marketing): refine hero waitlist flow
fix(i18n): correct spanish locale switch label
docs(readme): refresh repository onboarding
```

---

## Security

If you discover a security issue, **do not open a public issue**.

Contact: **security@profiletailors.com**

---

## Support

- **Discussions:** https://github.com/dallay/profiletailors.com/discussions
- **Issues:** https://github.com/dallay/profiletailors.com/issues
- **Email:** **dev@profiletailors.com**

---

## License

This project is licensed under the **GNU Affero General Public License v3.0 (AGPL-3.0)**.

See [`LICENSE`](LICENSE) for the full text.

---

<div align="center">

Built by the **Profile Tailors** team.

</div>
