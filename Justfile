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
# ────────────────────────────────────────────────────────────────

set positional-arguments := true

# ——— Paths ———————————————————————————————————————————————————
frontend-dir   := "apps/web/marketing"
# Auto-detect Gradle wrapper: `gradlew.bat` on Windows (CMD/PowerShell), `./gradlew` otherwise
gradle-root    := `if [ -n "${COMSPEC:-}" ] && [ -z "${MSYSTEM:-}" ]; then echo "gradlew.bat"; else echo "./gradlew"; fi`
docker-compose := "docker compose"

# ═══════════════════════════════════════════════════════════════
# SETUP
# ═══════════════════════════════════════════════════════════════

# Install all project dependencies (frozen lockfile)
install:
    cd {{frontend-dir}} && pnpm install --frozen-lockfile

# Full initial setup: .env → install → git hooks
setup:
    cp -n .env.example .env 2>/dev/null || true
    just install
    just hooks-install

# Install Lefthook git hooks
hooks-install:
    npx lefthook install

# ═══════════════════════════════════════════════════════════════
# FRONTEND  (pnpm / Astro / Biome / Vitest / Playwright)
# ═══════════════════════════════════════════════════════════════

# Start frontend dev server (portless)
frontend-dev:
    cd {{frontend-dir}} && pnpm dev

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

# ═══════════════════════════════════════════════════════════════
# BACKEND  (Gradle / Kotlin / Spring Boot / Detekt)
# ═══════════════════════════════════════════════════════════════

# Compile and package backend
backend-build:
    {{gradle-root}} :server:smp:build --no-daemon

# Run backend unit tests (optionally exclude tags: just backend-test 'modularity,postgres')
backend-test exclude-tags="":
    {{gradle-root}} :server:smp:test --no-daemon {{ if exclude-tags != "" { "-PexcludeTags=" + exclude-tags } else { "" } }}

# Run backend tests (fast: excludes modularity + postgres)
backend-test-fast:
    {{gradle-root}} :server:smp:test --no-daemon -PexcludeTags=modularity,postgres

# Run full check: tests + Detekt
backend-check:
    {{gradle-root}} :server:smp:check --no-daemon

# Run Detekt static analysis
backend-lint:
    {{gradle-root}} :server:smp:detekt --no-daemon

# Run Detekt across all shared modules
backend-lint-shared:
    {{gradle-root}} detekt --no-daemon 2>/dev/null || echo "No detekt config in shared"

# Start Spring Boot dev server (dev profile)
backend-run:
    {{gradle-root}} :server:smp:bootRun --args='--spring.profiles.active=dev'

# Run tests with JaCoCo coverage report
backend-coverage:
    {{gradle-root}} :server:smp:test :server:smp:jacocoTestReport --no-daemon -PexcludeTags=modularity,postgres

# Run fast BDD suite
backend-bdd-fast:
    {{gradle-root}} :server:smp:bddFastTest --no-daemon -x :shared:common:test -x :shared:spring-boot-common:test

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

# Run all fast checks locally — simulates CI pipeline
# Order: security → frontend (lint, test, build) → backend (lint, test, build)
# NOTE: Postgres BDD tests require 'just infra-up' first — not included here
ci-local:
    @echo "══════════════════════════════════════════════"
    @echo "  CI Pipeline Simulation"
    @echo "══════════════════════════════════════════════"
    @echo ""
    @echo "▸ Gitleaks (secrets scan)..."
    gitleaks protect --staged --redact --exit-code 1 --config .gitleaks.toml
    @echo ""
    @echo "▸ Frontend: Biome lint..."
    cd {{frontend-dir}} && pnpm lint
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
    {{gradle-root}} :server:smp:test --no-daemon -PexcludeTags=modularity,postgres
    @echo ""
    @echo "▸ Backend: build..."
    {{gradle-root}} :server:smp:build --no-daemon
    @echo ""
    @echo "══════════════════════════════════════════════"
    @echo "  ✅ CI Pipeline Simulation Complete"
    @echo "══════════════════════════════════════════════"

# Run CI checks including Postgres BDD tests (requires 'just infra-up')
ci-full: infra-up
    just ci-local
    @echo ""
    @echo "▸ Backend: Postgres BDD suite..."
    {{gradle-root}} :server:smp:bddPostgresTest --no-daemon -x :shared:common:test -x :shared:spring-boot-common:test
    @echo ""
    @echo "══════════════════════════════════════════════"
    @echo "  ✅ Full CI Suite Complete (incl. Postgres)"
    @echo "══════════════════════════════════════════════"

# ═══════════════════════════════════════════════════════════════
# CLEANUP
# ═══════════════════════════════════════════════════════════════

# Clean all build artifacts and caches
clean:
    rm -rf {{frontend-dir}}/dist
    rm -rf {{frontend-dir}}/coverage
    {{gradle-root}} clean --no-daemon 2>/dev/null || true
    rm -rf .gradle/build-cache
