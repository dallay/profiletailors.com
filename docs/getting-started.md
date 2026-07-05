# Getting Started — Local Development Environment

**Last Updated:** 2026-06-21
**Status:** Active

## Overview

This guide walks new contributors through setting up the Profile Tailors monorepo locally.
It covers prerequisites, bootstrapping, and verifying that everything works before opening a PR.

The repo uses [`just`](https://github.com/casey/just) as a centralized command runner.
All operations — frontend, backend, infrastructure, CI — live in the `Justfile`.
Run `just -l` to see every available recipe.

## Prerequisites

| Requirement | Version      | Install                                                                |
|-------------|--------------|------------------------------------------------------------------------|
| Java        | `>= 21`      | [sdkman.io](https://sdkman.io) or [adoptium.net](https://adoptium.net) |
| Node.js     | `>= 22.12.0` | [nodejs.org](https://nodejs.org)                                       |
| pnpm        | `>= 11`      | `npm install -g pnpm`                                                  |
| just        | `>= 1.30`    | See below                                                              |
| Docker      | latest       | [docs.docker.com](https://docs.docker.com)                             |

> **Windows users:** `just` runs natively on Windows. The Gradle wrapper is auto-detected
> (`gradlew.bat` in CMD/PowerShell, `./gradlew` in Git Bash/WSL). Recipes that use `rm -rf`
> still require a POSIX shell — use **Git Bash** (included
> with [Git for Windows](https://git-scm.com))
> or **WSL**.

## Install `just`

### macOS

```bash
brew install just
just --version
```

### Windows

```bash
winget install Casey.Just
just --version
```

### Ubuntu / Debian

```bash
# Official install script (recommended)
curl --proto '=https' --tlsv1.2 -sSf https://just.systems/install.sh | sudo bash -s -- --to /usr/local/bin

# Verify
just --version
```

#### Alternative installation methods

If the install script does not work on your distro, try these alternatives:

```bash
# Option A: cargo (requires Rust)
cargo install just

# Option B: snap
sudo snap install just --classic

# Option C: prebuilt binary
JUST_VERSION=$(curl -s https://api.github.com/repos/casey/just/releases/latest \
  | grep tag_name | cut -d'"' -f4)
wget "https://github.com/casey/just/releases/download/${JUST_VERSION}/just-${JUST_VERSION}-x86_64-unknown-linux-musl.tar.gz"
tar -xzf just-*.tar.gz just && sudo mv just /usr/local/bin/
```

## Bootstrap the workspace

After cloning the repo, run a single command:

```bash
git clone https://github.com/dallay/profiletailors.com.git
cd profiletailors.com
just setup
```

`just setup` performs four steps automatically:

1. **`.env` bootstrap** — copies `.env.example` to `.env` (skipped if `.env` already exists).
2. **Dependency installation** — runs `pnpm install --frozen-lockfile` to install all workspace
   dependencies (frontend + backend).
3. **Git hooks** — runs `just hooks-install` to install Lefthook (skipped if Git hooks are globally
   disabled, e.g. `core.hooksPath=/dev/null` in Jules or CI environments).
4. **AI agents** — runs `pnpm dlx @dallay/agentsync apply` to synchronize AI agent configurations
   and instructions.

If hooks are not installed, CI will catch any issues — so there is no risk in skipping them locally.

## Verify the setup

### Frontend (Marketing + App)

```bash
just frontend-dev
```

This starts both the Astro marketing site and the Vue 3 dashboard application in parallel.

- **Marketing site:** [https://profile-tailors.localhost](https://profile-tailors.localhost)
- **Web app dashboard:** [https://pt-app.localhost](https://pt-app.localhost)

Run the full frontend CI subset:

```bash
just ci-local
```

This runs Biome lint, Vitest unit tests, and a production build.

### Backend (Spring Boot / Kotlin)

```bash
just backend-run
```

The server starts in dev profile. API endpoints will be available at `http://localhost:7638` (configured via `SMP_BACKEND_PORT`).

Run fast tests:

```bash
just backend-test-fast
```

### Infrastructure (Docker)

Start Postgres for BDD integration tests:

```bash
just infra-up
just backend-bdd-postgres
just infra-down
```

## Command Reference

For a complete list of all recipes, run:

```bash
just -l
```

Key recipes:

| Command                  | Description                                     |
|--------------------------|-------------------------------------------------|
| `just setup`             | Full bootstrap: .env + deps + hooks + agentsync |
| `just frontend-dev`      | Start marketing (Astro) + app (Vue) dev servers |
| `just frontend-test`     | Run Vitest unit tests                           |
| `just frontend-test-e2e` | Run Playwright E2E tests                        |
| `just backend-run`       | Start Spring Boot (dev profile)                 |
| `just backend-test-fast` | Fast unit tests (no Postgres)                   |
| `just infra-up`          | Start Docker services                           |
| `just ci`                | Full CI pipeline (lint + tests + E2E)           |
| `just ci-local`          | Fast CI subset (no E2E, no Postgres)            |
| `just clean`             | Clean build artifacts                           |

## Troubleshooting

### pnpm install --frozen-lockfile fails with "Unsupported engine"

Node.js version is too old. Upgrade to `>= 22.12.0`:

```bash
nvm install 22
nvm use 22
```

### Port already in use

The Astro dev server defaults to port `4321`. If it is taken:

```bash
# Kill the process using the port
lsof -ti:4321 | xargs kill -9
```

### Gradle build is slow or fails

Ensure you have Java 21+:

```bash
java -version  # should be 21 or later
```

If using SDKMAN or jenv, verify the active version matches.

### Lefthook hooks not installed

Check if Git hooks are globally disabled:

```bash
git config --global core.hooksPath
```

If it prints `/dev/null`, hooks are intentionally disabled — this is fine for local development.
CI still runs the equivalent checks via `just ci`.

To reinstall hooks manually:

```bash
just hooks-install
```

## Next Steps

- Read the [Architecture Overview](architecture/) to understand the hexagonal structure.
- Explore the [API Versioning guide](api-versioning.md) for backend conventions.
- Set up [Portless](portless-setup.md) for named local HTTPS URLs.

## References

- [just — command runner](https://github.com/casey/just)
- [Astro](https://astro.build)
- [pnpm](https://pnpm.io)
- [Spring Boot 4](https://spring.io/projects/spring-boot)
- [Lefthook](https://github.com/evilmartians/lefthook)
