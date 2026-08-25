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
# Recipes are wired through cross-platform Node.js helpers where shell features differ.
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
set windows-shell := ["pwsh", "-NoLogo", "-Command"]

# ——— Paths ———————————————————————————————————————————————————
frontend-dir   := "apps/web/marketing"
app-dir        := "apps/web/app"
admin-dir      := "apps/web/admin"
# Auto-detect Gradle wrapper: `gradlew.bat` on Windows (CMD/PowerShell), `./gradlew` otherwise
gradle-root    := if os_family() == "windows" { "gradlew.bat" } else { "./gradlew" }
docker-compose := "docker compose"
production-compose := "infra/apps/smp/production/compose.yaml"
production-env := "infra/apps/smp/production/.env"
swarm-stack := "infra/apps/smp/swarm/stack.yaml"
swarm-env := "infra/apps/smp/swarm/.env"

# ═══════════════════════════════════════════════════════════════
# SETUP
# ═══════════════════════════════════════════════════════════════

# Install all workspace dependencies (frozen lockfile)
install:
    pnpm install --frozen-lockfile

# Full initial setup: .env → install → git hooks → agentsync → codegraph
setup:
    node -e "const fs=require('fs');if(!fs.existsSync('.env')&&fs.existsSync('.env.example'))fs.copyFileSync('.env.example','.env')"
    just install
    just hooks-install
    pnpm dlx @dallay/agentsync apply
    node scripts/setup-optional-tools.mjs

# Install Lefthook git hooks unless globally disabled
hooks-install:
    node scripts/hooks-install.mjs

# Verify worktree identity, runtime environment, and dynamic port allocation
worktree-check:
    node --test scripts/worktree-isolation.test.mjs

# Print the derived worktree namespace and local URLs
worktree-info:
    node scripts/worktree-context.mjs --json

# ═══════════════════════════════════════════════════════════════
# FRONTEND  (pnpm / Astro / Biome / Vitest / Playwright)
# ═══════════════════════════════════════════════════════════════

# Start both frontend dev servers in parallel (portless)
dev-frontend $force="":
    node scripts/dev-frontend.mjs "{{force}}"

# Start only the Vue 3 app (dashboard SPA)
app:
    node scripts/frontend-run.mjs app

# Start the platform admin SPA
admin:
    node scripts/frontend-run.mjs admin

# Build frontend for production
frontend-build:
    cd {{frontend-dir}} && pnpm build

# Build the dashboard SPA for production
app-build:
    cd {{app-dir}} && pnpm build

# Build the platform admin SPA for production
admin-build:
    cd {{admin-dir}} && pnpm build

# Build the dashboard SPA against a deployed API
release-dashboard-build api_base_url:
    cd {{app-dir}} && VITE_API_BASE_URL='{{api_base_url}}' pnpm build

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

# Run platform admin unit tests (Vitest)
admin-test:
    cd {{admin-dir}} && pnpm test:run

# Run platform admin type check
admin-check:
    cd {{admin-dir}} && pnpm type-check

# Run frontend unit tests with coverage (--coverage)
frontend-test-cov *flags="":
    cd {{frontend-dir}} && pnpm test:coverage {{flags}}

# Run E2E tests (Playwright headless)
frontend-test-e2e:
    cd {{frontend-dir}} && pnpm test:e2e
    pnpm --filter app test:e2e:media:mocked

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

# Build a local OCI backend image from the current source tree
backend-image image_name="profiletailors/smp:local" version="0.0.1-SNAPSHOT":
    BP_OCI_AUTHORS="Dallay" BP_OCI_CREATED="$(date -u +%Y-%m-%dT%H:%M:%SZ)" BP_OCI_DESCRIPTION="Backend service for the Profile Tailors social media management platform." BP_OCI_DOCUMENTATION="https://github.com/dallay/profiletailors.com/tree/main/server/smp" BP_OCI_LICENSES="AGPL-3.0-only" BP_OCI_REVISION="$(git rev-parse HEAD)" BP_OCI_SOURCE="https://github.com/dallay/profiletailors.com" BP_OCI_TITLE="Profile Tailors SMP" BP_OCI_URL="https://profiletailors.com" BP_OCI_VENDOR="Dallay" BP_OCI_VERSION="{{version}}" {{gradle-root}} :server:smp:bootBuildImage -PreleaseVersion="{{version}}" --imageName="{{image_name}}" --no-daemon

# Verify production startup, Liquibase migrations, and health with ephemeral infrastructure
release-backend-verify image_name:
    node scripts/run-shell-script.mjs scripts/verify-release-image.sh "{{image_name}}"

# Build a revision-tagged OCI backend image with Spring Boot buildpacks
release-backend-image version="0.1.0" image_repository="profiletailors/smp":
    node scripts/release-backend-image.mjs "{{version}}" "{{image_repository}}"

