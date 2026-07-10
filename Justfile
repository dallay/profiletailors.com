# ────────────────────────────────────────────────────────────────
# Profile Tailors — Monorepo Command Hub
# ────────────────────────────────────────────────────────────────
# Usage:  just <recipe>          # run a recipe
#         just -l                # list all recipes
#
# One interface to rule them all:
#   Frontend (pnpm/Astro)  ·  Backend (Gradle/Kotlin)
#   Infrastructure (Docker)       ·  Git hooks (Lefthook)
#   CI simulation
#
# Cross-platform: just itself runs on macOS, Linux, and Windows.
# The Gradle wrapper is auto-detected: `gradlew.bat` on Windows CMD/PowerShell,
# `./gradlew` on POSIX shells (macOS, Linux, Git Bash, WSL).
# Recipes that use `rm -rf` still require a POSIX shell (Git Bash or WSL on Windows).
#
# Every recipe delegates to its tool's native commands —
# nothing is replaced, just coordinated.
#
# ────────────────────────────────────────────────────────────────
# PREREQUISITES
# ────────────────────────────────────────────────────────────────
# Before running any `just` command, ensure you have:
#
#   1. Node.js >= 22.12.0 (for pnpm)
#   2. pnpm (npm install -g pnpm)
#   3. Docker & Docker Compose (for infra)
#   4. just command runner
#
# ────────────────────────────────────────────────────────────────
# Install `just` on Ubuntu/Debian:
#
#   # Option A: Official install script (recommended)
#   curl --proto '=https' --tlsv1.2 -sSf https://just.systems/install.sh | sudo bash -s -- --to /usr/local/bin
#
#   # Option B: Via cargo (requires Rust)
#   cargo install just
#
#   # Option C: Via snap
#   sudo snap install just --classic
#
#   # Option D: Prebuilt binary
#   JUST_VERSION=$(curl -s https://api.github.com/repos/casey/just/releases/latest | grep tag_name | cut -d'"' -f4)
#   wget "https://github.com/casey/just/releases/download/${JUST_VERSION}/just-${JUST_VERSION}-x86_64-unknown-linux-musl.tar.gz"
#   tar -xzf just-*.tar.gz just && sudo mv just /usr/local/bin/
#
#   # Verify installation
#   just --version
#
# ────────────────────────────────────────────────────────────────
# Install `just` on macOS:
#
#   brew install just
#
# ────────────────────────────────────────────────────────────────
# First time setup:
#
#   just setup
#
# ────────────────────────────────────────────────────────────────

set positional-arguments := true

# ——— Paths ———————————————————————————————————————————————————
frontend-dir   := "apps/web/marketing"
app-dir        := "apps/web/app"
# Auto-detect Gradle wrapper: `gradlew.bat` on Windows (CMD/PowerShell), `./gradlew` otherwise
gradle-root    := `if [ -n "${COMSPEC:-}" ] && [ -z "${MSYSTEM:-}" ]; then echo "gradlew.bat"; else echo "./gradlew"; fi`
docker-compose := "docker compose"

# ═══════════════════════════════════════════════════════════════
# SETUP
# ═══════════════════════════════════════════════════════════════

# Install all workspace dependencies (frozen lockfile)
install:
    pnpm install --frozen-lockfile

# Full initial setup: .env → install → git hooks → agentsync → codegraph
setup:
    cp -n .env.example .env 2>/dev/null || true
    just install
    just hooks-install
    pnpm dlx @dallay/agentsync apply
    command -v portless >/dev/null 2>&1 || pnpm add -g portless
    command -v codegraph >/dev/null 2>&1 && codegraph init || echo "⚠️  codegraph not found — skipping index init"

# Install Lefthook git hooks unless globally disabled
hooks-install:
    @HOOKS_PATH="$$(git config --global core.hooksPath 2>/dev/null || true)"; \
    if [ "$$HOOKS_PATH" = "/dev/null" ]; then \
        echo "Skipping Lefthook install: core.hooksPath=/dev/null"; \
    else \
        pnpm exec lefthook install; \
    fi

# ═══════════════════════════════════════════════════════════════
# FRONTEND  (pnpm / Astro / Biome / Vitest / Playwright)
# ═══════════════════════════════════════════════════════════════

