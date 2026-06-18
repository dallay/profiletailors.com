# Profile Tailors

<div align="center">

![Profile Tailors Logo](shared/assets/og.svg)

**Schedule smarter. Post everywhere.**

Public-facing marketing site for the Profile Tailors social media management platform.

[![License](https://img.shields.io/github/license/dallay/profiletailors.com?style=for-the-badge&color=2d3748)](LICENSE)
[![CI](https://img.shields.io/github/actions/workflow/status/dallay/profiletailors.com/release-please.yml?style=for-the-badge&color=2d3748&label=CI)](https://github.com/dallay/profiletailors.com/actions)
[![Astro](https://img.shields.io/badge/Astro-6.3.3-2d3748?style=for-the-badge&logo=astro&logoColor=ffffff)](https://astro.build)
[![Node.js](https://img.shields.io/badge/Node.js-22.12%2B-2d3748?style=for-the-badge&logo=node.js&logoColor=5fa04e)](https://nodejs.org)
[![pnpm](https://img.shields.io/badge/pnpm-10.x-2d3748?style=for-the-badge&logo=pnpm&logoColor=f69220)](https://pnpm.io)

</div>

---

## Overview

**Profile Tailors** is a social media management platform for scheduling, publishing, analyzing,
engaging, and collaborating across multiple platforms.

This repository contains the **marketing site** and the **backend service** (in early development).
The frontend is a lightweight, static-first Astro site at `apps/web/marketing/`.

### What ships in this repo today

- **English landing page** at `/`
- **Spanish landing page** at `/es/`
- **Client-side waitlist flow** for early access
- **Nothing-inspired, monochrome, typographically driven design system** — dark-first with
  equal-rigor light mode
- **Shared brand assets** served from `shared/assets/`
- **Backend service** in `server/smp/` (Spring Boot, Kotlin, work in progress)

> The product name is **Profile Tailors**. `profiletailors.com` is the repository/domain name.

---

## Tech Stack

| Category          | Technology                                    |
|-------------------|-----------------------------------------------|
| Frontend          | Astro 6, Tailwind CSS v4 + @theme, TypeScript |
| Backend           | Spring Boot 4, Kotlin, WebFlux (experimental) |
| Rendering model   | Static-first, no SSR                          |
| i18n              | Astro i18n routing (`en`, `es`)               |
| Icons             | `@dallay/astro-icon`, `@iconify-json/lucide`  |
| Linting           | Biome                                         |
| Package manager   | pnpm                                          |
| Workspace tooling | Bazel, Lefthook                               |
| CI/CD             | GitHub Actions, Release Please                |

---

## Repository Layout

```text
profiletailors.com/
├── apps/
│   └── web/
│       └── marketing/            # Active Astro marketing site
│           ├── public/           # Static files served as-is
│           ├── src/
│           │   ├── components/   # UI building blocks
│           │   ├── i18n/         # EN/ES locale content
│           │   ├── layouts/      # Shared page shells
│           │   ├── pages/        # Routes (`/` and `/es/`)
│           │   ├── scripts/      # Client-side behavior
│           │   └── styles/       # Global styles and tokens
│           ├── astro.config.mjs
│           └── package.json
├── server/
│   └── smp/                     # Backend service (Spring Boot, Kotlin, work in progress)
│       ├── src/                 # Kotlin source
│       ├── build.gradle.kts
│       └── compose.yaml
├── shared/
│   └── assets/                  # Shared logos, icons, and web assets
├── .agents/                      # Agent tooling config and skills
├── .github/workflows/            # CI and automation
├── docs/security/               # Security documentation
├── tmp/                          # Research notes and temporary planning docs
├── openspec/                    # SDD (Spec-Driven Development) artifacts
├── CONTRIBUTING.md
├── CLA.md
├── LICENSE
└── README.md
```

---

## Getting Started

### Prerequisites

| Requirement    | Version               | Install                                    |
|----------------|-----------------------|--------------------------------------------|
| Node.js        | `>= 22.12.0`          | [nodejs.org](https://nodejs.org)           |
| pnpm           | `>= 10`               | `npm install -g pnpm`                      |
| just           | `>= 1.30`             | `brew install just` / `winget install Casey.Just` / `cargo install just` |

> **Windows users:** `just` runs natively on Windows. The Gradle wrapper is auto-detected
> (`gradlew.bat` in CMD/PowerShell, `./gradlew` in Git Bash/WSL). Recipes that use `rm -rf`
> still require a POSIX shell — use **Git Bash** (included with [Git for Windows](https://git-scm.com))
> or **WSL**.

### Install and run locally

```bash
git clone https://github.com/dallay/profiletailors.com.git
cd profiletailors.com
just install       # installs all dependencies (frontend + backend)
just frontend-dev  # starts the Astro dev server
```

The site will be available at [http://localhost:4321](http://localhost:4321).

### Command Hub

This repo uses [`just`](https://github.com/casey/just) as a centralized command runner.
All common operations are available via `just <recipe>` — no need to remember pnpm, Gradle, or
Docker commands separately. Run `just -l` to list everything.

#### Frontend (Astro / pnpm)

| Command                     | What it does                        |
|-----------------------------|-------------------------------------|
| `just frontend-dev`         | Start the Astro dev server          |
| `just frontend-build`       | Build the production site           |
| `just frontend-preview`     | Preview the production build        |
| `just frontend-lint`        | Lint with Biome                     |
| `just frontend-format`      | Format code with Biome              |
| `just frontend-check`       | Run Astro type/content checks       |
| `just frontend-test`        | Run unit tests (Vitest)             |
| `just frontend-test-cov`    | Run unit tests with coverage        |
| `just frontend-test-e2e`    | Run E2E tests (Playwright headless) |

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
| `just setup`          | Full initial setup (.env + install + hooks) |
| `just hooks-install`  | Install Lefthook git hooks            |
| `just clean`          | Clean all build artifacts and caches  |

---

## Development Notes

- The active frontend app is `apps/web/marketing/`.
- The site uses Astro's built-in locale routing with **English as the default locale** and **Spanish
  under `/es/`**.
- User-facing copy is maintained in locale files under `apps/web/marketing/src/i18n/`.
- Shared web assets are sourced from `shared/assets/` and exposed by the app config during
  development and build.
- The current waitlist flow is **client-side only**.
- Code quality: **Biome** for linting and formatting in the frontend.
- The backend lives in `server/smp/` — Spring Boot 4 with Kotlin and WebFlux (experimental, not
  deployed).
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