# Run backend unit tests (optionally exclude tags: just backend-test 'postgres')
# Postgres integration tests use Testcontainers and require SMP_DB_TEST_PASSWORD,
# which is sourced from .env (or the shell) — same shape as the CI workflow.
backend-test exclude-tags="":
    node scripts/with-db-password-gradle.mjs :server:smp:test --no-daemon {{ if exclude-tags != "" { "-PexcludeTags=" + exclude-tags } else { "" } }}

# Run backend tests (fast)
backend-test-fast:
    node scripts/with-db-password-gradle.mjs :server:smp:test --no-daemon

# Run full check: tests + Detekt (aligns with CI — excludes BDD suites)
backend-check:
    node scripts/with-db-password-gradle.mjs :server:smp:check --no-daemon -x :server:smp:bddFastTest -x :server:smp:bddPostgresTest

# Run Detekt static analysis
backend-lint:
    {{gradle-root}} :server:smp:detekt --no-daemon

# Run Detekt across all shared modules
backend-lint-shared:
    {{gradle-root}} detekt --no-daemon 2>/dev/null || echo "No detekt config in shared"

# Start Spring Boot dev server (dev profile)
backend-run:
    node scripts/gradle-run.mjs :server:smp:bootRun --args=--spring.profiles.active=dev

# ═══════════════════════════════════════════════════════════════
# SERVE  (Backend + Frontend App)
# ═══════════════════════════════════════════════════════════════

# Start backend + frontend app in parallel
serve $force="":
    node scripts/serve-dev.mjs "{{force}}"

# Restart backend + frontend app, killing previous dev servers first
serve-force:
    just serve --force

# Kill only the dev processes owned by this worktree
kill-servers:
    node scripts/kill-servers.mjs

# Run tests with JaCoCo coverage report
backend-coverage:
    node scripts/with-db-password-gradle.mjs :server:smp:test :server:smp:jacocoTestReport --no-daemon

# Run fast BDD suite
backend-bdd-fast:
    node scripts/with-db-password-gradle.mjs :server:smp:bddFastTest --no-daemon -x :shared:common:test -x :shared:spring-boot-common:test

# Run PostgreSQL integration tests with Testcontainers
backend-test-postgres:
    node scripts/with-db-password-gradle.mjs :server:smp:postgresIntegrationTest --no-daemon -x :shared:common:test -x :shared:spring-boot-common:test

# Run Postgres BDD suite (requires infra-up first)
backend-bdd-postgres:
    node scripts/with-db-password-gradle.mjs :server:smp:bddPostgresTest --no-daemon -x :shared:common:test -x :shared:spring-boot-common:test

# ═══════════════════════════════════════════════════════════════
# INFRASTRUCTURE  (Docker Compose)
# ═══════════════════════════════════════════════════════════════

# Start infrastructure services (Postgres, etc.)
infra-up:
    node scripts/compose-run.mjs up -d

# Stop and remove infrastructure containers
infra-down:
    node scripts/compose-run.mjs down

# Tail infrastructure service logs (optionally filter by service name)
infra-logs *service="":
    node scripts/compose-run.mjs logs -f {{service}}

# Restart all infrastructure services
infra-restart:
    node scripts/compose-run.mjs restart

# Show host ports assigned to this worktree's infrastructure services
infra-info:
    node scripts/compose-run.mjs ports

# Generate local files required by the production Compose stack
production-prepare:
    node scripts/run-shell-script.mjs infra/apps/smp/production/prepare.sh

# Validate the fully resolved production Compose configuration
production-config:
    {{docker-compose}} --env-file {{production-env}} -f {{production-compose}} config --quiet

# Build the production dashboard image from source
production-build-dashboard:
    {{docker-compose}} --env-file {{production-env}} -f {{production-compose}} build dashboard

# Pull missing images and start the production Compose stack
production-up:
    {{docker-compose}} --env-file {{production-env}} -f {{production-compose}} up -d --wait

# Stop the production stack without deleting persistent data
production-down:
    {{docker-compose}} --env-file {{production-env}} -f {{production-compose}} down

# Show production stack status
production-status:
    {{docker-compose}} --env-file {{production-env}} -f {{production-compose}} ps

# Verify HTTP routing, migrations, production data, secrets, and container hardening
production-smoke *flags="":
    node scripts/run-shell-script.mjs infra/apps/smp/production/smoke-test.sh {{flags}}

# Tail production stack logs
production-logs *service="":
    {{docker-compose}} --env-file {{production-env}} -f {{production-compose}} logs -f {{service}}

# Generate local configuration and secret sources for Docker Swarm
swarm-prepare:
    node scripts/run-shell-script.mjs infra/apps/smp/swarm/prepare.sh