# Start both frontend dev servers in parallel (portless)
dev-frontend $force="":
    #!/usr/bin/env bash
    set -euo pipefail
    if [ "{{force}}" = "--force" ]; then
        echo "Killing frontend dev servers..."
        pkill -f "vite" || true
        sleep 0.5
    elif [ -n "{{force}}" ]; then
        echo "Unknown option: {{force}}"
        echo "Usage: just dev-frontend [--force]"
        exit 2
    fi
    echo "Starting frontend dev servers (marketing + app)..."
    pnpm --parallel --filter marketing --filter app dev

# Start only the Vue 3 app (dashboard SPA)
app:
    cd {{app-dir}} && pnpm dev

# Build frontend for production
frontend-build:
    cd {{frontend-dir}} && pnpm build

# Preview production build locally
frontend-preview:
    cd {{frontend-dir}} && pnpm preview

# Lint frontend with Biome
frontend-lint:
    cd {{frontend-dir}} && pnpm lint

# Format frontend code with Biome
frontend-format:
    cd {{frontend-dir}} && pnpm format

# Run Astro type check
frontend-check:
    cd {{frontend-dir}} && pnpm check

# Run frontend unit tests (Vitest)
frontend-test:
    cd {{frontend-dir}} && pnpm test

# Run frontend unit tests with coverage (--coverage)
frontend-test-cov *flags="":
    cd {{frontend-dir}} && pnpm test:coverage {{flags}}

# Run E2E tests (Playwright headless)
frontend-test-e2e:
    cd {{frontend-dir}} && pnpm test:e2e

# Run E2E tests in Playwright UI mode
frontend-test-e2e-ui:
    cd {{frontend-dir}} && pnpm test:e2e:ui

# Run E2E tests headed (visible browser)
frontend-test-e2e-headed:
    cd {{frontend-dir}} && pnpm test:e2e:headed

# Open Playwright HTML report
frontend-test-e2e-report:
    cd {{frontend-dir}} && pnpm test:e2e:report

# Run app Media Library mocked E2E tests (Playwright headless)
app-test-e2e-media-mocked:
    pnpm --filter app test:e2e:media:mocked

# Run app Media Library real-CAS smoke E2E tests (Playwright headless)
app-test-e2e-media-real:
    pnpm --filter app test:e2e:media:real

# Run available app Media Library E2E lanes (mocked + real)
app-test-e2e-media: app-test-e2e-media-mocked app-test-e2e-media-real

# ═══════════════════════════════════════════════════════════════
# BACKEND  (Gradle / Kotlin / Spring Boot / Detekt)
# ═══════════════════════════════════════════════════════════════

# Compile and package backend
backend-build:
    {{gradle-root}} :server:smp:build --no-daemon

# Run backend unit tests (optionally exclude tags: just backend-test 'modularity')
# Postgres integration tests use Testcontainers and require SMP_POSTGRES_TEST_PASSWORD,
# which is sourced from .env (or the shell) — same shape as the CI workflow.
backend-test exclude-tags="":
    export SMP_POSTGRES_TEST_PASSWORD=$(grep ^SMP_POSTGRES_TEST_PASSWORD= .env | cut -d= -f2-); \
    {{gradle-root}} :server:smp:test --no-daemon {{ if exclude-tags != "" { "-PexcludeTags=" + exclude-tags } else { "" } }}

# Run backend tests (fast: excludes modularity only)
backend-test-fast:
    export SMP_POSTGRES_TEST_PASSWORD=$(grep ^SMP_POSTGRES_TEST_PASSWORD= .env | cut -d= -f2-); \
    {{gradle-root}} :server:smp:test --no-daemon -PexcludeTags=modularity

# Run full check: tests + Detekt (aligns with CI — excludes modularity tag and BDD suites)
backend-check:
    export SMP_POSTGRES_TEST_PASSWORD=$(grep ^SMP_POSTGRES_TEST_PASSWORD= .env | cut -d= -f2-); \
    {{gradle-root}} :server:smp:check --no-daemon -PexcludeTags=modularity -x :server:smp:bddFastTest -x :server:smp:bddPostgresTest

# Run Detekt static analysis
backend-lint:
    {{gradle-root}} :server:smp:detekt --no-daemon

# Run Detekt across all shared modules
backend-lint-shared:
    {{gradle-root}} detekt --no-daemon 2>/dev/null || echo "No detekt config in shared"

# Start Spring Boot dev server (dev profile)
backend-run:
    {{gradle-root}} :server:smp:bootRun --args='--spring.profiles.active=dev'

# ═══════════════════════════════════════════════════════════════
# SERVE  (Backend + Frontend App)
# ═══════════════════════════════════════════════════════════════

# Start backend + frontend app in parallel
serve $force="":
    #!/usr/bin/env bash
    set -euo pipefail
    if [ "{{force}}" = "--force" ]; then
        just kill-servers
    elif [ -n "{{force}}" ]; then
        echo "Unknown option: {{force}}"
        echo "Usage: just serve [--force]"
        exit 2
    fi
    echo "Starting backend (Spring Boot) + frontend app (Vite)..."
    {{gradle-root}} :server:smp:bootRun --args='--spring.profiles.active=dev' &
    cd {{app-dir}} && pnpm dev

# Restart backend + frontend app, killing previous dev servers first
serve-force:
    just serve --force

# Kill running dev servers (backend, frontend, Gradle daemons)
kill-servers:
    #!/usr/bin/env bash
    echo "Stopping dev servers..."
    pkill -f "bootRun" || true
    pkill -f "vite" || true
    pkill -f "GradleDaemon" || true
    echo "✓ Servers stopped"

# Run tests with JaCoCo coverage report
backend-coverage:
    export SMP_POSTGRES_TEST_PASSWORD=$(grep ^SMP_POSTGRES_TEST_PASSWORD= .env | cut -d= -f2-); \
    {{gradle-root}} :server:smp:test :server:smp:jacocoTestReport --no-daemon -PexcludeTags=modularity

# Run fast BDD suite
backend-bdd-fast:
    export SMP_POSTGRES_TEST_PASSWORD=$(grep ^SMP_POSTGRES_PASSWORD= .env | cut -d= -f2) && {{gradle-root}} :server:smp:bddFastTest --no-daemon -x :shared:common:test -x :shared:spring-boot-common:test

# Run PostgreSQL integration tests with Testcontainers
backend-test-postgres:
    {{gradle-root}} :server:smp:postgresIntegrationTest --no-daemon -x :shared:common:test -x :shared:spring-boot-common:test

# Run Postgres BDD suite (requires infra-up first)
backend-bdd-postgres:
    {{gradle-root}} :server:smp:bddPostgresTest --no-daemon -x :shared:common:test -x :shared:spring-boot-common:test

# ═══════════════════════════════════════════════════════════════
# INFRASTRUCTURE  (Docker Compose)
# ═══════════════════════════════════════════════════════════════

# Start infrastructure services (Postgres, etc.)
infra-up:
    {{docker-compose}} up -d

# Stop and remove infrastructure containers
infra-down:
    {{docker-compose}} down

# Tail infrastructure service logs (optionally filter by service name)
infra-logs *service="":
    {{docker-compose}} logs -f {{service}}

# Restart all infrastructure services
infra-restart:
    {{docker-compose}} restart

# ═══════════════════════════════════════════════════════════════
# CI / VALIDATION
# ═══════════════════════════════════════════════════════════════

# Fast CI checks — lint, unit tests, builds (no E2E, no Postgres BDD)
ci-local:
    @echo "══════════════════════════════════════════════"
    @echo "  CI Pipeline Simulation"
    @echo "══════════════════════════════════════════════"
    @echo ""
    @echo "▸ Gitleaks (secrets scan)..."
    gitleaks protect --staged --redact --exit-code 1 --config .gitleaks.toml
    @echo ""
    @echo "▸ Marketing: Biome lint..."
    cd {{frontend-dir}} && pnpm lint
    @echo ""
    @echo "▸ App: Biome lint..."
    cd {{app-dir}} && pnpm lint
    @echo ""
    @echo "▸ App: unit tests..."
    cd {{app-dir}} && pnpm test:run
    @echo ""
    @echo "▸ Frontend: unit tests + coverage..."
    cd {{frontend-dir}} && pnpm test:coverage
    @echo ""
    @echo "▸ Frontend: build..."
    cd {{frontend-dir}} && pnpm build
    @echo ""
    @echo "▸ Backend: Detekt static analysis..."
    {{gradle-root}} :server:smp:detekt --no-daemon
    @echo ""
    @echo "▸ Backend: unit tests (fast)..."
    export SMP_POSTGRES_TEST_PASSWORD=$(grep ^SMP_POSTGRES_TEST_PASSWORD= .env | cut -d= -f2-); \
    {{gradle-root}} :server:smp:test --no-daemon -PexcludeTags=modularity
    @echo ""
    @echo "▸ Backend: build..."
    export SMP_POSTGRES_TEST_PASSWORD=$(grep ^SMP_POSTGRES_TEST_PASSWORD= .env | cut -d= -f2-); \
    {{gradle-root}} :server:smp:build --no-daemon -PexcludeTags=modularity -x :server:smp:bddFastTest -x :server:smp:bddPostgresTest
    @echo ""
    @echo "══════════════════════════════════════════════"
    @echo "  ✅ CI Pipeline Simulation Complete"
    @echo "══════════════════════════════════════════════"