# Label the single node that owns PostgreSQL and local media data
swarm-label-storage node:
    docker node update --label-add profiletailors.storage=true "{{node}}"

# Validate the rendered Docker Swarm stack
swarm-config:
    node scripts/swarm-env-run.mjs config

# Create secrets, deploy the Swarm stack, and wait for readiness
swarm-deploy:
    node scripts/run-shell-script.mjs infra/apps/smp/swarm/deploy.sh

# Show services in the deployed Swarm stack
swarm-status:
    node scripts/swarm-env-run.mjs status

# Tail logs for one Swarm service, for example: just swarm-logs backend
swarm-logs service:
    node scripts/swarm-env-run.mjs logs "{{service}}"

# Roll back one application service, for example: just swarm-rollback backend
swarm-rollback service:
    node scripts/swarm-env-run.mjs rollback "{{service}}"

# Remove the Swarm stack while preserving secrets and persistent volumes
swarm-remove:
    node scripts/run-shell-script.mjs infra/apps/smp/swarm/remove.sh

# ═══════════════════════════════════════════════════════════════
# LICENCE COMPLIANCE
# ═══════════════════════════════════════════════════════════════

# Scan all dependency licences for AGPL-3.0 compatibility (frontend + backend)
licence-check:
    @echo "▸ Frontend: dependency licence scan..."
    pnpm licenses list --json | node scripts/check-frontend-licences.mjs
    @echo "▸ Backend: dependency licence report..."
    {{gradle-root}} :server:smp:generateLicenseReport --no-daemon
    @echo "  Report: server/smp/build/reports/dependency-licence/dependency-licence.txt"

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
    @echo "▸ Dependency licence scan..."
    just licence-check
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
    @echo "▸ App: production build..."
    cd {{app-dir}} && pnpm build
    @echo ""
    @echo "▸ Admin: Biome lint..."
    cd {{admin-dir}} && pnpm lint
    @echo ""
    @echo "▸ Admin: unit tests..."
    cd {{admin-dir}} && pnpm test:run
    @echo ""
    @echo "▸ Admin: production build..."
    cd {{admin-dir}} && pnpm build
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
    node scripts/with-db-password-gradle.mjs :server:smp:test --no-daemon
    @echo ""
    @echo "▸ Backend: build..."
    node scripts/with-db-password-gradle.mjs :server:smp:assemble --no-daemon
    @echo ""
    @echo "══════════════════════════════════════════════"
    @echo "  ✅ CI Pipeline Simulation Complete"
    @echo "══════════════════════════════════════════════"

# Run CI checks including Postgres integration + BDD tests (requires 'just infra-up')
ci-full: infra-up
    just ci-local
    @echo ""
    @echo "▸ Backend: Postgres integration suite..."
    node scripts/with-db-password-gradle.mjs :server:smp:postgresIntegrationTest --no-daemon -x :shared:common:test -x :shared:spring-boot-common:test
    @echo ""
    @echo "▸ Backend: Postgres BDD suite..."
    node scripts/with-db-password-gradle.mjs :server:smp:bddPostgresTest --no-daemon -x :shared:common:test -x :shared:spring-boot-common:test
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
    @echo "▸ [1b/8] Dependency licence scan..."
    just licence-check
    @echo ""
    @echo "▸ [2/8] Marketing: Biome lint..."
    cd {{frontend-dir}} && pnpm lint
    @echo ""
    @echo "▸ [3/8] App: Biome lint + unit tests..."
    cd {{app-dir}} && pnpm lint
    cd {{app-dir}} && pnpm test:run
    cd {{app-dir}} && pnpm build
    @echo ""
    @echo "▸ [3b/8] Admin: Biome lint + unit tests..."
    cd {{admin-dir}} && pnpm lint
    cd {{admin-dir}} && pnpm test:run
    cd {{admin-dir}} && pnpm build
    @echo ""
    @echo "▸ [4/8] Frontend: unit tests + coverage..."
    cd {{frontend-dir}} && pnpm test:coverage
    @echo ""
    @echo "▸ [5/8] Backend: Detekt static analysis..."
    {{gradle-root}} :server:smp:detekt --no-daemon
    @echo ""
    @echo "▸ [6/8] Backend: unit tests (fast)..."
    node scripts/with-db-password-gradle.mjs :server:smp:test --no-daemon
    @echo ""
    @echo "▸ [7/8] Backend: BDD fast suite..."
    node scripts/with-db-password-gradle.mjs :server:smp:bddFastTest --no-daemon -x :shared:common:test -x :shared:spring-boot-common:test
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
    node scripts/kill-servers.mjs
    node scripts/workspace-clean.mjs