# Run CI checks including Postgres integration + BDD tests (requires 'just infra-up')
ci-full: infra-up
    just ci-local
    @echo ""
    @echo "▸ Backend: Postgres integration suite..."
    {{gradle-root}} :server:smp:postgresIntegrationTest --no-daemon -x :shared:common:test -x :shared:spring-boot-common:test
    @echo ""
    @echo "▸ Backend: Postgres BDD suite..."
    {{gradle-root}} :server:smp:bddPostgresTest --no-daemon -x :shared:common:test -x :shared:spring-boot-common:test
    @echo ""
    @echo "══════════════════════════════════════════════"
    @echo "  ✅ Full CI Suite Complete (incl. Postgres)"
    @echo "══════════════════════════════════════════════"

# Full CI pipeline — all checks, all tests (unit + E2E + BDD-fast), all builds
ci:
    @echo "════════════════════════════════════════════════"
    @echo "  🚀 Full CI Pipeline"
    @echo "════════════════════════════════════════════════"
    @echo ""
    @echo "▸ [1/8] Gitleaks (secrets scan)..."
    gitleaks protect --staged --redact --exit-code 1 --config .gitleaks.toml
    @echo ""
    @echo "▸ [2/8] Marketing: Biome lint..."
    cd {{frontend-dir}} && pnpm lint
    @echo ""
    @echo "▸ [3/8] App: Biome lint + unit tests..."
    cd {{app-dir}} && pnpm lint
    cd {{app-dir}} && pnpm test:run
    @echo ""
    @echo "▸ [4/8] Frontend: unit tests + coverage..."
    cd {{frontend-dir}} && pnpm test:coverage
    @echo ""
    @echo "▸ [5/8] Backend: Detekt static analysis..."
    {{gradle-root}} :server:smp:detekt --no-daemon
    @echo ""
    @echo "▸ [6/8] Backend: unit tests (fast)..."
    export SMP_POSTGRES_TEST_PASSWORD=$(grep ^SMP_POSTGRES_TEST_PASSWORD= .env | cut -d= -f2-); \
    {{gradle-root}} :server:smp:test --no-daemon -PexcludeTags=modularity
    @echo ""
    @echo "▸ [7/8] Backend: BDD fast suite..."
    export SMP_POSTGRES_TEST_PASSWORD=$(grep ^SMP_POSTGRES_PASSWORD= .env | cut -d= -f2) && {{gradle-root}} :server:smp:bddFastTest --no-daemon -x :shared:common:test -x :shared:spring-boot-common:test
    @echo ""
    @echo "▸ [8/8] Frontend: E2E tests (Playwright, all browsers)..."
    cd {{frontend-dir}} && pnpm test:e2e
    @echo ""
    @echo "════════════════════════════════════════════════"
    @echo "  ✅ Full CI Pipeline Complete — everything passed"
    @echo "════════════════════════════════════════════════"

# ═══════════════════════════════════════════════════════════════
# CLEANUP
# ═══════════════════════════════════════════════════════════════

# Clean all build artifacts and caches
clean:
    rm -rf {{frontend-dir}}/dist
    rm -rf {{frontend-dir}}/coverage
    {{gradle-root}} clean --no-daemon 2>/dev/null || true
    rm -rf .gradle/build-cache
